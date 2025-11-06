package com.arquitectura.servicios.conexion;

import java.util.List;

public interface ConnectionGateway {
    void closeSession(String sessionId);

    void broadcast(Object payload);

    void broadcastLocal(Object payload);

    void sendToChannel(Long canalId, Object payload);

    void sendToSession(String sessionId, Object payload);

    void sendToUser(Long userId, Object payload);

    List<SessionDescriptor> activeSessions();

    SessionDescriptor descriptor(String sessionId);
}
