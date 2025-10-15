package com.arquitectura.servicios;

import com.arquitectura.entidades.Canal;

public interface CanalService {
    Canal crearCanal(String nombre, boolean privado);

    void invitarUsuario(Long canalId, Long solicitanteId, Long invitadoId);

    void aceptarInvitacion(Long canalId, Long invitadoId);

    void rechazarInvitacion(Long canalId, Long invitadoId);
}
