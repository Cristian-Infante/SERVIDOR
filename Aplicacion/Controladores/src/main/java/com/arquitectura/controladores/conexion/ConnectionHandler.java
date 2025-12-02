package com.arquitectura.controladores.conexion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.arquitectura.dto.AckResponse;
import com.arquitectura.dto.ChannelRequest;
import com.arquitectura.dto.CommandEnvelope;
import com.arquitectura.dto.ErrorResponse;
import com.arquitectura.dto.InviteRequest;
import com.arquitectura.dto.LoginRequest;
import com.arquitectura.dto.LoginResponse;
import com.arquitectura.dto.MessageRequest;
import com.arquitectura.dto.MessageSyncResponse;
import com.arquitectura.dto.RegisterRequest;
import com.arquitectura.dto.UploadAudioRequest;
import com.arquitectura.dto.UploadAudioResponse;
import com.arquitectura.entidades.Canal;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.MessageSyncService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.metrics.ServerMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.prometheus.client.Histogram;

public class ConnectionHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());
    private static final int BASE64_PREVIEW_LENGTH = 10;

    private final RegistroService registroService;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final AudioStorageService audioStorageService;
    private final MessageSyncService messageSyncService;
    private final SessionEventBus eventBus;
    private final ConnectionRegistry registry;
    private final com.arquitectura.controladores.p2p.ServerPeerManager peerManager;
    private ConnectionHandlerPool pool;
    private final ObjectMapper mapper;
    
    {
        // Configurar Jackson para manejar LocalDateTime
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String sessionId;
    private Long clienteId;

    public ConnectionHandler(RegistroService registroService,
                              CanalService canalService,
                              MensajeriaService mensajeriaService,
                              ReporteService reporteService,
                              ConexionService conexionService,
                              AudioStorageService audioStorageService,
                              MessageSyncService messageSyncService,
                              SessionEventBus eventBus,
                              ConnectionRegistry registry,
                              com.arquitectura.controladores.p2p.ServerPeerManager peerManager) {
        this.registroService = Objects.requireNonNull(registroService, "registroService");
        this.canalService = Objects.requireNonNull(canalService, "canalService");
        this.mensajeriaService = Objects.requireNonNull(mensajeriaService, "mensajeriaService");
        this.reporteService = Objects.requireNonNull(reporteService, "reporteService");
        this.conexionService = Objects.requireNonNull(conexionService, "conexionService");
        this.audioStorageService = Objects.requireNonNull(audioStorageService, "audioStorageService");
        this.messageSyncService = Objects.requireNonNull(messageSyncService, "messageSyncService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.peerManager = peerManager; // Puede ser null si P2P está deshabilitado
    }

    public void attach(Socket socket) {
        this.socket = socket;
    }

    public void setPool(ConnectionHandlerPool pool) {
        this.pool = pool;
    }

    @Override
    public void run() {
        try {
            sessionId = registry.register(socket);
            writer = registry.writerOf(sessionId);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            listen();
        } catch (java.net.SocketException e) {
            // Conexión cerrada por el cliente, no loguear como error
            LOGGER.log(Level.FINE, "Cliente desconectado: {0}", e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error registrando conexión", e);
        } finally {
            cleanup();
            if (pool != null) {
                pool.release(this);
            }
        }
    }

    private void listen() throws IOException {

        String line;

        while ((line = reader.readLine()) != null) {

            if (line.isBlank()) {

                continue;

            }

            String rawCommand = "";

            Histogram.Timer latencyTimer = null;

            Histogram.Timer responseTimer = null;

            try {

                JsonNode node = mapper.readTree(line);

                rawCommand = node.hasNonNull("command") ? node.get("command").asText() : "";

                String command = rawCommand.toUpperCase(Locale.ROOT);

                JsonNode payload = node.get("payload");



                LOGGER.log(Level.INFO, "Comando recibido: {0}", command);

                if (payload != null) {

                    LOGGER.log(Level.INFO, "Payload: {0}", sanitizePayload(command, payload));

                }



                responseTimer = ServerMetrics.startServerResponseTimer();

                latencyTimer = ServerMetrics.startCommandTimer(command);

                processCommand(command, payload);

                ServerMetrics.finishCommand(command, "success", latencyTimer);

            } catch (IllegalArgumentException e) {

                LOGGER.log(Level.INFO, "Error de validación: {0}", e.getMessage());

                String cmd = normalizeCommandForMetrics(rawCommand);

                ServerMetrics.recordCommandError(cmd, "validation_error");

                ServerMetrics.finishCommand(cmd, "validation_error", latencyTimer);

                send("ERROR", new ErrorResponse(e.getMessage()));

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {

                LOGGER.log(Level.WARNING, "JSON inválido: {0}", e.getMessage());

                String cmd = normalizeCommandForMetrics(rawCommand);

                ServerMetrics.recordCommandError(cmd, "json_error");

                ServerMetrics.finishCommand(cmd, "json_error", latencyTimer);

                send("ERROR", new ErrorResponse("Formato JSON inválido"));

            } catch (Exception e) {

                LOGGER.log(Level.WARNING, "Error procesando comando: {0}", e.getMessage());

                String cmd = normalizeCommandForMetrics(rawCommand);

                ServerMetrics.recordCommandError(cmd, "internal_error");

                ServerMetrics.finishCommand(cmd, "internal_error", latencyTimer);

                send("ERROR", new ErrorResponse("Error interno del servidor"));

            } finally {

                ServerMetrics.observeServerResponse(responseTimer);

            }

        }

    }


    private void processCommand(String command, JsonNode payload) throws IOException {
        switch (command) {
            case "PING" -> handlePing();
            case "REGISTER" -> handleRegister(payload);
            case "LOGIN" -> handleLogin(payload);
            case "LOGOUT" -> handleLogout();
            case "UPLOAD_AUDIO" -> handleUploadAudio(payload);
            case "SEND_USER" -> handleSendUser(payload);
            case "SEND_CHANNEL" -> handleSendChannel(payload);
            case "CREATE_CHANNEL" -> handleCreateChannel(payload);
            case "INVITE" -> handleInvite(payload);
            case "ACCEPT" -> handleAccept(payload);
            case "REJECT" -> handleReject(payload);
            case "LIST_RECEIVED_INVITATIONS" -> handleListReceivedInvitations();
            case "LIST_SENT_INVITATIONS" -> handleListSentInvitations();
            case "LIST_USERS" -> send("LIST_USERS", reporteService.usuariosRegistrados(clienteId));
            case "LIST_CHANNELS" -> {
                ensureAuthenticated();
                send("LIST_CHANNELS", reporteService.canalesAccesiblesParaUsuario(clienteId));
            }
            case "LIST_CONNECTED" -> send("LIST_CONNECTED", reporteService.usuariosConectados(clienteId));
            case "CLOSE_CONN" -> handleClose();
            case "BROADCAST" -> handleBroadcast(payload);
            case "P2P_STATUS" -> handleP2PStatus();
            case "P2P_METRICS" -> handleP2PMetrics();
            case "P2P_PENDING" -> handleP2PPendingMessages();
            case "P2P_SEND_STATUS" -> handleP2PSendStatus();
            default -> processReports(command);
        }
    }

    private void processReports(String command) throws IOException {
        switch (command) {
            case "REPORT_USUARIOS" -> send("REPORT_USUARIOS", reporteService.usuariosRegistrados(clienteId));
            case "REPORT_CANALES" -> send("REPORT_CANALES", reporteService.canalesConUsuarios());
            case "REPORT_CONECTADOS" -> send("REPORT_CONECTADOS", reporteService.usuariosConectados(clienteId));
            case "REPORT_AUDIO" -> send("REPORT_AUDIO", reporteService.textoDeMensajesDeAudio());
            case "REPORT_LOGS" -> send("REPORT_LOGS", reporteService.logs());
            default -> send("ERROR", new ErrorResponse("Comando no soportado: " + command));
        }
    }

    private void handlePing() throws IOException {
        send("PING", new AckResponse("PONG"));
    }

    private void handleRegister(JsonNode payload) throws IOException {
        RegisterRequest request = mapper.treeToValue(payload, RegisterRequest.class);
        byte[] foto = new byte[0];
        if (request.getFotoBase64() != null) {
            foto = Base64.getDecoder().decode(request.getFotoBase64());
        }
        var cliente = registroService.registrarCliente(request.getUsuario(), request.getEmail(), request.getContrasenia(), foto, request.getIp());
        send("REGISTER", new AckResponse("Registro exitoso. Por favor inicia sesión."));
    }

    private void handleLogin(JsonNode payload) throws IOException {
        LoginRequest request = mapper.treeToValue(payload, LoginRequest.class);
        String ip = request.getIp();
        if ((ip == null || ip.isBlank()) && socket != null && socket.getInetAddress() != null) {
            ip = socket.getInetAddress().getHostAddress();
        }

        var cliente = registroService.autenticarCliente(request.getEmail(), request.getContrasenia(), ip);
        this.clienteId = cliente.getId();
        if (ip != null) {
            ip = ip.trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = cliente.getIp();
        }
        registry.updateCliente(sessionId, cliente.getId(), cliente.getNombreDeUsuario(), ip);

        // Enviar respuesta de login exitoso lo antes posible para evitar timeouts en el cliente
        String fotoBase64 = null;
        byte[] foto = cliente.getFoto();
        if (foto != null && foto.length > 0) {
            fotoBase64 = Base64.getEncoder().encodeToString(foto);
        }

        send("LOGIN", new LoginResponse(true, "Login exitoso", fotoBase64));

        try {
            eventBus.publish(new SessionEvent(SessionEventType.LOGIN, sessionId, cliente.getId(), null));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al notificar evento de login", e);
        }
        
        // Sincronizar mensajes del usuario
        try {
            MessageSyncResponse syncResponse = messageSyncService.sincronizarMensajes(cliente.getId());
            send("MESSAGE_SYNC", syncResponse);
            LOGGER.info(() -> "Mensajes sincronizados para usuario " + cliente.getNombreDeUsuario() + 
                             ": " + syncResponse.getTotalMensajes() + " mensajes");
        } catch (Exception e) {
            LOGGER.warning(() -> "Error sincronizando mensajes para usuario " + cliente.getId() + ": " + e.getMessage());
            // No fallar el login por error de sincronización
        }
    }

    private void handleLogout() throws IOException {
        ensureAuthenticated();
        
        Long userId = this.clienteId;
        String userName = registry.descriptorOf(sessionId) != null 
            ? registry.descriptorOf(sessionId).getUsuario() 
            : "Usuario desconocido";
        
        // Publicar evento de logout antes de limpiar el estado
        eventBus.publish(new SessionEvent(SessionEventType.LOGOUT, sessionId, userId, null));
        
        // Limpiar el estado de autenticación pero mantener la conexión
        registry.updateCliente(sessionId, null, null, null);
        this.clienteId = null;
        
        send("LOGOUT", new AckResponse("Sesión cerrada exitosamente"));
        LOGGER.info(() -> "Usuario '" + userName + "' (ID: " + userId + ") cerró sesión");
    }

    private void handleUploadAudio(JsonNode payload) throws IOException {
        ensureAuthenticated();
        UploadAudioRequest request = mapper.treeToValue(payload, UploadAudioRequest.class);
        
        try {
            String rutaGuardada = audioStorageService.guardarAudio(
                request.getAudioBase64(),
                clienteId,
                request.getMime()
            );
            
            UploadAudioResponse response = new UploadAudioResponse(
                true,
                rutaGuardada,
                "Audio guardado exitosamente"
            );
            send("UPLOAD_AUDIO", response);
            
        } catch (IllegalArgumentException e) {
            send("ERROR", new ErrorResponse("Datos de audio inválidos: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error guardando audio", e);
            send("ERROR", new ErrorResponse("Error guardando audio en el servidor"));
        }
    }

    private void handleSendUser(JsonNode payload) throws IOException {
        ensureAuthenticated();
        MessageRequest request = mapper.treeToValue(payload, MessageRequest.class);
        request.setEmisor(clienteId);
        mensajeriaService.enviarMensajeAUsuario(request);
        send("SEND_USER", new AckResponse("Mensaje enviado"));
    }

    private void handleSendChannel(JsonNode payload) throws IOException {
        ensureAuthenticated();
        MessageRequest request = mapper.treeToValue(payload, MessageRequest.class);
        request.setEmisor(clienteId);
        mensajeriaService.enviarMensajeACanal(request);
        send("SEND_CHANNEL", new AckResponse("Mensaje a canal enviado"));
    }

    private void handleCreateChannel(JsonNode payload) throws IOException {
        ensureAuthenticated();
        ChannelRequest request = mapper.treeToValue(payload, ChannelRequest.class);
        var canal = canalService.crearCanal(request.getNombre(), request.isPrivado(), clienteId);
        registry.joinChannel(sessionId, canal.getId());
        send("CREATE_CHANNEL", canal);
    }

    private void handleInvite(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        request.setSolicitanteId(clienteId);
        canalService.invitarUsuario(request.getCanalId(), request.getCanalUuid(), request.getSolicitanteId(), request.getInvitadoId());
        send("INVITE", new AckResponse("Invitación enviada"));
    }

    private void handleAccept(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        Canal canal = canalService.aceptarInvitacion(request.getCanalId(), request.getCanalUuid(), clienteId);
        registry.joinChannel(sessionId, canal.getId());
        send("ACCEPT", new AckResponse("Canal aceptado"));
    }

    private void handleReject(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        canalService.rechazarInvitacion(request.getCanalId(), request.getCanalUuid(), clienteId);
        send("REJECT", new AckResponse("Invitación rechazada"));
    }

    private void handleListReceivedInvitations() throws IOException {
        ensureAuthenticated();
        send("LIST_RECEIVED_INVITATIONS", canalService.obtenerInvitacionesRecibidas(clienteId));
    }

    private void handleListSentInvitations() throws IOException {
        ensureAuthenticated();
        send("LIST_SENT_INVITATIONS", canalService.obtenerInvitacionesEnviadas(clienteId));
    }

    private void handleBroadcast(JsonNode payload) throws IOException {
        ensureAuthenticated();
        String message = payload != null && payload.hasNonNull("message") ? payload.get("message").asText() : "";
        conexionService.broadcast(message);
        send("BROADCAST", new AckResponse("Broadcast enviado"));
    }

    private void handleClose() throws IOException {
        send("CLOSE_CONN", new AckResponse("Conexión cerrada"));
        conexionService.cerrarConexion(sessionId);
    }

    private void ensureAuthenticated() {
        if (clienteId == null) {
            throw new IllegalStateException("La sesión no está autenticada");
        }
    }

    private String sanitizePayload(String command, JsonNode payload) {
        if (payload == null) {
            return "null";
        }
        try {
            JsonNode copy = payload.deepCopy();

            // Para LOGIN y REGISTER, ocultar la contraseña
            if ("LOGIN".equalsIgnoreCase(command) || "REGISTER".equalsIgnoreCase(command)) {
                if (copy instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                    if (objectNode.has("contrasenia")) {
                        objectNode.put("contrasenia", "*******");
                    }
                }
            }

            JsonNode sanitized = sanitizeBase64ForLogging(copy);
            return sanitized != null ? sanitized.toPrettyString() : "null";
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private void send(String command, Object payload) throws IOException {
        if (writer == null) {
            return;
        }
        CommandEnvelope response = new CommandEnvelope(command, payload);
        String jsonResponse = mapper.writeValueAsString(response);
        writer.write(jsonResponse);
        writer.write('\n');
        writer.flush();
        
        // Logging de la respuesta enviada
        LOGGER.log(Level.INFO, "Respuesta enviada: {0}", command);
        try {
            JsonNode responseNode = mapper.readTree(jsonResponse);
            JsonNode sanitizedNode = sanitizeBase64ForLogging(responseNode.deepCopy());
            LOGGER.log(Level.INFO, "Payload respuesta: {0}", sanitizedNode.toPrettyString());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo formatear la respuesta para logging", e);
        }
    }

    private JsonNode sanitizeBase64ForLogging(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();

                if (child.isTextual() && shouldTruncateBase64(fieldName)) {
                    String value = child.asText();
                    if (value != null && value.length() > BASE64_PREVIEW_LENGTH) {
                        objectNode.put(fieldName, value.substring(0, BASE64_PREVIEW_LENGTH) + "...");
                    }
                } else {
                    sanitizeBase64ForLogging(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                sanitizeBase64ForLogging(item);
            }
        }

        return node;
    }

    private boolean shouldTruncateBase64(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.equals("audiobase64") || normalized.equals("fotobase64") || normalized.contains("fotoperfil");
    }

    private String normalizeCommandForMetrics(String command) {

        if (command == null || command.isBlank()) {

            return "UNKNOWN";

        }

        return command.trim().toUpperCase(Locale.ROOT);

    }

    // Comandos de diagnóstico P2P
    private void handleP2PStatus() throws IOException {
        if (peerManager == null) {
            send("P2P_STATUS", Map.of("error", "P2P no está habilitado"));
            return;
        }
        
        String report = peerManager.generateDiagnosticReport();
        send("P2P_STATUS", Map.of("report", report, "timestamp", java.time.LocalDateTime.now()));
    }

    private void handleP2PMetrics() throws IOException {
        if (peerManager == null) {
            send("P2P_METRICS", Map.of("error", "P2P no está habilitado"));
            return;
        }
        
        Map<String, Integer> metrics = peerManager.getReplicationMetrics();
        send("P2P_METRICS", Map.of(
            "metrics", metrics,
            "peers_connected", peerManager.connectedPeerIds().size(),
            "known_servers", peerManager.knownClusterServerIds().size(),
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

    private void handleP2PPendingMessages() throws IOException {
        if (peerManager == null) {
            send("P2P_PENDING", Map.of("error", "P2P no está habilitado"));
            return;
        }
        
        java.util.List<String> pendingIds = peerManager.getPendingMessageIds();
        send("P2P_PENDING", Map.of(
            "pending_messages", pendingIds,
            "count", pendingIds.size(),
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

    private void handleP2PSendStatus() throws IOException {
        if (peerManager == null) {
            send("P2P_SEND_STATUS", Map.of("error", "P2P no está habilitado"));
            return;
        }
        
        try {
            peerManager.sendReplicationStatusToAllPeers();
            send("P2P_SEND_STATUS", Map.of(
                "success", true, 
                "message", "Estado de replicación enviado a todos los peers",
                "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            send("P2P_SEND_STATUS", Map.of(
                "success", false, 
                "error", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    private void cleanup() {
        if (sessionId != null) {
            registry.unregister(sessionId);
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        if (clienteId != null) {
            eventBus.publish(new SessionEvent(SessionEventType.LOGOUT, sessionId, clienteId, null));
        }
        this.socket = null;
        this.reader = null;
        this.writer = null;
        this.sessionId = null;
        this.clienteId = null;
    }
}
