package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC de {@link ClienteRepository}.
 *
 * <p>Esquema esperado:</p>
 * <pre>
 * CREATE TABLE clientes (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   usuario VARCHAR(100) UNIQUE NOT NULL,
 *   email VARCHAR(150) UNIQUE NOT NULL,
 *   contrasenia VARCHAR(256) NOT NULL,
 *   foto LONGBLOB,
 *   ip VARCHAR(45),
 *   estado TINYINT(1) DEFAULT 0
 * );
 * </pre>
 */
public class JdbcClienteRepository extends BaseJdbcRepository implements ClienteRepository {

    public JdbcClienteRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Cliente save(Cliente cliente) {
        if (cliente.getId() == null) {
            return insert(cliente);
        }
        return update(cliente);
    }

    private Cliente insert(Cliente cliente) {
        final String sql = "INSERT INTO clientes (usuario, email, contrasenia, foto, ip, estado) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cliente.getNombreDeUsuario());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getContrasenia());
            ps.setBytes(4, cliente.getFoto());
            ps.setString(5, cliente.getIp());
            ps.setBoolean(6, Boolean.TRUE.equals(cliente.getEstado()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cliente.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error insertando cliente", e);
        }
        return cliente;
    }

    private Cliente update(Cliente cliente) {
        final String sql = "UPDATE clientes SET usuario=?, email=?, contrasenia=?, foto=?, ip=?, estado=? WHERE id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cliente.getNombreDeUsuario());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getContrasenia());
            ps.setBytes(4, cliente.getFoto());
            ps.setString(5, cliente.getIp());
            ps.setBoolean(6, Boolean.TRUE.equals(cliente.getEstado()));
            ps.setLong(7, cliente.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error actualizando cliente", e);
        }
        return cliente;
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        final String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error buscando cliente", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Cliente> findByEmail(String email) {
        final String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE email=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error buscando por email", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Cliente> findConnected() {
        final String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE estado = 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Cliente> clientes = new ArrayList<>();
            while (rs.next()) {
                clientes.add(map(rs));
            }
            return clientes;
        } catch (SQLException e) {
            throw new IllegalStateException("Error listando conectados", e);
        }
    }

    @Override
    public void setConnected(Long id, boolean connected) {
        final String sql = "UPDATE clientes SET estado=? WHERE id=?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, connected);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error actualizando estado", e);
        }
    }

    @Override
    public List<Cliente> all() {
        final String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Cliente> clientes = new ArrayList<>();
            while (rs.next()) {
                clientes.add(map(rs));
            }
            return clientes;
        } catch (SQLException e) {
            throw new IllegalStateException("Error listando clientes", e);
        }
    }

    private Cliente map(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setId(rs.getLong("id"));
        cliente.setNombreDeUsuario(rs.getString("usuario"));
        cliente.setEmail(rs.getString("email"));
        cliente.setContrasenia(rs.getString("contrasenia"));
        cliente.setFoto(rs.getBytes("foto"));
        cliente.setIp(rs.getString("ip"));
        cliente.setEstado(rs.getBoolean("estado"));
        return cliente;
    }
}
