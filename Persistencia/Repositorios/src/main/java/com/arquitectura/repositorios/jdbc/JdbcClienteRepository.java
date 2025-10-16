package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation. Expected schema:
 * <pre>
 * CREATE TABLE clientes (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   usuario VARCHAR(120) UNIQUE NOT NULL,
 *   email VARCHAR(180) UNIQUE NOT NULL,
 *   contrasenia VARBINARY(256) NOT NULL,
 *   foto LONGBLOB,
 *   ip VARCHAR(64),
 *   estado TINYINT(1) DEFAULT 0
 * );
 * </pre>
 */
public class JdbcClienteRepository extends JdbcSupport implements ClienteRepository {

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
        String sql = "INSERT INTO clientes(usuario, email, contrasenia, foto, ip, estado) VALUES(?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            return cliente;
        } catch (SQLException e) {
            throw new IllegalStateException("Error inserting client", e);
        }
    }

    private Cliente update(Cliente cliente) {
        String sql = "UPDATE clientes SET usuario=?, email=?, contrasenia=?, foto=?, ip=?, estado=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cliente.getNombreDeUsuario());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getContrasenia());
            ps.setBytes(4, cliente.getFoto());
            ps.setString(5, cliente.getIp());
            ps.setBoolean(6, Boolean.TRUE.equals(cliente.getEstado()));
            ps.setLong(7, cliente.getId());
            ps.executeUpdate();
            return cliente;
        } catch (SQLException e) {
            throw new IllegalStateException("Error updating client", e);
        }
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error finding client by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Cliente> findByEmail(String email) {
        String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE email=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error finding client by email", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Cliente> findConnected() {
        String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes WHERE estado=1";
        List<Cliente> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error finding connected clients", e);
        }
        return result;
    }

    @Override
    public void setConnected(Long id, boolean connected) {
        String sql = "UPDATE clientes SET estado=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, connected);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error updating connection state", e);
        }
    }

    @Override
    public void disconnectAll() {
        String sql = "UPDATE clientes SET estado=0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int updated = ps.executeUpdate();
            if (updated > 0) {
                System.out.println("Se desconectaron " + updated + " usuario(s) del inicio anterior");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error disconnecting all clients", e);
        }
    }

    @Override
    public List<Cliente> all() {
        String sql = "SELECT id, usuario, email, contrasenia, foto, ip, estado FROM clientes";
        List<Cliente> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listing clients", e);
        }
        return result;
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
