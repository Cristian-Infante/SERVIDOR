package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Canal;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CanalServiceImpl implements CanalService {

    private static final Logger LOGGER = Logger.getLogger(CanalServiceImpl.class.getName());

    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;
    private final SessionEventBus eventBus;
    private final Map<Long, Set<Long>> invitacionesPendientes = new ConcurrentHashMap<>();

    public CanalServiceImpl(CanalRepository canalRepository, ClienteRepository clienteRepository, SessionEventBus eventBus) {
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    @Override
    public Canal crearCanal(String nombre, boolean privado) {
        Canal canal = new Canal();
        canal.setNombre(nombre);
        canal.setPrivado(privado);
        Canal saved = canalRepository.save(canal);
        LOGGER.info(() -> "Canal creado: " + saved.getId());
        eventBus.publish(new SessionEvent(SessionEventType.CHANNEL_CREATED, null, null, saved));
        return saved;
    }

    @Override
    public void invitarUsuario(Long canalId, Long solicitanteId, Long invitadoId) {
        validarCanalYUsuario(canalId, invitadoId);
        invitacionesPendientes.computeIfAbsent(canalId, key -> ConcurrentHashMap.newKeySet()).add(invitadoId);
        LOGGER.info(() -> "Invitación registrada para canal " + canalId + " usuario " + invitadoId);
        eventBus.publish(new SessionEvent(SessionEventType.INVITE_SENT, null, solicitanteId, Map.of("canalId", canalId, "invitadoId", invitadoId)));
    }

    @Override
    public void aceptarInvitacion(Long canalId, Long invitadoId) {
        if (!isInvited(canalId, invitadoId)) {
            throw new IllegalStateException("No existe invitación para el canal");
        }
        canalRepository.linkUser(canalId, invitadoId);
        invitacionesPendientes.getOrDefault(canalId, ConcurrentHashMap.newKeySet()).remove(invitadoId);
        LOGGER.info(() -> "Usuario " + invitadoId + " unido a canal " + canalId);
    }

    @Override
    public void rechazarInvitacion(Long canalId, Long invitadoId) {
        if (!isInvited(canalId, invitadoId)) {
            return;
        }
        invitacionesPendientes.get(canalId).remove(invitadoId);
        LOGGER.info(() -> "Usuario " + invitadoId + " rechazó invitación de canal " + canalId);
    }

    private boolean isInvited(Long canalId, Long invitadoId) {
        return invitacionesPendientes.getOrDefault(canalId, Set.of()).contains(invitadoId);
    }

    private void validarCanalYUsuario(Long canalId, Long usuarioId) {
        Optional<Canal> canal = canalRepository.findById(canalId);
        if (canal.isEmpty()) {
            throw new IllegalArgumentException("Canal inexistente");
        }
        clienteRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario inexistente"));
    }
}
