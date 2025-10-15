package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.CanalRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC de {@link CanalRepository}.
 *
 * <p>Esquema sugerido:</p>
 * <pre>
 * CREATE TABLE canales (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   nombre VARCHAR(120) UNIQUE NOT NULL,
 *   privado TINYINT(1) NOT NULL
 * );
 *
 * CREATE TABLE canal_clientes (
 *   canal_id BIGINT NOT NULL,
 *   cliente_id BIGINT NOT NULL,
 *   PRIMARY KEY (canal_id, cliente_id)
 * );
 * </pre>
 */
public class JdbcCanalRepository extends BaseJdbcRepository implements CanalRepository {

    public JdbcCanalRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Canal save(Canal canal) {
        if (canal.getId() == null) {
            return insert(canal);
        }
        return update(canal);
    }

    private Canal insert(Canal canal) {
        final String sql = "INSERT INTO canales (nombre, privado) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, canal.getNombre());
            ps.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    canal.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error insertando canal", e);
        }
        return canal;
    }

    private Canal update(Canal canal) {
        final String sql = "UPDATE canales SET nombre=?, privado=? WHERE id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, canal.getNombre());
            ps.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            ps.setLong(3, canal.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error actualizando canal", e);
        }
        return canal;
    }

    @Override
    public Optional<Canal> findById(Long id) {
        final String sql = "SELECT id, nombre, privado FROM canales WHERE id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error buscando canal", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Canal> findAll() {
        final String sql = "SELECT id, nombre, privado FROM canales";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Canal> canales = new ArrayList<>();
            while (rs.next()) {
                canales.add(map(rs));
            }
            return canales;
        } catch (SQLException e) {
            throw new IllegalStateException("Error listando canales", e);
        }
    }

    @Override
    public List<Cliente> findUsers(Long canalId) {
        final String sql = "SELECT c.id, c.usuario, c.email, c.contrasenia, c.foto, c.ip, c.estado FROM clientes c " +
                "JOIN canal_clientes cc ON c.id = cc.cliente_id WHERE cc.canal_id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Cliente> clientes = new ArrayList<>();
                while (rs.next()) {
                    Cliente cliente = new Cliente();
                    cliente.setId(rs.getLong("id"));
                    cliente.setNombreDeUsuario(rs.getString("usuario"));
                    cliente.setEmail(rs.getString("email"));
                    cliente.setContrasenia(rs.getString("contrasenia"));
                    cliente.setFoto(rs.getBytes("foto"));
                    cliente.setIp(rs.getString("ip"));
                    cliente.setEstado(rs.getBoolean("estado"));
                    clientes.add(cliente);
                }
                return clientes;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listando usuarios del canal", e);
        }
    }

    @Override
    public void linkUser(Long canalId, Long clienteId) {
        final String sql = "INSERT IGNORE INTO canal_clientes (canal_id, cliente_id) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error vinculando usuario al canal", e);
        }
    }

    @Override
    public void unlinkUser(Long canalId, Long clienteId) {
        final String sql = "DELETE FROM canal_clientes WHERE canal_id=? AND cliente_id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error desvinculando usuario del canal", e);
        }
    }

    private Canal map(ResultSet rs) throws SQLException {
        Canal canal = new Canal();
        canal.setId(rs.getLong("id"));
        canal.setNombre(rs.getString("nombre"));
        canal.setPrivado(rs.getBoolean("privado"));
        return canal;
    }
}
