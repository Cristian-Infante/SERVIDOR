package com.arquitectura.controladores.conexion;

import com.arquitectura.dto.CommandEnvelope;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
    private final ObjectMapper mapper;
    private final SessionEventBus eventBus;
    
    public ConnectionRegistry(SessionEventBus eventBus) {
        this.eventBus = eventBus;
        // Configurar Jackson para manejar LocalDateTime
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String register(Socket socket) throws IOException {
        String sessionId = "session-" + sequence.incrementAndGet();
        String clientIp = socket.getRemoteSocketAddress().toString();
        ConnectionContext context = new ConnectionContext(sessionId, socket);
        // Crear descriptor inicial sin usuario (anónimo)
        context.descriptor = new SessionDescriptor(sessionId, null, "Anónimo", clientIp);
        contexts.put(sessionId, context);
        LOGGER.info(() -> "Nueva conexión TCP registrada " + sessionId + " desde " + clientIp);
        
        // Publicar evento de conexión TCP
        eventBus.publish(new SessionEvent(SessionEventType.TCP_CONNECTED, sessionId, null, context.descriptor));
        
        return sessionId;
    }

    public void unregister(String sessionId) {
        ConnectionContext context = contexts.remove(sessionId);
        if (context != null) {
            SessionDescriptor descriptor = context.descriptor;
            context.close();
            LOGGER.info(() -> "Sesión removida " + sessionId);
            
            // Publicar evento de desconexión TCP
            eventBus.publish(new SessionEvent(SessionEventType.TCP_DISCONNECTED, sessionId, 
                descriptor != null ? descriptor.getClienteId() : null, descriptor));
        }
    }

    public void updateCliente(String sessionId, Long clienteId, String usuario, String ip) {
        ConnectionContext context = contexts.get(sessionId);
        if (context != null) {
            if (clienteId == null && usuario == null) {
                // Logout: volver a estado anónimo
                String clientIp = context.socket.getRemoteSocketAddress().toString();
                context.descriptor = new SessionDescriptor(sessionId, null, "Anónimo", clientIp);
                LOGGER.info(() -> "Sesión " + sessionId + " cambió a estado anónimo");
            } else {
                // Login: actualizar con datos del usuario
                context.descriptor = new SessionDescriptor(sessionId, clienteId, usuario, ip);
                LOGGER.info(() -> "Sesión " + sessionId + " autenticada como " + usuario);
            }
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
            LOGGER.info(() -> String.format("👥 Usuario %s (%s) se unió al canal %d", 
                context.descriptor.getUsuario(),
                context.descriptor.getSessionId(),
                canalId));
        } else {
            LOGGER.warning("⚠️ No se pudo unir al canal " + canalId + " - sesión " + sessionId + " no encontrada");
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
        System.out.println("📡 ENVIANDO MENSAJE A CANAL " + canalId);
        
        // Obtener todos los contextos que pertenecen al canal
        var miembrosCanal = contexts.values().stream()
                .map(ctx -> ctx.descriptor)
                .filter(desc -> desc != null && desc.getCanales().contains(canalId))
                .toList();
        
        System.out.println("   👥 Miembros del canal encontrados: " + miembrosCanal.size());
        
        if (miembrosCanal.isEmpty()) {
            System.out.println("⚠️ NO SE ENCONTRARON MIEMBROS PARA EL CANAL " + canalId);
            System.out.println("📋 ESTADO ACTUAL DE CONEXIONES:");
            for (ConnectionContext ctx : contexts.values()) {
                if (ctx.descriptor != null) {
                    System.out.println("   - " + ctx.descriptor.getSessionId() + 
                                     " | Usuario: " + ctx.descriptor.getUsuario() + 
                                     " | Canales: " + ctx.descriptor.getCanales());
                }
            }
        } else {
            // Si el payload es un Mensaje, evitar reenviar al emisor
            Long emisorId = null;
            try {
                if (payload instanceof com.arquitectura.entidades.Mensaje m && m.getEmisor() != null) {
                    emisorId = m.getEmisor();
                }
            } catch (Throwable ignored) { }

            for (var descriptor : miembrosCanal) {
                if (emisorId != null && emisorId.equals(descriptor.getClienteId())) {
                    continue;
                }
                System.out.println(String.format("   → ENVIANDO A: %s (ID:%s, Usuario:%s)", 
                    descriptor.getSessionId(), 
                    descriptor.getClienteId(), 
                    descriptor.getUsuario()));
                
                ConnectionContext ctx = contexts.get(descriptor.getSessionId());
                send(ctx, payload);
            }
        }
        
        System.out.println("✅ PROCESO DE ENVÍO A CANAL " + canalId + " COMPLETADO");
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
            // Devolver todas las conexiones (incluidas las anónimas)
            if (context.descriptor != null) {
                descriptors.add(context.descriptor);
            }
        }
        return descriptors;
    }
    
    /**
     * Retorna el número total de conexiones activas (autenticadas y no autenticadas)
     */
    public int getTotalConnections() {
        return contexts.size();
    }
    
    /**
     * Retorna el número de conexiones autenticadas (con usuario)
     */
    public int getAuthenticatedConnections() {
        return (int) contexts.values().stream()
                .filter(ctx -> ctx.descriptor != null && ctx.descriptor.getClienteId() != null)
                .count();
    }
    
    /**
     * Lista todos los miembros de canales para debugging
     */
    public void logChannelMemberships() {
        LOGGER.info("📋 Estado actual de membresías de canales:");
        
        for (ConnectionContext context : contexts.values()) {
            if (context.descriptor != null && context.descriptor.getClienteId() != null) {
                String canales = context.descriptor.getCanales().isEmpty() 
                    ? "ninguno" 
                    : context.descriptor.getCanales().toString();
                    
                LOGGER.info(() -> String.format("   %s (%s) - Canales: %s", 
                    context.descriptor.getUsuario(),
                    context.descriptor.getSessionId(),
                    canales));
            }
        }
    }

    private void send(ConnectionContext ctx, Object payload) {
        if (ctx == null) {
            System.out.println("❌ CONTEXTO NULO - no se puede enviar mensaje");
            return;
        }
        try {
            CommandEnvelope envelope = new CommandEnvelope("EVENT", payload);
            String json = mapper.writeValueAsString(envelope);
            ctx.writer.write(json);
            ctx.writer.write('\n');
            ctx.writer.flush();

            String usuario = ctx.descriptor != null && ctx.descriptor.getUsuario() != null
                ? ctx.descriptor.getUsuario()
                : "(usuario no autenticado)";

            System.out.println(
                "✅ MENSAJE ENVIADO EXITOSAMENTE a sesión " + ctx.sessionId + " (usuario: " + usuario + ")"
            );
            System.out.println("   Contenido entregado: " + json);

        } catch (IOException e) {
            System.out.println("❌ ERROR IO enviando a " + ctx.sessionId + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("❌ ERROR INESPERADO enviando a " + ctx.sessionId + ": " + e.getMessage());
            e.printStackTrace();
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
