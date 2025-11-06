package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.security.PasswordHasher;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionEventTypes;

import java.util.Objects;
import java.util.logging.Logger;

public class RegistroServiceImpl implements RegistroService {

    private static final Logger LOGGER = Logger.getLogger(RegistroServiceImpl.class.getName());

    private final ClienteRepository clienteRepository;
    private final PasswordHasher passwordHasher;
    private final SessionEventBus eventBus;
    private final SessionEventType userRegisteredEventType;
    private ConexionService conexionService; // Inyección circular - se establece después

    public RegistroServiceImpl(ClienteRepository clienteRepository,
                               PasswordHasher passwordHasher,
                               SessionEventBus eventBus) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.userRegisteredEventType = SessionEventTypes.userRegistered();
        if (this.userRegisteredEventType == null) {
            LOGGER.warning("Tipo de evento USER_REGISTERED no disponible; se omitirá la publicación de registros en el bus de eventos");
        }
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
        cliente.setIp(normalizeIp(ip));
        cliente.setEstado(Boolean.FALSE);

        Cliente saved = clienteRepository.save(cliente);
        LOGGER.info(() -> "Cliente registrado: " + saved.getId());
        if (userRegisteredEventType != null) {
            eventBus.publish(new SessionEvent(userRegisteredEventType, null, saved.getId(), saved));
        }
        return saved;
    }

    @Override
    public Cliente autenticarCliente(String email, String contrasenia, String ip) {
        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordHasher.matches(contrasenia, cliente.getContrasenia())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        String sanitizedIp = normalizeIp(ip);
        if (sanitizedIp != null && !sanitizedIp.equals(cliente.getIp())) {
            cliente.setIp(sanitizedIp);
            clienteRepository.save(cliente);
        }

        LOGGER.info(() -> "Cliente autenticado: " + cliente.getId());
        return cliente;
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
