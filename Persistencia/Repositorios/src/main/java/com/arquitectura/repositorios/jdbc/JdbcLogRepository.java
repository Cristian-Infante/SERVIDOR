package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Schema reference:
 * <pre>
 * CREATE TABLE logs (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   tipo TINYINT(1) NOT NULL,
 *   detalle TEXT NOT NULL,
 *   fecha_hora DATETIME NOT NULL
 * );
 * </pre>
 */
public class JdbcLogRepository extends JdbcSupport implements LogRepository {

    public JdbcLogRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void append(Log log) {
        String sql = "INSERT INTO logs(tipo, detalle, fecha_hora) VALUES(?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setBoolean(1, Boolean.TRUE.equals(log.getTipo()));
            ps.setString(2, log.getDetalle());
            ps.setTimestamp(3, Timestamp.valueOf(log.getFechaHora()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error inserting log", e);
        }
    }

    @Override
    public List<Log> findAll() {
        String sql = "SELECT id, tipo, detalle, fecha_hora FROM logs ORDER BY fecha_hora DESC";
        List<Log> logs = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Log log = new Log();
                log.setId(rs.getLong("id"));
                log.setTipo(rs.getBoolean("tipo"));
                log.setDetalle(rs.getString("detalle"));
                Timestamp ts = rs.getTimestamp("fecha_hora");
                log.setFechaHora(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
                logs.add(log);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error retrieving logs", e);
        }
        return logs;
    }
}
