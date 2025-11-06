package com.arquitectura.controladores.p2p;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;
import com.arquitectura.servicios.eventos.SessionEventTypes;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Escucha eventos de registro de usuarios y los replica inmediatamente al clúster.
 */
public class ClusterUserRegistrationListener implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ClusterUserRegistrationListener.class.getName());

    private final ServerPeerManager peerManager;
    private final SessionEventType userRegisteredType;

    public ClusterUserRegistrationListener(ServerPeerManager peerManager, SessionEventBus eventBus) {
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager");
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
        this.userRegisteredType = SessionEventTypes.userRegistered();
        if (this.userRegisteredType == null) {
            LOGGER.warning("SessionEventType USER_REGISTERED no disponible; no se replicarán nuevos registros al clúster");
        }
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (userRegisteredType == null || event.getType() != userRegisteredType) {
            return;
        }
        if (!(event.getPayload() instanceof Cliente cliente)) {
            LOGGER.warning("Evento USER_REGISTERED sin payload de Cliente");
            return;
        }

        try {
            DatabaseSnapshot snapshot = new DatabaseSnapshot();
            DatabaseSnapshot.ClienteRecord record = new DatabaseSnapshot.ClienteRecord();
            record.setId(cliente.getId());
            record.setUsuario(cliente.getNombreDeUsuario());
            record.setEmail(cliente.getEmail());
            record.setContrasenia(cliente.getContrasenia());
            if (cliente.getFoto() != null && cliente.getFoto().length > 0) {
                record.setFotoBase64(Base64.getEncoder().encodeToString(cliente.getFoto()));
            }
            record.setIp(cliente.getIp());
            record.setEstado(cliente.getEstado());
            snapshot.setClientes(List.of(record));

            peerManager.broadcastDatabaseUpdate(snapshot);
            LOGGER.fine(() -> "Cliente " + cliente.getId() + " replicado al clúster tras registro");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error replicando registro de cliente al clúster", e);
        }
    }
}
