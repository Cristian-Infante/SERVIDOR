package com.arquitectura.servicios;

import com.arquitectura.entidades.Cliente;

public interface RegistroService {

    Cliente registrar(String usuario, String email, String passwordPlano, byte[] foto, String ip);
}
