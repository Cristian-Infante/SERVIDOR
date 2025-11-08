package com.arquitectura.servicios.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import com.arquitectura.dto.InvitationSummary;
import com.arquitectura.dto.SentInvitationSummary;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Invitacion;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.InvitacionRepository;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;

public class CanalServiceImpl implements CanalService {

    private static final Logger LOGGER = Logger.getLogger(CanalServiceImpl.class.getName());

    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;
    private final InvitacionRepository invitacionRepository;
    private final SessionEventBus eventBus;

    public CanalServiceImpl(CanalRepository canalRepository, ClienteRepository clienteRepository, 
                           InvitacionRepository invitacionRepository, SessionEventBus eventBus) {
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.invitacionRepository = Objects.requireNonNull(invitacionRepository, "invitacionRepository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    @Override
    public Canal crearCanal(String nombre, boolean privado, Long creadorId) {
        // Validar que el creador existe
        clienteRepository.findById(creadorId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario creador inexistente"));
        
        Canal canal = new Canal();
        canal.setUuid(UUID.randomUUID().toString());
        canal.setNombre(nombre);
        canal.setPrivado(privado);
        Canal saved = canalRepository.save(canal);
        
        // Vincular automáticamente al creador al canal
        canalRepository.linkUser(saved.getId(), creadorId);
        
        LOGGER.info(() -> "Canal creado: " + saved.getId() + " por usuario " + creadorId);
        eventBus.publish(new SessionEvent(SessionEventType.CHANNEL_CREATED, null, creadorId, saved));
        return saved;
    }

    @Override
    public void invitarUsuario(Long canalId, String canalUuid, Long solicitanteId, Long invitadoId) {
        Canal canal = validarCanalYUsuario(canalId, canalUuid, invitadoId);
        Long effectiveCanalId = canal.getId();

        // Verificar que el usuario no sea ya miembro
        List<Cliente> miembros = canalRepository.findUsers(effectiveCanalId);
        boolean yaMiembro = miembros.stream().anyMatch(c -> c.getId().equals(invitadoId));
        if (yaMiembro) {
            throw new IllegalArgumentException("El usuario ya es miembro del canal");
        }
        
        // Verificar si ya existe una invitación pendiente
        Optional<Invitacion> existente = invitacionRepository.findByCanalAndInvitado(effectiveCanalId, invitadoId);
        Invitacion invitacion;
        if (existente.isPresent()) {
            invitacion = existente.get();
            invitacionRepository.reactivarInvitacion(invitacion.getId(), solicitanteId);
            invitacion.setInvitadorId(solicitanteId);
            invitacion.setEstado("PENDIENTE");
            invitacion.setFechaInvitacion(java.time.LocalDateTime.now());
            LOGGER.info(() -> "Invitación reactivada: usuario " + solicitanteId +
                " reenviará invitación a usuario " + invitadoId + " para canal " + effectiveCanalId);
        } else {
            invitacion = new Invitacion(effectiveCanalId, solicitanteId, invitadoId);
            invitacionRepository.save(invitacion);
            LOGGER.info(() -> "Invitación registrada: usuario " + solicitanteId +
                " invitó a usuario " + invitadoId + " al canal " + effectiveCanalId);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("canalId", effectiveCanalId);
        payload.put("canalUuid", canal.getUuid());
        payload.put("invitadoId", invitadoId);
        payload.put("invitadorId", solicitanteId);
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_SENT, null, solicitanteId, payload));
    }

    @Override
    public Canal aceptarInvitacion(Long canalId, String canalUuid, Long invitadoId) {
        Canal canal = resolveCanal(canalId, canalUuid)
            .orElseThrow(() -> new IllegalArgumentException("Canal inexistente"));
        Long effectiveCanalId = canal.getId();
        // Buscar la invitación pendiente
        Optional<Invitacion> invitacionOpt = invitacionRepository.findByCanalAndInvitado(effectiveCanalId, invitadoId);
        if (invitacionOpt.isEmpty() || !"PENDIENTE".equals(invitacionOpt.get().getEstado())) {
            throw new IllegalStateException("No existe invitación pendiente para el canal");
        }

        Invitacion invitacion = invitacionOpt.get();
        Long invitadorId = invitacion.getInvitadorId();

        // Agregar al usuario al canal
        canalRepository.linkUser(effectiveCanalId, invitadoId);

        // Actualizar estado de la invitación
        invitacionRepository.updateEstado(invitacion.getId(), "ACEPTADA");

        LOGGER.info(() -> "Usuario " + invitadoId + " aceptó invitación y se unió al canal " + effectiveCanalId);

        // Publicar evento de invitación aceptada
        Map<String, Object> payload = new HashMap<>();
        payload.put("canalId", effectiveCanalId);
        payload.put("canalUuid", canal.getUuid());
        payload.put("invitadoId", invitadoId);
        payload.put("invitadorId", invitadorId);
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_ACCEPTED, null, invitadoId, payload));
        return canal;
    }

