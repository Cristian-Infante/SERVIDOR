package com.arquitectura.servicios.eventos;

import com.arquitectura.dto.RealtimeInvitationDto;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Invitacion;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.InvitacionRepository;
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
    private final InvitacionRepository invitacionRepository;

    public InvitationNotificationService(ConnectionGateway connectionGateway,
                                         CanalRepository canalRepository,
                                         ClienteRepository clienteRepository,
                                         InvitacionRepository invitacionRepository,
                                         SessionEventBus eventBus) {
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.invitacionRepository = Objects.requireNonNull(invitacionRepository, "invitacionRepository");
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
        context.canalUuid = asString(map.get("canalUuid"));
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

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private RealtimeInvitationDto buildDto(String evento, LocalDateTime timestamp,
                                           InvitationContext context, String estado) {
        RealtimeInvitationDto dto = new RealtimeInvitationDto();
        dto.setEvento(evento);
        dto.setTimestamp(timestamp);
        dto.setCanalId(context.canalId);
        dto.setCanalUuid(context.canalUuid);
        dto.setEstado(estado);

        Optional<Invitacion> invitacion = Optional.empty();
        Long canalId = context.canalId;
        if (canalId == null && context.canalUuid != null) {
            canalId = canalRepository.findByUuid(context.canalUuid)
                .map(Canal::getId)
                .orElse(null);
        }
        if (canalId != null && context.invitadoId != null) {
            invitacion = invitacionRepository.findByCanalAndInvitado(canalId, context.invitadoId);
        }

        Optional<Canal> canalOpt = Optional.empty();
        if (canalId != null) {
            canalOpt = canalRepository.findById(canalId);
        } else if (context.canalUuid != null) {
            canalOpt = canalRepository.findByUuid(context.canalUuid);
        }
        canalOpt.ifPresent(canal -> {
            dto.setCanalNombre(canal.getNombre());
            dto.setCanalPrivado(canal.getPrivado());
            dto.setCanalUuid(canal.getUuid());
            dto.setCanalId(canal.getId());
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

        invitacion.ifPresent(inv -> {
            dto.setInvitacionId(inv.getId());
            if (inv.getEstado() != null) {
                dto.setEstado(inv.getEstado());
            }
        });

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
        copy.setCanalUuid(original.getCanalUuid());
        copy.setCanalNombre(original.getCanalNombre());
        copy.setCanalPrivado(original.getCanalPrivado());
        copy.setInvitadorId(original.getInvitadorId());
        copy.setInvitadorNombre(original.getInvitadorNombre());
        copy.setInvitadoId(original.getInvitadoId());
        copy.setInvitadoNombre(original.getInvitadoNombre());
        copy.setEstado(original.getEstado());
        copy.setInvitacionId(original.getInvitacionId());
        return copy;
    }

    private static final class InvitationContext {
        private Long canalId;
        private String canalUuid;
        private Long invitadoId;
        private Long invitadorId;
    }
}
