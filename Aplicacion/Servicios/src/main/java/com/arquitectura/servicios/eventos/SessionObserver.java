package com.arquitectura.servicios.eventos;

/**
 * Interfaz observer para reaccionar ante eventos del servidor.
 */
@FunctionalInterface
public interface SessionObserver {

    void onEvent(SessionEvent event);
}
