package com.arquitectura.controladores;

import com.arquitectura.dto.*;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.servicios.*;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maneja la comunicación con un cliente TCP reutilizable mediante object pool.
 */
public class ConnectionHandler implements Runnable, Closeable {

    private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());

    private final RegistroService registroService;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;
    private final ConnectionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ConnectionHandlerPool pool;
    private Long clienteId;

    public ConnectionHandler(RegistroService registroService,
                             CanalService canalService,
                             MensajeriaService mensajeriaService,
                             ReporteService reporteService,
                             ConexionService conexionService,
                             ConnectionRegistry registry,
                             SessionEventBus eventBus) {
        this.registroService = Objects.requireNonNull(registroService);
        this.canalService = Objects.requireNonNull(canalService);
        this.mensajeriaService = Objects.requireNonNull(mensajeriaService);
        this.reporteService = Objects.requireNonNull(reporteService);
        this.conexionService = Objects.requireNonNull(conexionService);
        this.registry = Objects.requireNonNull(registry);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    void attach(Socket socket, ConnectionHandlerPool pool) throws IOException {
        this.socket = socket;
        this.pool = pool;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.clienteId = null;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                process(line);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error en la comunicación", e);
        } finally {
            cleanup();
        }
    }

    private void process(String json) {
        try {
            CommandEnvelope envelope = objectMapper.readValue(json, CommandEnvelope.class);
            String command = envelope.getCommand();
            JsonNode payload = envelope.getPayload();
            switch (command) {
                case "REGISTER" -> handleRegister(payload);
                case "SEND_USER" -> handleSendUser(payload);
                case "SEND_CHANNEL" -> handleSendChannel(payload);
                case "CREATE_CHANNEL" -> handleCreateChannel(payload);
                case "INVITE" -> handleInvite(payload);
                case "ACCEPT" -> handleAccept(payload);
                case "REJECT" -> handleReject(payload);
                case "LIST_USERS" -> handleListUsers();
                case "LIST_CHANNELS" -> handleListChannels();
                case "LIST_CONNECTED" -> handleListConnected();
                case "CLOSE_CONN" -> handleClose(payload);
                case "BROADCAST" -> handleBroadcast(payload);
                case "REPORT_USERS" -> handleReportUsers();
                case "REPORT_CHANNELS" -> handleReportChannels();
                case "REPORT_CONNECTED" -> handleListConnected();
                case "REPORT_AUDIO" -> handleReportAudio();
                case "REPORT_LOGS" -> handleReportLogs();
                default -> send(AckResponse.fail("Comando no soportado: " + command));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error procesando comando", e);
            send(AckResponse.fail("Error: " + e.getMessage()));
        }
    }

    private void handleRegister(JsonNode payload) throws IOException {
        RegisterRequest request = objectMapper.treeToValue(payload, RegisterRequest.class);
        byte[] foto = request.getFotoBase64() != null ? Base64.getDecoder().decode(request.getFotoBase64()) : null;
        Cliente cliente = registroService.registrar(request.getUsuario(), request.getEmail(), request.getPassword(), foto, request.getIp());
        this.clienteId = cliente.getId();
        registry.registrar(cliente.getId(), cliente.getNombreDeUsuario(), this);
        send(AckResponse.ok("Registrado con id " + cliente.getId()));
    }

    private void handleSendUser(JsonNode payload) throws IOException {
        MessageRequest request = objectMapper.treeToValue(payload, MessageRequest.class);
        Mensaje mensaje = mensajeriaService.enviarMensajeATexto(request.getEmisorId(), request.getReceptorId(), request.getContenido());
        send(objectMapper.createObjectNode().put("mensajeId", mensaje.getId()));
    }

    private void handleSendChannel(JsonNode payload) throws IOException {
        MessageRequest request = objectMapper.treeToValue(payload, MessageRequest.class);
        Mensaje mensaje = mensajeriaService.enviarMensajeACanalTexto(request.getEmisorId(), request.getCanalId(), request.getContenido());
        send(objectMapper.createObjectNode().put("mensajeId", mensaje.getId()));
    }

    private void handleCreateChannel(JsonNode payload) throws IOException {
        ChannelRequest request = objectMapper.treeToValue(payload, ChannelRequest.class);
        Canal canal = canalService.crearCanal(request.getNombre(), request.isPrivado());
        send(objectMapper.createObjectNode().put("canalId", canal.getId()));
    }

    private void handleInvite(JsonNode payload) throws IOException {
        InviteRequest request = objectMapper.treeToValue(payload, InviteRequest.class);
        canalService.invitarUsuario(request.getCanalId(), request.getClienteId());
        send(AckResponse.ok("Invitación enviada"));
    }

    private void handleAccept(JsonNode payload) throws IOException {
        InviteRequest request = objectMapper.treeToValue(payload, InviteRequest.class);
        canalService.aceptarInvitacion(request.getCanalId(), request.getClienteId());
        send(AckResponse.ok("Invitación aceptada"));
    }

    private void handleReject(JsonNode payload) throws IOException {
        InviteRequest request = objectMapper.treeToValue(payload, InviteRequest.class);
        canalService.rechazarInvitacion(request.getCanalId(), request.getClienteId());
        send(AckResponse.ok("Invitación rechazada"));
    }

    private void handleListUsers() {
        List<UserSummary> users = reporteService.usuariosRegistrados().stream().map(cliente -> {
            UserSummary summary = new UserSummary();
            summary.setId(cliente.getId());
            summary.setUsuario(cliente.getNombreDeUsuario());
            summary.setEmail(cliente.getEmail());
            summary.setConectado(Boolean.TRUE.equals(cliente.getEstado()));
            return summary;
        }).collect(Collectors.toList());
        send(users);
    }

    private void handleListChannels() {
        List<ChannelSummary> canales = reporteService.canalesConUsuarios().entrySet().stream().map(entry -> {
            ChannelSummary summary = new ChannelSummary();
            summary.setId(entry.getKey().getId());
            summary.setNombre(entry.getKey().getNombre());
            summary.setPrivado(Boolean.TRUE.equals(entry.getKey().getPrivado()));
            List<UserSummary> usuarios = entry.getValue().stream().map(cliente -> {
                UserSummary summaryUsuario = new UserSummary();
                summaryUsuario.setId(cliente.getId());
                summaryUsuario.setUsuario(cliente.getNombreDeUsuario());
                summaryUsuario.setEmail(cliente.getEmail());
                summaryUsuario.setConectado(Boolean.TRUE.equals(cliente.getEstado()));
                return summaryUsuario;
            }).collect(Collectors.toList());
            summary.setUsuarios(usuarios);
            return summary;
        }).collect(Collectors.toList());
        send(canales);
    }

    private void handleListConnected() {
        List<UserSummary> users = reporteService.usuariosConectados().stream().map(cliente -> {
            UserSummary summary = new UserSummary();
            summary.setId(cliente.getId());
            summary.setUsuario(cliente.getNombreDeUsuario());
            summary.setEmail(cliente.getEmail());
            summary.setConectado(true);
            return summary;
        }).collect(Collectors.toList());
        send(users);
    }

    private void handleClose(JsonNode payload) throws IOException {
        Long id = payload.has("clienteId") ? payload.get("clienteId").asLong() : clienteId;
        if (id != null) {
            conexionService.cerrarConexion(id);
            send(AckResponse.ok("Conexión cerrada"));
        } else {
            send(AckResponse.fail("Sin cliente asociado"));
        }
    }

    private void handleBroadcast(JsonNode payload) {
        String mensaje = payload.has("mensaje") ? payload.get("mensaje").asText() : "";
        conexionService.broadcast(mensaje);
        send(AckResponse.ok("Broadcast enviado"));
    }

    private void handleReportUsers() {
        handleListUsers();
    }

    private void handleReportChannels() {
        handleListChannels();
    }

    private void handleReportAudio() {
        List<Mensaje> mensajes = reporteService.textoDeMensajesDeAudio();
        List<AudioMetadataDto> audios = mensajes.stream()
                .filter(m -> "AUDIO".equalsIgnoreCase(m.getTipo()))
                .map(m -> {
                    AudioMetadataDto dto = new AudioMetadataDto();
                    dto.setMensajeId(m.getId());
                    if (m instanceof com.arquitectura.entidades.AudioMensaje audio) {
                        dto.setRuta(audio.getRutaArchivo());
                        dto.setMime(audio.getMime());
                        dto.setDuracion(audio.getDuracionSeg());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
        send(audios);
    }

    private void handleReportLogs() {
        List<LogEntryDto> logs = reporteService.logs().stream().map(log -> {
            LogEntryDto dto = new LogEntryDto();
            dto.setDetalle(log.getDetalle());
            dto.setFechaHora(log.getFechaHora());
            dto.setTipo(Boolean.TRUE.equals(log.getTipo()));
            return dto;
        }).collect(Collectors.toList());
        send(logs);
    }

    public void send(Object obj) {
        try {
            String serialized = objectMapper.writeValueAsString(obj);
            sendRaw(serialized);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error serializando respuesta", e);
        }
    }

    public void sendRaw(String json) {
        try {
            writer.write(json);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error enviando respuesta", e);
        }
    }

    public void shutdown() {
        cleanup();
    }

    @Override
    public void close() {
        cleanup();
    }

    private void cleanup() {
        if (clienteId != null) {
            String usuario = registry.usuarioDe(clienteId);
            registry.desregistrar(clienteId);
            eventBus.publish(new SessionEvent(
                    SessionEvent.Type.LOGOUT,
                    java.util.Map.of(
                            "clienteId", clienteId,
                            "usuario", usuario
                    )));
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        reader = null;
        writer = null;
        if (pool != null) {
            pool.release(this);
        }
    }

    private static class CommandEnvelope {
        private String command;
        private JsonNode payload;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public void setPayload(JsonNode payload) {
            this.payload = payload;
        }
    }
}
