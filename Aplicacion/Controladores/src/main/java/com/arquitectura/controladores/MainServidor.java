package com.arquitectura.controladores;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainServidor {

    private static final Logger LOGGER = Logger.getLogger(MainServidor.class.getName());

    public static void main(String[] args) {
        ServerBootstrap bootstrap = ServerBootstrap.createDefault();
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                bootstrap.start();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error iniciando servidor", e);
            }
        });

        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> clazz = Class.forName("com.arquitectura.entidades.vistas.ServidorVistaAdapter");
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (!(instance instanceof ServidorView view)) {
                    throw new IllegalStateException("La vista no implementa ServidorView");
                }
                new ServidorController(view,
                        bootstrap.getRegistroService(),
                        bootstrap.getReporteService(),
                        bootstrap.getConexionService(),
                        bootstrap.getEventBus());
                view.setVisible(true);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     NoSuchMethodException | InvocationTargetException ex) {
                LOGGER.log(Level.SEVERE, "No se pudo crear la vista del servidor", ex);
            }
        });
    }
}
