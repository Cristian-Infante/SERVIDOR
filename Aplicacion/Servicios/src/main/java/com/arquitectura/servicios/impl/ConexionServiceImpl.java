package com.arquitectura.servicios.impl;

import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.conexiones.ConnectionGateway;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;

import java.util.Objects;

public class ConexionServiceImpl implements ConexionService {

    private final ConnectionGateway gateway;
    private final SessionEventBus eventBus;

    public ConexionServiceImpl(ConnectionGateway gateway, SessionEventBus eventBus) {
        this.gateway = Objects.requireNonNull(gateway);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public void cerrarConexion(Long clienteId) {
        gateway.close(clienteId);
        String usuario = gateway.usuarioDe(clienteId);
        if (usuario == null) {
            usuario = "";
        }
        eventBus.publish(new SessionEvent(SessionEvent.Type.LOGOUT, java.util.Map.of(
                "clienteId", clienteId,
                "usuario", usuario
        )));
    }

    @Override
    public void broadcast(String mensaje) {
        gateway.broadcast(mensaje);
    }

    @Override
    public void enviarACanal(Long canalId, String mensaje) {
        gateway.sendToChannel(canalId, mensaje);
    }
}
