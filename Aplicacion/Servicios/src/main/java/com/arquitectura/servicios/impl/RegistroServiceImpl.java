package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Cliente;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.util.PasswordHasher;

import java.util.Objects;

/**
 * Servicio encargado del registro de clientes.
 */
public class RegistroServiceImpl implements RegistroService {

    private final ClienteRepository clienteRepository;
    private final SessionEventBus eventBus;

    public RegistroServiceImpl(ClienteRepository clienteRepository, SessionEventBus eventBus) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public Cliente registrar(String usuario, String email, String passwordPlano, byte[] foto, String ip) {
        clienteRepository.findByEmail(email).ifPresent(c -> {
            throw new IllegalArgumentException("El email ya se encuentra registrado");
        });
        boolean usuarioEnUso = clienteRepository.all().stream()
                .anyMatch(cliente -> usuario.equalsIgnoreCase(cliente.getNombreDeUsuario()));
        if (usuarioEnUso) {
            throw new IllegalArgumentException("El nombre de usuario ya existe");
        }
        Cliente cliente = new Cliente();
        cliente.setNombreDeUsuario(usuario);
        cliente.setEmail(email);
        cliente.setContrasenia(PasswordHasher.hash(passwordPlano));
        cliente.setFoto(foto);
        cliente.setIp(ip);
        cliente.setEstado(Boolean.TRUE);
        Cliente guardado = clienteRepository.save(cliente);
        eventBus.publish(new SessionEvent(SessionEvent.Type.LOGIN, java.util.Map.of(
                "clienteId", guardado.getId(),
                "usuario", guardado.getNombreDeUsuario()
        )));
        return guardado;
    }
}
