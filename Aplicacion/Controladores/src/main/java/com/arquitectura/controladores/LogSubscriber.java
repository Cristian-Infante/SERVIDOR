package com.arquitectura.controladores;

import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionObserver;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Observer que escribe eventos relevantes en el log del servidor.
 */
public class LogSubscriber implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(LogSubscriber.class.getName());

    @Override
    public void onEvent(SessionEvent event) {
        LOGGER.log(Level.INFO, "Evento {0} - payload {1}", new Object[]{event.getType(), event.getPayload()});
    }
}
