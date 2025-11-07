package com.arquitectura.repositorios;

import com.arquitectura.entidades.Invitacion;

import java.util.List;
import java.util.Optional;

public interface InvitacionRepository {
    
    /**
     * Guarda una nueva invitación
     */
    Invitacion save(Invitacion invitacion);
    
    /**
     * Encuentra una invitación por canal e invitado
     */
    Optional<Invitacion> findByCanalAndInvitado(Long canalId, Long invitadoId);
    
    /**
     * Obtiene todas las invitaciones pendientes de un usuario
     */
    List<Invitacion> findPendientesByInvitado(Long invitadoId);
    
    /**
     * Obtiene todas las invitaciones enviadas por un usuario
     */
    List<Invitacion> findByInvitador(Long invitadorId);
    
    /**
     * Actualiza el estado de una invitación
     */
    void updateEstado(Long id, String estado);

    /**
     * Actualiza la información básica de una invitación existente para reenviarla.
     */
    void reactivarInvitacion(Long id, Long invitadorId);

    /**
     * Elimina una invitación
     */
    void delete(Long id);
}
