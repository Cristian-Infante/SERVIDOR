package com.arquitectura.controladores.p2p;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Invitacion;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.InvitacionRepository;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

/**
 * Replica los cambios de invitaciones entre servidores del clúster
 * transmitiendo snapshots incrementales cada vez que se crea o actualiza una
 * invitación.
 */
public class ClusterInvitationReplicationListener implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ClusterInvitationReplicationListener.class.getName());

    private final ServerPeerManager peerManager;
    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;
    private final InvitacionRepository invitacionRepository;

    public ClusterInvitationReplicationListener(ServerPeerManager peerManager,
                                                CanalRepository canalRepository,
                                                ClienteRepository clienteRepository,
                                                InvitacionRepository invitacionRepository,
                                                SessionEventBus eventBus) {
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.invitacionRepository = Objects.requireNonNull(invitacionRepository, "invitacionRepository");
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null) {
            return;
        }
        SessionEventType type = event.getType();
        if (type == null) {
            return;
        }
        if (type != SessionEventType.INVITE_SENT
            && type != SessionEventType.INVITE_ACCEPTED
            && type != SessionEventType.INVITE_REJECTED) {
            return;
        }
        InvitationKey key = extractKey(event.getPayload());
        if (key == null) {
            return;
        }
        replicateInvitation(key);
    }

    private void replicateInvitation(InvitationKey key) {
        if (key.canalId() == null || key.invitadoId() == null) {
            return;
        }
        try {
            Optional<Invitacion> invitacionOpt = invitacionRepository.findByCanalAndInvitado(key.canalId(), key.invitadoId());
            if (invitacionOpt.isEmpty()) {
                return;
            }
            Invitacion invitacion = invitacionOpt.get();
            String canalUuid = resolveCanalUuid(key, invitacion);
            if (canalUuid == null || canalUuid.isBlank()) {
                LOGGER.log(Level.FINE,
                    () -> "Omitiendo replicación de invitación del canal " + key.canalId()
                        + " porque no se pudo resolver el UUID");
                return;
            }
            DatabaseSnapshot snapshot = new DatabaseSnapshot();

            // Incluir datos básicos del canal para garantizar que el receptor pueda
            // resolver el UUID aunque aún no haya sincronizado el canal.
            canalRepository.findById(invitacion.getCanalId()).ifPresent(canal -> {
                DatabaseSnapshot.CanalRecord canalRecord = new DatabaseSnapshot.CanalRecord();
                canalRecord.setId(canal.getId());
                canalRecord.setUuid(canalUuid);
                canalRecord.setNombre(canal.getNombre());
                canalRecord.setPrivado(canal.getPrivado());
                snapshot.setCanales(List.of(canalRecord));
            });

            DatabaseSnapshot.InvitationRecord record = new DatabaseSnapshot.InvitationRecord();
            record.setId(invitacion.getId());
            record.setCanalId(invitacion.getCanalId());
            record.setCanalUuid(canalUuid);
            record.setInvitadorId(invitacion.getInvitadorId());
            record.setInvitadoId(invitacion.getInvitadoId());
            
            // Incluir emails para identificación global entre servidores
            if (invitacion.getInvitadorId() != null) {
                clienteRepository.findById(invitacion.getInvitadorId())
                    .map(Cliente::getEmail)
                    .ifPresent(record::setInvitadorEmail);
            }
            if (invitacion.getInvitadoId() != null) {
                clienteRepository.findById(invitacion.getInvitadoId())
                    .map(Cliente::getEmail)
                    .ifPresent(record::setInvitadoEmail);
            }
            
            LocalDateTime fecha = invitacion.getFechaInvitacion();
            record.setFechaInvitacion(fecha != null ? fecha.toString() : null);
            record.setEstado(invitacion.getEstado());
            snapshot.setInvitaciones(List.of(record));
            peerManager.broadcastDatabaseUpdate(snapshot);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo replicar la invitación del canal " + key.canalId(), ex);
        }
    }

    private String resolveCanalUuid(InvitationKey key, Invitacion invitacion) {
        if (key.canalUuid() != null && !key.canalUuid().isBlank()) {
            return key.canalUuid();
        }
        if (invitacion != null) {
            return canalRepository.findById(invitacion.getCanalId())
                .map(Canal::getUuid)
                .orElse(null);
        }
        return null;
    }

    private InvitationKey extractKey(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Long canalId = asLong(map.get("canalId"));
        Long invitadoId = asLong(map.get("invitadoId"));
        if (canalId == null || invitadoId == null) {
            return null;
        }
        String canalUuid = asString(map.get("canalUuid"));
        return new InvitationKey(canalId, invitadoId, canalUuid);
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

    private record InvitationKey(Long canalId, Long invitadoId, String canalUuid) {
    }
}
