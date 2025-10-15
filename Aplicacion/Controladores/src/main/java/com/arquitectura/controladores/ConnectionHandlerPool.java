package com.arquitectura.controladores;

import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pool simple de {@link ConnectionHandler} reutilizables.
 */
public class ConnectionHandlerPool {

    private static final Logger LOGGER = Logger.getLogger(ConnectionHandlerPool.class.getName());

    private final BlockingQueue<ConnectionHandler> pool;

    public ConnectionHandlerPool(int capacity,
                                 RegistroService registroService,
                                 CanalService canalService,
                                 MensajeriaService mensajeriaService,
                                 ReporteService reporteService,
                                 ConexionService conexionService,
                                 ConnectionRegistry registry,
                                 com.arquitectura.servicios.eventos.SessionEventBus eventBus) {
        this.pool = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < capacity; i++) {
            pool.offer(new ConnectionHandler(registroService, canalService, mensajeriaService, reporteService, conexionService, registry, eventBus));
        }
    }

    public ConnectionHandler borrow() {
        ConnectionHandler handler = pool.poll();
        if (handler == null) {
            LOGGER.log(Level.WARNING, "Pool sin capacidad disponible");
        }
        return handler;
    }

    public void release(ConnectionHandler handler) {
        if (handler != null) {
            if (!pool.offer(handler)) {
                LOGGER.log(Level.WARNING, "No fue posible devolver el handler al pool");
            }
        }
    }
}
