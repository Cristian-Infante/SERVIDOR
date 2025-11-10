package com.arquitectura.servicios.eventos;

public enum SessionEventType {
    LOGIN,
    LOGOUT,
    USER_REGISTERED,
    TCP_CONNECTED,      // Nueva conexión TCP establecida
    TCP_DISCONNECTED,   // Conexión TCP cerrada
    MESSAGE_SENT,
    NEW_MESSAGE,        // Nuevo mensaje recibido (para notificar al receptor)
    NEW_CHANNEL_MESSAGE, // Nuevo mensaje en canal (para notificar a miembros)
    CHANNEL_CREATED,
    INVITE_SENT,
    INVITE_ACCEPTED,
    INVITE_REJECTED,
    AUDIO_SENT,
    CLUSTER_STATE_UPDATED
}
