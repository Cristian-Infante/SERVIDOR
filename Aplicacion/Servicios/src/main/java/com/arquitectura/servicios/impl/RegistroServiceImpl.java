package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.security.PasswordHasher;

import java.util.Objects;
import java.util.logging.Logger;

public class RegistroServiceImpl implements RegistroService {

    private static final Logger LOGGER = Logger.getLogger(RegistroServiceImpl.class.getName());

    private final ClienteRepository clienteRepository;
    private final PasswordHasher passwordHasher;

    public RegistroServiceImpl(ClienteRepository clienteRepository, PasswordHasher passwordHasher) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    @Override
    public Cliente registrarCliente(String usuario, String email, String contrasenia, byte[] foto, String ip) {
        clienteRepository.findByEmail(email).ifPresent(existing -> {
            throw new IllegalArgumentException("El email ya está registrado");
        });

        boolean usuarioDuplicado = clienteRepository.all().stream()
                .anyMatch(cli -> cli.getNombreDeUsuario().equalsIgnoreCase(usuario));
        if (usuarioDuplicado) {
            throw new IllegalArgumentException("El usuario ya está registrado");
        }

        Cliente cliente = new Cliente();
        cliente.setNombreDeUsuario(usuario);
        cliente.setEmail(email);
        cliente.setContrasenia(passwordHasher.hash(contrasenia));
        cliente.setFoto(foto);
        cliente.setIp(ip);
        cliente.setEstado(Boolean.FALSE);

        Cliente saved = clienteRepository.save(cliente);
        LOGGER.info(() -> "Cliente registrado: " + saved.getId());
        return saved;
    }
}
