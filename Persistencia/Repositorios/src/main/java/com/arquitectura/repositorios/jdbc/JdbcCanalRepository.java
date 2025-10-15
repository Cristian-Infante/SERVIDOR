package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.CanalRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC para {@link CanalRepository}.
 *
 * <p>Esquema sugerido:</p>
 * <pre>
 * CREATE TABLE canales (
 *     id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *     nombre VARCHAR(120) NOT NULL,
 *     privado TINYINT(1) DEFAULT 0
 * );
 *
 * CREATE TABLE canal_clientes (
 *     canal_id BIGINT NOT NULL,
 *     cliente_id BIGINT NOT NULL,
 *     PRIMARY KEY(canal_id, cliente_id)
 * );
 * </pre>
 */
public class JdbcCanalRepository implements CanalRepository {

    private final DataSource dataSource;

    public JdbcCanalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Canal save(Canal canal) {
        if (canal.getId() == null) {
            return insert(canal);
        }
        return update(canal);
    }

    private Canal insert(Canal canal) {
        final String sql = "INSERT INTO canales (nombre, privado) VALUES (?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, canal.getNombre());
            statement.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    canal.setId(rs.getLong(1));
                }
            }
            return canal;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar canal", e);
        }
    }

    private Canal update(Canal canal) {
        final String sql = "UPDATE canales SET nombre=?, privado=? WHERE id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, canal.getNombre());
            statement.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            statement.setLong(3, canal.getId());
            statement.executeUpdate();
            return canal;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al actualizar canal", e);
        }
    }

    @Override
    public Optional<Canal> findById(Long id) {
        final String sql = "SELECT * FROM canales WHERE id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCanal(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al buscar canal por id", e);
        }
    }

    @Override
    public List<Canal> findAll() {
        final String sql = "SELECT * FROM canales";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Canal> canales = new ArrayList<>();
            while (rs.next()) {
                canales.add(mapCanal(rs));
            }
            return canales;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar canales", e);
        }
    }

    @Override
    public List<Cliente> findUsers(Long canalId) {
        final String sql = "SELECT c.* FROM clientes c INNER JOIN canal_clientes cc ON cc.cliente_id=c.id WHERE cc.canal_id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, canalId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Cliente> clientes = new ArrayList<>();
                while (rs.next()) {
                    Cliente cliente = new Cliente();
                    cliente.setId(rs.getLong("id"));
                    cliente.setNombreDeUsuario(rs.getString("usuario"));
                    cliente.setEmail(rs.getString("email"));
                    cliente.setContrasenia(rs.getString("password_hash"));
                    cliente.setFoto(rs.getBytes("foto"));
                    cliente.setIp(rs.getString("ip"));
                    cliente.setEstado(rs.getBoolean("conectado"));
                    clientes.add(cliente);
                }
                return clientes;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar usuarios por canal", e);
        }
    }

    @Override
    public void linkUser(Long canalId, Long clienteId) {
        final String sql = "INSERT IGNORE INTO canal_clientes (canal_id, cliente_id) VALUES (?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, canalId);
            statement.setLong(2, clienteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al vincular usuario al canal", e);
        }
    }

    @Override
    public void unlinkUser(Long canalId, Long clienteId) {
        final String sql = "DELETE FROM canal_clientes WHERE canal_id=? AND cliente_id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, canalId);
            statement.setLong(2, clienteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al desvincular usuario del canal", e);
        }
    }

    private Canal mapCanal(ResultSet rs) throws SQLException {
        Canal canal = new Canal();
        canal.setId(rs.getLong("id"));
        canal.setNombre(rs.getString("nombre"));
        canal.setPrivado(rs.getBoolean("privado"));
        return canal;
    }
}
