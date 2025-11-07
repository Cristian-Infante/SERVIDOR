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
            DatabaseSnapshot snapshot = new DatabaseSnapshot();
            DatabaseSnapshot.CanalRecord record = new DatabaseSnapshot.CanalRecord();
            record.setId(canal.getId());
            record.setUuid(canal.getUuid());
            record.setNombre(canal.getNombre());
            record.setPrivado(canal.getPrivado());
            snapshot.setCanales(List.of(record));

            List<DatabaseSnapshot.ChannelMembershipRecord> membershipRecords = new ArrayList<>();
            for (Cliente miembro : canalRepository.findUsers(canal.getId())) {
                if (miembro == null || miembro.getId() == null) {
                    continue;
                }
                DatabaseSnapshot.ChannelMembershipRecord membership = new DatabaseSnapshot.ChannelMembershipRecord();
                membership.setCanalId(canal.getId());
                membership.setCanalUuid(canal.getUuid());
                membership.setClienteId(miembro.getId());
                membershipRecords.add(membership);
            }
            snapshot.setCanalMiembros(membershipRecords);

            peerManager.broadcastDatabaseUpdate(snapshot);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo replicar la creación del canal " + canal.getId(), ex);
        }
    }
}

