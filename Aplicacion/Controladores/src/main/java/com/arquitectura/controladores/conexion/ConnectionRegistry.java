package com.arquitectura.controladores.conexion;

import com.arquitectura.controladores.p2p.ServerPeerManager;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.Objects;

public class ConnectionRegistry implements ConnectionGateway {

    private static final Logger LOGGER = Logger.getLogger(ConnectionRegistry.class.getName());
    private static final String DEFAULT_SERVER_ID = "local-server";

    private final Map<String, ConnectionContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, RemoteSessionSnapshot> remoteSessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ObjectMapper mapper;
    private final SessionEventBus eventBus;
    private final String localServerId;
    private final Set<String> knownRemoteServers = ConcurrentHashMap.newKeySet();

    private volatile ServerPeerManager peerManager;

    public ConnectionRegistry(SessionEventBus eventBus) {
        this(eventBus, DEFAULT_SERVER_ID);
    }

    public ConnectionRegistry(SessionEventBus eventBus, String localServerId) {
        this.eventBus = eventBus;
        this.localServerId = localServerId != null ? localServerId : DEFAULT_SERVER_ID;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void setPeerManager(ServerPeerManager peerManager) {
        this.peerManager = peerManager;
    }

    public String getLocalServerId() {
        return localServerId;
    }

    public String register(Socket socket) throws IOException {
        String sessionId = "session-" + sequence.incrementAndGet();
        String clientIp = socket.getRemoteSocketAddress().toString();
        ConnectionContext context = new ConnectionContext(sessionId, socket);
        context.descriptor = new SessionDescriptor(sessionId, null, "Anónimo", clientIp, localServerId, true);
        contexts.put(sessionId, context);
        LOGGER.info(() -> "Nueva conexión TCP registrada " + sessionId + " desde " + clientIp);

        eventBus.publish(new SessionEvent(SessionEventType.TCP_CONNECTED, sessionId, null, context.descriptor));
        return sessionId;
    }

    public void unregister(String sessionId) {
        ConnectionContext context = contexts.remove(sessionId);
        if (context != null) {
            SessionDescriptor descriptor = context.descriptor;
            if (descriptor != null && descriptor.getClienteId() != null && peerManager != null) {
                peerManager.notifyClientLogout(toSnapshot(descriptor));
            }
            context.close();
            LOGGER.info(() -> "Sesión removida " + sessionId);

            eventBus.publish(new SessionEvent(SessionEventType.TCP_DISCONNECTED, sessionId,
                descriptor != null ? descriptor.getClienteId() : null, descriptor));
        }
    }

    public void updateCliente(String sessionId, Long clienteId, String usuario, String ip) {
        ConnectionContext context = contexts.get(sessionId);
        if (context == null) {
            LOGGER.warning(() -> "No se encontró la sesión " + sessionId + " para actualizar cliente");
            return;
        }

        SessionDescriptor previous = context.descriptor;
        if (clienteId == null && usuario == null) {
            if (previous != null && previous.getClienteId() != null && peerManager != null) {
                peerManager.notifyClientLogout(toSnapshot(previous));
            }
            String clientIp = context.socket.getRemoteSocketAddress().toString();
            context.descriptor = new SessionDescriptor(sessionId, null, "Anónimo", clientIp, localServerId, true);
            LOGGER.info(() -> "Sesión " + sessionId + " cambió a estado anónimo");
        } else {
            Set<Long> canales = previous != null ? new HashSet<>(previous.getCanales()) : Set.of();
            context.descriptor = new SessionDescriptor(sessionId, clienteId, usuario, ip, localServerId, true, canales);
            LOGGER.info(() -> "Sesión " + sessionId + " autenticada como " + usuario);
            if (peerManager != null) {
                peerManager.notifyClientLogin(toSnapshot(context.descriptor));
            }
        }
    }

    public BufferedWriter writerOf(String sessionId) {
        ConnectionContext context = contexts.get(sessionId);
        return context != null ? context.writer : null;
    }

    public SessionDescriptor descriptorOf(String sessionId) {
        ConnectionContext context = contexts.get(sessionId);
        if (context != null) {
            return context.descriptor;
        }
        return null;
    }

    @Override
    public SessionDescriptor descriptor(String sessionId) {
        SessionDescriptor descriptor = descriptorOf(sessionId);
        if (descriptor != null) {
            return descriptor;
        }
        RemoteSessionSnapshot remote = findRemoteSessionByCompositeId(sessionId);
        if (remote != null) {
            return new SessionDescriptor(composeRemoteSessionId(remote), remote.getClienteId(),
                remote.getUsuario(), remote.getIp(), remote.getServerId(), false, remote.getCanales());
        }
        return null;
    }

    public void joinChannel(String sessionId, Long canalId) {
        ConnectionContext context = contexts.get(sessionId);
        if (context != null && context.descriptor != null) {
            context.descriptor.joinChannel(canalId);
            LOGGER.info(() -> String.format("👥 Usuario %s (%s) se unió al canal %d",
                context.descriptor.getUsuario(),
                context.descriptor.getSessionId(),
                canalId));
            if (peerManager != null && context.descriptor.getClienteId() != null) {
                peerManager.notifyChannelJoin(toSnapshot(context.descriptor), canalId);
            }
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
        broadcastInternal(payload, true);
    }

    @Override
    public void broadcastLocal(Object payload) {
        broadcastInternal(payload, false);
    }

    private void broadcastInternal(Object payload, boolean includeRemote) {
        contexts.values().forEach(ctx -> send(ctx, payload));
        if (includeRemote && peerManager != null) {
            peerManager.broadcast(payload);
        }
    }

    @Override
    public void sendToChannel(Long canalId, Object payload) {
        sendToChannelInternal(canalId, payload, true);
    }

    public void deliverToChannelLocally(Long canalId, Object payload) {
        sendToChannelInternal(canalId, payload, false);
    }

    private void sendToChannelInternal(Long canalId, Object payload, boolean includeRemote) {
        System.out.println("📡 ENVIANDO MENSAJE A CANAL " + canalId);

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
                    System.out.println("   - " + ctx.descriptor.getSessionId()
                        + " | Usuario: " + ctx.descriptor.getUsuario()
                        + " | Canales: " + ctx.descriptor.getCanales());
                }
            }
        } else {
            Long emisorId = extractEmisorId(payload);
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

        if (includeRemote && peerManager != null) {
            Set<String> targetServers = remoteSessions.values().stream()
                .filter(snapshot -> snapshot.getCanales().contains(canalId))
                .map(RemoteSessionSnapshot::getServerId)
                .collect(Collectors.toSet());
            for (String serverId : targetServers) {
                peerManager.forwardToChannel(serverId, canalId, payload);
            }
        }

        System.out.println("✅ PROCESO DE ENVÍO A CANAL " + canalId + " COMPLETADO");
    }

