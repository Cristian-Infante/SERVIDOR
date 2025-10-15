package com.arquitectura.entidades;

import java.time.LocalDateTime;

public abstract class Mensaje {
    private Long id;
    private LocalDateTime timeStamp;
    private String tipo;
    private Long emisor;
    private Long receptor;
    private Long canalId;

    public Mensaje() {
    }

    public Mensaje(Long id, LocalDateTime timeStamp, String tipo, Long emisor, Long receptor, Long canalId) {
        this.id = id;
        this.timeStamp = timeStamp;
        this.tipo = tipo;
        this.emisor = emisor;
        this.receptor = receptor;
        this.canalId = canalId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getEmisor() {
        return emisor;
    }

    public void setEmisor(Long emisor) {
        this.emisor = emisor;
    }

    public Long getReceptor() {
        return receptor;
    }

    public void setReceptor(Long receptor) {
        this.receptor = receptor;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }
}
