package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.MensajeRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Expected schema fragment:
 * <pre>
 * CREATE TABLE mensajes (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   timestamp DATETIME NOT NULL,
 *   tipo VARCHAR(32) NOT NULL,
 *   emisor_id BIGINT NOT NULL,
 *   receptor_id BIGINT,
 *   canal_id BIGINT,
 *   contenido TEXT,
 *   ruta_archivo VARCHAR(400),
 *   mime VARCHAR(120),
 *   duracion_seg INT
 * );
 * </pre>
 */
public class JdbcMensajeRepository extends JdbcSupport implements MensajeRepository {

    public JdbcMensajeRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Mensaje save(Mensaje mensaje) {
        if (mensaje.getId() == null) {
            return insert(mensaje);
        }
        return update(mensaje);
    }

    private Mensaje insert(Mensaje mensaje) {
        String sql = "INSERT INTO mensajes(timestamp, tipo, emisor_id, receptor_id, canal_id, contenido, ruta_archivo, mime, duracion_seg, transcripcion) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindCommonFields(mensaje, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    mensaje.setId(rs.getLong(1));
                }
            }
            return mensaje;
        } catch (SQLException e) {
            throw new IllegalStateException("Error inserting message", e);
        }
    }

    private Mensaje update(Mensaje mensaje) {
        String sql = "UPDATE mensajes SET timestamp=?, tipo=?, emisor_id=?, receptor_id=?, canal_id=?, contenido=?, ruta_archivo=?, mime=?, duracion_seg=?, transcripcion=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindCommonFields(mensaje, ps);
            ps.setLong(11, mensaje.getId());
            ps.executeUpdate();
            return mensaje;
        } catch (SQLException e) {
            throw new IllegalStateException("Error updating message", e);
        }
    }

    private void bindCommonFields(Mensaje mensaje, PreparedStatement ps) throws SQLException {
        ps.setTimestamp(1, Timestamp.valueOf(mensaje.getTimeStamp()));
        ps.setString(2, mensaje.getTipo());
        ps.setLong(3, mensaje.getEmisor());
        setNullableLong(ps, 4, mensaje.getReceptor());
        setNullableLong(ps, 5, mensaje.getCanalId());
        if (mensaje instanceof TextoMensaje texto) {
            ps.setString(6, texto.getContenido());
            ps.setNull(7, Types.VARCHAR);
            ps.setNull(8, Types.VARCHAR);
            ps.setNull(9, Types.INTEGER);
            ps.setNull(10, Types.VARCHAR);
        } else if (mensaje instanceof AudioMensaje audio) {
            ps.setNull(6, Types.VARCHAR);
            ps.setString(7, audio.getRutaArchivo());
            ps.setString(8, audio.getMime());
            ps.setInt(9, audio.getDuracionSeg());
            ps.setString(10, audio.getTranscripcion());
        } else if (mensaje instanceof ArchivoMensaje archivo) {
            ps.setNull(6, Types.VARCHAR);
            ps.setString(7, archivo.getRutaArchivo());
            ps.setString(8, archivo.getMime());
            ps.setNull(9, Types.INTEGER);
            ps.setNull(10, Types.VARCHAR);
        } else {
            ps.setNull(6, Types.VARCHAR);
            ps.setNull(7, Types.VARCHAR);
            ps.setNull(8, Types.VARCHAR);
            ps.setNull(9, Types.INTEGER);
            ps.setNull(10, Types.VARCHAR);
        }
    }

    @Override
    public List<Mensaje> findTextAudioLogs() {
        String sql = "SELECT * FROM mensajes WHERE tipo IN ('TEXTO', 'AUDIO') ORDER BY timestamp";
        return queryMessages(sql);
    }

    @Override
    public List<Mensaje> findByCanal(Long canalId) {
        String sql = "SELECT * FROM mensajes WHERE canal_id=? ORDER BY timestamp";
        return queryMessages(sql, canalId);
    }

    @Override
    public List<Mensaje> findBetweenUsers(Long emisor, Long receptor) {
        String sql = "SELECT * FROM mensajes WHERE (emisor_id=? AND receptor_id=?) OR (emisor_id=? AND receptor_id=?) ORDER BY timestamp";
        return queryMessages(sql, emisor, receptor, receptor, emisor);
    }
    
    @Override
    public List<Mensaje> findAllByUser(Long usuarioId) {
        String sql = "SELECT * FROM mensajes WHERE emisor_id=? OR receptor_id=? ORDER BY timestamp";
        return queryMessages(sql, usuarioId, usuarioId);
    }

    private List<Mensaje> queryMessages(String sql, Object... params) {
        List<Mensaje> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error querying messages", e);
        }
        return result;
    }

    private Mensaje map(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        Mensaje mensaje;
        switch (tipo) {
            case "AUDIO" -> {
                AudioMensaje audio = new AudioMensaje();
                audio.setRutaArchivo(rs.getString("ruta_archivo"));
                audio.setMime(rs.getString("mime"));
                audio.setDuracionSeg(rs.getInt("duracion_seg"));
                audio.setTranscripcion(rs.getString("transcripcion"));
                mensaje = audio;
            }
            case "ARCHIVO" -> {
                ArchivoMensaje archivo = new ArchivoMensaje();
                archivo.setRutaArchivo(rs.getString("ruta_archivo"));
                archivo.setMime(rs.getString("mime"));
                mensaje = archivo;
            }
            default -> {
                TextoMensaje texto = new TextoMensaje();
                texto.setContenido(rs.getString("contenido"));
                mensaje = texto;
            }
        }
        mensaje.setId(rs.getLong("id"));
        Timestamp ts = rs.getTimestamp("timestamp");
        mensaje.setTimeStamp(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
        mensaje.setTipo(tipo);
        mensaje.setEmisor(rs.getLong("emisor_id"));
        mensaje.setReceptor(getNullableLong(rs, "receptor_id"));
        mensaje.setCanalId(getNullableLong(rs, "canal_id"));
        return mensaje;
    }
}
