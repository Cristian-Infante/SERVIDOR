package com.arquitectura.entidades.vistas;

import javax.swing.*;
import java.awt.*;

public class ServidorVista extends JFrame {
    // Registrar Cliente
    private final JTextField txtEmail;
    private final JTextField txtUsuario;
    private final JPasswordField txtContrasena;
    private final JTextField txtFotoRuta;
    private final JButton btnSeleccionarFoto;
    private final JTextField txtDireccionIp;
    private final JButton btnEnviarRegistro;

    // Conexiones activas
    private final JList<String> lstConexiones;
    private final JButton btnCerrarConexion;

    // Reportes
    private final JButton btnGenerarUsuarios;
    private final JButton btnGenerarCanales;
    private final JButton btnGenerarConectados;
    private final JButton btnGenerarLogs;

    public ServidorVista() {
        super("Servidor - Panel");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        // Inicializar componentes finales aquí (constructor)
        txtEmail = new JTextField();
        txtUsuario = new JTextField();
        txtContrasena = new JPasswordField();
        txtFotoRuta = new JTextField();
        txtFotoRuta.setEditable(false);
        btnSeleccionarFoto = new JButton("Seleccionar");
        stylePrimary(btnSeleccionarFoto);
        txtDireccionIp = new JTextField();
        btnEnviarRegistro = new JButton("Enviar");
        stylePrimary(btnEnviarRegistro);

        lstConexiones = new JList<>(new DefaultListModel<>());
        btnCerrarConexion = new JButton("Cerrar");
        stylePrimary(btnCerrarConexion);

        btnGenerarUsuarios = new JButton("Generar");
        stylePrimary(btnGenerarUsuarios);
        btnGenerarCanales = new JButton("Generar");
        stylePrimary(btnGenerarCanales);
        btnGenerarConectados = new JButton("Generar");
        stylePrimary(btnGenerarConectados);
        btnGenerarLogs = new JButton("Generar");
        stylePrimary(btnGenerarLogs);

        JPanel main = new JPanel(new GridLayout(1, 2, 24, 0));
        main.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        // Panel Derecho: Conexiones + Reportes
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(buildTitulo("Conexiones activas"));
        right.add(Box.createVerticalStrut(8));
        right.add(buildConexionesPanel());
        right.add(Box.createVerticalStrut(24));
        right.add(buildTitulo("Reportes"));
        right.add(Box.createVerticalStrut(8));
        right.add(buildReportesPanel());

        main.add(right);
        setContentPane(main);
    }

    private JComponent buildTitulo(String texto) {
        JLabel title = new JLabel(texto);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        return title;
    }

