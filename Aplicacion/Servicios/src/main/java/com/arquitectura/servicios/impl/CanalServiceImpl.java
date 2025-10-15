package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Canal;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;

import java.util.List;
import java.util.Objects;

public class CanalServiceImpl implements CanalService {

    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;
    private final SessionEventBus eventBus;

    public CanalServiceImpl(CanalRepository canalRepository, ClienteRepository clienteRepository, SessionEventBus eventBus) {
        this.canalRepository = Objects.requireNonNull(canalRepository);
        this.clienteRepository = Objects.requireNonNull(clienteRepository);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public Canal crearCanal(String nombre, boolean privado) {
        Canal canal = new Canal();
        canal.setNombre(nombre);
        canal.setPrivado(privado);
        Canal guardado = canalRepository.save(canal);
        eventBus.publish(new SessionEvent(SessionEvent.Type.CHANNEL_CREATED, java.util.Map.of(
                "canalId", guardado.getId(),
                "nombre", guardado.getNombre()
        )));
        return guardado;
    }

    @Override
    public void invitarUsuario(Long canalId, Long clienteId) {
        validarExistencia(canalId, clienteId);
        eventBus.publish(new SessionEvent(SessionEvent.Type.INVITE_SENT, java.util.Map.of(
                "canalId", canalId,
                "clienteId", clienteId
        )));
    }

    @Override
    public void aceptarInvitacion(Long canalId, Long clienteId) {
        validarExistencia(canalId, clienteId);
        canalRepository.linkUser(canalId, clienteId);
    }

    @Override
    public void rechazarInvitacion(Long canalId, Long clienteId) {
        validarExistencia(canalId, clienteId);
        canalRepository.unlinkUser(canalId, clienteId);
    }

    @Override
    public List<Canal> listarCanales() {
        return canalRepository.findAll();
    }

    private void validarExistencia(Long canalId, Long clienteId) {
        canalRepository.findById(canalId).orElseThrow(() -> new IllegalArgumentException("Canal no encontrado"));
        clienteRepository.findById(clienteId).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
    }
}
