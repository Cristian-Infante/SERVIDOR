package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;

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
 * Implementación JDBC para los registros de auditoría.
 *
 * <pre>
 * CREATE TABLE logs (
 *     id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *     tipo TINYINT(1) NOT NULL,
 *     detalle TEXT NOT NULL,
 *     fecha DATETIME NOT NULL
 * );
 * </pre>
 */
public class JdbcLogRepository implements LogRepository {

    private final DataSource dataSource;

    public JdbcLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(Log log) {
        final String sql = "INSERT INTO logs (tipo, detalle, fecha) VALUES (?,?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setBoolean(1, Boolean.TRUE.equals(log.getTipo()));
            statement.setString(2, log.getDetalle());
            statement.setTimestamp(3, Timestamp.valueOf(log.getFechaHora() != null ? log.getFechaHora() : LocalDateTime.now()));
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar log", e);
        }
    }

    @Override
    public List<Log> findAll() {
        final String sql = "SELECT * FROM logs ORDER BY fecha DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Log> logs = new ArrayList<>();
            while (rs.next()) {
                Log log = new Log();
                log.setId(rs.getLong("id"));
                log.setTipo(rs.getBoolean("tipo"));
                log.setDetalle(rs.getString("detalle"));
                log.setFechaHora(rs.getTimestamp("fecha").toLocalDateTime());
                logs.add(log);
            }
            return logs;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al consultar logs", e);
        }
    }
}
