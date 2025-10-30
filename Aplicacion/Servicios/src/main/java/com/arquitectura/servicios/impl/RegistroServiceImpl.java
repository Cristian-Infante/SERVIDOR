package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.security.PasswordHasher;

import java.util.Objects;
import java.util.logging.Logger;

public class RegistroServiceImpl implements RegistroService {

    private static final Logger LOGGER = Logger.getLogger(RegistroServiceImpl.class.getName());

    private final ClienteRepository clienteRepository;
    private final PasswordHasher passwordHasher;
    private ConexionService conexionService; // Inyección circular - se establece después

    public RegistroServiceImpl(ClienteRepository clienteRepository, PasswordHasher passwordHasher) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }
    
    /**
     * Establece el servicio de conexión (para evitar dependencia circular)
     */
    public void setConexionService(ConexionService conexionService) {
        this.conexionService = conexionService;
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

    @Override
    public Cliente autenticarCliente(String email, String contrasenia, String ip) {
        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordHasher.matches(contrasenia, cliente.getContrasenia())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        if (ip != null && !ip.isBlank() && !ip.equals(cliente.getIp())) {
            cliente.setIp(ip);
            clienteRepository.save(cliente);
        }

        LOGGER.info(() -> "Cliente autenticado: " + cliente.getId());
        return cliente;
    }
}
