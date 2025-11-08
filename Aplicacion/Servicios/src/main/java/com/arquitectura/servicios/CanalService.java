package com.arquitectura.servicios;

import java.util.List;

import com.arquitectura.dto.InvitationSummary;
import com.arquitectura.dto.SentInvitationSummary;
import com.arquitectura.entidades.Canal;

public interface CanalService {
    Canal crearCanal(String nombre, boolean privado, Long creadorId);

    void invitarUsuario(Long canalId, String canalUuid, Long solicitanteId, Long invitadoId);

    Canal aceptarInvitacion(Long canalId, String canalUuid, Long invitadoId);

    void rechazarInvitacion(Long canalId, String canalUuid, Long invitadoId);

    List<InvitationSummary> obtenerInvitacionesRecibidas(Long usuarioId);

    List<SentInvitationSummary> obtenerInvitacionesEnviadas(Long usuarioId);
}
