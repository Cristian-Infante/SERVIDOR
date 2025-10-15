package com.arquitectura.dto;

import java.time.LocalDateTime;

public class LogEntryDto {

    private Long id;
    private boolean tipo;
    private String detalle;
    private LocalDateTime fechaHora;

    public LogEntryDto() {
    }

    public LogEntryDto(Long id, boolean tipo, String detalle, LocalDateTime fechaHora) {
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

    public boolean getTipo() {
        return tipo;
    }

    public boolean isTipo() {
        return tipo;
    }

    public void setTipo(boolean tipo) {
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
