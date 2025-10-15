package com.arquitectura.entidades.vistas;

import com.arquitectura.bootstrap.ServidorApplication;

public class Vistas {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ServidorApplication application = new ServidorApplication();
            ServidorVistaAdapter vista = new ServidorVistaAdapter();
            application.createServidorController(vista);
            vista.setVisible(true);
        });
    }
}