    @Override
    public void sendToSession(String sessionId, Object payload) {
        ConnectionContext ctx = contexts.get(sessionId);
        if (ctx != null) {
            send(ctx, payload);
            return;
        }
        if (peerManager != null) {
            RemoteSessionSnapshot remote = findRemoteSessionByCompositeId(sessionId);
            if (remote != null) {
                peerManager.forwardToSession(remote.getServerId(), remote.getSessionId(), payload);
            } else {
                LOGGER.warning(() -> "No se encontró la sesión " + sessionId + " para envío directo");
            }
        }
    }

    public void deliverToSessionLocal(String sessionId, Object payload) {
        ConnectionContext ctx = contexts.get(sessionId);
        send(ctx, payload);
    }

    @Override
    public void sendToUser(Long userId, Object payload) {
        sendToUserInternal(userId, payload, true);
    }

    public void deliverToUserLocally(Long userId, Object payload) {
        sendToUserInternal(userId, payload, false);
    }

    private void sendToUserInternal(Long userId, Object payload, boolean includeRemote) {
        contexts.values().stream()
            .filter(ctx -> ctx.descriptor != null && userId.equals(ctx.descriptor.getClienteId()))
            .forEach(ctx -> send(ctx, payload));

        if (includeRemote && peerManager != null) {
            Set<String> servers = remoteSessions.values().stream()
                .filter(snapshot -> snapshot.getClienteId() != null && snapshot.getClienteId().equals(userId))
                .map(RemoteSessionSnapshot::getServerId)
                .collect(Collectors.toSet());
            for (String serverId : servers) {
                peerManager.forwardToUser(serverId, userId, payload);
            }
        }
    }

