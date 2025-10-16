package com.arquitectura.dto;

import java.util.List;

/**
 * Respuesta de sincronización de mensajes
 * Se envía al cliente después del login para sincronizar su historial
 */
public class MessageSyncResponse {

    private List<SyncedMessageDto> mensajes;  // Lista de mensajes con metadatos enriquecidos
    private int totalMensajes;
    private String ultimaSincronizacion;

    public MessageSyncResponse() {
    }

    public MessageSyncResponse(List<SyncedMessageDto> mensajes) {
        this.mensajes = mensajes;
        this.totalMensajes = mensajes != null ? mensajes.size() : 0;
        this.ultimaSincronizacion = java.time.LocalDateTime.now().toString();
    }

    public List<SyncedMessageDto> getMensajes() {
        return mensajes;
    }

    public void setMensajes(List<SyncedMessageDto> mensajes) {
        this.mensajes = mensajes;
        this.totalMensajes = mensajes != null ? mensajes.size() : 0;
    }

    public int getTotalMensajes() {
        return totalMensajes;
    }

    public void setTotalMensajes(int totalMensajes) {
        this.totalMensajes = totalMensajes;
    }

    public String getUltimaSincronizacion() {
        return ultimaSincronizacion;
    }

    public void setUltimaSincronizacion(String ultimaSincronizacion) {
        this.ultimaSincronizacion = ultimaSincronizacion;
    }
}
