package com.arquitectura.servicios.eventos;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;

import java.util.Objects;
import java.util.logging.Logger;

public class LogSubscriber implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(LogSubscriber.class.getName());

    private final LogRepository logRepository;

    public LogSubscriber(LogRepository logRepository, SessionEventBus bus) {
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        bus.subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        Log log = new Log();
        log.setFechaHora(event.getTimestamp());
        log.setTipo(Boolean.FALSE);
        log.setDetalle(describir(event));
        logRepository.append(log);
        LOGGER.fine(() -> "Evento registrado: " + event.getType());
    }

    private String describir(SessionEvent event) {
        return switch (event.getType()) {
            case LOGIN -> "Login de usuario " + event.getActorId();
            case LOGOUT -> "Logout de usuario " + event.getActorId();
            case MESSAGE_SENT -> "Mensaje enviado";
            case CHANNEL_CREATED -> "Canal creado";
            case INVITE_SENT -> "Invitación emitida";
            case AUDIO_SENT -> "Audio enviado";
        };
    }
}
