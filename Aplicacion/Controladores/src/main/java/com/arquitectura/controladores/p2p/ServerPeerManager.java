package com.arquitectura.controladores.p2p;

import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.controladores.conexion.RemoteSessionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerPeerManager {

    private static final Logger LOGGER = Logger.getLogger(ServerPeerManager.class.getName());

    private final ConnectionRegistry registry;
    private final ObjectMapper mapper;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final Set<PeerConnection> connections = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<PeerStatusListener> peerListeners = new CopyOnWriteArrayList<>();
    private final String serverId;
    private final int peerPort;
    private final List<PeerAddress> bootstrapPeers;

    private volatile boolean running;
    private ServerSocket serverSocket;

    public ServerPeerManager(String serverId,
                             int peerPort,
                             List<String> configuredPeers,
                             ConnectionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.peerPort = peerPort;
        this.bootstrapPeers = parsePeers(configuredPeers);
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        startAcceptor();
        connectToBootstrapPeers();
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
        connections.forEach(PeerConnection::closeSilently);
        connections.clear();
        peers.clear();
    }

    public Set<String> connectedPeerIds() {
        return peers.keySet();
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
        payload.setAction("JOIN");
        broadcast(PeerMessageType.CHANNEL_MEMBERSHIP, payload);
    }

    public void broadcast(Object payload) {
        broadcast(PeerMessageType.BROADCAST, payload);
    }

    public void forwardToUser(String targetServerId, Long userId, Object payload) {
        if (targetServerId == null || userId == null || payload == null) {
            return;
        }
        DirectMessagePayload message = new DirectMessagePayload();
        message.setUserId(userId);
        message.setMessage(mapper.valueToTree(payload));
        sendToPeer(targetServerId, PeerMessageType.DIRECT_MESSAGE, message);
    }

    public void forwardToChannel(String targetServerId, Long canalId, Object payload) {
        if (targetServerId == null || canalId == null || payload == null) {
            return;
        }
        ChannelMessagePayload message = new ChannelMessagePayload();
        message.setCanalId(canalId);
        message.setMessage(mapper.valueToTree(payload));
        sendToPeer(targetServerId, PeerMessageType.CHANNEL_MESSAGE, message);
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
        JsonNode node = payload instanceof JsonNode ? (JsonNode) payload : mapper.valueToTree(payload);
        PeerEnvelope envelope = new PeerEnvelope(type, serverId, node);
        for (PeerConnection connection : peers.values()) {
            connection.send(envelope);
        }
    }

    private void sendToPeer(String targetServerId, PeerMessageType type, Object payload) {
        PeerConnection connection = peers.get(targetServerId);
        if (connection == null) {
            LOGGER.fine(() -> "No hay conexión con el servidor " + targetServerId + " para reenviar mensaje");
            return;
        }
        JsonNode node = payload instanceof JsonNode ? (JsonNode) payload : mapper.valueToTree(payload);
        PeerEnvelope envelope = new PeerEnvelope(type, serverId, node);
        connection.send(envelope);
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
        connections.add(connection);
        connection.start();
    }

    private void onHello(PeerConnection connection, HelloPayload payload, String origin) {
        String announcedId = payload != null && payload.getServerId() != null ? payload.getServerId() : origin;
        String resolvedId = resolveRemoteId(connection, announcedId);
        if (resolvedId == null) {
            return;
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
            previous.closeSilently();
        }
        LOGGER.info(() -> String.format(
            "Conexión P2P establecida con servidor %s (%s, %s desde %s)",
            remoteId,
            connection.describeAnnouncedId(),
            connection.isInitiator() ? "iniciada" : "entrante",
            connection.remoteSummary()
        ));
        LOGGER.info(() -> "Sincronizando con servidor " + remoteId);
        sendSyncState(connection);
        notifyPeerConnected(remoteId);
    }

    private String resolveRemoteId(PeerConnection connection, String announcedId) {
        String trimmed = announcedId != null ? announcedId.trim() : null;
        if (trimmed == null || trimmed.isEmpty()) {
            String fallback = connection.fallbackIdentifier("peer");
            LOGGER.warning(() -> String.format(
                "Servidor remoto %s no envió un identificador válido. Se usará identificador alterno %s.",
                connection.remoteSummary(),
                fallback
            ));
            return fallback;
        }
        if (trimmed.equals(serverId)) {
            String fallback = connection.fallbackIdentifier(trimmed);
            LOGGER.warning(() -> String.format(
                "Servidor remoto %s anunció el mismo ID (%s) que este servidor. Se utilizará identificador alterno %s.",
                connection.remoteSummary(),
                serverId,
                fallback
            ));
            return fallback;
        }
        return trimmed;
    }

    private void sendSyncState(PeerConnection connection) {
        if (connection.getRemoteServerId() == null) {
            return;
        }
        SyncStatePayload payload = new SyncStatePayload();
        payload.setServers(registry.snapshotSessionsByServer());
        connection.send(new PeerEnvelope(PeerMessageType.SYNC_STATE, serverId, mapper.valueToTree(payload)));
    }

    private boolean shouldLogPayload(PeerMessageType type) {
        return type != null && type != PeerMessageType.HELLO;
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
        String message = rawJson != null ? rawJson : envelope.toString();
        LOGGER.info(() -> String.format("Recibido %s desde %s: %s",
            envelope.getType(),
            finalOrigin,
            message));
    }

    private void handleIncoming(PeerConnection connection, String json) {
        try {
            PeerEnvelope envelope = mapper.readValue(json, PeerEnvelope.class);
            PeerMessageType type = envelope.getType();
            if (type == null) {
                return;
            }
            logIncomingPayload(connection, envelope, json);
            switch (type) {
                case HELLO -> {
                    HelloPayload payload = mapper.treeToValue(envelope.getPayload(), HelloPayload.class);
                    onHello(connection, payload, envelope.getOrigin());
                }
                case SYNC_STATE -> handleSyncState(connection, envelope);
                case CLIENT_CONNECTED -> handleClientConnected(connection, envelope);
                case CLIENT_DISCONNECTED -> handleClientDisconnected(connection, envelope);
                case CHANNEL_MEMBERSHIP -> handleChannelMembership(connection, envelope);
                case DIRECT_MESSAGE -> handleDirectMessage(envelope.getPayload());
                case CHANNEL_MESSAGE -> handleChannelMessage(envelope.getPayload());
                case SESSION_MESSAGE -> handleSessionMessage(envelope.getPayload());
                case BROADCAST -> handleBroadcast(envelope.getPayload());
                default -> LOGGER.fine(() -> "Mensaje P2P no soportado: " + type);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error procesando mensaje P2P", e);
        }
    }

    private void handleSyncState(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        SyncStatePayload sync = mapper.treeToValue(envelope.getPayload(), SyncStatePayload.class);
        if (sync == null || sync.getServers().isEmpty()) {
            return;
        }
        boolean updated = false;
        for (Map.Entry<String, List<RemoteSessionSnapshot>> entry : sync.getServers().entrySet()) {
            String targetServer = entry.getKey();
            if (targetServer == null || targetServer.equals(serverId)) {
                continue;
            }
            updated |= registry.registerRemoteSessions(targetServer, entry.getValue());
        }
        if (updated) {
            relayStateUpdate(connection, PeerMessageType.SYNC_STATE, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleClientConnected(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        RemoteSessionSnapshot snapshot = mapper.treeToValue(envelope.getPayload(), RemoteSessionSnapshot.class);
        String fallbackId = envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId();
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
        String fallbackId = envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId();
        boolean removed = registry.removeRemoteSession(fallbackId, data.getSessionId(), data.getClienteId());
        if (removed) {
            relayStateUpdate(connection, PeerMessageType.CLIENT_DISCONNECTED, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleChannelMembership(PeerConnection connection, PeerEnvelope envelope) throws IOException {
        ChannelUpdatePayload update = mapper.treeToValue(envelope.getPayload(), ChannelUpdatePayload.class);
        if (update == null || update.getCanalId() == null) {
            return;
        }
        String fallbackId = envelope.getOrigin() != null ? envelope.getOrigin() : connection.getRemoteServerId();
        boolean changed = registry.updateRemoteChannel(fallbackId, update.getSessionId(), update.getCanalId(),
            "JOIN".equalsIgnoreCase(update.getAction()));
        if (changed) {
            relayStateUpdate(connection, PeerMessageType.CHANNEL_MEMBERSHIP, envelope.getPayload(), envelope.getOrigin());
        }
    }

    private void handleDirectMessage(JsonNode payload) throws IOException {
        DirectMessagePayload message = mapper.treeToValue(payload, DirectMessagePayload.class);
        if (message != null && message.getUserId() != null && message.getMessage() != null) {
            registry.deliverToUserLocally(message.getUserId(), message.getMessage());
        }
    }

    private void handleChannelMessage(JsonNode payload) throws IOException {
        ChannelMessagePayload message = mapper.treeToValue(payload, ChannelMessagePayload.class);
        if (message != null && message.getCanalId() != null && message.getMessage() != null) {
            registry.deliverToChannelLocally(message.getCanalId(), message.getMessage());
        }
    }

    private void handleSessionMessage(JsonNode payload) throws IOException {
        SessionForwardPayload message = mapper.treeToValue(payload, SessionForwardPayload.class);
        if (message != null && message.getSessionId() != null && message.getMessage() != null) {
            registry.deliverToSessionLocal(message.getSessionId(), message.getMessage());
        }
    }

    private void handleBroadcast(JsonNode payload) throws IOException {
        BroadcastPayload message = mapper.treeToValue(payload, BroadcastPayload.class);
        if (message != null && message.getMessage() != null) {
            registry.broadcastLocal(message.getMessage());
        }
    }

    private void relayStateUpdate(PeerConnection source, PeerMessageType type, JsonNode payload, String origin) {
        if (payload == null) {
            return;
        }
        String effectiveOrigin = origin != null ? origin : serverId;
        JsonNode copy = payload.deepCopy();
        PeerEnvelope envelope = new PeerEnvelope(type, effectiveOrigin, copy);
        for (PeerConnection peer : peers.values()) {
            if (peer == source) {
                continue;
            }
            if (effectiveOrigin != null && peer.matchesIdentity(effectiveOrigin)) {
                continue;
            }
            peer.send(envelope);
        }
    }

    private void onConnectionClosed(PeerConnection connection) {
        connections.remove(connection);
        String remoteId = connection.getRemoteServerId();
        if (remoteId != null) {
            boolean removed = peers.remove(remoteId, connection);
            registry.clearRemoteSessions(remoteId);
            LOGGER.info(() -> "Conexión con servidor " + remoteId + " cerrada");
            if (removed) {
                notifyPeerDisconnected(remoteId);
            }
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

    private final class PeerConnection {
        private final Socket socket;
        private final boolean initiator;
        private BufferedReader reader;
        private BufferedWriter writer;
        private volatile String remoteServerId;
        private volatile String announcedServerId;
        private volatile boolean helloSent;
        private volatile boolean helloReceived;

        private PeerConnection(Socket socket, boolean initiator) {
            this.socket = socket;
            this.initiator = initiator;
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
                    closeSilently();
                    onConnectionClosed(this);
                }
            }, "PeerConnection-" + socket.getRemoteSocketAddress());
            thread.setDaemon(true);
            thread.start();
        }

        private void send(PeerEnvelope envelope) {
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
                closeSilently();
                onConnectionClosed(this);
            }
        }

        private void sendHello() {
            if (helloSent) {
                return;
            }
            helloSent = true;
            send(new PeerEnvelope(PeerMessageType.HELLO, serverId,
                mapper.valueToTree(new HelloPayload(serverId))));
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
            helloReceived = true;
            if (!helloSent) {
                sendHello();
            } else {
                onHandshakeComplete(this);
            }
        }

        private void closeSilently() {
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

        private String getRemoteServerId() {
            return remoteServerId;
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
            String target = remoteServerId != null ? remoteServerId
                : announcedServerId != null ? announcedServerId : remoteSummary();
            String finalTarget = target;
            LOGGER.info(() -> String.format("Enviando %s a %s: %s",
                envelope.getType(),
                finalTarget,
                serialized));
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
            String sanitizedBase = (base != null && !base.isBlank()) ? base : "peer";
            return sanitizedBase + "@" + host;
        }

        private boolean matchesIdentity(String candidate) {
            if (candidate == null) {
                return false;
            }
            return candidate.equals(remoteServerId) || candidate.equals(announcedServerId);
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
        BROADCAST
    }

    private static final class PeerEnvelope {
        private PeerMessageType type;
        private String origin;
        private JsonNode payload;

        private PeerEnvelope() {
        }

        private PeerEnvelope(PeerMessageType type, String origin, JsonNode payload) {
            this.type = type;
            this.origin = origin;
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
    }

    private static final class HelloPayload {
        private String serverId;

        private HelloPayload() {
        }

        private HelloPayload(String serverId) {
            this.serverId = serverId;
        }

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }
    }

    private static final class SyncStatePayload {
        private Map<String, List<RemoteSessionSnapshot>> servers = new ConcurrentHashMap<>();

        public Map<String, List<RemoteSessionSnapshot>> getServers() {
            return servers;
        }

        public void setServers(Map<String, List<RemoteSessionSnapshot>> servers) {
            this.servers = servers != null ? new ConcurrentHashMap<>(servers) : new ConcurrentHashMap<>();
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

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }

    private static final class DirectMessagePayload {
        private Long userId;
        private JsonNode message;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public JsonNode getMessage() {
            return message;
        }

        public void setMessage(JsonNode message) {
            this.message = message;
        }
    }

    private static final class ChannelMessagePayload {
        private Long canalId;
        private JsonNode message;

        public Long getCanalId() {
            return canalId;
        }

        public void setCanalId(Long canalId) {
            this.canalId = canalId;
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

    public interface PeerStatusListener {
        void onPeerConnected(String serverId);

        void onPeerDisconnected(String serverId);
    }
}
