package com.arquitectura.controladores.conexion;

import com.arquitectura.dto.AckResponse;
import com.arquitectura.dto.ChannelRequest;
import com.arquitectura.dto.CommandEnvelope;
import com.arquitectura.dto.ErrorResponse;
import com.arquitectura.dto.InviteRequest;
import com.arquitectura.dto.MessageRequest;
import com.arquitectura.dto.RegisterRequest;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());

    private final RegistroService registroService;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;
    private final ConnectionRegistry registry;
    private ConnectionHandlerPool pool;
    private final ObjectMapper mapper = new ObjectMapper();

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
                              SessionEventBus eventBus,
                              ConnectionRegistry registry) {
        this.registroService = Objects.requireNonNull(registroService, "registroService");
        this.canalService = Objects.requireNonNull(canalService, "canalService");
        this.mensajeriaService = Objects.requireNonNull(mensajeriaService, "mensajeriaService");
        this.reporteService = Objects.requireNonNull(reporteService, "reporteService");
        this.conexionService = Objects.requireNonNull(conexionService, "conexionService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.registry = Objects.requireNonNull(registry, "registry");
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
            try {
                JsonNode node = mapper.readTree(line);
                String command = node.hasNonNull("command") ? node.get("command").asText() : "";
                JsonNode payload = node.get("payload");
                processCommand(command.toUpperCase(Locale.ROOT), payload);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Comando inválido", e);
                send("ERROR", new ErrorResponse(e.getMessage()));
            }
        }
    }

    private void processCommand(String command, JsonNode payload) throws IOException {
        switch (command) {
            case "REGISTER" -> handleRegister(payload);
            case "SEND_USER" -> handleSendUser(payload);
            case "SEND_CHANNEL" -> handleSendChannel(payload);
            case "CREATE_CHANNEL" -> handleCreateChannel(payload);
            case "INVITE" -> handleInvite(payload);
            case "ACCEPT" -> handleAccept(payload);
            case "REJECT" -> handleReject(payload);
            case "LIST_USERS" -> send("LIST_USERS", reporteService.usuariosRegistrados());
            case "LIST_CHANNELS" -> send("LIST_CHANNELS", reporteService.canalesConUsuarios());
            case "LIST_CONNECTED" -> send("LIST_CONNECTED", reporteService.usuariosConectados());
            case "CLOSE_CONN" -> handleClose();
            case "BROADCAST" -> handleBroadcast(payload);
            default -> processReports(command);
        }
    }

    private void processReports(String command) throws IOException {
        switch (command) {
            case "REPORT_USUARIOS" -> send("REPORT_USUARIOS", reporteService.usuariosRegistrados());
            case "REPORT_CANALES" -> send("REPORT_CANALES", reporteService.canalesConUsuarios());
            case "REPORT_CONECTADOS" -> send("REPORT_CONECTADOS", reporteService.usuariosConectados());
            case "REPORT_AUDIO" -> send("REPORT_AUDIO", reporteService.textoDeMensajesDeAudio());
            case "REPORT_LOGS" -> send("REPORT_LOGS", reporteService.logs());
            default -> send("ERROR", new ErrorResponse("Comando no soportado: " + command));
        }
    }

    private void handleRegister(JsonNode payload) throws IOException {
        RegisterRequest request = mapper.treeToValue(payload, RegisterRequest.class);
        byte[] foto = new byte[0];
        if (request.getFotoBase64() != null) {
            foto = Base64.getDecoder().decode(request.getFotoBase64());
        }
        var cliente = registroService.registrarCliente(request.getUsuario(), request.getEmail(), request.getContrasenia(), foto, request.getIp());
        this.clienteId = cliente.getId();
        registry.updateCliente(sessionId, cliente.getId(), cliente.getNombreDeUsuario(), request.getIp());
        eventBus.publish(new SessionEvent(SessionEventType.LOGIN, sessionId, cliente.getId(), null));
        send("REGISTER", new AckResponse("Registro exitoso"));
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
        if (request.getCanalId() != null) {
            registry.joinChannel(sessionId, request.getCanalId());
        }
        send("SEND_CHANNEL", new AckResponse("Mensaje a canal enviado"));
    }

    private void handleCreateChannel(JsonNode payload) throws IOException {
        ensureAuthenticated();
        ChannelRequest request = mapper.treeToValue(payload, ChannelRequest.class);
        var canal = canalService.crearCanal(request.getNombre(), request.isPrivado());
        registry.joinChannel(sessionId, canal.getId());
        send("CREATE_CHANNEL", canal);
    }

    private void handleInvite(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        request.setSolicitanteId(clienteId);
        canalService.invitarUsuario(request.getCanalId(), request.getSolicitanteId(), request.getInvitadoId());
        send("INVITE", new AckResponse("Invitación enviada"));
    }

    private void handleAccept(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        canalService.aceptarInvitacion(request.getCanalId(), clienteId);
        registry.joinChannel(sessionId, request.getCanalId());
        send("ACCEPT", new AckResponse("Canal aceptado"));
    }

    private void handleReject(JsonNode payload) throws IOException {
        ensureAuthenticated();
        InviteRequest request = mapper.treeToValue(payload, InviteRequest.class);
        canalService.rechazarInvitacion(request.getCanalId(), clienteId);
        send("REJECT", new AckResponse("Invitación rechazada"));
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

    private void send(String command, Object payload) throws IOException {
        if (writer == null) {
            return;
        }
        CommandEnvelope response = new CommandEnvelope(command, payload);
        writer.write(mapper.writeValueAsString(response));
        writer.write('\n');
        writer.flush();
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
