package com.arquitectura.entidades.vistas;

import javax.swing.*;

public class Vistas {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ServidorVista().setVisible(true);
        });
    }
}

