package com.arquitectura.servicios.eventos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Evento emitido cuando ocurre una acción relevante en una sesión del servidor.
 */
public final class SessionEvent {

    public enum Type {
        LOGIN,
        LOGOUT,
        MESSAGE_SENT,
        CHANNEL_CREATED,
        INVITE_SENT,
        AUDIO_SENT
    }

    private final Type type;
    private final LocalDateTime timestamp;
    private final Map<String, Object> payload;

    public SessionEvent(Type type, Map<String, Object> payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
