package com.arquitectura.servicios;

import com.arquitectura.entidades.Cliente;

public interface RegistroService {
    Cliente registrarCliente(String usuario, String email, String contrasenia, byte[] foto, String ip);

    Cliente autenticarCliente(String email, String contrasenia, String ip);
}
