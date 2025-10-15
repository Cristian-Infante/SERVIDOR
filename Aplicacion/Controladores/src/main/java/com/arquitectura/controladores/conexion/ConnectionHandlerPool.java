package com.arquitectura.controladores.conexion;

import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;

public class ConnectionHandlerPool {

    private final Queue<ConnectionHandler> pool;

    public ConnectionHandlerPool(int capacity, Supplier<ConnectionHandler> factory) {
        this.pool = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < capacity; i++) {
            ConnectionHandler handler = factory.get();
            handler.setPool(this);
            pool.add(handler);
        }
    }

    public synchronized ConnectionHandler acquire(Socket socket) {
        ConnectionHandler handler = pool.poll();
        if (handler == null) {
            return null;
        }
        handler.attach(socket);
        return handler;
    }

    public synchronized void release(ConnectionHandler handler) {
        if (handler != null) {
            pool.offer(handler);
        }
    }
}
