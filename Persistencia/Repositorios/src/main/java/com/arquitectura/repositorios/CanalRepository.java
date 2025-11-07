package com.arquitectura.repositorios;

import java.util.List;
import java.util.Optional;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;

public interface CanalRepository {
    Canal save(Canal canal);

    Optional<Canal> findById(Long id);

    Optional<Canal> findByUuid(String uuid);

    List<Canal> findAll();

    List<Cliente> findUsers(Long canalId);

    void linkUser(Long canalId, Long clienteId);

    void unlinkUser(Long canalId, Long clienteId);
}
