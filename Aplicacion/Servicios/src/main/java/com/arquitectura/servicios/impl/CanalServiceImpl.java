package com.arquitectura.servicios.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    public void invitarUsuario(Long canalId, Long solicitanteId, Long invitadoId) {
        validarCanalYUsuario(canalId, invitadoId);
        
        // Verificar que el usuario no sea ya miembro
        List<Cliente> miembros = canalRepository.findUsers(canalId);
        boolean yaMiembro = miembros.stream().anyMatch(c -> c.getId().equals(invitadoId));
        if (yaMiembro) {
            throw new IllegalArgumentException("El usuario ya es miembro del canal");
        }
        
        // Verificar si ya existe una invitación pendiente
        Optional<Invitacion> existente = invitacionRepository.findByCanalAndInvitado(canalId, invitadoId);
        Invitacion invitacion;
        if (existente.isPresent()) {
            invitacion = existente.get();
            invitacionRepository.reactivarInvitacion(invitacion.getId(), solicitanteId);
            invitacion.setInvitadorId(solicitanteId);
            invitacion.setEstado("PENDIENTE");
            invitacion.setFechaInvitacion(java.time.LocalDateTime.now());
            LOGGER.info(() -> "Invitación reactivada: usuario " + solicitanteId +
                " reenviará invitación a usuario " + invitadoId + " para canal " + canalId);
        } else {
            invitacion = new Invitacion(canalId, solicitanteId, invitadoId);
            invitacionRepository.save(invitacion);
            LOGGER.info(() -> "Invitación registrada: usuario " + solicitanteId +
                " invitó a usuario " + invitadoId + " al canal " + canalId);
        }

        eventBus.publish(new SessionEvent(SessionEventType.INVITE_SENT, null, solicitanteId,
            Map.of("canalId", canalId, "invitadoId", invitadoId, "invitadorId", solicitanteId)));
    }

    @Override
    public void aceptarInvitacion(Long canalId, Long invitadoId) {
        // Buscar la invitación pendiente
        Optional<Invitacion> invitacionOpt = invitacionRepository.findByCanalAndInvitado(canalId, invitadoId);
        if (invitacionOpt.isEmpty() || !"PENDIENTE".equals(invitacionOpt.get().getEstado())) {
            throw new IllegalStateException("No existe invitación pendiente para el canal");
        }
        
        Invitacion invitacion = invitacionOpt.get();
        Long invitadorId = invitacion.getInvitadorId();
        
        // Agregar al usuario al canal
        canalRepository.linkUser(canalId, invitadoId);
        
        // Actualizar estado de la invitación
        invitacionRepository.updateEstado(invitacion.getId(), "ACEPTADA");
        
        LOGGER.info(() -> "Usuario " + invitadoId + " aceptó invitación y se unió al canal " + canalId);
        
        // Publicar evento de invitación aceptada
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_ACCEPTED, null, invitadoId, 
            Map.of("canalId", canalId, "invitadoId", invitadoId, "invitadorId", invitadorId)));
    }

    @Override
    public void rechazarInvitacion(Long canalId, Long invitadoId) {
        // Buscar la invitación pendiente
        Optional<Invitacion> invitacionOpt = invitacionRepository.findByCanalAndInvitado(canalId, invitadoId);
        if (invitacionOpt.isEmpty() || !"PENDIENTE".equals(invitacionOpt.get().getEstado())) {
            throw new IllegalStateException("No existe invitación pendiente para el canal");
        }
        
        Invitacion invitacion = invitacionOpt.get();
        Long invitadorId = invitacion.getInvitadorId();
        
        // Actualizar estado de la invitación
        invitacionRepository.updateEstado(invitacion.getId(), "RECHAZADA");
        
        LOGGER.info(() -> "Usuario " + invitadoId + " rechazó invitación del canal " + canalId);
        
        // Publicar evento de invitación rechazada
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_REJECTED, null, invitadoId, 
            Map.of("canalId", canalId, "invitadoId", invitadoId, "invitadorId", invitadorId)));
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

    private void validarCanalYUsuario(Long canalId, Long usuarioId) {
        Optional<Canal> canal = canalRepository.findById(canalId);
        if (canal.isEmpty()) {
            throw new IllegalArgumentException("Canal inexistente");
        }
        clienteRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario inexistente"));
    }
}
