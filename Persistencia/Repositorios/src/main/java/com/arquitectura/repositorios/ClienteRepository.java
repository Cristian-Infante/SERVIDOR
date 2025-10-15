package com.arquitectura.repositorios;

import com.arquitectura.entidades.Cliente;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos para entidades {@link Cliente}.
 */
public interface ClienteRepository {

    Cliente save(Cliente cliente);

    Optional<Cliente> findById(Long id);

    Optional<Cliente> findByEmail(String email);

    List<Cliente> findConnected();

    void setConnected(Long id, boolean connected);

    List<Cliente> all();
}
