package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.MensajeFactory;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.MensajeRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC para la tabla de mensajes.
 *
 * <pre>
 * CREATE TABLE mensajes (
 *     id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *     tipo VARCHAR(20) NOT NULL,
 *     timestamp DATETIME NOT NULL,
 *     emisor_id BIGINT,
 *     receptor_id BIGINT,
 *     canal_id BIGINT,
 *     contenido TEXT,
 *     ruta VARCHAR(255),
 *     mime VARCHAR(120),
 *     duracion_seg INT
 * );
 * </pre>
 */
public class JdbcMensajeRepository implements MensajeRepository {

    private final DataSource dataSource;

    public JdbcMensajeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Mensaje save(Mensaje mensaje) {
        if (mensaje.getId() == null) {
            return insert(mensaje);
        }
        return update(mensaje);
    }

    private Mensaje insert(Mensaje mensaje) {
        final String sql = "INSERT INTO mensajes (tipo, timestamp, emisor_id, receptor_id, canal_id, contenido, ruta, mime, duracion_seg) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, mensaje);
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    mensaje.setId(rs.getLong(1));
                }
            }
            return mensaje;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar mensaje", e);
        }
    }

    private Mensaje update(Mensaje mensaje) {
        final String sql = "UPDATE mensajes SET tipo=?, timestamp=?, emisor_id=?, receptor_id=?, canal_id=?, contenido=?, ruta=?, mime=?, duracion_seg=? WHERE id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, mensaje);
            statement.setLong(10, mensaje.getId());
            statement.executeUpdate();
            return mensaje;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al actualizar mensaje", e);
        }
    }

    private void bind(PreparedStatement statement, Mensaje mensaje) throws SQLException {
        statement.setString(1, mensaje.getTipo());
        statement.setTimestamp(2, Timestamp.valueOf(mensaje.getTimeStamp() != null ? mensaje.getTimeStamp() : LocalDateTime.now()));
        if (mensaje.getEmisor() != null) {
            statement.setLong(3, mensaje.getEmisor());
        } else {
            statement.setNull(3, java.sql.Types.BIGINT);
        }
        if (mensaje.getReceptor() != null) {
            statement.setLong(4, mensaje.getReceptor());
        } else {
            statement.setNull(4, java.sql.Types.BIGINT);
        }
        if (mensaje.getCanalId() != null) {
            statement.setLong(5, mensaje.getCanalId());
        } else {
            statement.setNull(5, java.sql.Types.BIGINT);
        }
        if (mensaje instanceof TextoMensaje texto) {
            statement.setString(6, texto.getContenido());
            statement.setNull(7, java.sql.Types.VARCHAR);
            statement.setNull(8, java.sql.Types.VARCHAR);
            statement.setNull(9, java.sql.Types.INTEGER);
        } else if (mensaje instanceof AudioMensaje audio) {
            statement.setNull(6, java.sql.Types.VARCHAR);
            statement.setString(7, audio.getRutaArchivo());
            statement.setString(8, audio.getMime());
            statement.setInt(9, audio.getDuracionSeg());
        } else if (mensaje instanceof ArchivoMensaje archivo) {
            statement.setNull(6, java.sql.Types.VARCHAR);
            statement.setString(7, archivo.getRutaArchivo());
            statement.setString(8, archivo.getMime());
            statement.setNull(9, java.sql.Types.INTEGER);
        } else {
            statement.setNull(6, java.sql.Types.VARCHAR);
            statement.setNull(7, java.sql.Types.VARCHAR);
            statement.setNull(8, java.sql.Types.VARCHAR);
            statement.setNull(9, java.sql.Types.INTEGER);
        }
    }

    @Override
    public List<Mensaje> findTextAudioLogs() {
        final String sql = "SELECT * FROM mensajes WHERE tipo IN ('TEXTO','AUDIO') ORDER BY timestamp DESC";
        return queryList(sql);
    }

    @Override
    public List<Mensaje> findByCanal(Long canalId) {
        final String sql = "SELECT * FROM mensajes WHERE canal_id=? ORDER BY timestamp DESC";
        return queryList(sql, canalId);
    }

    @Override
    public List<Mensaje> findBetweenUsers(Long emisor, Long receptor) {
        final String sql = "SELECT * FROM mensajes WHERE (emisor_id=? AND receptor_id=?) OR (emisor_id=? AND receptor_id=?) ORDER BY timestamp";
        return queryList(sql, emisor, receptor, receptor, emisor);
    }

    private List<Mensaje> queryList(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Mensaje> mensajes = new ArrayList<>();
                while (rs.next()) {
                    mensajes.add(mapMensaje(rs));
                }
                return mensajes;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al ejecutar consulta de mensajes", e);
        }
    }

    private Mensaje mapMensaje(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
        Long id = rs.getLong("id");
        Long emisor = rs.getObject("emisor_id", Long.class);
        Long receptor = rs.getObject("receptor_id", Long.class);
        Long canal = rs.getObject("canal_id", Long.class);
        Mensaje mensaje;
        switch (tipo) {
            case "TEXTO" -> {
                String contenido = rs.getString("contenido");
                TextoMensaje textoMensaje = MensajeFactory.crearMensajeTexto(contenido, emisor, receptor, canal);
                mensaje = textoMensaje;
            }
            case "AUDIO" -> {
                String ruta = rs.getString("ruta");
                String mime = rs.getString("mime");
                int duracion = rs.getInt("duracion_seg");
                AudioMensaje audioMensaje = MensajeFactory.crearMensajeAudio(ruta, duracion, mime, emisor, receptor, canal);
                mensaje = audioMensaje;
            }
            case "ARCHIVO" -> {
                String ruta = rs.getString("ruta");
                String mime = rs.getString("mime");
                ArchivoMensaje archivoMensaje = MensajeFactory.crearMensajeArchivo(ruta, mime, emisor, receptor, canal);
                mensaje = archivoMensaje;
            }
            default -> throw new IllegalStateException("Tipo de mensaje no soportado: " + tipo);
        }
        mensaje.setId(id);
        mensaje.setTimeStamp(timestamp);
        return mensaje;
    }
}
