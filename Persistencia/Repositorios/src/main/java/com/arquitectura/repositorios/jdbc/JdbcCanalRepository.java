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
 * Expected schema snippet:
 * <pre>
 * CREATE TABLE canales (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   nombre VARCHAR(160) NOT NULL,
 *   privado TINYINT(1) NOT NULL
 * );
 *
 * CREATE TABLE canal_clientes (
 *   canal_id BIGINT NOT NULL,
 *   cliente_id BIGINT NOT NULL,
 *   PRIMARY KEY(canal_id, cliente_id)
 * );
 * </pre>
 */
public class JdbcCanalRepository extends JdbcSupport implements CanalRepository {

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
        String sql = "INSERT INTO canales(nombre, privado) VALUES(?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, canal.getNombre());
            ps.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    canal.setId(rs.getLong(1));
                }
            }
            return canal;
        } catch (SQLException e) {
            throw new IllegalStateException("Error inserting channel", e);
        }
    }

    private Canal update(Canal canal) {
        String sql = "UPDATE canales SET nombre=?, privado=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canal.getNombre());
            ps.setBoolean(2, Boolean.TRUE.equals(canal.getPrivado()));
            ps.setLong(3, canal.getId());
            ps.executeUpdate();
            return canal;
        } catch (SQLException e) {
            throw new IllegalStateException("Error updating channel", e);
        }
    }

    @Override
    public Optional<Canal> findById(Long id) {
        String sql = "SELECT id, nombre, privado FROM canales WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Canal canal = new Canal();
                    canal.setId(rs.getLong("id"));
                    canal.setNombre(rs.getString("nombre"));
                    canal.setPrivado(rs.getBoolean("privado"));
                    return Optional.of(canal);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error finding channel", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Canal> findAll() {
        String sql = "SELECT id, nombre, privado FROM canales";
        List<Canal> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Canal canal = new Canal();
                canal.setId(rs.getLong("id"));
                canal.setNombre(rs.getString("nombre"));
                canal.setPrivado(rs.getBoolean("privado"));
                result.add(canal);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listing channels", e);
        }
        return result;
    }

    @Override
    public List<Cliente> findUsers(Long canalId) {
        String sql = "SELECT c.id, c.usuario, c.email, c.contrasenia, c.foto, c.ip, c.estado " +
                "FROM clientes c INNER JOIN canal_clientes cc ON c.id = cc.cliente_id WHERE cc.canal_id=?";
        List<Cliente> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente cliente = new Cliente();
                    cliente.setId(rs.getLong("id"));
                    cliente.setNombreDeUsuario(rs.getString("usuario"));
                    cliente.setEmail(rs.getString("email"));
                    cliente.setContrasenia(rs.getString("contrasenia"));
                    cliente.setFoto(rs.getBytes("foto"));
                    cliente.setIp(rs.getString("ip"));
                    cliente.setEstado(rs.getBoolean("estado"));
                    result.add(cliente);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listing channel users", e);
        }
        return result;
    }

    @Override
    public void linkUser(Long canalId, Long clienteId) {
        String sql = "INSERT IGNORE INTO canal_clientes(canal_id, cliente_id) VALUES(?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error linking user to channel", e);
        }
    }

    @Override
    public void unlinkUser(Long canalId, Long clienteId) {
        String sql = "DELETE FROM canal_clientes WHERE canal_id=? AND cliente_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error unlinking user from channel", e);
        }
    }
}
