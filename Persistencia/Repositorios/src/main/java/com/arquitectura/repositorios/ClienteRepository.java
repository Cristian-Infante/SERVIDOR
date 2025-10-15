package com.arquitectura.repositorios;

import com.arquitectura.entidades.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository {
    Cliente save(Cliente cliente);

    Optional<Cliente> findById(Long id);

    Optional<Cliente> findByEmail(String email);

    List<Cliente> findConnected();

    void setConnected(Long id, boolean connected);

    List<Cliente> all();
}
