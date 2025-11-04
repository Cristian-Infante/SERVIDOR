package com.arquitectura.controladores;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

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
    JTextField getTxtPeerEndpoint();
    JButton getBtnConectarServidor();
    JList<String> getLstServidores();
    JButton getBtnCerrarConexion();
    JButton getBtnGenerarUsuarios();
    JButton getBtnGenerarCanales();
    JButton getBtnGenerarConectados();
    JButton getBtnGenerarLogs();
    JButton getBtnApagarServidor();
    void setVisible(boolean visible);
    
    /**
     * Muestra un diálogo moderno con el reporte y permite guardarlo.
     * @param titulo Título del diálogo
     * @param contenido Contenido del reporte
     * @return true si el usuario desea guardar el reporte, false en caso contrario
     */
    boolean mostrarDialogoReporte(String titulo, String contenido);
}
