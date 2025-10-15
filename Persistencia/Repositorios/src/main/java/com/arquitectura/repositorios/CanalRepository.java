package com.arquitectura.repositorios;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para la entidad {@link Canal}.
 */
public interface CanalRepository {

    Canal save(Canal canal);

    Optional<Canal> findById(Long id);

    List<Canal> findAll();

    List<Cliente> findUsers(Long canalId);

    void linkUser(Long canalId, Long clienteId);

    void unlinkUser(Long canalId, Long clienteId);
}
