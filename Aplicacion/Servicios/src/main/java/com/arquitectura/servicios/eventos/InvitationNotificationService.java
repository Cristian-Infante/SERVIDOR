package com.arquitectura.servicios.eventos;

import com.arquitectura.dto.RealtimeInvitationDto;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.conexion.ConnectionGateway;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InvitationNotificationService implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(InvitationNotificationService.class.getName());

    private final ConnectionGateway connectionGateway;
    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;

    public InvitationNotificationService(ConnectionGateway connectionGateway,
                                         CanalRepository canalRepository,
                                         ClienteRepository clienteRepository,
                                         SessionEventBus eventBus) {
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        try {
            switch (event.getType()) {
                case INVITE_SENT -> handleInviteSent(event);
                case INVITE_ACCEPTED -> handleInviteResponse(event, "INVITE_ACCEPTED", "ACEPTADA");
                case INVITE_REJECTED -> handleInviteResponse(event, "INVITE_REJECTED", "RECHAZADA");
                default -> {
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo notificar evento de invitación", ex);
        }
    }

    private void handleInviteSent(SessionEvent event) {
        InvitationContext context = extractContext(event.getPayload());
        if (context == null || context.invitadoId == null) {
            return;
        }
        RealtimeInvitationDto dto = buildDto("INVITE_SENT", event.getTimestamp(), context, "PENDIENTE");
        deliverToUser(context.invitadoId, dto);
        if (context.invitadorId != null) {
            deliverToUser(context.invitadorId, clone(dto));
        }
    }

    private void handleInviteResponse(SessionEvent event, String evento, String estado) {
        InvitationContext context = extractContext(event.getPayload());
        if (context == null) {
            return;
        }
        RealtimeInvitationDto dto = buildDto(evento, event.getTimestamp(), context, estado);
        if (context.invitadorId != null) {
            deliverToUser(context.invitadorId, dto);
        }
        if (context.invitadoId != null) {
            deliverToUser(context.invitadoId, clone(dto));
        }
    }

    private void deliverToUser(Long userId, RealtimeInvitationDto dto) {
        if (userId == null || dto == null) {
            return;
        }
        connectionGateway.sendToUser(userId, dto);
    }

    private InvitationContext extractContext(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        InvitationContext context = new InvitationContext();
        context.canalId = asLong(map.get("canalId"));
        context.invitadoId = asLong(map.get("invitadoId"));
        context.invitadorId = asLong(map.get("invitadorId"));
        return context;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private RealtimeInvitationDto buildDto(String evento, LocalDateTime timestamp,
                                           InvitationContext context, String estado) {
        RealtimeInvitationDto dto = new RealtimeInvitationDto();
        dto.setEvento(evento);
        dto.setTimestamp(timestamp);
        dto.setCanalId(context.canalId);
        dto.setEstado(estado);

        Optional<Canal> canalOpt = context.canalId != null ? canalRepository.findById(context.canalId) : Optional.empty();
        canalOpt.ifPresent(canal -> {
            dto.setCanalNombre(canal.getNombre());
            dto.setCanalPrivado(canal.getPrivado());
        });

        Optional<Cliente> invitadorOpt = context.invitadorId != null
            ? clienteRepository.findById(context.invitadorId)
            : Optional.empty();
        invitadorOpt.ifPresent(cliente -> dto.setInvitadorNombre(cliente.getNombreDeUsuario()));
        dto.setInvitadorId(context.invitadorId);

        Optional<Cliente> invitadoOpt = context.invitadoId != null
            ? clienteRepository.findById(context.invitadoId)
            : Optional.empty();
        invitadoOpt.ifPresent(cliente -> dto.setInvitadoNombre(cliente.getNombreDeUsuario()));
        dto.setInvitadoId(context.invitadoId);

        return dto;
    }

    private RealtimeInvitationDto clone(RealtimeInvitationDto original) {
        if (original == null) {
            return null;
        }
        RealtimeInvitationDto copy = new RealtimeInvitationDto();
        copy.setEvento(original.getEvento());
        copy.setTimestamp(original.getTimestamp());
        copy.setCanalId(original.getCanalId());
        copy.setCanalNombre(original.getCanalNombre());
        copy.setCanalPrivado(original.getCanalPrivado());
        copy.setInvitadorId(original.getInvitadorId());
        copy.setInvitadorNombre(original.getInvitadorNombre());
        copy.setInvitadoId(original.getInvitadoId());
        copy.setInvitadoNombre(original.getInvitadoNombre());
        copy.setEstado(original.getEstado());
        return copy;
    }

    private static final class InvitationContext {
        private Long canalId;
        private Long invitadoId;
        private Long invitadorId;
    }
}
