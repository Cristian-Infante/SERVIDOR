package com.arquitectura.servicios.eventos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionEventBus {

    private final List<SessionObserver> observers = new CopyOnWriteArrayList<>();

    public void subscribe(SessionObserver observer) {
        observers.add(observer);
    }

    public void unsubscribe(SessionObserver observer) {
        observers.remove(observer);
    }

    public void publish(SessionEvent event) {
        for (SessionObserver observer : observers) {
            observer.onEvent(event);
        }
    }
}
