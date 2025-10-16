package com.arquitectura.servicios;

import java.util.List;

import com.arquitectura.dto.InvitationSummary;
import com.arquitectura.dto.SentInvitationSummary;
import com.arquitectura.entidades.Canal;

public interface CanalService {
    Canal crearCanal(String nombre, boolean privado, Long creadorId);

    void invitarUsuario(Long canalId, Long solicitanteId, Long invitadoId);

    void aceptarInvitacion(Long canalId, Long invitadoId);

    void rechazarInvitacion(Long canalId, Long invitadoId);

    List<InvitationSummary> obtenerInvitacionesRecibidas(Long usuarioId);

    List<SentInvitationSummary> obtenerInvitacionesEnviadas(Long usuarioId);
}
