package com.arquitectura.servicios.metrics;

import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

import java.util.Objects;

/**
 * Observador del bus de eventos que traduce eventos de dominio a métricas
 * para Grafana/Prometheus (logins, mensajes, invitaciones, etc.).
 */
public class MetricsSessionObserver implements SessionObserver {

    public MetricsSessionObserver(SessionEventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus").subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        SessionEventType type = event.getType();
        switch (type) {
            case TCP_CONNECTED -> ServerMetrics.onTcpSessionRegistered();
            case TCP_DISCONNECTED -> ServerMetrics.onTcpSessionUnregistered();
            case LOGIN -> ServerMetrics.onSessionAuthenticated();
            case LOGOUT -> ServerMetrics.onSessionLogout();
            case MESSAGE_SENT -> ServerMetrics.recordRealtimeEvent("MESSAGE_SENT");
            case NEW_MESSAGE -> ServerMetrics.recordRealtimeEvent("NEW_MESSAGE");
            case NEW_CHANNEL_MESSAGE -> ServerMetrics.recordRealtimeEvent("NEW_CHANNEL_MESSAGE");
            case INVITE_SENT -> ServerMetrics.recordRealtimeEvent("INVITE_SENT");
            case INVITE_ACCEPTED -> ServerMetrics.recordRealtimeEvent("INVITE_ACCEPTED");
            case INVITE_REJECTED -> ServerMetrics.recordRealtimeEvent("INVITE_REJECTED");
            case AUDIO_SENT -> ServerMetrics.recordAudioMessage();
            default -> {
                // Otros eventos no se miden por ahora
            }
        }
    }
}
