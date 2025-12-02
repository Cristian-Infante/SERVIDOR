package com.arquitectura.controladores.p2p;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.arquitectura.dto.ConnectionStatusUpdateDto;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

/**
 * Escucha eventos de login/logout y replica el cambio de estado de conexión al clúster P2P.
 * Esto permite que todos los servidores del clúster conozcan el estado de conexión actual
 * de todos los usuarios, sin importar a qué servidor estén conectados.
 */
public class ClusterUserStatusReplicationListener implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ClusterUserStatusReplicationListener.class.getName());

    private final ServerPeerManager peerManager;
    private final ClienteRepository clienteRepository;
    
    // Contador de sesiones activas por usuario para determinar si es la primera/última conexión
    private final ConcurrentHashMap<Long, AtomicInteger> activeSessions = new ConcurrentHashMap<>();

    public ClusterUserStatusReplicationListener(ServerPeerManager peerManager,
                                                 ClienteRepository clienteRepository,
                                                 SessionEventBus eventBus) {
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
        LOGGER.info("ClusterUserStatusReplicationListener inicializado - replicará cambios de estado de conexión al clúster");
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null || event.getType() == null || event.getActorId() == null) {
            return;
        }

        Long userId = event.getActorId();
        boolean shouldBroadcast = false;
        boolean connected = false;

        if (event.getType() == SessionEventType.LOGIN) {
            shouldBroadcast = handleLogin(userId);
            connected = true;
        } else if (event.getType() == SessionEventType.LOGOUT) {
            shouldBroadcast = handleLogout(userId);
            connected = false;
        }

        if (shouldBroadcast) {
            broadcastStatusChange(userId, connected);
        }
    }

    /**
     * Maneja un evento de login. Retorna true si es la primera sesión del usuario
     * (es decir, el usuario pasó de desconectado a conectado).
     */
    private boolean handleLogin(Long userId) {
        AtomicInteger sessions = activeSessions.compute(userId, (id, counter) -> {
            if (counter == null) {
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });
        
        // Solo notificar si es la primera sesión (el usuario estaba desconectado)
        return sessions != null && sessions.get() == 1;
    }

    /**
     * Maneja un evento de logout. Retorna true si era la última sesión del usuario
     * (es decir, el usuario pasó de conectado a desconectado).
     */
    private boolean handleLogout(Long userId) {
        AtomicInteger remaining = activeSessions.computeIfPresent(userId, (id, counter) -> {
            int value = counter.decrementAndGet();
            if (value <= 0) {
                return null; // Eliminar del mapa
            }
            return counter;
        });
        
        // Solo notificar si no quedan sesiones (el usuario ya no está conectado)
        return remaining == null;
    }

    /**
     * Replica el cambio de estado al clúster P2P.
     */
    private void broadcastStatusChange(Long userId, boolean connected) {
        try {
            Cliente cliente = clienteRepository.findById(userId).orElse(null);
            if (cliente == null) {
                LOGGER.warning(() -> "No se encontró el cliente " + userId + " para replicar su estado al clúster");
                return;
            }

            // Obtener el número actual de sesiones
            AtomicInteger sessionCounter = activeSessions.get(userId);
            int sesiones = sessionCounter != null ? sessionCounter.get() : 0;

            ConnectionStatusUpdateDto dto = new ConnectionStatusUpdateDto();
            dto.setEvento("USER_STATUS_CHANGED");
            dto.setUsuarioId(userId);
            dto.setUsuarioNombre(cliente.getNombreDeUsuario());
            dto.setUsuarioEmail(cliente.getEmail());
            dto.setConectado(connected);
            dto.setSesionesActivas(sesiones);
            dto.setTimestamp(LocalDateTime.now());

            // Broadcast al clúster P2P (esto llegará a los otros servidores)
            peerManager.broadcast(dto);
            
            LOGGER.info(() -> String.format(
                "Estado de usuario %d (%s) replicado al clúster: %s",
                userId,
                cliente.getNombreDeUsuario(),
                connected ? "CONECTADO" : "DESCONECTADO"
            ));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error replicando cambio de estado de usuario " + userId + " al clúster", e);
        }
    }
}
