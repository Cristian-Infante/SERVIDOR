package com.arquitectura.servicios.impl;

import com.arquitectura.dto.MessageRequest;
import com.arquitectura.dto.RealtimeMessageDto;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.AudioTranscriptionService;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.MessageNotificationService;
import com.arquitectura.servicios.eventos.SessionEventBus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MensajeriaServiceImplTest {

    @Test
    void notificaNuevoMensajePrivadoATodosLosInvolucrados() {
        InMemoryMensajeRepository mensajeRepository = new InMemoryMensajeRepository();
        InMemoryLogRepository logRepository = new InMemoryLogRepository();
        CapturingConnectionGateway connectionGateway = new CapturingConnectionGateway();
        SessionEventBus eventBus = new SessionEventBus();
        StubClienteRepository clienteRepository = new StubClienteRepository();
        clienteRepository.put(1L, "alice");
        clienteRepository.put(2L, "bob");
        StubCanalRepository canalRepository = new StubCanalRepository();
        AudioTranscriptionService transcriptionService = audioPath -> "";
        AudioStorageService audioStorageService = new NoopAudioStorageService();

        new MessageNotificationService(connectionGateway, canalRepository, clienteRepository, eventBus);

        MensajeriaServiceImpl service = new MensajeriaServiceImpl(
                mensajeRepository,
                logRepository,
                connectionGateway,
                eventBus,
                transcriptionService,
                audioStorageService
        );

        MessageRequest request = new MessageRequest();
        request.setTipo("TEXTO");
        request.setContenido("hola bob");
        request.setEmisor(1L);
        request.setReceptor(2L);

        service.enviarMensajeAUsuario(request);

        assertTrue(connectionGateway.hasDeliveriesFor(2L), "El receptor debe recibir el mensaje");
        assertTrue(connectionGateway.hasDeliveriesFor(1L), "El emisor debe recibir la réplica del mensaje");

        RealtimeMessageDto receptorDto = (RealtimeMessageDto) connectionGateway.deliveriesFor(2L).get(0);
        assertEquals("NEW_MESSAGE", receptorDto.getEvento());
        assertEquals(1L, receptorDto.getEmisorId());
        assertEquals(2L, receptorDto.getReceptorId());
        assertEquals("hola bob", receptorDto.getContenido().get("contenido"));

        RealtimeMessageDto emisorDto = (RealtimeMessageDto) connectionGateway.deliveriesFor(1L).get(0);
        assertEquals("NEW_MESSAGE", emisorDto.getEvento());
        assertEquals("hola bob", emisorDto.getContenido().get("contenido"));
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

    private static class CapturingConnectionGateway implements ConnectionGateway {
        private final Map<Long, List<Object>> delivered = new HashMap<>();

        @Override
        public void closeSession(String sessionId) {
        }

        @Override
        public void broadcast(Object payload) {
        }

        @Override
        public void broadcastLocal(Object payload) {
        }

        @Override
        public void sendToChannel(Long canalId, Object payload) {
        }

        @Override
        public void sendToSession(String sessionId, Object payload) {
        }

        @Override
        public void sendToUser(Long userId, Object payload) {
            delivered.computeIfAbsent(userId, id -> new ArrayList<>()).add(payload);
        }

        @Override
        public List<SessionDescriptor> activeSessions() {
            return Collections.emptyList();
        }

        @Override
        public SessionDescriptor descriptor(String sessionId) {
            return null;
        }

        boolean hasDeliveriesFor(Long userId) {
            return delivered.containsKey(userId);
        }

        List<Object> deliveriesFor(Long userId) {
            return delivered.getOrDefault(userId, Collections.emptyList());
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
