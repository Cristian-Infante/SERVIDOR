package com.arquitectura.entidades;

import java.time.LocalDateTime;

public class Log {
    private Long id;
    private Boolean tipo;
    private String detalle;
    private LocalDateTime fechaHora;

    public Log() {
    }

    public Log(Long id, Boolean tipo, String detalle, LocalDateTime fechaHora) {
        this.id = id;
        this.tipo = tipo;
        this.detalle = detalle;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getTipo() {
        return tipo;
    }

    public void setTipo(Boolean tipo) {
        this.tipo = tipo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
