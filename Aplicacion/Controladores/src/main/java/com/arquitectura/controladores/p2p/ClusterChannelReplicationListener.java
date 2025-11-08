package com.arquitectura.controladores.p2p;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replica la creación de canales a otros nodos del clúster.
 */
public class ClusterChannelReplicationListener implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ClusterChannelReplicationListener.class.getName());

    private final ServerPeerManager peerManager;
    private final CanalRepository canalRepository;

    public ClusterChannelReplicationListener(ServerPeerManager peerManager,
                                             CanalRepository canalRepository,
                                             SessionEventBus eventBus) {
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null || event.getType() != SessionEventType.CHANNEL_CREATED) {
            return;
        }
        Canal canal = extractCanal(event.getPayload());
        if (canal == null || canal.getId() == null) {
            return;
        }
        replicateChannel(canal);
    }

    private Canal extractCanal(Object payload) {
        if (payload instanceof Canal canal) {
            return canal;
        }
        return null;
    }

    private void replicateChannel(Canal canal) {
        try {
            Long canalId = canal.getId();
            Canal canonical = fetchCanonical(canalId, canal);
            String canalUuid = resolveUuid(canalId, canonical);

            DatabaseSnapshot snapshot = new DatabaseSnapshot();
            DatabaseSnapshot.CanalRecord record = new DatabaseSnapshot.CanalRecord();
            record.setId(canalId);
            record.setUuid(canalUuid);
            record.setNombre(extractNombre(canonical));
            record.setPrivado(extractPrivado(canonical));
            if (record.getNombre() == null && canonical != canal) {
                record.setNombre(extractNombre(canal));
            }
            if (record.getPrivado() == null && canonical != canal) {
                record.setPrivado(extractPrivado(canal));
            }
            snapshot.setCanales(List.of(record));

            String effectiveUuid = canalUuid != null && !canalUuid.isBlank()
                ? canalUuid
                : resolveUuid(canalId, null);

            List<DatabaseSnapshot.ChannelMembershipRecord> membershipRecords = new ArrayList<>();
            for (Cliente miembro : canalRepository.findUsers(canalId)) {
                if (miembro == null || miembro.getId() == null) {
                    continue;
                }
                DatabaseSnapshot.ChannelMembershipRecord membership = new DatabaseSnapshot.ChannelMembershipRecord();
                membership.setCanalId(canalId);
                membership.setCanalUuid(effectiveUuid);
                membership.setClienteId(miembro.getId());
                membershipRecords.add(membership);
            }
            snapshot.setCanalMiembros(membershipRecords);

            peerManager.broadcastDatabaseUpdate(snapshot);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "No se pudo replicar la creación del canal " + canal.getId(), ex);
        }
    }

    private String resolveUuid(Long canalId, Canal canal) {
        String extracted = extractUuid(canal);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        if (canalId == null) {
            return null;
        }
        try {
            return canalRepository.findById(canalId)
                .map(this::extractUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .orElse(null);
        } catch (Throwable ex) {
            LOGGER.log(Level.FINE, "No se pudo resolver UUID para el canal " + canalId, ex);
            return null;
        }
    }

    private Canal fetchCanonical(Long canalId, Canal fallback) {
        if (canalId == null) {
            return fallback;
        }
        try {
            return canalRepository.findById(canalId).orElse(fallback);
        } catch (Throwable ex) {
            LOGGER.log(Level.FINE, "No se pudo recuperar el canal " + canalId + " desde el repositorio", ex);
            return fallback;
        }
    }

    private String extractUuid(Canal canal) {
        Object value = extractProperty(canal, "Uuid", "uuid");
        return value != null ? value.toString() : null;
    }

    private String extractNombre(Canal canal) {
        Object value = extractProperty(canal, "Nombre", "nombre");
        return value != null ? value.toString() : null;
    }

    private Boolean extractPrivado(Canal canal) {
        Object value = extractProperty(canal, "Privado", "privado");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private Object extractProperty(Canal canal, String getterSuffix, String fieldName) {
        if (canal == null) {
            return null;
        }
        try {
            var getter = canal.getClass().getMethod("get" + getterSuffix);
            return getter.invoke(canal);
        } catch (NoSuchMethodException e) {
            try {
                var field = canal.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(canal);
            } catch (ReflectiveOperationException reflectionException) {
                LOGGER.log(Level.FINEST,
                    "No se pudo obtener la propiedad '" + fieldName + "' del canal via reflexión",
                    reflectionException);
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.FINEST,
                "Error invocando getter 'get" + getterSuffix + "' del canal",
                ex);
        }
        return null;
    }
}

