package com.arquitectura.servicios.conexiones;

/**
 * Punto de extensión para que la capa de control proporcione el manejo de conexiones.
 */
public interface ConnectionGateway {

    void close(Long clienteId);

    void broadcast(String message);

    void sendToChannel(Long canalId, String message);

    default String usuarioDe(Long clienteId) {
        return null;
    }
}
