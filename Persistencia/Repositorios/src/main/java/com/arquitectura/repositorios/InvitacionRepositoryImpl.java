package com.arquitectura.repositorios;

import com.arquitectura.configdb.DBConfig;
import com.arquitectura.entidades.Invitacion;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InvitacionRepositoryImpl implements InvitacionRepository {
    
    private final DataSource dataSource;
    
    public InvitacionRepositoryImpl() {
        this.dataSource = DBConfig.getInstance().getMySqlDataSource();
        crearTablaInvitacionesSiNoExiste();
    }
    
    private void crearTablaInvitacionesSiNoExiste() {
        String sql = """
            CREATE TABLE IF NOT EXISTS invitaciones (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                canal_id BIGINT NOT NULL,
                invitador_id BIGINT NOT NULL,
                invitado_id BIGINT NOT NULL,
                fecha_invitacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                estado VARCHAR(20) DEFAULT 'PENDIENTE',
                FOREIGN KEY (canal_id) REFERENCES canales(id) ON DELETE CASCADE,
                FOREIGN KEY (invitador_id) REFERENCES clientes(id) ON DELETE CASCADE,
                FOREIGN KEY (invitado_id) REFERENCES clientes(id) ON DELETE CASCADE,
                UNIQUE KEY unique_invitacion (canal_id, invitado_id)
            )
            """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear tabla invitaciones", e);
        }
    }
    
    @Override
    public Invitacion save(Invitacion invitacion) {
        String sql = """
            INSERT INTO invitaciones (canal_id, invitador_id, invitado_id, fecha_invitacion, estado)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, invitacion.getCanalId());
            stmt.setLong(2, invitacion.getInvitadorId());
            stmt.setLong(3, invitacion.getInvitadoId());
            stmt.setTimestamp(4, Timestamp.valueOf(invitacion.getFechaInvitacion()));
            stmt.setString(5, invitacion.getEstado());
            
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                invitacion.setId(rs.getLong(1));
            }
            
            return invitacion;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar invitación", e);
        }
    }
    
    @Override
    public Optional<Invitacion> findByCanalAndInvitado(Long canalId, Long invitadoId) {
        String sql = """
            SELECT id, canal_id, invitador_id, invitado_id, fecha_invitacion, estado
            FROM invitaciones
            WHERE canal_id = ? AND invitado_id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, canalId);
            stmt.setLong(2, invitadoId);
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapearInvitacion(rs));
            }
            
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar invitación", e);
        }
    }
    
    @Override
    public List<Invitacion> findPendientesByInvitado(Long invitadoId) {
        String sql = """
            SELECT id, canal_id, invitador_id, invitado_id, fecha_invitacion, estado
            FROM invitaciones
            WHERE invitado_id = ? AND estado = 'PENDIENTE'
            ORDER BY fecha_invitacion DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invitadoId);
            
            ResultSet rs = stmt.executeQuery();
            List<Invitacion> invitaciones = new ArrayList<>();
            
            while (rs.next()) {
                invitaciones.add(mapearInvitacion(rs));
            }
            
            return invitaciones;
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar invitaciones pendientes", e);
        }
    }
    
    @Override
    public List<Invitacion> findByInvitador(Long invitadorId) {
        String sql = """
            SELECT id, canal_id, invitador_id, invitado_id, fecha_invitacion, estado
            FROM invitaciones
            WHERE invitador_id = ?
            ORDER BY fecha_invitacion DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invitadorId);
            
            ResultSet rs = stmt.executeQuery();
            List<Invitacion> invitaciones = new ArrayList<>();
            
            while (rs.next()) {
                invitaciones.add(mapearInvitacion(rs));
            }
            
            return invitaciones;
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar invitaciones enviadas", e);
        }
    }
    
    @Override
    public void updateEstado(Long id, String estado) {
        String sql = "UPDATE invitaciones SET estado = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, estado);
            stmt.setLong(2, id);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar estado de invitación", e);
        }
    }
    
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM invitaciones WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar invitación", e);
        }
    }
    
    private Invitacion mapearInvitacion(ResultSet rs) throws SQLException {
        Invitacion inv = new Invitacion(
            rs.getLong("canal_id"),
            rs.getLong("invitador_id"),
            rs.getLong("invitado_id")
        );
        inv.setId(rs.getLong("id"));
        
        Timestamp timestamp = rs.getTimestamp("fecha_invitacion");
        if (timestamp != null) {
            inv.setFechaInvitacion(timestamp.toLocalDateTime());
        }
        
        inv.setEstado(rs.getString("estado"));
        
        return inv;
    }
}
