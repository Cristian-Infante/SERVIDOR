package com.arquitectura.controladores.p2p;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.controladores.conexion.RemoteSessionSnapshot;
import com.arquitectura.dto.ConnectionStatusUpdateDto;
import com.arquitectura.entidades.Canal;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ServerPeerManager {

    private static final Logger LOGGER = Logger.getLogger(ServerPeerManager.class.getName());
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=_-]+$");
    private static final Pattern BASE64_FALLBACK_PATTERN = Pattern.compile("([A-Za-z0-9+/=_-]{20,})");

    private final ConnectionRegistry registry;
    private final ObjectMapper mapper;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final Map<String, PeerConnection> peersByAlias = new ConcurrentHashMap<>();
    private final Map<String, PeerConnection> routeHints = new ConcurrentHashMap<>();
    private final Set<PeerConnection> connections = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<PeerStatusListener> peerListeners = new CopyOnWriteArrayList<>();
    private final String serverId;
    private final int peerPort;
    private final List<PeerAddress> bootstrapPeers;
    private final DatabaseSyncCoordinator databaseSync;
    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final LocalAliasRegistry localAliases;
    private final String instanceId;
    private final String normalizedServerId;
    
    // Sistema de confirmación de mensajes
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, Integer> replicationMetrics = new ConcurrentHashMap<>();
    private java.util.concurrent.ScheduledExecutorService retryExecutor;
    
    // Configuración de reintento
    private static final long MESSAGE_TIMEOUT_MS = 30000; // 30 segundos
    private static final int MAX_RETRIES = 3;

    private volatile boolean running;
    private ServerSocket serverSocket;

    public ServerPeerManager(String serverId,
                             int peerPort,
                             List<String> configuredPeers,
                             ConnectionRegistry registry,
                             DatabaseSyncCoordinator databaseSync,
                             ClienteRepository clienteRepository,
                             CanalRepository canalRepository) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.peerPort = peerPort;
        this.bootstrapPeers = parsePeers(configuredPeers);
        this.databaseSync = databaseSync;
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.localAliases = new LocalAliasRegistry(serverId);
        this.instanceId = UUID.randomUUID().toString();
        this.normalizedServerId = normalizeServerId(serverId);
        this.localAliases.initialize();
        
        // Inicializar el executor de reintento
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "P2P-Retry-" + this.serverId));
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        startAcceptor();
        connectToBootstrapPeers();
        startRetrySystem();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error cerrando socket de peers", e);
        }
        
        // Detener sistema de reintentos
        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdown();
            try {
                if (!retryExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        connections.forEach(PeerConnection::closeSilently);
        connections.clear();
        peers.clear();
        peersByAlias.clear();
        routeHints.clear();
        pendingMessages.clear();
    }

    public Set<String> connectedPeerIds() {
        return peers.keySet();
    }

    public Set<String> knownClusterServerIds() {
        TreeSet<String> servers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        servers.addAll(peers.keySet());
        servers.addAll(registry.knownServersSnapshot());
        servers.remove(serverId);
        return Collections.unmodifiableSet(servers);
    }

    public void addPeerStatusListener(PeerStatusListener listener) {
        if (listener != null) {
            peerListeners.addIfAbsent(listener);
        }
    }

    public void removePeerStatusListener(PeerStatusListener listener) {
        peerListeners.remove(listener);
    }

    public void connectToPeer(String host, int port) throws IOException {
        if (!running) {
            throw new IllegalStateException("El gestor de servidores no está activo");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }
        if (port == peerPort && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))) {
            throw new IllegalArgumentException("No se puede conectar a este mismo servidor");
        }

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 3_000);
            LOGGER.info(() -> "Conectado a servidor P2P " + host + ':' + port);
            registerConnection(new PeerConnection(socket, true));
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException suppressed) {
                LOGGER.log(Level.FINE, "Error cerrando socket tras fallo de conexión", suppressed);
            }
            throw e;
        }
    }

    public void notifyClientLogin(RemoteSessionSnapshot snapshot) {
        if (!running || snapshot == null || snapshot.getClienteId() == null) {
            return;
        }
        broadcast(PeerMessageType.CLIENT_CONNECTED, snapshot);
    }

    public void notifyClientLogout(RemoteSessionSnapshot snapshot) {
        if (!running || snapshot == null) {
            return;
        }
        ClientDisconnectionPayload payload = new ClientDisconnectionPayload();
        payload.setSessionId(snapshot.getSessionId());
        payload.setClienteId(snapshot.getClienteId());
        broadcast(PeerMessageType.CLIENT_DISCONNECTED, payload);
    }

    public void notifyChannelJoin(RemoteSessionSnapshot snapshot, Long canalId) {
        if (!running || snapshot == null || canalId == null) {
            return;
        }
        ChannelUpdatePayload payload = new ChannelUpdatePayload();
        payload.setSessionId(snapshot.getSessionId());
        payload.setClienteId(snapshot.getClienteId());
        payload.setCanalId(canalId);
        payload.setCanalUuid(resolveChannelUuid(canalId));
        payload.setAction("JOIN");
        broadcast(PeerMessageType.CHANNEL_MEMBERSHIP, payload);
    }

    public void broadcastDatabaseUpdate(DatabaseSnapshot snapshot) {
        if (!running || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        SyncStatePayload payload = new SyncStatePayload();
        payload.setDatabase(snapshot);
        PeerEnvelope envelope = new PeerEnvelope(PeerMessageType.SYNC_STATE, serverId, mapper.valueToTree(payload));
        routeEnvelope(null, envelope);
    }

    public void broadcast(Object payload) {
        broadcast(PeerMessageType.BROADCAST, payload);
    }

    public void forwardToUser(String targetServerId, Long userId, Object payload) {
        if (targetServerId == null || userId == null || payload == null) {
            return;
        }
        
        // Obtener el email del usuario para identificación global entre servidores
        String userEmail = null;
        try {
            userEmail = clienteRepository.findById(userId)
                .map(cliente -> cliente.getEmail())
                .orElse(null);
        } catch (Exception e) {
            LOGGER.warning(() -> "No se pudo obtener email para usuario " + userId + ": " + e.getMessage());
        }
        
        final String finalUserEmail = userEmail; // Para usar en lambda
        
        String messageId = generateMessageId();
        DirectMessagePayload message = new DirectMessagePayload();
        message.setMessageId(messageId);
        message.setUserId(userId);
        message.setUserEmail(userEmail); // Usar email para identificación global
        message.setMessage(mapper.valueToTree(payload));
        
        // Rastrear mensaje para confirmación
        PendingMessage pending = new PendingMessage(messageId, targetServerId, 
                                                   PeerMessageType.DIRECT_MESSAGE, 
                                                   mapper.valueToTree(message));
        pendingMessages.put(messageId, pending);
        
        LOGGER.info(() -> String.format("📤 Reenviando mensaje directo %s a servidor %s para usuario %d (email: %s)", 
            messageId, targetServerId, userId, finalUserEmail));
        
        sendToPeer(targetServerId, PeerMessageType.DIRECT_MESSAGE, message);
        incrementMetric("messages_sent");
    }

    public void forwardToChannel(String targetServerId, Long canalId, Object payload) {
        if (targetServerId == null || canalId == null || payload == null) {
            return;
        }
        
        String messageId = generateMessageId();
        ChannelMessagePayload message = new ChannelMessagePayload();
        message.setMessageId(messageId);
        message.setCanalId(canalId);
        message.setCanalUuid(resolveChannelUuid(canalId));
        message.setMessage(mapper.valueToTree(payload));
        
        // Rastrear mensaje para confirmación
        PendingMessage pending = new PendingMessage(messageId, targetServerId, 
                                                   PeerMessageType.CHANNEL_MESSAGE, 
                                                   mapper.valueToTree(message));
        pendingMessages.put(messageId, pending);
        
        LOGGER.info(() -> String.format("📤 Reenviando mensaje de canal %s a servidor %s para canal %d", 
            messageId, targetServerId, canalId));
        
        sendToPeer(targetServerId, PeerMessageType.CHANNEL_MESSAGE, message);
        incrementMetric("channel_messages_sent");
    }

    public void forwardToSession(String targetServerId, String sessionId, Object payload) {
        if (targetServerId == null || sessionId == null || payload == null) {
            return;
        }
        SessionForwardPayload message = new SessionForwardPayload();
        message.setSessionId(sessionId);
        message.setMessage(mapper.valueToTree(payload));
        sendToPeer(targetServerId, PeerMessageType.SESSION_MESSAGE, message);
    }

    private void broadcast(PeerMessageType type, Object payload) {
        JsonNode node;
        if (type == PeerMessageType.BROADCAST) {
            node = wrapBroadcastPayload(payload);
        } else {
            node = payload instanceof JsonNode ? (JsonNode) payload : mapper.valueToTree(payload);
        }
        PeerEnvelope envelope = new PeerEnvelope(type, serverId, node);
        for (PeerConnection connection : peers.values()) {
            connection.send(envelope);
        }
    }

    private void sendToPeer(String targetServerId, PeerMessageType type, Object payload) {
        if (targetServerId == null || targetServerId.isBlank()) {
            LOGGER.fine(() -> "Destino inválido para mensaje P2P " + type);
            return;
        }
        JsonNode node = payload instanceof JsonNode ? (JsonNode) payload : mapper.valueToTree(payload);
        PeerEnvelope envelope = new PeerEnvelope(type, serverId, targetServerId, node);
        routeEnvelope(null, envelope);
    }

    private void routeEnvelope(PeerConnection source, PeerEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        if (!envelope.markVisited(instanceId)) {
            LOGGER.fine(() -> "Mensaje " + envelope.getType() + " ya pasó por " + serverId + ", descartando para evitar bucles");
            return;
        }

        String target = envelope.getTarget();
        if (target != null) {
            PeerConnection direct = findPeerConnection(target);
            if (direct != null && direct != source) {
                direct.send(envelope);
                return;
            }
        }

        boolean forwarded = false;
        for (PeerConnection peer : peers.values()) {
            if (peer == source) {
                continue;
            }
            if (target != null && peer.matchesIdentity(target)) {
                peer.send(envelope);
                forwarded = true;
                continue;
            }
            String peerRouteId = peer.getRouteIdentifier();
            if (peerRouteId != null && envelope.hasVisited(peerRouteId)) {
                continue;
            }
            peer.send(envelope);
            forwarded = true;
        }

        if (!forwarded) {
            LOGGER.fine(() -> "No se encontró ruta para mensaje " + envelope.getType()
                + " con destino " + target);
        }
    }

    private PeerConnection findPeerConnection(String target) {
        if (target == null) {
            return null;
        }
        PeerConnection direct = peers.get(target);
        if (direct != null) {
            return direct;
        }
        String normalized = normalizeServerId(target);
        if (normalized == null) {
            return null;
        }
        PeerConnection alias = peersByAlias.get(normalized);
        if (alias != null) {
            return alias;
        }
        PeerConnection hinted = routeHints.get(normalized);
        if (hinted != null) {
            return hinted;
        }
        int separator = target.indexOf('@');
        if (separator > 0) {
            String prefix = target.substring(0, separator);
            alias = peersByAlias.get(normalizeServerId(prefix));
            if (alias != null) {
                return alias;
            }
            String suffix = target.substring(separator + 1);
            alias = peersByAlias.get(normalizeServerId(suffix));
            if (alias != null) {
                return alias;
            }
        }
        return null;
    }

    private void registerPeerAliases(PeerConnection connection) {
        if (connection == null) {
            return;
        }
        unregisterPeerAliases(connection);
        registerPeerAlias(connection, connection.getRemoteServerId());
        registerPeerAlias(connection, connection.getAnnouncedServerId());
        registerPeerAlias(connection, connection.getRemoteInstanceId());
    }

    private void registerPeerAlias(PeerConnection connection, String identifier) {
        String normalized = normalizeServerId(identifier);
        if (normalized == null) {
            return;
        }
        peersByAlias.put(normalized, connection);
        registerRouteHint(connection, identifier);
        int separator = identifier.indexOf('@');
        if (separator > 0) {
            String prefix = identifier.substring(0, separator);
            String suffix = identifier.substring(separator + 1);
            String normalizedPrefix = normalizeServerId(prefix);
            if (normalizedPrefix != null) {
                peersByAlias.putIfAbsent(normalizedPrefix, connection);
                registerRouteHintIdentifier(connection, normalizedPrefix);
            }
            String normalizedSuffix = normalizeServerId(suffix);
            if (normalizedSuffix != null) {
                peersByAlias.putIfAbsent(normalizedSuffix, connection);
                registerRouteHintIdentifier(connection, normalizedSuffix);
            }
        }
    }

    private void unregisterPeerAliases(PeerConnection connection) {
        if (connection == null) {
            return;
        }
        peersByAlias.entrySet().removeIf(entry -> entry.getValue() == connection);
    }

    private void registerRouteHint(PeerConnection connection, String identifier) {
        if (connection == null) {
            return;
        }
        String normalized = normalizeServerId(identifier);
        registerRouteHintIdentifier(connection, normalized);
        if (identifier == null) {
            return;
        }
        int separator = identifier.indexOf('@');
        if (separator > 0) {
            registerRouteHintIdentifier(connection, normalizeServerId(identifier.substring(0, separator)));
            registerRouteHintIdentifier(connection, normalizeServerId(identifier.substring(separator + 1)));
        }
    }

    private void registerRouteHintIdentifier(PeerConnection connection, String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        if (normalizedServerId != null && normalizedServerId.equals(normalized)) {
            return;
        }
        PeerConnection direct = peersByAlias.get(normalized);
        if (direct != null && direct != connection) {
            return;
        }
        routeHints.put(normalized, connection);
    }

    private void unregisterRouteHints(PeerConnection connection) {
        if (connection == null) {
            return;
        }
        routeHints.entrySet().removeIf(entry -> entry.getValue() == connection);
    }

    private String normalizeServerId(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void startAcceptor() {
        Thread acceptor = new Thread(() -> {
            try (ServerSocket listener = new ServerSocket()) {
                serverSocket = listener;
                listener.bind(new InetSocketAddress(peerPort));
                LOGGER.info(() -> "Servidor P2P escuchando en puerto " + peerPort);
                while (running) {
                    Socket socket = listener.accept();
                    LOGGER.info(() -> "Conexión entrante desde " + socket.getRemoteSocketAddress());
                    registerConnection(new PeerConnection(socket, false));
                }
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Error en el servidor P2P", e);
                }
            }
        }, "Peer-Acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void connectToBootstrapPeers() {
        for (PeerAddress address : bootstrapPeers) {
            Thread connector = new Thread(() -> {
                try {
                    connectToPeer(address.host(), address.port());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "No se pudo conectar al servidor P2P " + address, e);
                }
            }, "Peer-Connector-" + address.host() + ":" + address.port());
            connector.setDaemon(true);
            connector.start();
        }
    }

    private void registerConnection(PeerConnection connection) {
        if (connection != null && connection.socket != null) {
            localAliases.registerAddress(connection.socket.getLocalAddress());
        }
        connections.add(connection);
        connection.start();
    }

    private void onHello(PeerConnection connection, HelloPayload payload, String origin) {
        String announcedId = payload != null && payload.getServerId() != null ? payload.getServerId() : origin;
        String resolvedId = resolveRemoteId(connection, announcedId);
        if (resolvedId == null) {
            return;
        }
        if (payload != null && payload.getInstanceId() != null) {
            connection.setRemoteInstanceId(payload.getInstanceId());
        }
        connection.markHelloReceived(announcedId, resolvedId);
    }

    private void onHandshakeComplete(PeerConnection connection) {
        String remoteId = connection.getRemoteServerId();
        if (remoteId == null) {
            return;
        }
        PeerConnection previous = peers.put(remoteId, connection);
        if (previous != null && previous != connection) {
            unregisterPeerAliases(previous);
            unregisterRouteHints(previous);
            previous.closeSilently();
        }
        registerPeerAliases(connection);
        registerRouteHint(connection, remoteId);
        LOGGER.info(() -> String.format(
            "Conexión P2P establecida con servidor %s (%s, %s desde %s)",
            remoteId,
            connection.describeAnnouncedId(),
            connection.isInitiator() ? "iniciada" : "entrante",
            connection.remoteSummary()
        ));
        registry.markServerKnown(remoteId);
        LOGGER.info(() -> "Sincronizando con servidor " + remoteId);
        JsonNode syncPayload = sendSyncState(connection);
        broadcastRoutingUpdate(connection, syncPayload);
        notifyPeerConnected(remoteId);
    }

    private String resolveRemoteId(PeerConnection connection, String announcedId) {
        String trimmed = announcedId != null ? announcedId.trim() : null;
        if (trimmed == null || trimmed.isEmpty()) {
            String base = chooseAliasBase(connection, null);
            String fallbackCandidate = connection != null
                ? connection.fallbackIdentifier(base)
                : base;
            String fallback = ensureUniqueRemoteId(connection, fallbackCandidate);
            LOGGER.warning(() -> String.format(
                "Servidor remoto %s no envió un identificador válido. Se usará identificador alterno %s.",
                connection.remoteSummary(),
                fallback
            ));
            return fallback;
        }
        if (trimmed.equals(serverId)) {
            String base = chooseAliasBase(connection, trimmed);
            String fallbackCandidate = connection != null
                ? connection.fallbackIdentifier(base)
                : base;
            String fallback = ensureUniqueRemoteId(connection, fallbackCandidate);
            LOGGER.warning(() -> String.format(
                "Servidor remoto %s anunció el mismo ID (%s) que este servidor. Se utilizará identificador alterno %s.",
                connection.remoteSummary(),
                serverId,
                fallback
            ));
            return fallback;
        }
        String unique = ensureUniqueRemoteId(connection, trimmed);
        if (!unique.equals(trimmed)) {
            String original = trimmed;
            LOGGER.warning(() -> String.format(
                "El identificador remoto %s de %s ya está en uso. Se asignó identificador alterno %s.",
                original,
                connection.remoteSummary(),
                unique
            ));
        }
        return unique;
    }

    private String ensureUniqueRemoteId(PeerConnection connection, String candidate) {
        String current = candidate != null ? candidate.trim() : null;
        if (current == null || current.isEmpty()) {
            String aliasBase = chooseAliasBase(connection, null);
            current = connection != null ? connection.fallbackIdentifier(aliasBase) : aliasBase;
        }
        String aliasBase = chooseAliasBase(connection, extractBaseIdentifier(current));
        int attempts = 0;
        while (true) {
            String normalized = normalizeServerId(current);
            boolean conflict = normalized == null
                || normalized.equals(normalizedServerId)
                || isRemoteIdentifierInUse(current, normalized, connection);
            if (!conflict) {
                return current;
            }
            String nextBase = aliasBase;
            if (attempts > 0) {
                nextBase = aliasBase + '-' + attempts;
            }
            current = connection != null ? connection.fallbackIdentifier(nextBase) : nextBase;
            attempts++;
            if (attempts > 8) {
                String randomBase = aliasBase + '-' + UUID.randomUUID();
                current = connection != null ? connection.fallbackIdentifier(randomBase) : randomBase;
            }
        }
    }

    private String extractBaseIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int separator = trimmed.indexOf('@');
        if (separator > 0) {
            return trimmed.substring(0, separator);
        }
        return trimmed;
    }

    private String chooseAliasBase(PeerConnection connection, String preferredBase) {
        String sanitizedPreferred = sanitizeAliasBase(preferredBase);
        String normalizedPreferred = normalizeServerId(sanitizedPreferred);
        if (normalizedPreferred != null && normalizedPreferred.equals(normalizedServerId)) {
            sanitizedPreferred = null;
        }
        if (sanitizedPreferred != null) {
            return sanitizedPreferred;
        }
        if (connection != null) {
            String suggestion = connection.suggestAliasBase(serverId);
            String sanitizedSuggestion = sanitizeAliasBase(suggestion);
            if (sanitizedSuggestion != null) {
                return sanitizedSuggestion;
            }
        }
        if (serverId != null && !serverId.isBlank()) {
            String sanitizedLocal = sanitizeAliasBase(serverId + "-peer");
            String normalizedLocal = normalizeServerId(sanitizedLocal);
            if (sanitizedLocal != null && (normalizedLocal == null || !normalizedLocal.equals(normalizedServerId))) {
                return sanitizedLocal;
            }
        }
        return "peer";
    }

    private String sanitizeAliasBase(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(trimmed.length());
        boolean lastSeparator = false;
        for (char ch : trimmed.toCharArray()) {
            char lower = Character.toLowerCase(ch);
            if (Character.isLetterOrDigit(lower)) {
                sanitized.append(lower);
                lastSeparator = false;
            } else if (lower == '-' || lower == '_') {
                sanitized.append(lower);
                lastSeparator = false;
            } else if (!lastSeparator) {
                sanitized.append('-');
                lastSeparator = true;
            }
        }
        int start = 0;
        int end = sanitized.length();
        while (start < end && (sanitized.charAt(start) == '-' || sanitized.charAt(start) == '_')) {
            start++;
        }
        while (end > start && (sanitized.charAt(end - 1) == '-' || sanitized.charAt(end - 1) == '_')) {
            end--;
        }
        if (end <= start) {
            return null;
        }
        return sanitized.substring(start, end);
    }

    private boolean isRemoteIdentifierInUse(String identifier, String normalized, PeerConnection candidateConnection) {
        if (identifier != null) {
            PeerConnection existing = peers.get(identifier);
            if (existing != null && existing != candidateConnection && !isSameRemote(existing, candidateConnection)) {
                return true;
            }
        }
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        PeerConnection aliasPeer = peersByAlias.get(normalized);
        return aliasPeer != null && aliasPeer != candidateConnection && !isSameRemote(aliasPeer, candidateConnection);
    }

    private boolean isSameRemote(PeerConnection first, PeerConnection second) {
        if (first == null || second == null || first.socket == null || second.socket == null) {
            return false;
        }
        InetAddress addressFirst = first.socket.getInetAddress();
        InetAddress addressSecond = second.socket.getInetAddress();
        if (addressFirst != null && addressFirst.equals(addressSecond)) {
            return true;
        }
        return Objects.equals(first.socket.getRemoteSocketAddress(), second.socket.getRemoteSocketAddress());
    }

    private JsonNode sendSyncState(PeerConnection connection) {
        if (connection.getRemoteServerId() == null) {
            return null;
        }
        JsonNode payload = createSyncStatePayload();
        if (payload == null) {
            return null;
        }
        connection.send(new PeerEnvelope(PeerMessageType.SYNC_STATE, serverId, payload));
        return payload;
    }

    private JsonNode createSyncStatePayload() {
        SyncStatePayload payload = new SyncStatePayload();
        payload.setServers(registry.snapshotSessionsByServer());
        if (databaseSync != null) {
            try {
                payload.setDatabase(databaseSync.captureSnapshot());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error capturando snapshot de base de datos para sincronización", e);
            }
        }
        return mapper.valueToTree(payload);
    }

    private void broadcastRoutingUpdate(PeerConnection excluded, JsonNode payload) {
        if (payload == null) {
            return;
        }
        for (PeerConnection peer : peers.values()) {
            if (peer == excluded) {
                continue;
            }
            JsonNode copy = payload.deepCopy();
            peer.send(new PeerEnvelope(PeerMessageType.SYNC_STATE, serverId, copy));
        }
    }

    private String resolveRemoteServerAlias(PeerConnection connection, String declaredServerId) {
        if (declaredServerId == null || connection == null) {
            return declaredServerId;
        }
        if (!declaredServerId.equals(serverId)) {
            return declaredServerId;
        }
        String remoteAlias = connection.getRemoteServerId();
        if (remoteAlias != null && !remoteAlias.equals(serverId)) {
            return remoteAlias;
        }
        return declaredServerId;
    }

    private String resolveSnapshotServerId(PeerConnection connection, String declaredServerId, String fallbackAlias) {
        String normalizedDeclared = normalizeServerId(declaredServerId);
        if (normalizedDeclared == null || normalizedDeclared.equals(normalizedServerId)) {
            String normalizedFallback = normalizeServerId(fallbackAlias);
            if (normalizedFallback != null && !normalizedFallback.equals(normalizedServerId)) {
                return fallbackAlias;
            }
            if (connection != null) {
                String remoteAlias = connection.getRemoteServerId();
                String normalizedRemote = normalizeServerId(remoteAlias);
                if (normalizedRemote != null && !normalizedRemote.equals(normalizedServerId)) {
                    return remoteAlias;
                }
            }
        }
        if (declaredServerId != null) {
            return declaredServerId;
        }
        return fallbackAlias;
    }

    private List<RemoteSessionSnapshot> remapSnapshotsForServer(List<RemoteSessionSnapshot> snapshots, String serverId) {
        if (snapshots == null || serverId == null) {
            return snapshots;
        }
        List<RemoteSessionSnapshot> remapped = new ArrayList<>(snapshots.size());
        for (RemoteSessionSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            RemoteSessionSnapshot newSnapshot = new RemoteSessionSnapshot(
                serverId,
                snapshot.getSessionId(),
                snapshot.getClienteId(),
                snapshot.getUsuario(),
                snapshot.getIp(),
                snapshot.getCanales()
            );
            newSnapshot.setEmail(snapshot.getEmail());
            remapped.add(newSnapshot);
        }
        return remapped;
    }

    private boolean shouldLogPayload(PeerMessageType type) {
        if (type == null || type == PeerMessageType.HELLO) {
            return false;
        }
        // Log importante para mensajes de replicación y sus confirmaciones
        return type == PeerMessageType.DIRECT_MESSAGE || 
               type == PeerMessageType.CHANNEL_MESSAGE ||
               type == PeerMessageType.DIRECT_MESSAGE_ACK ||
               type == PeerMessageType.CHANNEL_MESSAGE_ACK ||
               type == PeerMessageType.REPLICATION_STATUS ||
               type == PeerMessageType.SYNC_STATE;
    }

    private void logIncomingPayload(PeerConnection connection, PeerEnvelope envelope, String rawJson) {
        if (!shouldLogPayload(envelope.getType())) {
            return;
        }
        String origin = envelope.getOrigin();
        if (origin == null || origin.isBlank()) {
            origin = Optional.ofNullable(connection.getRemoteServerId())
                .orElse(connection.remoteSummary());
        }
        String finalOrigin = origin;
        String message = rawJson != null ? sanitizePayloadForLogging(rawJson)
            : sanitizePayloadForLogging(envelope.toString());
        LOGGER.info(() -> String.format("Recibido %s desde %s: %s",
            envelope.getType(),
            finalOrigin,
            message));
    }

    private String sanitizePayloadForLogging(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode sanitized = sanitizeNode(node, null);
            return mapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return sanitizeBase64InRawString(json);
        }
    }

    private JsonNode sanitizeNode(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = objectNode.get(name);
                JsonNode sanitizedChild = sanitizeNode(child, name);
                if (sanitizedChild != child) {
                    objectNode.set(name, sanitizedChild);
                }
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                JsonNode sanitizedChild = sanitizeNode(child, fieldName);
                if (sanitizedChild != child) {
                    arrayNode.set(i, sanitizedChild);
                }
            }
            return arrayNode;
        }
        if (node.isTextual() && shouldTruncateBase64(fieldName, node.textValue())) {
            return TextNode.valueOf(truncateBase64(node.textValue()));
        }
        return node;
    }

    private boolean shouldTruncateBase64(String fieldName, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 10) {
            return false;
        }
        boolean looksLikeBase64 = BASE64_PATTERN.matcher(trimmed).matches();
        if (!looksLikeBase64) {
            return false;
        }
        if (fieldName != null) {
            String normalized = fieldName.toLowerCase(Locale.ROOT);
            if (normalized.contains("base64") || normalized.contains("audio")
                || normalized.contains("contenido") || normalized.contains("archivo")) {
                return true;
            }
        }
        return trimmed.length() > 64;
    }

    private String truncateBase64(String value) {
        if (value == null || value.length() <= 10) {
            return value;
        }
        return value.substring(0, 10) + "...";
    }

    private String sanitizeBase64InRawString(String raw) {
        Matcher matcher = BASE64_FALLBACK_PATTERN.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group(1);
            String replacement = truncateBase64(group);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void handleIncoming(PeerConnection connection, String json) {
        try {
            PeerEnvelope envelope = mapper.readValue(json, PeerEnvelope.class);
            PeerMessageType type = envelope.getType();
            if (type == null) {
                return;
            }
            logIncomingPayload(connection, envelope, json);
            if (connection != null) {
                registerRouteHint(connection, envelope.getOrigin());
            }
            String target = envelope.getTarget();
            if (target != null && !target.isBlank() && !localAliases.isLocal(target)) {
                routeEnvelope(connection, envelope);
                return;
            }
            switch (type) {
                case HELLO -> {
                    HelloPayload payload = mapper.treeToValue(envelope.getPayload(), HelloPayload.class);
                    onHello(connection, payload, envelope.getOrigin());
                }
                case SYNC_STATE -> handleSyncState(connection, envelope);
                case CLIENT_CONNECTED -> handleClientConnected(connection, envelope);
                case CLIENT_DISCONNECTED -> handleClientDisconnected(connection, envelope);
                case CHANNEL_MEMBERSHIP -> handleChannelMembership(connection, envelope);
                case DIRECT_MESSAGE -> handleDirectMessage(envelope.getPayload(), envelope.getOrigin());
                case CHANNEL_MESSAGE -> handleChannelMessage(envelope.getPayload(), envelope.getOrigin());
                case SESSION_MESSAGE -> handleSessionMessage(envelope.getPayload());
                case BROADCAST -> handleBroadcast(connection, envelope);
                case DIRECT_MESSAGE_ACK -> handleDirectMessageAck(envelope.getPayload());
                case CHANNEL_MESSAGE_ACK -> handleChannelMessageAck(envelope.getPayload());
                case REPLICATION_STATUS -> handleReplicationStatus(envelope.getPayload());
                default -> LOGGER.fine(() -> "Mensaje P2P no soportado: " + type);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error procesando mensaje P2P", e);
        }
    }

    private void handleSyncState(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        SyncStatePayload sync = mapper.treeToValue(envelope.getPayload(), SyncStatePayload.class);
        if (sync == null) {
            return;
        }
        boolean hasServers = sync.getServers() != null && !sync.getServers().isEmpty();
        boolean hasDatabase = sync.getDatabase() != null && !sync.getDatabase().isEmpty();
        if (!hasServers && !hasDatabase) {
            return;
        }

        boolean updated = false;
        boolean dbChanged = false;
        ObjectNode serversNode = null;
        JsonNode rawPayload = envelope.getPayload();
        if (rawPayload instanceof ObjectNode objectNode && objectNode.has("servers") && objectNode.get("servers").isObject()) {
            serversNode = (ObjectNode) objectNode.get("servers");
        }
        if (hasServers) {
            for (Map.Entry<String, List<RemoteSessionSnapshot>> entry : sync.getServers().entrySet()) {
                String declaredServerId = entry.getKey();
                String effectiveServerId = resolveRemoteServerAlias(connection, declaredServerId);
                if (effectiveServerId == null || effectiveServerId.equals(serverId)) {
                    continue;
                }
                List<RemoteSessionSnapshot> remap = remapSnapshotsForServer(entry.getValue(), effectiveServerId);
                if (serversNode != null && declaredServerId != null && !declaredServerId.equals(effectiveServerId)) {
                    serversNode.remove(declaredServerId);
                    serversNode.set(effectiveServerId, mapper.valueToTree(remap));
                }
                updated |= registry.registerRemoteSessions(effectiveServerId, remap);
                registerRouteHint(connection, effectiveServerId);
            }
        }
        if (hasDatabase && databaseSync != null) {
            try {
                dbChanged = databaseSync.applySnapshot(sync.getDatabase());
                if (dbChanged) {
                    LOGGER.info(() -> "Base de datos sincronizada con estado recibido de " +
                        Optional.ofNullable(connection.getRemoteServerId()).orElse("peer desconocido"));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error aplicando snapshot de base de datos recibido del clúster", e);
            }
        }
        if (updated || dbChanged) {
            relayStateUpdate(connection, PeerMessageType.SYNC_STATE, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleClientConnected(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        RemoteSessionSnapshot snapshot = mapper.treeToValue(envelope.getPayload(), RemoteSessionSnapshot.class);
        String fallbackId = resolveRemoteServerAlias(connection,
            envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId());
        registerRouteHint(connection, fallbackId);
        if (snapshot != null) {
            String resolvedServer = resolveSnapshotServerId(connection, snapshot.getServerId(), fallbackId);
            snapshot.setServerId(resolvedServer);
            JsonNode payloadNode = envelope.getPayload();
            if (payloadNode instanceof ObjectNode objectNode && resolvedServer != null) {
                objectNode.put("serverId", resolvedServer);
            }
        }
        boolean updated = registry.registerRemoteSession(fallbackId, snapshot);
        if (updated) {
            relayStateUpdate(connection, PeerMessageType.CLIENT_CONNECTED, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleClientDisconnected(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        ClientDisconnectionPayload data = mapper.treeToValue(envelope.getPayload(), ClientDisconnectionPayload.class);
        if (data == null) {
            return;
        }
        String fallbackId = resolveRemoteServerAlias(connection,
            envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId());
        registerRouteHint(connection, fallbackId);
        boolean removed = registry.removeRemoteSession(fallbackId, data.getSessionId(), data.getClienteId());
        if (removed) {
            relayStateUpdate(connection, PeerMessageType.CLIENT_DISCONNECTED, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleChannelMembership(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        ChannelUpdatePayload update = mapper.treeToValue(envelope.getPayload(), ChannelUpdatePayload.class);
        if (update == null) {
            return;
        }
        String fallbackId = resolveRemoteServerAlias(connection,
            envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId());
        registerRouteHint(connection, fallbackId);
        Long localCanalId = resolveChannelId(update.getCanalId(), update.getCanalUuid());
        if (localCanalId == null) {
            return;
        }
        boolean changed = registry.updateRemoteChannel(fallbackId,
            update.getSessionId(), localCanalId,
            "JOIN".equalsIgnoreCase(update.getAction()));
        if (changed) {
            relayStateUpdate(connection, PeerMessageType.CHANNEL_MEMBERSHIP, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleDirectMessage(JsonNode payload, String originServerId) throws IOException {
        DirectMessagePayload message = mapper.treeToValue(payload, DirectMessagePayload.class);
        boolean success = false;
        String error = null;
        
        try {
            if (message != null && message.getMessage() != null) {
                // Resolver el ID local del usuario usando el email (identificador global)
                Long localUserId = resolveLocalUserId(message.getUserId(), message.getUserEmail());
                
                if (localUserId != null) {
                    final Long finalLocalUserId = localUserId;
                    LOGGER.info(() -> String.format("📨 Recibido mensaje directo P2P para usuario local %d (email: %s, remoto: %d) desde %s", 
                        finalLocalUserId, message.getUserEmail(), message.getUserId(), originServerId));
                    registry.deliverToUserLocally(localUserId, message.getMessage());
                    success = true;
                    incrementMetric("messages_received");
                } else {
                    final String notFoundError = String.format("Usuario no encontrado localmente (email: %s, userId remoto: %d)", 
                        message.getUserEmail(), message.getUserId());
                    error = notFoundError;
                    LOGGER.warning(() -> notFoundError);
                }
            } else {
                error = "Payload inválido para mensaje directo";
                LOGGER.warning(() -> "Payload inválido en mensaje directo P2P: " + payload);
            }
        } catch (Exception e) {
            error = "Error procesando mensaje directo: " + e.getMessage();
            LOGGER.log(Level.WARNING, "Error procesando mensaje directo P2P", e);
        }
        
        // Enviar confirmación
        sendMessageAck(originServerId, message != null ? message.getMessageId() : null, 
                      PeerMessageType.DIRECT_MESSAGE_ACK, success, error);
    }
    
    /**
     * Resuelve el ID local del usuario usando el email como identificador global.
     * Si el email está disponible, lo usa para buscar el usuario local.
     * Si no, intenta usar el userId remoto directamente (fallback para compatibilidad).
     */
    private Long resolveLocalUserId(Long remoteUserId, String userEmail) {
        // Prioridad 1: Buscar por email (identificador global único)
        if (userEmail != null && !userEmail.isBlank()) {
            try {
                return clienteRepository.findByEmail(userEmail)
                    .map(cliente -> cliente.getId())
                    .orElse(null);
            } catch (Exception e) {
                LOGGER.warning(() -> "Error buscando usuario por email " + userEmail + ": " + e.getMessage());
            }
        }
        
        // Fallback: Usar el userId remoto directamente (solo funciona si las bases de datos están sincronizadas)
        LOGGER.warning(() -> String.format("⚠️ No se encontró email, usando userId remoto %d como fallback", remoteUserId));
        return remoteUserId;
    }

    private void handleChannelMessage(JsonNode payload, String originServerId) throws IOException {
        ChannelMessagePayload message = mapper.treeToValue(payload, ChannelMessagePayload.class);
        boolean success = false;
        String error = null;
        
        try {
            if (message == null || message.getMessage() == null) {
                error = "Payload inválido para mensaje de canal";
                LOGGER.warning(() -> "Payload inválido en mensaje de canal P2P: " + payload);
            } else {
                Long localCanalId = resolveChannelId(message.getCanalId(), message.getCanalUuid());
                if (localCanalId == null) {
                    String finalError = "Canal no encontrado: " + message.getCanalId() + "/" + message.getCanalUuid();
                    error = finalError;
                    LOGGER.warning(() -> finalError);
                } else {
                    LOGGER.info(() -> String.format("📨 Recibido mensaje de canal P2P para canal %d desde %s", 
                        localCanalId, originServerId));
                    registry.deliverToChannelLocally(localCanalId, message.getMessage());
                    success = true;
                    incrementMetric("channel_messages_received");
                }
            }
        } catch (Exception e) {
            error = "Error procesando mensaje de canal: " + e.getMessage();
            LOGGER.log(Level.WARNING, "Error procesando mensaje de canal P2P", e);
        }
        
        // Enviar confirmación
        sendMessageAck(originServerId, message != null ? message.getMessageId() : null, 
                      PeerMessageType.CHANNEL_MESSAGE_ACK, success, error);
    }

    private void handleSessionMessage(JsonNode payload) throws IOException {
        SessionForwardPayload message = mapper.treeToValue(payload, SessionForwardPayload.class);
        if (message != null && message.getSessionId() != null && message.getMessage() != null) {
            registry.deliverToSessionLocal(message.getSessionId(), message.getMessage());
        }
    }

    private void handleBroadcast(PeerConnection connection, PeerEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        JsonNode payload = envelope.getPayload();
        JsonNode messageNode = extractBroadcastMessage(payload);
        if (messageNode != null) {
            // Transformar el mensaje si es un USER_STATUS_CHANGED para usar ID local
            JsonNode transformedNode = transformUserStatusForLocalBroadcast(messageNode);
            registry.broadcastLocal(transformedNode);
            applyClusterSideEffects(messageNode);
        }
        relayStateUpdate(connection, PeerMessageType.BROADCAST, payload, envelope.getOrigin());
    }
    
    /**
     * Transforma un mensaje USER_STATUS_CHANGED para usar el ID local del usuario.
     * Esto es necesario porque los IDs de usuario pueden diferir entre servidores.
     */
    private JsonNode transformUserStatusForLocalBroadcast(JsonNode messageNode) {
        if (messageNode == null || !messageNode.isObject()) {
            return messageNode;
        }
        
        JsonNode eventNode = messageNode.get("evento");
        if (eventNode == null || !"USER_STATUS_CHANGED".equals(eventNode.asText())) {
            return messageNode; // No es un evento de estado, devolver sin modificar
        }
        
        JsonNode emailNode = messageNode.get("usuarioEmail");
        if (emailNode == null || emailNode.isNull()) {
            return messageNode; // No hay email, no podemos transformar
        }
        
        String email = emailNode.asText();
        if (email == null || email.isBlank()) {
            return messageNode;
        }
        
        // Buscar el usuario local por email
        var localCliente = clienteRepository.findByEmail(email).orElse(null);
        if (localCliente == null) {
            return messageNode; // No encontrado localmente, devolver sin modificar
        }
        
        // Crear una copia del mensaje con el ID local
        try {
            com.fasterxml.jackson.databind.node.ObjectNode modifiedNode = messageNode.deepCopy();
            modifiedNode.put("usuarioId", localCliente.getId());
            return modifiedNode;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error transformando USER_STATUS_CHANGED para broadcast local", e);
            return messageNode;
        }
    }

    private void applyClusterSideEffects(JsonNode messageNode) {
        if (messageNode == null || !messageNode.isObject()) {
            return;
        }
        JsonNode eventNode = messageNode.get("evento");
        if (eventNode == null) {
            return;
        }
        String eventType = eventNode.asText(null);
        if (eventType == null || !"USER_STATUS_CHANGED".equalsIgnoreCase(eventType)) {
            return;
        }
        try {
            ConnectionStatusUpdateDto update = mapper.treeToValue(messageNode, ConnectionStatusUpdateDto.class);
            
            // Primero intentar buscar por email (más confiable entre servidores con diferentes IDs)
            Long localUserId = null;
            if (update.getUsuarioEmail() != null && !update.getUsuarioEmail().isBlank()) {
                localUserId = clienteRepository.findByEmail(update.getUsuarioEmail())
                    .map(cliente -> cliente.getId())
                    .orElse(null);
            }
            
            // Fallback al ID si no encontramos por email
            if (localUserId == null) {
                localUserId = update.getUsuarioId();
            }
            
            if (localUserId == null) {
                LOGGER.warning(() -> "No se pudo resolver usuario local para actualización de estado: " + 
                    update.getUsuarioEmail());
                return;
            }
            
            clienteRepository.setConnected(localUserId, update.isConectado());
            
            final Long finalLocalUserId = localUserId;
            LOGGER.fine(() -> String.format(
                "Estado de usuario %d (email: %s) actualizado por broadcast remoto (%s)",
                finalLocalUserId,
                update.getUsuarioEmail(),
                update.isConectado() ? "conectado" : "desconectado"));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo aplicar actualización de estado recibida del clúster", e);
        }
    }

    // Métodos para el sistema de confirmación de mensajes
    private void sendMessageAck(String targetServerId, String messageId, PeerMessageType ackType, 
                                boolean success, String error) {
        if (targetServerId == null) {
            return;
        }
        
        MessageAck ack = new MessageAck();
        ack.setMessageId(messageId);
        ack.setOriginServerId(serverId);
        ack.setSuccess(success);
        ack.setError(error);
        
        LOGGER.info(() -> String.format("📤 Enviando ACK %s para mensaje %s a %s: %s", 
            ackType, messageId, targetServerId, success ? "SUCCESS" : "ERROR: " + error));
        
        sendToPeer(targetServerId, ackType, ack);
    }

    private void handleDirectMessageAck(JsonNode payload) throws IOException {
        MessageAck ack = mapper.treeToValue(payload, MessageAck.class);
        if (ack != null) {
            processMessageAck(ack, PeerMessageType.DIRECT_MESSAGE);
        }
    }

    private void handleChannelMessageAck(JsonNode payload) throws IOException {
        MessageAck ack = mapper.treeToValue(payload, MessageAck.class);
        if (ack != null) {
            processMessageAck(ack, PeerMessageType.CHANNEL_MESSAGE);
        }
    }

    private void handleReplicationStatus(JsonNode payload) throws IOException {
        ReplicationStatusPayload status = mapper.treeToValue(payload, ReplicationStatusPayload.class);
        if (status != null) {
            LOGGER.info(() -> String.format("📊 Estado de replicación recibido de %s: %s", 
                status.getServerId(), status.getMetrics()));
        }
    }

    private void processMessageAck(MessageAck ack, PeerMessageType originalType) {
        if (ack.getMessageId() == null) {
            return;
        }
        
        PendingMessage pending = pendingMessages.remove(ack.getMessageId());
        if (pending != null) {
            if (ack.isSuccess()) {
                LOGGER.info(() -> String.format("✅ Confirmación exitosa para mensaje %s (%s)", 
                    ack.getMessageId(), originalType));
                incrementMetric("messages_confirmed");
            } else {
                LOGGER.warning(() -> String.format("❌ Error en mensaje %s (%s): %s", 
                    ack.getMessageId(), originalType, ack.getError()));
                incrementMetric("messages_failed");
            }
        } else {
            LOGGER.fine(() -> String.format("Confirmación recibida para mensaje desconocido: %s", 
                ack.getMessageId()));
        }
    }

    private void incrementMetric(String key) {
        replicationMetrics.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    private String generateMessageId() {
        return serverId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public Map<String, Integer> getReplicationMetrics() {
        return new ConcurrentHashMap<>(replicationMetrics);
    }

    public List<String> getPendingMessageIds() {
        return new ArrayList<>(pendingMessages.keySet());
    }

    // Métodos de diagnóstico
    public String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== REPORTE DE ESTADO P2P ===\n");
        report.append(String.format("Servidor ID: %s\n", serverId));
        report.append(String.format("Estado: %s\n", running ? "ACTIVO" : "DETENIDO"));
        report.append(String.format("Puerto P2P: %d\n", peerPort));
        report.append("\n--- CONEXIONES ---\n");
        report.append(String.format("Peers conectados: %d\n", peers.size()));
        
        for (String peerId : peers.keySet()) {
            report.append(String.format("  - %s\n", peerId));
        }
        
        report.append("\n--- MÉTRICAS DE REPLICACIÓN ---\n");
        Map<String, Integer> metrics = getReplicationMetrics();
        if (metrics.isEmpty()) {
            report.append("No hay métricas disponibles\n");
        } else {
            for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
                report.append(String.format("%s: %d\n", entry.getKey(), entry.getValue()));
            }
        }
        
        report.append("\n--- MENSAJES PENDIENTES ---\n");
        List<String> pendingIds = getPendingMessageIds();
        report.append(String.format("Total pendientes: %d\n", pendingIds.size()));
        
        if (!pendingIds.isEmpty()) {
            report.append("IDs de mensajes pendientes:\n");
            for (String messageId : pendingIds) {
                PendingMessage pending = pendingMessages.get(messageId);
                if (pending != null) {
                    long ageMs = System.currentTimeMillis() - pending.getTimestamp();
                    report.append(String.format("  - %s (edad: %ds, reintentos: %d, destino: %s)\n", 
                        messageId, ageMs / 1000, pending.getRetryCount(), pending.getTargetServerId()));
                }
            }
        }
        
        report.append("\n--- ESTADO DE CONECTIVIDAD ---\n");
        Set<String> knownServers = knownClusterServerIds();
        report.append(String.format("Servidores conocidos en el clúster: %d\n", knownServers.size()));
        
        for (String serverId : knownServers) {
            boolean connected = peers.containsKey(serverId);
            report.append(String.format("  - %s: %s\n", serverId, connected ? "CONECTADO" : "DESCONECTADO"));
        }
        
        return report.toString();
    }

    public void sendReplicationStatusToAllPeers() {
        ReplicationStatusPayload status = new ReplicationStatusPayload();
        status.setServerId(serverId);
        status.setMetrics(getReplicationMetrics());
        status.setPendingMessages(getPendingMessageIds());
        
        broadcast(PeerMessageType.REPLICATION_STATUS, status);
        LOGGER.info("📊 Estado de replicación enviado a todos los peers");
    }

    public void printDiagnosticReport() {
        String report = generateDiagnosticReport();
        System.out.println(report);
        LOGGER.info("📊 Reporte de diagnóstico P2P generado");
    }

    private void startRetrySystem() {
        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            // Programar verificación periódica de mensajes pendientes cada 10 segundos
            retryExecutor.scheduleWithFixedDelay(this::checkPendingMessages, 10, 10, 
                java.util.concurrent.TimeUnit.SECONDS);
            LOGGER.info("🔄 Sistema de reintento P2P iniciado");
        }
    }

    private void checkPendingMessages() {
        if (!running) {
            return;
        }
        
        List<String> expiredMessageIds = new ArrayList<>();
        List<PendingMessage> toRetry = new ArrayList<>();
        
        // Identificar mensajes expirados y que necesitan reintento
        for (Map.Entry<String, PendingMessage> entry : pendingMessages.entrySet()) {
            PendingMessage pending = entry.getValue();
            
            if (pending.isExpired(MESSAGE_TIMEOUT_MS)) {
                if (pending.getRetryCount() >= MAX_RETRIES) {
                    // Mensaje falló definitivamente
                    expiredMessageIds.add(entry.getKey());
                    LOGGER.warning(() -> String.format("❌ Mensaje %s falló después de %d reintentos", 
                        pending.getMessageId(), pending.getRetryCount()));
                    incrementMetric("messages_failed_permanent");
                } else {
                    // Reintentar mensaje
                    toRetry.add(pending);
                }
            }
        }
        
        // Limpiar mensajes que fallaron definitivamente
        expiredMessageIds.forEach(pendingMessages::remove);
        
        // Reintentar mensajes
        for (PendingMessage pending : toRetry) {
            retryMessage(pending);
        }
        
        // Log de estado periódico
        if (!pendingMessages.isEmpty()) {
            LOGGER.info(() -> String.format("📊 Mensajes P2P pendientes: %d, reintentando: %d, fallaron: %d", 
                pendingMessages.size(), toRetry.size(), expiredMessageIds.size()));
        }
    }

    private void retryMessage(PendingMessage pending) {
        pending.incrementRetryCount();
        pending.updateTimestamp(); // Actualizar timestamp para nuevo timeout
        
        LOGGER.info(() -> String.format("🔄 Reintentando mensaje %s (intento %d/%d) a servidor %s", 
            pending.getMessageId(), pending.getRetryCount(), MAX_RETRIES, pending.getTargetServerId()));
        
        // Reenviar mensaje
        sendToPeer(pending.getTargetServerId(), pending.getMessageType(), pending.getPayload());
        incrementMetric("messages_retried");
    }

    private JsonNode wrapBroadcastPayload(Object payload) {
        if (payload instanceof JsonNode jsonNode) {
            if (jsonNode.hasNonNull("message") && jsonNode.size() == 1) {
                return jsonNode;
            }
            BroadcastPayload wrapper = new BroadcastPayload();
            wrapper.setMessage(jsonNode);
            return mapper.valueToTree(wrapper);
        }
        if (payload instanceof BroadcastPayload broadcastPayload && broadcastPayload.getMessage() != null) {
            return mapper.valueToTree(broadcastPayload);
        }
        BroadcastPayload wrapper = new BroadcastPayload();
        wrapper.setMessage(mapper.valueToTree(payload));
        return mapper.valueToTree(wrapper);
    }

    private JsonNode extractBroadcastMessage(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (payload.hasNonNull("message")) {
            return payload.get("message");
        }
        return payload;
    }

    private void relayStateUpdate(PeerConnection source, PeerMessageType type, JsonNode payload, String origin) {
        if (payload == null) {
            return;
        }
        String effectiveOrigin = resolveEffectiveOrigin(source, origin);
        PeerEnvelope envelope = new PeerEnvelope(type, effectiveOrigin, payload.deepCopy());
        routeEnvelope(source, envelope);
    }

    private Long resolveChannelId(Long canalId, String canalUuid) {
        if (canalRepository == null) {
            return canalId;
        }
        if (canalUuid != null && !canalUuid.isBlank()) {
            Optional<Canal> byUuid = canalRepository.findByUuid(canalUuid);
            if (byUuid.isPresent()) {
                return byUuid.get().getId();
            }
            if (canalId != null) {
                return canalRepository.findById(canalId)
                    .filter(c -> canalUuid.equals(c.getUuid()))
                    .map(Canal::getId)
                    .orElse(null);
            }
            return null;
        }
        if (canalId != null) {
            return canalRepository.findById(canalId)
                .map(Canal::getId)
                .orElse(canalId);
        }
        return null;
    }

    private String resolveChannelUuid(Long canalId) {
        if (canalRepository == null || canalId == null) {
            return null;
        }
        return canalRepository.findById(canalId)
            .map(Canal::getUuid)
            .orElse(null);
    }

    private String resolveEffectiveOrigin(PeerConnection source, String origin) {
        String effectiveOrigin = (origin == null || origin.isBlank()) ? serverId : origin;
        if (source == null) {
            return effectiveOrigin;
        }
        String remapped = resolveRemoteServerAlias(source, effectiveOrigin);
        return remapped != null ? remapped : effectiveOrigin;
    }



    private void onConnectionClosed(PeerConnection connection) {
        connections.remove(connection);
        String remoteId = connection.getRemoteServerId();
        if (remoteId != null) {
            boolean removed = peers.remove(remoteId, connection);
            unregisterPeerAliases(connection);
            unregisterRouteHints(connection);
            List<RemoteSessionSnapshot> drained = registry.drainRemoteSessions(remoteId);
            registry.forgetRemoteServer(remoteId);
            if (!drained.isEmpty()) {
                for (RemoteSessionSnapshot snapshot : drained) {
                    ClientDisconnectionPayload payload = new ClientDisconnectionPayload();
                    payload.setSessionId(snapshot.getSessionId());
                    payload.setClienteId(snapshot.getClienteId());
                    relayStateUpdate(null, PeerMessageType.CLIENT_DISCONNECTED,
                        mapper.valueToTree(payload), remoteId);
                    
                    // Notificar a los clientes locales que este usuario se desconectó
                    notifyLocalClientsUserDisconnected(snapshot);
                }
            }
            LOGGER.info(() -> "Conexión con servidor " + remoteId + " cerrada");
            if (removed) {
                notifyPeerDisconnected(remoteId);
            }
        }
    }
    
    /**
     * Notifica a los clientes locales que un usuario de un servidor remoto se ha desconectado.
     * Esto es necesario para actualizar el estado de los usuarios cuando un servidor peer se cae.
     */
    private void notifyLocalClientsUserDisconnected(RemoteSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getClienteId() == null) {
            return;
        }
        
        try {
            // Buscar el usuario local por email para obtener el ID correcto
            Long localUserId = null;
            String email = snapshot.getEmail();
            if (email != null && !email.isBlank()) {
                localUserId = clienteRepository.findByEmail(email)
                    .map(cliente -> cliente.getId())
                    .orElse(null);
            }
            
            if (localUserId == null) {
                LOGGER.fine(() -> "No se encontró usuario local para notificar desconexión: " + email);
                return;
            }
            
            // Actualizar el estado en la BD local
            clienteRepository.setConnected(localUserId, false);
            
            // Crear y enviar notificación a clientes locales
            ConnectionStatusUpdateDto dto = new ConnectionStatusUpdateDto();
            dto.setEvento("USER_STATUS_CHANGED");
            dto.setUsuarioId(localUserId);
            dto.setUsuarioNombre(snapshot.getUsuario());
            dto.setUsuarioEmail(email);
            dto.setConectado(false);
            dto.setSesionesActivas(0);
            dto.setTimestamp(java.time.LocalDateTime.now());
            
            registry.broadcastLocal(dto);
            
            final Long finalLocalUserId = localUserId;
            LOGGER.info(() -> String.format(
                "Notificado a clientes locales: usuario %d (%s) desconectado por caída del servidor remoto",
                finalLocalUserId, email));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error notificando desconexión de usuario remoto a clientes locales", e);
        }
    }

    private void notifyPeerConnected(String serverId) {
        if (serverId == null) {
            return;
        }
        for (PeerStatusListener listener : peerListeners) {
            try {
                listener.onPeerConnected(serverId);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error notificando conexión de peer", e);
            }
        }
    }

    private void notifyPeerDisconnected(String serverId) {
        if (serverId == null) {
            return;
        }
        for (PeerStatusListener listener : peerListeners) {
            try {
                listener.onPeerDisconnected(serverId);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error notificando desconexión de peer", e);
            }
        }
    }

    private List<PeerAddress> parsePeers(List<String> endpoints) {
        if (endpoints == null) {
            return List.of();
        }
        List<PeerAddress> addresses = new ArrayList<>();
        for (String endpoint : endpoints) {
            if (endpoint == null || endpoint.isBlank()) {
                continue;
            }
            String trimmed = endpoint.trim();
            int idx = trimmed.lastIndexOf(':');
            if (idx <= 0 || idx == trimmed.length() - 1) {
                LOGGER.warning(() -> "Formato inválido de peer: " + trimmed);
                continue;
            }
            String host = trimmed.substring(0, idx);
            int port;
            try {
                port = Integer.parseInt(trimmed.substring(idx + 1));
            } catch (NumberFormatException e) {
                LOGGER.warning(() -> "Puerto inválido en peer: " + trimmed);
                continue;
            }
            if (port == peerPort && (host.equals("localhost") || host.equals("127.0.0.1"))) {
                continue;
            }
            addresses.add(new PeerAddress(host, port));
        }
        return addresses;
    }

    private final class LocalAliasRegistry {
        private final String baseIdentifier;
        private final Set<String> identifiers = ConcurrentHashMap.newKeySet();
        private final Set<String> hostRepresentations = ConcurrentHashMap.newKeySet();

        private LocalAliasRegistry(String baseIdentifier) {
            this.baseIdentifier = baseIdentifier;
        }

        private void initialize() {
            if (baseIdentifier != null && !baseIdentifier.isBlank()) {
                identifiers.add(baseIdentifier);
            }
            discoverNetworkAddresses();
        }

        private void discoverNetworkAddresses() {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces == null) {
                    return;
                }
                for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                    if (networkInterface == null || !networkInterface.isUp()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    if (addresses == null) {
                        continue;
                    }
                    for (InetAddress address : Collections.list(addresses)) {
                        registerAddress(address);
                    }
                }
            } catch (SocketException e) {
                LOGGER.log(Level.FINE, "No se pudieron enumerar las direcciones locales para aliases", e);
            }
        }

        void registerAddress(InetAddress address) {
            if (address == null || address.isAnyLocalAddress()
                || baseIdentifier == null || baseIdentifier.isBlank()) {
                return;
            }
            registerHostRepresentation(address.getHostAddress());
            registerHostRepresentation(address.getHostName());
            registerHostRepresentation(address.getCanonicalHostName());
        }

        private void registerHostRepresentation(String representation) {
            if (representation == null || representation.isBlank()) {
                return;
            }
            if (hostRepresentations.add(representation)) {
                identifiers.add(baseIdentifier + "@" + representation);
            }
        }

        boolean isLocal(String candidate) {
            if (candidate == null) {
                return false;
            }
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            if (identifiers.contains(trimmed)) {
                return true;
            }
            if (baseIdentifier != null && baseIdentifier.equalsIgnoreCase(trimmed)) {
                identifiers.add(trimmed);
                return true;
            }
            int separator = trimmed.indexOf('@');
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                return false;
            }
            String prefix = trimmed.substring(0, separator);
            String hostPart = trimmed.substring(separator + 1);
            if (hostPart.isBlank()) {
                return false;
            }
            for (String knownHost : hostRepresentations) {
                if (knownHost.equalsIgnoreCase(hostPart)) {
                    identifiers.add(trimmed);
                    registerHostRepresentation(hostPart);
                    return true;
                }
            }
            if (baseIdentifier != null && baseIdentifier.equalsIgnoreCase(prefix)) {
                registerHostRepresentation(hostPart);
                identifiers.add(trimmed);
                return true;
            }
            // NO registrar automáticamente cualquier dirección con el mismo prefijo de servidor
            // Solo es local si la IP/host coincide con las direcciones conocidas de este servidor
            return false;
        }
    }

    private final class PeerConnection {
        private final Socket socket;
        private final boolean initiator;
        private BufferedReader reader;
        private BufferedWriter writer;
        private final ExecutorService outboundExecutor;
        private volatile String remoteServerId;
        private volatile String announcedServerId;
        private volatile boolean helloSent;
        private volatile boolean helloReceived;
        private volatile String remoteInstanceId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PeerConnection(Socket socket, boolean initiator) {
            this.socket = socket;
            this.initiator = initiator;
            this.outboundExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r,
                    "PeerConnection-Writer-" + socket.getRemoteSocketAddress());
                thread.setDaemon(true);
                return thread;
            });
        }

        private void start() {
            Thread thread = new Thread(() -> {
                try {
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    if (initiator) {
                        sendHello();
                    }
                    String line;
                    while ((line = reader.readLine()) != null) {
                        handleIncoming(this, line);
                    }
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.FINE, "Conexión P2P cerrada: " + e.getMessage(), e);
                    }
                } finally {
                    closeWithNotification();
                }
            }, "PeerConnection-" + socket.getRemoteSocketAddress());
            thread.setDaemon(true);
            thread.start();
        }

        private void send(PeerEnvelope envelope) {
            if (envelope == null) {
                return;
            }
            try {
                outboundExecutor.execute(() -> doSend(envelope));
            } catch (RejectedExecutionException ex) {
                LOGGER.log(Level.FINE, "No se pudo encolar mensaje para peer (conexión cerrada)", ex);
            }
        }

        private void doSend(PeerEnvelope envelope) {
            try {
                if (writer == null) {
                    return;
                }
                String serialized = mapper.writeValueAsString(envelope);
                logOutgoingPayload(envelope, serialized);
                synchronized (writer) {
                    writer.write(serialized);
                    writer.write('\n');
                    writer.flush();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error enviando mensaje P2P", e);
                closeWithNotification();
            }
        }

        private void sendHello() {
            if (helloSent) {
                return;
            }
            helloSent = true;
            send(new PeerEnvelope(PeerMessageType.HELLO, serverId,
                mapper.valueToTree(new HelloPayload(serverId, instanceId))));
            if (helloReceived) {
                onHandshakeComplete(this);
            }
        }

        private void markHelloReceived(String announcedId, String resolvedId) {
            if (announcedServerId == null) {
                announcedServerId = announcedId;
            }
            if (remoteServerId == null) {
                remoteServerId = resolvedId;
            }
            if (remoteInstanceId == null) {
                remoteInstanceId = resolvedId;
            }
            helloReceived = true;
            registerPeerAliases(this);
            if (!helloSent) {
                sendHello();
            } else {
                onHandshakeComplete(this);
            }
        }

        private void setRemoteInstanceId(String remoteInstanceId) {
            if (remoteInstanceId != null && !remoteInstanceId.isBlank()) {
                this.remoteInstanceId = remoteInstanceId;
                registerPeerAliases(this);
            }
        }

        private String getRouteIdentifier() {
            return remoteInstanceId != null ? remoteInstanceId : remoteServerId;
        }

        private void closeSilently() {
            outboundExecutor.shutdownNow();
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private void closeWithNotification() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            closeSilently();
            onConnectionClosed(this);
        }

        private String getRemoteServerId() {
            return remoteServerId;
        }

        private String getAnnouncedServerId() {
            return announcedServerId;
        }

        private String getRemoteInstanceId() {
            return remoteInstanceId;
        }

        private boolean isInitiator() {
            return initiator;
        }

        private String describeAnnouncedId() {
            if (announcedServerId == null || announcedServerId.equals(remoteServerId)) {
                return remoteServerId != null ? "ID confirmado" : "sin ID";
            }
            return "anunció '" + announcedServerId + "'";
        }

        private void logOutgoingPayload(PeerEnvelope envelope, String serialized) {
            if (!shouldLogPayload(envelope.getType())) {
                return;
            }
            String destinationLabel = envelope.getTarget();
            if (destinationLabel == null) {
                destinationLabel = remoteServerId != null ? remoteServerId
                    : announcedServerId != null ? announcedServerId : remoteSummary();
            } else {
                String routeTarget = remoteServerId != null ? remoteServerId
                    : announcedServerId != null ? announcedServerId : remoteSummary();
                if (!destinationLabel.equals(routeTarget)) {
                    destinationLabel = destinationLabel + " vía " + routeTarget;
                }
            }
            String finalDestination = destinationLabel;
            String sanitized = sanitizePayloadForLogging(serialized);
            LOGGER.info(() -> String.format("Enviando %s a %s: %s",
                envelope.getType(),
                finalDestination,
                sanitized));
        }

        private String remoteSummary() {
            String address = Optional.ofNullable(socket.getRemoteSocketAddress())
                .map(Object::toString)
                .orElse("dirección desconocida");
            if (address.startsWith("/")) {
                address = address.substring(1);
            }
            return address;
        }

        private String fallbackIdentifier(String base) {
            String host = Optional.ofNullable(socket.getInetAddress())
                .map(inet -> inet.getHostAddress() != null ? inet.getHostAddress() : inet.getHostName())
                .filter(addr -> !addr.isBlank())
                .orElse(remoteSummary());
            String sanitizedBase = ServerPeerManager.this.sanitizeAliasBase(base);
            if (sanitizedBase == null) {
                sanitizedBase = "peer";
            }
            return sanitizedBase + "@" + host;
        }

        private String suggestAliasBase(String localServerId) {
            InetAddress address = socket != null ? socket.getInetAddress() : null;
            String host = null;
            if (address != null) {
                String hostName = address.getHostName();
                String hostAddress = address.getHostAddress();
                if (hostName != null && !hostName.isBlank() && !hostName.equals(hostAddress)) {
                    host = hostName;
                } else {
                    host = hostAddress;
                }
            }
            String sanitizedHost = ServerPeerManager.this.sanitizeAliasBase(host);
            String normalizedLocal = ServerPeerManager.this.normalizeServerId(localServerId);
            if (sanitizedHost != null) {
                String normalizedHost = ServerPeerManager.this.normalizeServerId(sanitizedHost);
                if (normalizedLocal != null && normalizedLocal.equals(normalizedHost)) {
                    sanitizedHost = ServerPeerManager.this.sanitizeAliasBase(sanitizedHost + "-peer");
                }
                if (sanitizedHost != null) {
                    return sanitizedHost;
                }
            }
            String sanitizedSummary = ServerPeerManager.this.sanitizeAliasBase(remoteSummary());
            if (sanitizedSummary != null) {
                String normalizedSummary = ServerPeerManager.this.normalizeServerId(sanitizedSummary);
                if (normalizedLocal != null && normalizedLocal.equals(normalizedSummary)) {
                    sanitizedSummary = ServerPeerManager.this.sanitizeAliasBase(sanitizedSummary + "-peer");
                }
                if (sanitizedSummary != null) {
                    return sanitizedSummary;
                }
            }
            if (localServerId != null && !localServerId.isBlank()) {
                String fallback = ServerPeerManager.this.sanitizeAliasBase(localServerId + "-peer");
                if (fallback != null) {
                    return fallback;
                }
            }
            return "peer";
        }

        private boolean matchesIdentity(String candidate) {
            if (candidate == null) {
                return false;
            }
            String normalized = normalizeServerId(candidate);
            if (normalized == null) {
                return false;
            }
            if (matchesNormalized(normalized, remoteServerId)) {
                return true;
            }
            if (matchesNormalized(normalized, announcedServerId)) {
                return true;
            }
            return matchesNormalized(normalized, remoteInstanceId);
        }

        private boolean matchesNormalized(String normalizedCandidate, String identifier) {
            String normalizedIdentifier = normalizeServerId(identifier);
            return normalizedIdentifier != null && normalizedIdentifier.equals(normalizedCandidate);
        }
    }

    private enum PeerMessageType {
        HELLO,
        SYNC_STATE,
        CLIENT_CONNECTED,
        CLIENT_DISCONNECTED,
        CHANNEL_MEMBERSHIP,
        DIRECT_MESSAGE,
        CHANNEL_MESSAGE,
        SESSION_MESSAGE,
        BROADCAST,
        DIRECT_MESSAGE_ACK,
        CHANNEL_MESSAGE_ACK,
        REPLICATION_STATUS
    }

    private static final class PeerEnvelope {
        private PeerMessageType type;
        private String origin;
        private String target;
        private List<String> route;
        private JsonNode payload;

        private PeerEnvelope() {
        }

        private PeerEnvelope(PeerMessageType type, String origin, JsonNode payload) {
            this(type, origin, null, payload);
        }

        private PeerEnvelope(PeerMessageType type, String origin, String target, JsonNode payload) {
            this.type = type;
            this.origin = origin;
            this.target = target;
            this.payload = payload;
        }

        public PeerMessageType getType() {
            return type;
        }

        public void setType(PeerMessageType type) {
            this.type = type;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public void setPayload(JsonNode payload) {
            this.payload = payload;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public List<String> getRoute() {
            return route;
        }

        public void setRoute(List<String> route) {
            this.route = route;
        }

        private synchronized boolean markVisited(String serverId) {
            if (serverId == null || serverId.isBlank()) {
                return true;
            }
            if (route == null) {
                route = new ArrayList<>();
            }
            if (route.contains(serverId)) {
                return false;
            }
            route.add(serverId);
            return true;
        }

        private synchronized boolean hasVisited(String serverId) {
            if (serverId == null || serverId.isBlank() || route == null) {
                return false;
            }
            return route.contains(serverId);
        }
    }

    private static final class HelloPayload {
        private String serverId;
        private String instanceId;

        private HelloPayload() {
        }

        private HelloPayload(String serverId, String instanceId) {
            this.serverId = serverId;
            this.instanceId = instanceId;
        }

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }
    }

    private static final class SyncStatePayload {
        private Map<String, List<RemoteSessionSnapshot>> servers = new ConcurrentHashMap<>();
        private DatabaseSnapshot database;

        public Map<String, List<RemoteSessionSnapshot>> getServers() {
            return servers;
        }

        public void setServers(Map<String, List<RemoteSessionSnapshot>> servers) {
            this.servers = servers != null ? new ConcurrentHashMap<>(servers) : new ConcurrentHashMap<>();
        }

        public DatabaseSnapshot getDatabase() {
            return database;
        }

        public void setDatabase(DatabaseSnapshot database) {
            this.database = database;
        }
    }

    private static final class ClientDisconnectionPayload {
        private String sessionId;
        private Long clienteId;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public Long getClienteId() {
            return clienteId;
        }

        public void setClienteId(Long clienteId) {
            this.clienteId = clienteId;
        }
    }

    private static final class ChannelUpdatePayload {
        private String sessionId;
        private Long clienteId;
        private Long canalId;
        private String action;
        private String canalUuid;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public Long getClienteId() {
            return clienteId;
        }

        public void setClienteId(Long clienteId) {
            this.clienteId = clienteId;
        }

        public Long getCanalId() {
            return canalId;
        }

        public void setCanalId(Long canalId) {
            this.canalId = canalId;
        }

        public String getCanalUuid() {
            return canalUuid;
        }

        public void setCanalUuid(String canalUuid) {
            this.canalUuid = canalUuid;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }

    private static final class DirectMessagePayload {
        private String messageId;
        private Long userId;
        private String userEmail; // Email para identificación global entre servidores
        private JsonNode message;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public String getUserEmail() {
            return userEmail;
        }
        
        public void setUserEmail(String userEmail) {
            this.userEmail = userEmail;
        }

        public JsonNode getMessage() {
            return message;
        }

        public void setMessage(JsonNode message) {
            this.message = message;
        }
    }

    private static final class ChannelMessagePayload {
        private String messageId;
        private Long canalId;
        private JsonNode message;
        private String canalUuid;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public Long getCanalId() {
            return canalId;
        }

        public void setCanalId(Long canalId) {
            this.canalId = canalId;
        }

        public String getCanalUuid() {
            return canalUuid;
        }

        public void setCanalUuid(String canalUuid) {
            this.canalUuid = canalUuid;
        }

        public JsonNode getMessage() {
            return message;
        }

        public void setMessage(JsonNode message) {
            this.message = message;
        }
    }

    private static final class SessionForwardPayload {
        private String sessionId;
        private JsonNode message;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public JsonNode getMessage() {
            return message;
        }

        public void setMessage(JsonNode message) {
            this.message = message;
        }
    }

    private static final class BroadcastPayload {
        private JsonNode message;

        public JsonNode getMessage() {
            return message;
        }

        public void setMessage(JsonNode message) {
            this.message = message;
        }
    }

    private record PeerAddress(String host, int port) {
        @Override
        public String toString() {
            return host + ':' + port;
        }
    }

    // Clases para el sistema de confirmación de mensajes
    private static final class PendingMessage {
        private final String messageId;
        private final String targetServerId;
        private final PeerMessageType messageType;
        private final JsonNode payload;
        private long timestamp;
        private int retryCount = 0;

        public PendingMessage(String messageId, String targetServerId, PeerMessageType messageType, JsonNode payload) {
            this.messageId = messageId;
            this.targetServerId = targetServerId;
            this.messageType = messageType;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getMessageId() { return messageId; }
        public String getTargetServerId() { return targetServerId; }
        public PeerMessageType getMessageType() { return messageType; }
        public JsonNode getPayload() { return payload; }
        public long getTimestamp() { return timestamp; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { retryCount++; }
        public void updateTimestamp() { this.timestamp = System.currentTimeMillis(); }
        
        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - timestamp > timeoutMs;
        }
    }

    private static final class MessageAck {
        private String messageId;
        private String originServerId;
        private boolean success;
        private String error;

        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getOriginServerId() { return originServerId; }
        public void setOriginServerId(String originServerId) { this.originServerId = originServerId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    private static final class ReplicationStatusPayload {
        private Map<String, Integer> metrics;
        private List<String> pendingMessages;
        private String serverId;

        public Map<String, Integer> getMetrics() { return metrics; }
        public void setMetrics(Map<String, Integer> metrics) { this.metrics = metrics; }
        public List<String> getPendingMessages() { return pendingMessages; }
        public void setPendingMessages(List<String> pendingMessages) { this.pendingMessages = pendingMessages; }
        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }
    }

    public interface PeerStatusListener {
        void onPeerConnected(String serverId);

        void onPeerDisconnected(String serverId);
    }
}
