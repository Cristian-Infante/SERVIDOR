package com.arquitectura.dto;

import java.time.LocalDateTime;

/**
 * Evento enviado a los clientes cuando cambia el estado de conexión de un usuario.
 */
public class ConnectionStatusUpdateDto {

    private String evento;
    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private boolean conectado;
    private int sesionesActivas;
    private LocalDateTime timestamp;

    public ConnectionStatusUpdateDto() {
    }

    public ConnectionStatusUpdateDto(String evento,
                                     Long usuarioId,
                                     String usuarioNombre,
                                     String usuarioEmail,
                                     boolean conectado,
                                     int sesionesActivas,
                                     LocalDateTime timestamp) {
        this.evento = evento;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.usuarioEmail = usuarioEmail;
        this.conectado = conectado;
        this.sesionesActivas = sesionesActivas;
        this.timestamp = timestamp;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public void setUsuarioEmail(String usuarioEmail) {
        this.usuarioEmail = usuarioEmail;
    }

    public boolean isConectado() {
        return conectado;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }

    public int getSesionesActivas() {
        return sesionesActivas;
    }

    public void setSesionesActivas(int sesionesActivas) {
        this.sesionesActivas = sesionesActivas;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
