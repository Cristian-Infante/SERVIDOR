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
 * Implementación JDBC del repositorio de mensajes.
 *
 * <p>Esquema esperado:</p>
 * <pre>
 * CREATE TABLE mensajes (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   tipo VARCHAR(20) NOT NULL,
 *   contenido TEXT,
 *   ruta_archivo VARCHAR(255),
 *   mime VARCHAR(120),
 *   duracion_seg INT,
 *   emisor BIGINT,
 *   receptor BIGINT,
 *   canal_id BIGINT,
 *   timestamp TIMESTAMP NOT NULL
 * );
 * </pre>
 */
public class JdbcMensajeRepository extends BaseJdbcRepository implements MensajeRepository {

    public JdbcMensajeRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Mensaje save(Mensaje mensaje) {
        final String sql = "INSERT INTO mensajes (tipo, contenido, ruta_archivo, mime, duracion_seg, emisor, receptor, canal_id, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mensaje.getTipo());
            if (mensaje instanceof TextoMensaje texto) {
                ps.setString(2, texto.getContenido());
                ps.setString(3, null);
                ps.setString(4, null);
                ps.setObject(5, null);
            } else if (mensaje instanceof AudioMensaje audio) {
                ps.setString(2, null);
                ps.setString(3, audio.getRutaArchivo());
                ps.setString(4, audio.getMime());
                ps.setInt(5, audio.getDuracionSeg());
            } else if (mensaje instanceof ArchivoMensaje archivo) {
                ps.setString(2, null);
                ps.setString(3, archivo.getRutaArchivo());
                ps.setString(4, archivo.getMime());
                ps.setObject(5, null);
            } else {
                ps.setString(2, null);
                ps.setString(3, null);
                ps.setString(4, null);
                ps.setObject(5, null);
            }
            ps.setObject(6, mensaje.getEmisor());
            ps.setObject(7, mensaje.getReceptor());
            ps.setObject(8, mensaje.getCanalId());
            ps.setTimestamp(9, Timestamp.valueOf(mensaje.getTimeStamp() != null ? mensaje.getTimeStamp() : LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    mensaje.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error guardando mensaje", e);
        }
        return mensaje;
    }

    @Override
    public List<Mensaje> findTextAudioLogs() {
        final String sql = "SELECT * FROM mensajes WHERE tipo IN ('TEXTO', 'AUDIO') ORDER BY timestamp DESC";
        return executeQuery(sql);
    }

    @Override
    public List<Mensaje> findByCanal(Long canalId) {
        final String sql = "SELECT * FROM mensajes WHERE canal_id=? ORDER BY timestamp";
        return executeQuery(sql, canalId);
    }

    @Override
    public List<Mensaje> findBetweenUsers(Long emisor, Long receptor) {
        final String sql = "SELECT * FROM mensajes WHERE (emisor=? AND receptor=?) OR (emisor=? AND receptor=?) ORDER BY timestamp";
        return executeQuery(sql, emisor, receptor, receptor, emisor);
    }

    private List<Mensaje> executeQuery(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Mensaje> mensajes = new ArrayList<>();
                while (rs.next()) {
                    mensajes.add(map(rs));
                }
                return mensajes;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error ejecutando consulta de mensajes", e);
        }
    }

    private Mensaje map(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        Mensaje mensaje;
        switch (tipo) {
            case "TEXTO" -> {
                TextoMensaje texto = new TextoMensaje();
                texto.setContenido(rs.getString("contenido"));
                mensaje = texto;
            }
            case "AUDIO" -> {
                AudioMensaje audio = new AudioMensaje();
                audio.setRutaArchivo(rs.getString("ruta_archivo"));
                audio.setMime(rs.getString("mime"));
                audio.setDuracionSeg(rs.getInt("duracion_seg"));
                mensaje = audio;
            }
            case "ARCHIVO" -> {
                ArchivoMensaje archivo = new ArchivoMensaje();
                archivo.setRutaArchivo(rs.getString("ruta_archivo"));
                archivo.setMime(rs.getString("mime"));
                mensaje = archivo;
            }
            default -> throw new IllegalArgumentException("Tipo de mensaje desconocido: " + tipo);
        }
        mensaje.setId(rs.getLong("id"));
        mensaje.setTipo(tipo);
        Timestamp timestamp = rs.getTimestamp("timestamp");
        if (timestamp != null) {
            mensaje.setTimeStamp(timestamp.toLocalDateTime());
        }
        mensaje.setEmisor((Long) rs.getObject("emisor"));
        mensaje.setReceptor((Long) rs.getObject("receptor"));
        mensaje.setCanalId((Long) rs.getObject("canal_id"));
        return mensaje;
    }
}
