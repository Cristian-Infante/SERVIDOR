package com.arquitectura.servicios.eventos;

import java.time.LocalDateTime;
import java.util.Objects;

public class SessionEvent {

    private final SessionEventType type;
    private final String sessionId;
    private final Long actorId;
    private final Object payload;
    private final LocalDateTime timestamp;

    public SessionEvent(SessionEventType type, String sessionId, Long actorId, Object payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.sessionId = sessionId;
        this.actorId = actorId;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }

    public SessionEventType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getActorId() {
        return actorId;
    }

    public Object getPayload() {
        return payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
