package com.arquitectura.dto;

public class SessionInfoDto {

    private String sessionId;
    private Long clienteId;
    private String usuario;
    private String ip;

    public SessionInfoDto() {
    }

    public SessionInfoDto(String sessionId, Long clienteId, String usuario, String ip) {
        this.sessionId = sessionId;
        this.clienteId = clienteId;
        this.usuario = usuario;
        this.ip = ip;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String toString() {
        return usuario + " (" + ip + ")";
    }
}
