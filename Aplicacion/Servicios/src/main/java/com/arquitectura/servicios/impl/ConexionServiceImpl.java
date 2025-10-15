package com.arquitectura.servicios.impl;

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
import java.util.logging.Logger;

public class ConexionServiceImpl implements ConexionService, SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(ConexionServiceImpl.class.getName());

    private final ConnectionGateway connectionGateway;
    private final ClienteRepository clienteRepository;
    private final SessionEventBus eventBus;

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
        LOGGER.info(() -> "Cerrando conexión " + sessionId);
        var descriptor = connectionGateway.descriptor(sessionId);
        connectionGateway.closeSession(sessionId);
        Long actor = descriptor != null ? descriptor.getClienteId() : null;
        eventBus.publish(new SessionEvent(SessionEventType.LOGOUT, sessionId, actor, null));
    }

    @Override
    public void broadcast(String mensaje) {
        connectionGateway.broadcast(mensaje);
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
        if (event.getType() == SessionEventType.LOGIN && event.getActorId() != null) {
            clienteRepository.setConnected(event.getActorId(), true);
        } else if (event.getType() == SessionEventType.LOGOUT && event.getActorId() != null) {
            clienteRepository.setConnected(event.getActorId(), false);
        }
    }
}
