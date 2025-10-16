package com.arquitectura.servicios;

import com.arquitectura.servicios.conexion.SessionDescriptor;

import java.util.List;

public interface ConexionService {
    /**
     * Cierra una conexión específica, enviando notificación al cliente antes de cerrar
     */
    void cerrarConexion(String sessionId);
    
    /**
     * Cierra una conexión específica con una razón
     */
    void cerrarConexion(String sessionId, String razon);

    /**
     * Envía un mensaje broadcast a todos los clientes conectados
     */
    void broadcast(String mensaje);
    
    /**
     * Notifica a todos los clientes que el servidor se está apagando
     */
    void notificarApagado(String mensaje);

    /**
     * Envía un mensaje a todos los usuarios de un canal
     */
    void enviarACanal(Long canalId, String mensaje);

    /**
     * Obtiene la lista de sesiones activas
     */
    List<SessionDescriptor> sesionesActivas();
}
