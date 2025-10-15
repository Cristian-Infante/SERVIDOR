package com.arquitectura.repositorios.jdbc;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;

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
 * Implementación de {@link ClienteRepository} usando JDBC contra MySQL.
 *
 * <p>Esquema esperado:</p>
 * <pre>
 * CREATE TABLE clientes (
 *     id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *     usuario VARCHAR(100) NOT NULL UNIQUE,
 *     email VARCHAR(150) NOT NULL UNIQUE,
 *     password_hash VARCHAR(255) NOT NULL,
 *     foto LONGBLOB,
 *     ip VARCHAR(64),
 *     conectado TINYINT(1) DEFAULT 0
 * );
 * </pre>
 */
public class JdbcClienteRepository implements ClienteRepository {

    private final DataSource dataSource;

    public JdbcClienteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Cliente save(Cliente cliente) {
        if (cliente.getId() == null) {
            return insert(cliente);
        }
        return update(cliente);
    }

    private Cliente insert(Cliente cliente) {
        final String sql = "INSERT INTO clientes (usuario, email, password_hash, foto, ip, conectado) VALUES (?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, cliente.getNombreDeUsuario());
            statement.setString(2, cliente.getEmail());
            statement.setString(3, cliente.getContrasenia());
            statement.setBytes(4, cliente.getFoto());
            statement.setString(5, cliente.getIp());
            statement.setBoolean(6, Boolean.TRUE.equals(cliente.getEstado()));
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    cliente.setId(rs.getLong(1));
                }
            }
            return cliente;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar cliente", e);
        }
    }

    private Cliente update(Cliente cliente) {
        final String sql = "UPDATE clientes SET usuario=?, email=?, password_hash=?, foto=?, ip=?, conectado=? WHERE id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cliente.getNombreDeUsuario());
            statement.setString(2, cliente.getEmail());
            statement.setString(3, cliente.getContrasenia());
            statement.setBytes(4, cliente.getFoto());
            statement.setString(5, cliente.getIp());
            statement.setBoolean(6, Boolean.TRUE.equals(cliente.getEstado()));
            statement.setLong(7, cliente.getId());
            statement.executeUpdate();
            return cliente;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al actualizar cliente", e);
        }
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        final String sql = "SELECT * FROM clientes WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCliente(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al buscar cliente por id", e);
        }
    }

    @Override
    public Optional<Cliente> findByEmail(String email) {
        final String sql = "SELECT * FROM clientes WHERE email = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCliente(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al buscar cliente por email", e);
        }
    }

    @Override
    public List<Cliente> findConnected() {
        final String sql = "SELECT * FROM clientes WHERE conectado = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Cliente> clientes = new ArrayList<>();
            while (rs.next()) {
                clientes.add(mapCliente(rs));
            }
            return clientes;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar clientes conectados", e);
        }
    }

    @Override
    public void setConnected(Long id, boolean connected) {
        final String sql = "UPDATE clientes SET conectado=? WHERE id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, connected);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error al actualizar estado de conexión", e);
        }
    }

    @Override
    public List<Cliente> all() {
        final String sql = "SELECT * FROM clientes";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Cliente> clientes = new ArrayList<>();
            while (rs.next()) {
                clientes.add(mapCliente(rs));
            }
            return clientes;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar clientes", e);
        }
    }

    private Cliente mapCliente(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setId(rs.getLong("id"));
        cliente.setNombreDeUsuario(rs.getString("usuario"));
        cliente.setEmail(rs.getString("email"));
        cliente.setContrasenia(rs.getString("password_hash"));
        cliente.setFoto(rs.getBytes("foto"));
        cliente.setIp(rs.getString("ip"));
        cliente.setEstado(rs.getBoolean("conectado"));
        return cliente;
    }
}