    private JComponent buildRegistroPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 0, 2, 0);

        JLabel lblEmail = new JLabel("Email");
        p.add(lblEmail, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        txtEmail.setName("txtEmail");
        txtEmail.putClientProperty("JTextField.placeholderText", "Ingresa tu Email");
        p.add(txtEmail, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 0, 2, 0); gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel lblUsuario = new JLabel("Usuario");
        p.add(lblUsuario, gbc);
        gbc.gridy++; gbc.insets = new Insets(0, 0, 8, 0); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        txtUsuario.setName("txtUsuarioRegistro");
        txtUsuario.putClientProperty("JTextField.placeholderText", "Ingresa un Usuario");
        p.add(txtUsuario, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 0, 2, 0); gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel lblContrasena = new JLabel("Contraseña");
        p.add(lblContrasena, gbc);
        gbc.gridy++; gbc.insets = new Insets(0, 0, 8, 0); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        txtContrasena.setName("txtContrasenaRegistro");
        txtContrasena.putClientProperty("JTextField.placeholderText", "Ingresa una contraseña de acceso");
        p.add(txtContrasena, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 0, 2, 0); gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel lblFoto = new JLabel("Foto");
        p.add(lblFoto, gbc);
        gbc.gridy++; gbc.insets = new Insets(0, 0, 8, 0); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPanel fotoRow = new JPanel(new BorderLayout(8, 0));
        txtFotoRuta.setName("txtFotoRuta");
        txtFotoRuta.putClientProperty("JTextField.placeholderText", "Selecciona una foto de perfil");
        btnSeleccionarFoto.setName("btnSeleccionarFoto");
        fotoRow.add(txtFotoRuta, BorderLayout.CENTER);
        fotoRow.add(btnSeleccionarFoto, BorderLayout.EAST);
        p.add(fotoRow, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 0, 2, 0); gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel lblIp = new JLabel("DireccionIp (Con puntos)");
        p.add(lblIp, gbc);
        gbc.gridy++; gbc.insets = new Insets(0, 0, 12, 0); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        txtDireccionIp.setName("txtDireccionIp");
        txtDireccionIp.putClientProperty("JTextField.placeholderText", "xxx.xx.xx.xxx");
        p.add(txtDireccionIp, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 0, 8, 0); gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST;
        btnEnviarRegistro.setName("btnEnviarRegistro");
        p.add(btnEnviarRegistro, gbc);

        return p;
    }

    private JComponent buildConexionesPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 24));

        JLabel lblMax = new JLabel("Máximo: 5");
        p.add(lblMax, BorderLayout.NORTH);

        lstConexiones.setName("lstConexiones");
        JScrollPane scroll = new JScrollPane(lstConexiones);
        scroll.setPreferredSize(new Dimension(380, 280));
        p.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnCerrarConexion.setName("btnCerrarConexion");
        actions.add(btnCerrarConexion);
        p.add(actions, BorderLayout.SOUTH);

        return p;
    }

    private JComponent buildReportesPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 8);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;

        // Usuarios registrados
        p.add(new JLabel("Usuarios registrados"), gbc);
        gbc.gridx = 1; btnGenerarUsuarios.setName("btnGenerarUsuarios");
        p.add(btnGenerarUsuarios, gbc);

        // Canales con usuarios vinculados
        gbc.gridx = 0; gbc.gridy++; p.add(new JLabel("Canales con usuarios vinculados"), gbc);
        gbc.gridx = 1; btnGenerarCanales.setName("btnGenerarCanales");
        p.add(btnGenerarCanales, gbc);

        // Usuarios conectados
        gbc.gridx = 0; gbc.gridy++; p.add(new JLabel("Usuarios conectados"), gbc);
        gbc.gridx = 1; btnGenerarConectados.setName("btnGenerarConectados");
        p.add(btnGenerarConectados, gbc);

        // Logs
        gbc.gridx = 0; gbc.gridy++; p.add(new JLabel("Logs"), gbc);
        gbc.gridx = 1; btnGenerarLogs.setName("btnGenerarLogs");
        p.add(btnGenerarLogs, gbc);

        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private void stylePrimary(AbstractButton b) {
        b.setBackground(new Color(63, 35, 255));
        b.setForeground(Color.BLACK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    // Getters para uso posterior en controladores/handlers
    public JTextField getTxtEmail() { return txtEmail; }
    public JTextField getTxtUsuario() { return txtUsuario; }
    public JPasswordField getTxtContrasena() { return txtContrasena; }
    public JTextField getTxtFotoRuta() { return txtFotoRuta; }
    public JButton getBtnSeleccionarFoto() { return btnSeleccionarFoto; }
    public JTextField getTxtDireccionIp() { return txtDireccionIp; }
    public JButton getBtnEnviarRegistro() { return btnEnviarRegistro; }
    public JList<String> getLstConexiones() { return lstConexiones; }
    public JButton getBtnCerrarConexion() { return btnCerrarConexion; }
    public JButton getBtnGenerarUsuarios() { return btnGenerarUsuarios; }
    public JButton getBtnGenerarCanales() { return btnGenerarCanales; }
    public JButton getBtnGenerarConectados() { return btnGenerarConectados; }
    public JButton getBtnGenerarLogs() { return btnGenerarLogs; }
}
