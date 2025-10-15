package com.arquitectura.controladores.conexion;

import com.arquitectura.dto.CommandEnvelope;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionRegistry implements ConnectionGateway {

    private static final Logger LOGGER = Logger.getLogger(ConnectionRegistry.class.getName());

    private final Map<String, ConnectionContext> contexts = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ObjectMapper mapper = new ObjectMapper();

    public String register(Socket socket) throws IOException {
        String sessionId = "session-" + sequence.incrementAndGet();
        ConnectionContext context = new ConnectionContext(sessionId, socket);
        contexts.put(sessionId, context);
        LOGGER.info(() -> "Nueva sesión registrada " + sessionId);
        return sessionId;
    }

    public void unregister(String sessionId) {
        ConnectionContext context = contexts.remove(sessionId);
        if (context != null) {
            context.close();
            LOGGER.info(() -> "Sesión removida " + sessionId);
        }
    }

    public void updateCliente(String sessionId, Long clienteId, String usuario, String ip) {
        ConnectionContext context = contexts.get(sessionId);
        if (context != null) {
            context.descriptor = new SessionDescriptor(sessionId, clienteId, usuario, ip);
        }
    }

    public BufferedWriter writerOf(String sessionId) {
        ConnectionContext context = contexts.get(sessionId);
        return context != null ? context.writer : null;
    }

    public SessionDescriptor descriptorOf(String sessionId) {
        ConnectionContext context = contexts.get(sessionId);
        return context != null ? context.descriptor : null;
    }

    @Override
    public SessionDescriptor descriptor(String sessionId) {
        return descriptorOf(sessionId);
    }

    public void joinChannel(String sessionId, Long canalId) {
        ConnectionContext context = contexts.get(sessionId);
        if (context != null && context.descriptor != null) {
            context.descriptor.joinChannel(canalId);
        }
    }

    @Override
    public void closeSession(String sessionId) {
        unregister(sessionId);
    }

    @Override
    public void broadcast(Object payload) {
        contexts.values().forEach(ctx -> send(ctx, payload));
    }

    @Override
    public void sendToChannel(Long canalId, Object payload) {
        contexts.values().stream()
                .map(ctx -> ctx.descriptor)
                .filter(desc -> desc != null && desc.getCanales().contains(canalId))
                .map(desc -> contexts.get(desc.getSessionId()))
                .forEach(ctx -> send(ctx, payload));
    }

    @Override
    public void sendToSession(String sessionId, Object payload) {
        ConnectionContext ctx = contexts.get(sessionId);
        send(ctx, payload);
    }

    @Override
    public void sendToUser(Long userId, Object payload) {
        contexts.values().stream()
                .filter(ctx -> ctx.descriptor != null && userId.equals(ctx.descriptor.getClienteId()))
                .forEach(ctx -> send(ctx, payload));
    }

    @Override
    public List<SessionDescriptor> activeSessions() {
        List<SessionDescriptor> descriptors = new ArrayList<>();
        for (ConnectionContext context : contexts.values()) {
            if (context.descriptor != null) {
                descriptors.add(context.descriptor);
            }
        }
        return descriptors;
    }

    private void send(ConnectionContext ctx, Object payload) {
        if (ctx == null) {
            return;
        }
        try {
            CommandEnvelope envelope = new CommandEnvelope("EVENT", payload);
            String json = mapper.writeValueAsString(envelope);
            ctx.writer.write(json);
            ctx.writer.write('\n');
            ctx.writer.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo enviar mensaje a la sesión " + (ctx != null ? ctx.sessionId : "desconocida"), e);
        }
    }

    private static class ConnectionContext {
        private final String sessionId;
        private final Socket socket;
        private final BufferedWriter writer;
        private SessionDescriptor descriptor;

        private ConnectionContext(String sessionId, Socket socket) throws IOException {
            this.sessionId = sessionId;
            this.socket = socket;
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void close() {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