    @Override
    public List<SessionDescriptor> activeSessions() {
        List<SessionDescriptor> descriptors = new ArrayList<>();
        for (ConnectionContext context : contexts.values()) {
            if (context.descriptor != null) {
                descriptors.add(context.descriptor);
            }
        }
        for (RemoteSessionSnapshot snapshot : remoteSessions.values()) {
            descriptors.add(new SessionDescriptor(
                composeRemoteSessionId(snapshot),
                snapshot.getClienteId(),
                snapshot.getUsuario(),
                snapshot.getIp(),
                snapshot.getServerId(),
                false,
                snapshot.getCanales()
            ));
        }
        return descriptors;
    }

    public int getTotalConnections() {
        return contexts.size();
    }

    public int getAuthenticatedConnections() {
        return (int) contexts.values().stream()
            .filter(ctx -> ctx.descriptor != null && ctx.descriptor.getClienteId() != null)
            .count();
    }

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

        for (RemoteSessionSnapshot snapshot : remoteSessions.values()) {
            if (snapshot.getClienteId() != null) {
                String canales = snapshot.getCanales().isEmpty()
                    ? "ninguno"
                    : snapshot.getCanales().toString();
                LOGGER.info(() -> String.format("   %s (%s@%s) - Canales: %s",
                    snapshot.getUsuario(),
                    snapshot.getSessionId(),
                    snapshot.getServerId(),
                    canales));
            }
        }
    }

    public List<RemoteSessionSnapshot> snapshotLocalSessions() {
        List<RemoteSessionSnapshot> snapshots = new ArrayList<>();
        for (ConnectionContext context : contexts.values()) {
            if (context.descriptor != null && context.descriptor.getClienteId() != null) {
                snapshots.add(toSnapshot(context.descriptor));
            }
        }
        return snapshots;
    }

    public Map<String, List<RemoteSessionSnapshot>> snapshotSessionsByServer() {
        Map<String, List<RemoteSessionSnapshot>> snapshot = new HashMap<>();
        snapshot.put(localServerId, snapshotLocalSessions());
        remoteSessions.values().forEach(remote -> {
            if (remote == null || remote.getServerId() == null) {
                return;
            }
            snapshot.computeIfAbsent(remote.getServerId(), key -> new ArrayList<>())
                .add(copySnapshot(remote));
        });
        for (String serverId : knownRemoteServers) {
            if (serverId == null || serverId.equals(localServerId)) {
                continue;
            }
            snapshot.computeIfAbsent(serverId, key -> new ArrayList<>());
        }
        return snapshot;
    }

    public boolean markServerKnown(String serverId) {
        if (serverId == null || serverId.isBlank() || serverId.equals(localServerId)) {
            return false;
        }
        return knownRemoteServers.add(serverId);
    }

    public boolean registerRemoteSessions(String serverId, List<RemoteSessionSnapshot> snapshots) {
        if (serverId == null || serverId.equals(localServerId)) {
            return false;
        }
        boolean changed = knownRemoteServers.add(serverId);
        changed |= clearRemoteSessions(serverId);
        if (snapshots == null) {
            return changed;
        }
        for (RemoteSessionSnapshot snapshot : snapshots) {
            changed |= registerRemoteSession(serverId, snapshot);
        }
        return changed;
    }

    public boolean registerRemoteSession(String fallbackServerId, RemoteSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getSessionId() == null) {
            return false;
        }
        String effectiveServerId = snapshot.getServerId();
        if (effectiveServerId == null || effectiveServerId.isBlank()) {
            effectiveServerId = fallbackServerId;
        }
        if (effectiveServerId == null || effectiveServerId.equals(localServerId)) {
            return false;
        }

        RemoteSessionSnapshot copy = copySnapshot(snapshot);
        copy.setServerId(effectiveServerId);

        knownRemoteServers.add(effectiveServerId);

        String key = remoteKey(effectiveServerId, copy.getSessionId());
        RemoteSessionSnapshot current = remoteSessions.get(key);
        if (snapshotsEquivalent(current, copy)) {
            return false;
        }
        remoteSessions.put(key, copy);
        return true;
    }

    public boolean removeRemoteSession(String serverId, String sessionId, Long clienteId) {
        boolean removed = false;
        if (serverId != null && sessionId != null) {
            removed = remoteSessions.remove(remoteKey(serverId, sessionId)) != null;
            if (removed) {
                return true;
            }
        }
        if (serverId != null && clienteId != null) {
            removed = remoteSessions.entrySet().removeIf(entry -> {
                RemoteSessionSnapshot snapshot = entry.getValue();
                return serverId.equals(snapshot.getServerId()) && clienteId.equals(snapshot.getClienteId());
            });
        }
        return removed;
    }

    public boolean updateRemoteChannel(String serverId, String sessionId, Long canalId, boolean joined) {
        if (serverId == null || sessionId == null || canalId == null) {
            return false;
        }
        RemoteSessionSnapshot snapshot = remoteSessions.get(remoteKey(serverId, sessionId));
        if (snapshot == null) {
            return false;
        }
        if (joined) {
            return snapshot.getCanales().add(canalId);
        }
        return snapshot.getCanales().remove(canalId);
    }

    public List<RemoteSessionSnapshot> drainRemoteSessions(String serverId) {
        if (serverId == null) {
            return List.of();
        }
        List<RemoteSessionSnapshot> removed = new ArrayList<>();
        remoteSessions.entrySet().removeIf(entry -> {
            RemoteSessionSnapshot snapshot = entry.getValue();
            if (serverId.equals(snapshot.getServerId())) {
                removed.add(copySnapshot(snapshot));
                return true;
            }
            return false;
        });
        return removed;
    }

    public boolean clearRemoteSessions(String serverId) {
        return !drainRemoteSessions(serverId).isEmpty();
    }

    public void forgetRemoteServer(String serverId) {
        if (serverId == null) {
            return;
        }
        knownRemoteServers.remove(serverId);
    }

    private RemoteSessionSnapshot copySnapshot(RemoteSessionSnapshot snapshot) {
        return new RemoteSessionSnapshot(
            snapshot.getServerId(),
            snapshot.getSessionId(),
            snapshot.getClienteId(),
            snapshot.getUsuario(),
            snapshot.getIp(),
            snapshot.getCanales()
        );
    }

    private boolean snapshotsEquivalent(RemoteSessionSnapshot a, RemoteSessionSnapshot b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getServerId(), b.getServerId())
            && Objects.equals(a.getSessionId(), b.getSessionId())
            && Objects.equals(a.getClienteId(), b.getClienteId())
            && Objects.equals(a.getUsuario(), b.getUsuario())
            && Objects.equals(a.getIp(), b.getIp())
            && Objects.equals(new HashSet<>(a.getCanales()), new HashSet<>(b.getCanales()));
    }

    private RemoteSessionSnapshot toSnapshot(SessionDescriptor descriptor) {
        Set<Long> canales = descriptor != null ? new HashSet<>(descriptor.getCanales()) : Set.of();
        return new RemoteSessionSnapshot(localServerId,
            descriptor.getSessionId(),
            descriptor.getClienteId(),
            descriptor.getUsuario(),
            descriptor.getIp(),
            canales);
    }

    private RemoteSessionSnapshot findRemoteSessionByCompositeId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        int separator = sessionId.indexOf(':');
        if (separator > 0) {
            String serverId = sessionId.substring(0, separator);
            String remoteId = sessionId.substring(separator + 1);
            return remoteSessions.get(remoteKey(serverId, remoteId));
        }
        return remoteSessions.values().stream()
            .filter(snapshot -> sessionId.equals(snapshot.getSessionId()))
            .findFirst()
            .orElse(null);
    }

    private String composeRemoteSessionId(RemoteSessionSnapshot snapshot) {
        return snapshot.getServerId() + ":" + snapshot.getSessionId();
    }

    private String remoteKey(String serverId, String sessionId) {
        return serverId + ":" + sessionId;
    }

    private Long extractEmisorId(Object payload) {
        try {
            if (payload instanceof com.arquitectura.entidades.Mensaje m && m.getEmisor() != null) {
                return m.getEmisor();
            }
        } catch (Throwable ignored) {
        }
        return null;
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
