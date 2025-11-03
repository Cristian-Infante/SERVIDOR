package com.arquitectura.servicios.impl;

import com.arquitectura.dto.ConnectionStatusUpdateDto;
import com.arquitectura.dto.ServerNotification;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ConexionServiceImpl implements ConexionService, SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ConexionServiceImpl.class.getName());

    private final ConnectionGateway connectionGateway;
    private final ClienteRepository clienteRepository;
    private final SessionEventBus eventBus;
    private final ConcurrentHashMap<Long, AtomicInteger> activeSessions = new ConcurrentHashMap<>();

    public ConexionServiceImpl(ConnectionGateway connectionGateway,
                               ClienteRepository clienteRepository,
                               SessionEventBus eventBus) {
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.eventBus.subscribe(this);
    }

    @Override
    public void cerrarConexion(String sessionId) {
        cerrarConexion(sessionId, "El administrador cerró tu conexión");
    }
    
    @Override
    public void cerrarConexion(String sessionId, String razon) {
        LOGGER.info(() -> "Cerrando conexión " + sessionId + " - Razón: " + razon);
        var descriptor = connectionGateway.descriptor(sessionId);
        
        // Enviar notificación al cliente antes de cerrar
        try {
            ServerNotification notificacion = new ServerNotification(
                "KICKED",
                "Tu conexión ha sido cerrada por el servidor",
                razon
            );
            connectionGateway.sendToSession(sessionId, notificacion);
            
            // Dar tiempo para que llegue la notificación
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warning(() -> "Error enviando notificación de cierre: " + e.getMessage());
        }
        
        // Cerrar la conexión
        connectionGateway.closeSession(sessionId);
        Long actor = descriptor != null ? descriptor.getClienteId() : null;
        eventBus.publish(new SessionEvent(SessionEventType.LOGOUT, sessionId, actor, null));
    }

    @Override
    public void broadcast(String mensaje) {
        connectionGateway.broadcast(mensaje);
    }
    
    @Override
    public void notificarApagado(String mensaje) {
        LOGGER.info(() -> "Notificando apagado del servidor a todos los clientes");
        ServerNotification notificacion = new ServerNotification(
            "SERVER_SHUTDOWN",
            mensaje != null ? mensaje : "El servidor se está apagando",
            "Mantenimiento programado"
        );
        connectionGateway.broadcast(notificacion);
    }

    @Override
    public void enviarACanal(Long canalId, String mensaje) {
        connectionGateway.sendToChannel(canalId, mensaje);
    }

    @Override
    public List<SessionDescriptor> sesionesActivas() {
        return connectionGateway.activeSessions();
    }

    @Override
    public void onEvent(SessionEvent event) {
        Long actorId = event.getActorId();
        if (actorId == null) {
            return;
        }

        boolean statusChanged = false;

        if (event.getType() == SessionEventType.LOGIN) {
            statusChanged = handleLoginEvent(actorId);
            if (statusChanged) {
                notifyStatusChange(actorId, true);
            }
        } else if (event.getType() == SessionEventType.LOGOUT) {
            statusChanged = handleLogoutEvent(actorId);
            if (statusChanged) {
                notifyStatusChange(actorId, false);
            }
        }
    }

    private boolean handleLoginEvent(Long actorId) {
        AtomicInteger sessions = activeSessions.compute(actorId, (id, counter) -> {
            if (counter == null) {
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });

        if (sessions != null && sessions.get() == 1) {
            clienteRepository.setConnected(actorId, true);
            return true;
        }
        return false;
    }

    private boolean handleLogoutEvent(Long actorId) {
        AtomicInteger remaining = activeSessions.computeIfPresent(actorId, (id, counter) -> {
            int value = counter.decrementAndGet();
            if (value <= 0) {
                return null;
            }
            return counter;
        });

        if (remaining == null) {
            activeSessions.remove(actorId);
            clienteRepository.setConnected(actorId, false);
            return true;
        }
        return false;
    }

    private void notifyStatusChange(Long actorId, boolean connected) {
        var clienteOpt = clienteRepository.findById(actorId);
        if (clienteOpt.isEmpty()) {
            LOGGER.warning(() -> "No se encontró información del usuario " + actorId + " para notificar su estado");
            return;
        }

        int sesiones = (int) connectionGateway.activeSessions().stream()
            .filter(descriptor -> descriptor.getClienteId() != null && descriptor.getClienteId().equals(actorId))
            .count();

        ConnectionStatusUpdateDto dto = new ConnectionStatusUpdateDto();
        dto.setEvento("USER_STATUS_CHANGED");
        dto.setUsuarioId(actorId);
        dto.setUsuarioNombre(clienteOpt.get().getNombreDeUsuario());
        dto.setUsuarioEmail(clienteOpt.get().getEmail());
        dto.setConectado(connected);
        dto.setSesionesActivas(sesiones);
        dto.setTimestamp(LocalDateTime.now());

        LOGGER.info(() -> String.format(
            "Notificando cambio de estado para usuario %d (%s): %s",
            actorId,
            dto.getUsuarioNombre(),
            connected ? "conectado" : "desconectado"
        ));

        connectionGateway.broadcast(dto);
    }
}
