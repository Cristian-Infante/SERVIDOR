package com.arquitectura.servicios;

import com.arquitectura.servicios.conexion.SessionDescriptor;

import java.util.List;

public interface ConexionService {
    void cerrarConexion(String sessionId);

    void broadcast(String mensaje);

    void enviarACanal(Long canalId, String mensaje);

    List<SessionDescriptor> sesionesActivas();
}
