package com.arquitectura.dto;

import java.time.LocalDateTime;

public class RealtimeInvitationDto {

    private String evento;
    private LocalDateTime timestamp;
    private Long canalId;
    private String canalUuid;
    private String canalNombre;
    private Boolean canalPrivado;
    private Long invitadorId;
    private String invitadorNombre;
    private Long invitadoId;
    private String invitadoNombre;
    private String estado;
    private Long invitacionId;

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public String getCanalUuid() {
        return canalUuid;
    }

    public void setCanalUuid(String canalUuid) {
        this.canalUuid = canalUuid;
    }

    public String getCanalNombre() {
        return canalNombre;
    }

    public void setCanalNombre(String canalNombre) {
        this.canalNombre = canalNombre;
    }

    public Boolean getCanalPrivado() {
        return canalPrivado;
    }

    public void setCanalPrivado(Boolean canalPrivado) {
        this.canalPrivado = canalPrivado;
    }

    public Long getInvitadorId() {
        return invitadorId;
    }

    public void setInvitadorId(Long invitadorId) {
        this.invitadorId = invitadorId;
    }

    public String getInvitadorNombre() {
        return invitadorNombre;
    }

    public void setInvitadorNombre(String invitadorNombre) {
        this.invitadorNombre = invitadorNombre;
    }

    public Long getInvitadoId() {
        return invitadoId;
    }

    public void setInvitadoId(Long invitadoId) {
        this.invitadoId = invitadoId;
    }

    public String getInvitadoNombre() {
        return invitadoNombre;
    }

    public void setInvitadoNombre(String invitadoNombre) {
        this.invitadoNombre = invitadoNombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getInvitacionId() {
        return invitacionId;
    }

    public void setInvitacionId(Long invitacionId) {
        this.invitacionId = invitacionId;
    }
}
