package com.arquitectura.controladores.conexion;

import com.arquitectura.dto.MessageRequest;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClusterMessageMirroringIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void replicaMensajesParaSesionesDelMismoUsuarioEnServidoresDistintos() throws Exception {
        SessionEventBus busA = new SessionEventBus();
        SessionEventBus busB = new SessionEventBus();

        InMemoryClienteRepository clienteRepository = new InMemoryClienteRepository();
        clienteRepository.put(1L, "alice");
        clienteRepository.put(2L, "bob");

        InMemoryMensajeRepository mensajeRepository = new InMemoryMensajeRepository();
        InMemoryLogRepository logRepository = new InMemoryLogRepository();
        StubCanalRepository canalRepository = new StubCanalRepository();
        AudioTranscriptionService transcriptionService = audio -> "";
        AudioStorageService audioStorageService = new NoopAudioStorageService();

        ConnectionRegistry registryA = new ConnectionRegistry(busA, "server-a");
        ConnectionRegistry registryB = new ConnectionRegistry(busB, "server-a");

        int portA = freePort();
        int portB = freePort();

        com.arquitectura.controladores.p2p.ServerPeerManager peerA = new com.arquitectura.controladores.p2p.ServerPeerManager(
            "server-a", portA, List.of("127.0.0.1:" + portB), registryA, null, clienteRepository);
        com.arquitectura.controladores.p2p.ServerPeerManager peerB = new com.arquitectura.controladores.p2p.ServerPeerManager(
            "server-a", portB, List.of(), registryB, null, clienteRepository);

        registryA.setPeerManager(peerA);
        registryB.setPeerManager(peerB);

        peerB.start();
        peerA.start();

        await(() -> !peerA.connectedPeerIds().isEmpty()
            && !peerB.connectedPeerIds().isEmpty(), 5_000);

        new MessageNotificationService(registryA, canalRepository, clienteRepository, busA);
        new MessageNotificationService(registryB, canalRepository, clienteRepository, busB);

        MensajeriaServiceImpl serviceA = new MensajeriaServiceImpl(
            mensajeRepository,
            logRepository,
            registryA,
            busA,
            transcriptionService,
            audioStorageService
        );

        try {
            try (ConnectionHandle aliceA = register(registryA);
                 ConnectionHandle aliceB = register(registryB);
                 ConnectionHandle bobA = register(registryA)) {

                registryA.updateCliente(aliceA.sessionId, 1L, "alice", "127.0.0.1");
                registryB.updateCliente(aliceB.sessionId, 1L, "alice", "127.0.0.1");
                registryA.updateCliente(bobA.sessionId, 2L, "bob", "127.0.0.1");

            await(() -> hasRemoteSession(registryA, 1L, "server-b")
                && hasRemoteSession(registryB, 1L, "server-a"), 5_000);

            MessageRequest request = new MessageRequest();
            request.setTipo("TEXTO");
            request.setContenido("hola bob");
            request.setEmisor(1L);
            request.setReceptor(2L);

            serviceA.enviarMensajeAUsuario(request);

            JsonNode bobEvent = readEvent(bobA);
            JsonNode aliceAEvent = readEvent(aliceA);
            JsonNode aliceBEvent = readEvent(aliceB);

            assertEquals("EVENT", bobEvent.get("command").asText());
            assertEquals("NEW_MESSAGE", bobEvent.get("payload").get("evento").asText());

            assertEquals("EVENT", aliceAEvent.get("command").asText());
            assertEquals("NEW_MESSAGE", aliceAEvent.get("payload").get("evento").asText());

            assertEquals("EVENT", aliceBEvent.get("command").asText());
            assertEquals("NEW_MESSAGE", aliceBEvent.get("payload").get("evento").asText());

            MessageRequest respuesta = new MessageRequest();
            respuesta.setTipo("TEXTO");
            respuesta.setContenido("hola alice");
            respuesta.setEmisor(2L);
            respuesta.setReceptor(1L);

            serviceA.enviarMensajeAUsuario(respuesta);

            JsonNode aliceARecibido = readEvent(aliceA);
            JsonNode aliceBRecibido = readEvent(aliceB);

            assertEquals("EVENT", aliceARecibido.get("command").asText());
            assertEquals("NEW_MESSAGE", aliceARecibido.get("payload").get("evento").asText());

            assertEquals("EVENT", aliceBRecibido.get("command").asText());
            assertEquals("NEW_MESSAGE", aliceBRecibido.get("payload").get("evento").asText());
            }
        } finally {
            peerA.stop();
            peerB.stop();
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
            client.setSoTimeout(5000);
            Socket serverSide = accepted.get(2, TimeUnit.SECONDS);
            String sessionId = registry.register(serverSide);
            return new ConnectionHandle(sessionId, client);
        }
    }

    private void await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("Timeout esperando condición");
        }
    }

    private boolean hasRemoteSession(ConnectionRegistry registry, long userId, String serverId) {
        return registry.activeSessions().stream()
            .anyMatch(descriptor -> descriptor != null
                && !descriptor.isLocal()
                && descriptor.getClienteId() != null
                && descriptor.getClienteId().equals(userId));
    }

    private int freePort() throws IOException {
        try (ServerSocket server = new ServerSocket(0)) {
            return server.getLocalPort();
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
        private final Map<Long, com.arquitectura.entidades.Mensaje> storage = new ConcurrentHashMap<>();

        @Override
        public com.arquitectura.entidades.Mensaje save(com.arquitectura.entidades.Mensaje mensaje) {
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
        public List<com.arquitectura.entidades.Mensaje> findTextAudioLogs() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<com.arquitectura.entidades.Mensaje> findByCanal(Long canalId) {
            return Collections.emptyList();
        }

        @Override
        public List<com.arquitectura.entidades.Mensaje> findBetweenUsers(Long emisor, Long receptor) {
            return Collections.emptyList();
        }

        @Override
        public List<com.arquitectura.entidades.Mensaje> findAllByUser(Long usuarioId) {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<com.arquitectura.entidades.Mensaje> findAllOrdered() {
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

    private static class InMemoryClienteRepository implements ClienteRepository {
        private final Map<Long, com.arquitectura.entidades.Cliente> clientes = new ConcurrentHashMap<>();

        void put(Long id, String nombre) {
            com.arquitectura.entidades.Cliente cliente = new com.arquitectura.entidades.Cliente();
            cliente.setId(id);
            cliente.setNombreDeUsuario(nombre);
            cliente.setEmail(nombre + "@mail.test");
            clientes.put(id, cliente);
        }

        @Override
        public com.arquitectura.entidades.Cliente save(com.arquitectura.entidades.Cliente cliente) {
            clientes.put(cliente.getId(), cliente);
            return cliente;
        }

        @Override
        public Optional<com.arquitectura.entidades.Cliente> findById(Long id) {
            return Optional.ofNullable(clientes.get(id));
        }

        @Override
        public Optional<com.arquitectura.entidades.Cliente> findByEmail(String email) {
            return clientes.values().stream()
                .filter(c -> email.equals(c.getEmail()))
                .findFirst();
        }

        @Override
        public List<com.arquitectura.entidades.Cliente> findConnected() {
            return new ArrayList<>(clientes.values());
        }

        @Override
        public void setConnected(Long id, boolean connected) {
        }

        @Override
        public void disconnectAll() {
        }

        @Override
        public List<com.arquitectura.entidades.Cliente> all() {
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

