package com.arquitectura.servicios.conexion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SessionDescriptor {

    private final String sessionId;
    private final Long clienteId;
    private final String usuario;
    private final String ip;
    private final Set<Long> canales = new HashSet<>();

    public SessionDescriptor(String sessionId, Long clienteId, String usuario, String ip) {
        this.sessionId = sessionId;
        this.clienteId = clienteId;
        this.usuario = usuario;
        this.ip = ip;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getIp() {
        return ip;
    }

    public void joinChannel(Long canalId) {
        if (canalId != null) {
            canales.add(canalId);
        }
    }

    public void leaveChannel(Long canalId) {
        if (canalId != null) {
            canales.remove(canalId);
        }
    }

    public Set<Long> getCanales() {
        return Collections.unmodifiableSet(canales);
    }
}
