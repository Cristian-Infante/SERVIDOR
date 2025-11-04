package com.arquitectura.controladores.conexion;

import com.arquitectura.dto.MessageRequest;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.AudioTranscriptionService;
import com.arquitectura.servicios.eventos.MessageNotificationService;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.impl.MensajeriaServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DirectMessageDeliveryIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enviaEventoNewMessageAlEmisorYReceptor() throws Exception {
        SessionEventBus eventBus = new SessionEventBus();
        ConnectionRegistry registry = new ConnectionRegistry(eventBus);

        StubClienteRepository clienteRepository = new StubClienteRepository();
        clienteRepository.put(1L, "alice");
        clienteRepository.put(2L, "bob");
        StubCanalRepository canalRepository = new StubCanalRepository();

        new MessageNotificationService(registry, canalRepository, clienteRepository, eventBus);

        MensajeriaServiceImpl service = new MensajeriaServiceImpl(
                new InMemoryMensajeRepository(),
                new InMemoryLogRepository(),
                registry,
                eventBus,
                audioPath -> "",
                new NoopAudioStorageService()
        );

        try (ConnectionHandle emisor = register(registry);
             ConnectionHandle receptor = register(registry)) {

            registry.updateCliente(emisor.sessionId, 1L, "alice", "127.0.0.1");
            registry.updateCliente(receptor.sessionId, 2L, "bob", "127.0.0.1");

            MessageRequest request = new MessageRequest();
            request.setTipo("TEXTO");
            request.setContenido("hola bob");
            request.setEmisor(1L);
            request.setReceptor(2L);

            service.enviarMensajeAUsuario(request);

            JsonNode receptorEvent = readEvent(receptor);
            JsonNode emisorEvent = readEvent(emisor);

            assertEquals("EVENT", receptorEvent.get("command").asText());
            JsonNode receptorPayload = receptorEvent.get("payload");
            assertEquals("NEW_MESSAGE", receptorPayload.get("evento").asText());
            assertEquals(1L, receptorPayload.get("emisorId").asLong());
            assertEquals(2L, receptorPayload.get("receptorId").asLong());
            assertEquals("hola bob", receptorPayload.get("contenido").get("contenido").asText());

            assertEquals("EVENT", emisorEvent.get("command").asText());
            JsonNode emisorPayload = emisorEvent.get("payload");
            assertEquals("NEW_MESSAGE", emisorPayload.get("evento").asText());
            assertEquals("hola bob", emisorPayload.get("contenido").get("contenido").asText());

            registry.closeSession(emisor.sessionId);
            registry.closeSession(receptor.sessionId);
        }
    }

    private JsonNode readEvent(ConnectionHandle handle) throws IOException {
        String json = handle.reader.readLine();
        assertNotNull(json, "El cliente debe recibir un evento");
        return mapper.readTree(json);
    }

    private ConnectionHandle register(ConnectionRegistry registry) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return server.accept();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            client.setSoTimeout(2000);
            Socket serverSide = accepted.get(2, TimeUnit.SECONDS);
            String sessionId = registry.register(serverSide);
            return new ConnectionHandle(sessionId, client);
        }
    }

    private static class ConnectionHandle implements AutoCloseable {
        private final String sessionId;
        private final Socket clientSocket;
        private final BufferedReader reader;

        private ConnectionHandle(String sessionId, Socket clientSocket) throws IOException {
            this.sessionId = sessionId;
            this.clientSocket = clientSocket;
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            clientSocket.close();
        }
    }

    private static class InMemoryMensajeRepository implements MensajeRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Mensaje> storage = new ConcurrentHashMap<>();

        @Override
        public Mensaje save(Mensaje mensaje) {
            if (mensaje.getId() == null) {
                mensaje.setId(sequence.incrementAndGet());
                if (mensaje.getTimeStamp() == null) {
                    mensaje.setTimeStamp(LocalDateTime.now());
                }
            }
            storage.put(mensaje.getId(), mensaje);
            return mensaje;
        }

        @Override
        public List<Mensaje> findTextAudioLogs() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<Mensaje> findByCanal(Long canalId) {
            return Collections.emptyList();
        }

        @Override
        public List<Mensaje> findBetweenUsers(Long emisor, Long receptor) {
            return Collections.emptyList();
        }

        @Override
        public List<Mensaje> findAllByUser(Long usuarioId) {
            return Collections.emptyList();
        }

        @Override
        public List<Mensaje> findAllOrdered() {
            return new ArrayList<>(storage.values());
        }
    }

    private static class InMemoryLogRepository implements LogRepository {
        private final List<com.arquitectura.entidades.Log> logs = new ArrayList<>();

        @Override
        public void append(com.arquitectura.entidades.Log log) {
            logs.add(log);
        }

        @Override
        public List<com.arquitectura.entidades.Log> findAll() {
            return logs;
        }
    }

    private static class StubCanalRepository implements CanalRepository {
        @Override
        public com.arquitectura.entidades.Canal save(com.arquitectura.entidades.Canal canal) {
            return canal;
        }

        @Override
        public Optional<com.arquitectura.entidades.Canal> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<com.arquitectura.entidades.Canal> findAll() {
            return Collections.emptyList();
        }

        @Override
        public List<com.arquitectura.entidades.Cliente> findUsers(Long canalId) {
            return Collections.emptyList();
        }

        @Override
        public void linkUser(Long canalId, Long clienteId) {
        }

        @Override
        public void unlinkUser(Long canalId, Long clienteId) {
        }
    }

    private static class StubClienteRepository implements ClienteRepository {
        private final Map<Long, Cliente> clientes = new HashMap<>();

        void put(Long id, String nombre) {
            Cliente cliente = new Cliente();
            cliente.setId(id);
            cliente.setNombreDeUsuario(nombre);
            cliente.setEmail(nombre + "@mail.test");
            clientes.put(id, cliente);
        }

        @Override
        public Cliente save(Cliente cliente) {
            clientes.put(cliente.getId(), cliente);
            return cliente;
        }

        @Override
        public Optional<Cliente> findById(Long id) {
            return Optional.ofNullable(clientes.get(id));
        }

        @Override
        public Optional<Cliente> findByEmail(String email) {
            return clientes.values().stream()
                    .filter(c -> email.equals(c.getEmail()))
                    .findFirst();
        }

        @Override
        public List<Cliente> findConnected() {
            return new ArrayList<>(clientes.values());
        }

        @Override
        public void setConnected(Long id, boolean connected) {
        }

        @Override
        public void disconnectAll() {
        }

        @Override
        public List<Cliente> all() {
            return new ArrayList<>(clientes.values());
        }
    }

    private static class NoopAudioStorageService implements AudioStorageService {
        @Override
        public String guardarAudio(String audioBase64, Long usuarioId, String mime) {
            return "";
        }

        @Override
        public boolean existeAudio(String rutaArchivo) {
            return false;
        }

        @Override
        public boolean eliminarAudio(String rutaArchivo) {
            return false;
        }

        @Override
        public String cargarAudioBase64(String rutaArchivo) {
            return null;
        }
    }
}
