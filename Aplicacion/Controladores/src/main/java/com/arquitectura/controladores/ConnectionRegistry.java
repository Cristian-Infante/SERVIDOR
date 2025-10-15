package com.arquitectura.controladores;

import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.servicios.conexiones.ConnectionGateway;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro en memoria de las conexiones activas del servidor.
 */
public class ConnectionRegistry implements ConnectionGateway {

    private static final Logger LOGGER = Logger.getLogger(ConnectionRegistry.class.getName());

    private final Map<Long, ConnectionHandler> conexiones = new ConcurrentHashMap<>();
    private final Map<Long, String> usuarios = new ConcurrentHashMap<>();
    private final CanalRepository canalRepository;

    public ConnectionRegistry(CanalRepository canalRepository) {
        this.canalRepository = Objects.requireNonNull(canalRepository);
    }

    public void registrar(Long clienteId, String usuario, ConnectionHandler handler) {
        conexiones.put(clienteId, handler);
        usuarios.put(clienteId, usuario);
        LOGGER.log(Level.INFO, "Cliente {0} ({1}) conectado", new Object[]{clienteId, usuario});
    }

    public void desregistrar(Long clienteId) {
        conexiones.remove(clienteId);
        usuarios.remove(clienteId);
        LOGGER.log(Level.INFO, "Cliente {0} desconectado", clienteId);
    }

    @Override
    public void close(Long clienteId) {
        Optional.ofNullable(conexiones.get(clienteId)).ifPresent(ConnectionHandler::shutdown);
    }

    @Override
    public void broadcast(String message) {
        conexiones.values().forEach(handler -> handler.sendRaw(message));
    }

    @Override
    public void sendToChannel(Long canalId, String message) {
        canalRepository.findUsers(canalId).forEach(cliente -> {
            Optional.ofNullable(conexiones.get(cliente.getId())).ifPresent(handler -> handler.sendRaw(message));
        });
    }

    public Set<Long> clientesConectados() {
        return conexiones.keySet();
    }

    @Override
    public String usuarioDe(Long clienteId) {
        return usuarios.get(clienteId);
    }
}
