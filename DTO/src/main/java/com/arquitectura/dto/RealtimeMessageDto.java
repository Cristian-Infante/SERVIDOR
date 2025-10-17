package com.arquitectura.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Representa un mensaje enviado en tiempo real a través del canal de eventos del
 * servidor. Incluye metadatos similares a los mensajes sincronizados más un
 * indicador del tipo de evento para que el cliente pueda reaccionar
 * adecuadamente.
 */
public class RealtimeMessageDto {

    private String evento;
    private Long id;
    private String tipoMensaje;
    private LocalDateTime timestamp;
    private Long emisorId;
    private String emisorNombre;
    private Long receptorId;
    private String receptorNombre;
    private Long canalId;
    private String canalNombre;
    private String tipoConversacion;
    private Map<String, Object> contenido;

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipoMensaje() {
        return tipoMensaje;
    }

    public void setTipoMensaje(String tipoMensaje) {
        this.tipoMensaje = tipoMensaje;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getEmisorId() {
        return emisorId;
    }

    public void setEmisorId(Long emisorId) {
        this.emisorId = emisorId;
    }

    public String getEmisorNombre() {
        return emisorNombre;
    }

    public void setEmisorNombre(String emisorNombre) {
        this.emisorNombre = emisorNombre;
    }

    public Long getReceptorId() {
        return receptorId;
    }

    public void setReceptorId(Long receptorId) {
        this.receptorId = receptorId;
    }

    public String getReceptorNombre() {
        return receptorNombre;
    }

    public void setReceptorNombre(String receptorNombre) {
        this.receptorNombre = receptorNombre;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public String getCanalNombre() {
        return canalNombre;
    }

    public void setCanalNombre(String canalNombre) {
        this.canalNombre = canalNombre;
    }

    public String getTipoConversacion() {
        return tipoConversacion;
    }

    public void setTipoConversacion(String tipoConversacion) {
        this.tipoConversacion = tipoConversacion;
    }

    public Map<String, Object> getContenido() {
        return contenido;
    }

    public void setContenido(Map<String, Object> contenido) {
        this.contenido = contenido;
    }
}
