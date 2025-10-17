package com.arquitectura.servicios.impl;

import com.arquitectura.dto.ServerNotification;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

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

        if (event.getType() == SessionEventType.LOGIN) {
            handleLoginEvent(actorId);
        } else if (event.getType() == SessionEventType.LOGOUT) {
            handleLogoutEvent(actorId);
        }
    }

    private void handleLoginEvent(Long actorId) {
        AtomicInteger sessions = activeSessions.compute(actorId, (id, counter) -> {
            if (counter == null) {
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });

        if (sessions != null && sessions.get() == 1) {
            clienteRepository.setConnected(actorId, true);
        }
    }

    private void handleLogoutEvent(Long actorId) {
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
        }
    }
}
