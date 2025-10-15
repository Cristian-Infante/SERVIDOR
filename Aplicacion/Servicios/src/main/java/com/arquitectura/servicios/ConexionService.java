package com.arquitectura.servicios;

public interface ConexionService {

    void cerrarConexion(Long clienteId);

    void broadcast(String mensaje);

    void enviarACanal(Long canalId, String mensaje);
}