    @Override
    public void rechazarInvitacion(Long canalId, String canalUuid, Long invitadoId) {
        Canal canal = resolveCanal(canalId, canalUuid)
            .orElseThrow(() -> new IllegalArgumentException("Canal inexistente"));
        Long effectiveCanalId = canal.getId();
        // Buscar la invitación pendiente
        Optional<Invitacion> invitacionOpt = invitacionRepository.findByCanalAndInvitado(effectiveCanalId, invitadoId);
        if (invitacionOpt.isEmpty() || !"PENDIENTE".equals(invitacionOpt.get().getEstado())) {
            throw new IllegalStateException("No existe invitación pendiente para el canal");
        }

        Invitacion invitacion = invitacionOpt.get();
        Long invitadorId = invitacion.getInvitadorId();

        // Actualizar estado de la invitación
        invitacionRepository.updateEstado(invitacion.getId(), "RECHAZADA");

        LOGGER.info(() -> "Usuario " + invitadoId + " rechazó invitación del canal " + effectiveCanalId);

        // Publicar evento de invitación rechazada
        Map<String, Object> payload = new HashMap<>();
        payload.put("canalId", effectiveCanalId);
        payload.put("canalUuid", canal.getUuid());
        payload.put("invitadoId", invitadoId);
        payload.put("invitadorId", invitadorId);
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_REJECTED, null, invitadoId, payload));
    }

    @Override
    public List<InvitationSummary> obtenerInvitacionesRecibidas(Long usuarioId) {
        List<Invitacion> invitaciones = invitacionRepository.findPendientesByInvitado(usuarioId);
        List<InvitationSummary> resumen = new ArrayList<>();
        
        for (Invitacion inv : invitaciones) {
            // Obtener información del canal
            Optional<Canal> canalOpt = canalRepository.findById(inv.getCanalId());
            if (canalOpt.isEmpty()) {
                continue;
            }
            Canal canal = canalOpt.get();
            
            // Obtener información del invitador
            Optional<Cliente> invitadorOpt = clienteRepository.findById(inv.getInvitadorId());
            String invitadorNombre = invitadorOpt.map(Cliente::getNombreDeUsuario).orElse("Desconocido");
            
            InvitationSummary summary = new InvitationSummary(
                inv.getCanalId(),
                canal.getUuid(),
                canal.getNombre(),
                canal.getPrivado(),
                inv.getInvitadorId(),
                invitadorNombre
            );
            resumen.add(summary);
        }
        
        return resumen;
    }

    @Override
    public List<SentInvitationSummary> obtenerInvitacionesEnviadas(Long usuarioId) {
        List<Invitacion> invitaciones = invitacionRepository.findByInvitador(usuarioId);
        List<SentInvitationSummary> resumen = new ArrayList<>();
        
        for (Invitacion inv : invitaciones) {
            // Obtener información del canal
            Optional<Canal> canalOpt = canalRepository.findById(inv.getCanalId());
            if (canalOpt.isEmpty()) {
                continue;
            }
            Canal canal = canalOpt.get();
            
            // Obtener información del invitado
            Optional<Cliente> invitadoOpt = clienteRepository.findById(inv.getInvitadoId());
            String invitadoNombre = invitadoOpt.map(Cliente::getNombreDeUsuario).orElse("Desconocido");
            
            SentInvitationSummary summary = new SentInvitationSummary(
                inv.getCanalId(),
                canal.getUuid(),
                canal.getNombre(),
                canal.getPrivado(),
                inv.getInvitadoId(),
                invitadoNombre,
                inv.getEstado()
            );
            resumen.add(summary);
        }
        
        return resumen;
    }

    private Canal validarCanalYUsuario(Long canalId, String canalUuid, Long usuarioId) {
        Canal canal = resolveCanal(canalId, canalUuid)
            .orElseThrow(() -> new IllegalArgumentException("Canal inexistente"));
        clienteRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario inexistente"));
        return canal;
    }

    private Optional<Canal> resolveCanal(Long canalId, String canalUuid) {
        if (canalUuid != null && !canalUuid.isBlank()) {
            Optional<Canal> byUuid = canalRepository.findByUuid(canalUuid);
            if (byUuid.isPresent()) {
                return byUuid;
            }
            if (canalId != null) {
                return canalRepository.findById(canalId)
                    .filter(c -> canalUuid.equals(c.getUuid()));
            }
            return Optional.empty();
        }
        if (canalId != null) {
            return canalRepository.findById(canalId);
        }
        return Optional.empty();
    }
}
