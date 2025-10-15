package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC del repositorio de logs.
 *
 * <p>Esquema esperado:</p>
 * <pre>
 * CREATE TABLE logs (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   tipo TINYINT(1) NOT NULL,
 *   detalle TEXT NOT NULL,
 *   fecha_hora TIMESTAMP NOT NULL
 * );
 * </pre>
 */
public class JdbcLogRepository extends BaseJdbcRepository implements LogRepository {

    public JdbcLogRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void append(Log log) {
        final String sql = "INSERT INTO logs (tipo, detalle, fecha_hora) VALUES (?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, Boolean.TRUE.equals(log.getTipo()));
            ps.setString(2, log.getDetalle());
            ps.setTimestamp(3, Timestamp.valueOf(log.getFechaHora() != null ? log.getFechaHora() : LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error guardando log", e);
        }
    }

    @Override
    public List<Log> findAll() {
        final String sql = "SELECT id, tipo, detalle, fecha_hora FROM logs ORDER BY fecha_hora DESC";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Log> logs = new ArrayList<>();
            while (rs.next()) {
                Log log = new Log();
                log.setId(rs.getLong("id"));
                log.setTipo(rs.getBoolean("tipo"));
                log.setDetalle(rs.getString("detalle"));
                Timestamp timestamp = rs.getTimestamp("fecha_hora");
                if (timestamp != null) {
                    log.setFechaHora(timestamp.toLocalDateTime());
                }
                logs.add(log);
            }
            return logs;
        } catch (SQLException e) {
            throw new IllegalStateException("Error listando logs", e);
        }
    }
}
