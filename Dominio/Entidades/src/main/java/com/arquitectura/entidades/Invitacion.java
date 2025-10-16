package com.arquitectura.entidades;

import java.time.LocalDateTime;

public class Invitacion {
    private Long id;
    private Long canalId;
    private Long invitadorId;
    private Long invitadoId;
    private LocalDateTime fechaInvitacion;
    private String estado; // "PENDIENTE", "ACEPTADA", "RECHAZADA"

    public Invitacion() {
    }

    public Invitacion(Long canalId, Long invitadorId, Long invitadoId) {
        this.canalId = canalId;
        this.invitadorId = invitadorId;
        this.invitadoId = invitadoId;
        this.fechaInvitacion = LocalDateTime.now();
        this.estado = "PENDIENTE";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public Long getInvitadorId() {
        return invitadorId;
    }

    public void setInvitadorId(Long invitadorId) {
        this.invitadorId = invitadorId;
    }

    public Long getInvitadoId() {
        return invitadoId;
    }

    public void setInvitadoId(Long invitadoId) {
        this.invitadoId = invitadoId;
    }

    public LocalDateTime getFechaInvitacion() {
        return fechaInvitacion;
    }

    public void setFechaInvitacion(LocalDateTime fechaInvitacion) {
        this.fechaInvitacion = fechaInvitacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
