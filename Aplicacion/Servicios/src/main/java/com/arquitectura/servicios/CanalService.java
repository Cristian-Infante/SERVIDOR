package com.arquitectura.servicios;

import com.arquitectura.entidades.Canal;

import java.util.List;

public interface CanalService {

    Canal crearCanal(String nombre, boolean privado);

    void invitarUsuario(Long canalId, Long clienteId);

    void aceptarInvitacion(Long canalId, Long clienteId);

    void rechazarInvitacion(Long canalId, Long clienteId);

    List<Canal> listarCanales();
}
