package com.arquitectura.controladores.conexion;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.arquitectura.controladores.p2p.ServerPeerManager;
import com.arquitectura.dto.CommandEnvelope;
import com.arquitectura.entidades.Canal;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ConnectionRegistry implements ConnectionGateway {

    private static final Logger LOGGER = Logger.getLogger(ConnectionRegistry.class.getName());
    private static final String DEFAULT_SERVER_ID = "local-server";

    private final Map<String, ConnectionContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, RemoteSessionSnapshot> remoteSessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ObjectMapper mapper;
    private final SessionEventBus eventBus;
    private final String localServerId;
    private final CanalRepository canalRepository;
    private final Set<String> knownRemoteServers = ConcurrentHashMap.newKeySet();

    private volatile ServerPeerManager peerManager;

    public ConnectionRegistry(SessionEventBus eventBus, CanalRepository canalRepository) {
        this(eventBus, DEFAULT_SERVER_ID, canalRepository);
    }

    public ConnectionRegistry(SessionEventBus eventBus, String localServerId, CanalRepository canalRepository) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.localServerId = localServerId != null ? localServerId : DEFAULT_SERVER_ID;
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
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

    public Set<String> knownServersSnapshot() {
        Set<String> servers = new LinkedHashSet<>();
        servers.add(localServerId);
        servers.addAll(knownRemoteServers);
        for (RemoteSessionSnapshot snapshot : remoteSessions.values()) {
            String serverId = snapshot.getServerId();
            if (serverId != null && !serverId.isBlank()) {
                servers.add(serverId);
            }
        }
        return Collections.unmodifiableSet(servers);
    }

    public boolean markServerKnown(String serverId) {
        if (serverId == null || serverId.isBlank() || serverId.equals(localServerId)) {
            return false;
        }
        boolean added = knownRemoteServers.add(serverId);
        if (added) {
            publishClusterStateUpdate();
        }
        return added;
    }

    public boolean registerRemoteSessions(String serverId, List<RemoteSessionSnapshot> snapshots) {
        if (serverId == null || serverId.equals(localServerId)) {
            return false;
        }
        boolean changed = knownRemoteServers.add(serverId);
        List<RemoteSessionSnapshot> removed = drainRemoteSessions(serverId, true);
        if (!removed.isEmpty()) {
            changed = true;
        }
        if (snapshots != null) {
            for (RemoteSessionSnapshot snapshot : snapshots) {
                if (registerRemoteSessionInternal(serverId, snapshot, false)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            publishClusterStateUpdate();
        }
        return changed;
    }

    public boolean registerRemoteSession(String fallbackServerId, RemoteSessionSnapshot snapshot) {
        return registerRemoteSessionInternal(fallbackServerId, snapshot, true);
    }

    private boolean registerRemoteSessionInternal(String fallbackServerId,
                                                  RemoteSessionSnapshot snapshot,
                                                  boolean notify) {
        if (snapshot == null || snapshot.getSessionId() == null) {
            return false;
        }
        String effectiveServerId = normalizeServerId(snapshot.getServerId());
        if (effectiveServerId == null || effectiveServerId.isBlank() || effectiveServerId.equals(localServerId)) {
            effectiveServerId = normalizeServerId(fallbackServerId);
        }
        if (effectiveServerId == null || effectiveServerId.isBlank() || effectiveServerId.equals(localServerId)) {
            effectiveServerId = resolveKnownAlias(snapshot, fallbackServerId);
        }
        if (effectiveServerId == null || effectiveServerId.isBlank() || effectiveServerId.equals(localServerId)) {
            return false;
        }

        RemoteSessionSnapshot copy = copySnapshot(snapshot);
        copy.setServerId(effectiveServerId);
        remapChannelMembership(copy);

        boolean conflictsRemoved = removeConflictingRemoteSessions(copy);

        boolean knownChanged = knownRemoteServers.add(effectiveServerId);

        String key = remoteKey(effectiveServerId, copy.getSessionId());
        RemoteSessionSnapshot current = remoteSessions.get(key);
        if (snapshotsEquivalent(current, copy)) {
            boolean changed = knownChanged || conflictsRemoved;
            if (changed && notify) {
                publishClusterStateUpdate();
            }
            return changed;
        }
        remoteSessions.put(key, copy);
        if (notify) {
            publishClusterStateUpdate();
        }
        return true;
    }

    private boolean removeConflictingRemoteSessions(RemoteSessionSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String effectiveServerId = snapshot.getServerId();
        String effectiveBase = baseServerId(effectiveServerId);
        Long clienteId = snapshot.getClienteId();
        String sessionId = snapshot.getSessionId();
        final boolean[] removed = {false};
        remoteSessions.entrySet().removeIf(entry -> {
            RemoteSessionSnapshot current = entry.getValue();
            if (current == null) {
                return false;
            }
            if (!Objects.equals(current.getClienteId(), clienteId)) {
                return false;
            }
            if (sessionId != null && !sessionId.equals(current.getSessionId())) {
                return false;
            }
            if (Objects.equals(current.getServerId(), effectiveServerId)) {
                return false;
            }
            String currentBase = baseServerId(current.getServerId());
            boolean shouldRemove = currentBase != null && currentBase.equalsIgnoreCase(effectiveBase);
            if (shouldRemove) {
                removed[0] = true;
            }
            return shouldRemove;
        });
        return removed[0];
    }

    public boolean removeRemoteSession(String serverId, String sessionId, Long clienteId) {
        boolean removed = false;
        if (serverId != null && sessionId != null) {
            removed = remoteSessions.remove(remoteKey(serverId, sessionId)) != null;
        }
        if (!removed && serverId != null && clienteId != null) {
            removed = remoteSessions.entrySet().removeIf(entry -> {
                RemoteSessionSnapshot snapshot = entry.getValue();
                return serverId.equals(snapshot.getServerId()) && clienteId.equals(snapshot.getClienteId());
            });
        }
        if (removed) {
            publishClusterStateUpdate();
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
        boolean changed;
        if (joined) {
            changed = snapshot.getCanales().add(canalId);
        } else {
            changed = snapshot.getCanales().remove(canalId);
        }
        if (changed) {
            publishClusterStateUpdate();
        }
        return changed;
    }

    public List<RemoteSessionSnapshot> drainRemoteSessions(String serverId) {
        return drainRemoteSessions(serverId, false);
    }

    public List<RemoteSessionSnapshot> drainRemoteSessions(String serverId, boolean silent) {
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
        if (!silent && !removed.isEmpty()) {
            publishClusterStateUpdate();
        }
        return removed;
    }

    public boolean clearRemoteSessions(String serverId) {
        return !drainRemoteSessions(serverId).isEmpty();
    }

    public void forgetRemoteServer(String serverId) {
        if (serverId == null) {
            return;
        }
        if (knownRemoteServers.remove(serverId)) {
            publishClusterStateUpdate();
        }
    }

    private RemoteSessionSnapshot copySnapshot(RemoteSessionSnapshot snapshot) {
        RemoteSessionSnapshot copy = new RemoteSessionSnapshot(
            snapshot.getServerId(),
            snapshot.getSessionId(),
            snapshot.getClienteId(),
            snapshot.getUsuario(),
            snapshot.getIp(),
            snapshot.getCanales()
        );
        copy.setEmail(snapshot.getEmail());
        copy.setChannelUuids(snapshot.getChannelUuids());
        return copy;
    }

    private void populateChannelMetadata(RemoteSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getCanales() == null) {
            return;
        }
        Map<Long, String> mapping = new HashMap<>();
        for (Long canalId : snapshot.getCanales()) {
            if (canalId == null) {
                continue;
            }
            canalRepository.findById(canalId)
                .map(Canal::getUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .ifPresent(uuid -> mapping.put(canalId, uuid));
        }
        snapshot.setChannelUuids(mapping);
    }

    private void remapChannelMembership(RemoteSessionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Set<Long> original = snapshot.getCanales() != null ? new HashSet<>(snapshot.getCanales()) : Set.of();
        Map<Long, String> provided = snapshot.getChannelUuids() != null
            ? new HashMap<>(snapshot.getChannelUuids())
            : Map.of();
        if (original.isEmpty() && provided.isEmpty()) {
            return;
        }
        Set<Long> remapped = new HashSet<>();
        Map<Long, String> updated = new HashMap<>();
        for (Long remoteId : original) {
            if (remoteId == null) {
                continue;
            }
            String uuid = provided.get(remoteId);
            Long localId = resolveChannelId(remoteId, uuid);
            if (localId != null) {
                remapped.add(localId);
                String resolvedUuid = resolveChannelUuid(localId, uuid);
                if (resolvedUuid != null && !resolvedUuid.isBlank()) {
                    updated.put(localId, resolvedUuid);
                }
            } else {
                remapped.add(remoteId);
                if (uuid != null && !uuid.isBlank()) {
                    updated.put(remoteId, uuid);
                }
            }
        }
        if (remapped.isEmpty() && !provided.isEmpty()) {
            for (String uuid : provided.values()) {
                Long localId = resolveChannelId(null, uuid);
                if (localId != null) {
                    remapped.add(localId);
                    String resolvedUuid = resolveChannelUuid(localId, uuid);
                    if (resolvedUuid != null && !resolvedUuid.isBlank()) {
                        updated.put(localId, resolvedUuid);
                    }
                }
            }
        }
        snapshot.setCanales(remapped);
        snapshot.setChannelUuids(updated);
    }

    private Long resolveChannelId(Long remoteId, String canalUuid) {
        if (canalRepository == null) {
            return remoteId;
        }
        if (canalUuid != null && !canalUuid.isBlank()) {
            Optional<Canal> byUuid = canalRepository.findByUuid(canalUuid);
            if (byUuid.isPresent()) {
                return byUuid.get().getId();
            }
            if (remoteId != null) {
                return canalRepository.findById(remoteId)
                    .filter(c -> canalUuid.equals(c.getUuid()))
                    .map(Canal::getId)
                    .orElse(null);
            }
            return null;
        }
        if (remoteId != null) {
            return canalRepository.findById(remoteId)
                .map(Canal::getId)
                .orElse(remoteId);
        }
        return null;
    }

    private String resolveChannelUuid(Long localId, String fallbackUuid) {
        if (localId == null) {
            return fallbackUuid;
        }
        return canalRepository.findById(localId)
            .map(Canal::getUuid)
            .filter(uuid -> uuid != null && !uuid.isBlank())
            .orElse(fallbackUuid);
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
            && Objects.equals(new HashSet<>(a.getCanales()), new HashSet<>(b.getCanales()))
            && Objects.equals(a.getChannelUuids(), b.getChannelUuids());
    }

    private String normalizeServerId(String serverId) {
        if (serverId == null) {
            return null;
        }
        String trimmed = serverId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveKnownAlias(RemoteSessionSnapshot snapshot, String fallbackServerId) {
        String host = extractHost(snapshot != null ? snapshot.getIp() : null);
        if (host == null) {
            host = extractAliasHost(fallbackServerId);
        }
        if (host == null) {
            return null;
        }
        for (String candidate : knownRemoteServers) {
            if (candidate == null || candidate.isBlank() || candidate.equals(localServerId)) {
                continue;
            }
            String candidateHost = extractAliasHost(candidate);
            if (candidateHost != null && candidateHost.equalsIgnoreCase(host)) {
                return candidate;
            }
        }
        return null;
    }

    private String extractAliasHost(String serverId) {
        if (serverId == null) {
            return null;
        }
        int separator = serverId.indexOf('@');
        if (separator < 0 || separator == serverId.length() - 1) {
            return null;
        }
        return extractHost(serverId.substring(separator + 1));
    }

    private String extractHost(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            trimmed = trimmed.substring(0, separator);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RemoteSessionSnapshot toSnapshot(SessionDescriptor descriptor) {
        Set<Long> canales = descriptor != null ? new HashSet<>(descriptor.getCanales()) : Set.of();
        RemoteSessionSnapshot snapshot = new RemoteSessionSnapshot(localServerId,
            descriptor != null ? descriptor.getSessionId() : null,
            descriptor != null ? descriptor.getClienteId() : null,
            descriptor != null ? descriptor.getUsuario() : null,
            descriptor != null ? descriptor.getIp() : null,
            canales);
        populateChannelMetadata(snapshot);
        return snapshot;
    }

    private void publishClusterStateUpdate() {
        eventBus.publish(new SessionEvent(SessionEventType.CLUSTER_STATE_UPDATED, null, null, knownServersSnapshot()));
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

    private String baseServerId(String serverId) {
        if (serverId == null) {
            return null;
        }
        int separator = serverId.indexOf('@');
        if (separator > 0) {
            return serverId.substring(0, separator);
        }
        return serverId;
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
