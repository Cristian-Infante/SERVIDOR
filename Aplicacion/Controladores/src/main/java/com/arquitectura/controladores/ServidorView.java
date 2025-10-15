package com.arquitectura.controladores;

import javax.swing.*;

public interface ServidorView {
    java.awt.Component asComponent();
    JTextField getTxtEmail();
    JTextField getTxtUsuario();
    JPasswordField getTxtContrasena();
    JTextField getTxtFotoRuta();
    JButton getBtnSeleccionarFoto();
    JTextField getTxtDireccionIp();
    JButton getBtnEnviarRegistro();
    JList<String> getLstConexiones();
    JButton getBtnCerrarConexion();
    JButton getBtnGenerarUsuarios();
    JButton getBtnGenerarCanales();
    JButton getBtnGenerarConectados();
    JButton getBtnGenerarLogs();
    void setVisible(boolean visible);
}
