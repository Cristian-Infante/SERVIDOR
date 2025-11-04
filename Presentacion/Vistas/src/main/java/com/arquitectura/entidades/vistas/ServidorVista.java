package com.arquitectura.entidades.vistas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class ServidorVista extends JFrame {
    // Colores modernos
    private static final Color BG_PRIMARY = new Color(17, 24, 39);      // Gris oscuro principal
    private static final Color BG_SECONDARY = new Color(31, 41, 55);    // Gris oscuro secundario
    private static final Color BG_CARD = new Color(55, 65, 81);         // Gris para tarjetas
    private static final Color TEXT_PRIMARY = new Color(243, 244, 246); // Blanco suave
    private static final Color TEXT_SECONDARY = new Color(156, 163, 175); // Gris texto
    private static final Color ACCENT_BLUE = new Color(59, 130, 246);   // Azul moderno
    private static final Color ACCENT_BLUE_HOVER = new Color(37, 99, 235);
    private static final Color ACCENT_RED = new Color(239, 68, 68);     // Rojo moderno
    private static final Color ACCENT_RED_HOVER = new Color(220, 38, 38);
    private static final Color BORDER_COLOR = new Color(75, 85, 99);    // Borde sutil

    // Componentes de registro (mantenidos para compatibilidad pero no se muestran)
    private final JTextField txtEmail;
    private final JTextField txtUsuario;
    private final JPasswordField txtContrasena;
    private final JTextField txtFotoRuta;
    private final JButton btnSeleccionarFoto;
    private final JTextField txtDireccionIp;
    private final JButton btnEnviarRegistro;

    // Conexiones activas
    private final JList<String> lstConexiones;
    private final JList<String> lstServidores;
    private final JButton btnCerrarConexion;
    private final JButton btnConectarServidor;

    // Reportes
    private final JButton btnGenerarUsuarios;
    private final JButton btnGenerarCanales;
    private final JButton btnGenerarConectados;
    private final JButton btnGenerarLogs;
    
    // Control del servidor
    private final JButton btnApagarServidor;

    // Configuración del servidor
    private final int maxConnections;
    private final JTextField txtPeerEndpoint;

    public ServidorVista() {
        this(5); // Valor por defecto para compatibilidad
    }
    
    public ServidorVista(int maxConnections) {
        super("Panel de Control del Servidor");
        this.maxConnections = maxConnections;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Inicializar componentes (mantenidos para compatibilidad)
        txtEmail = new JTextField();
        txtUsuario = new JTextField();
        txtContrasena = new JPasswordField();
        txtFotoRuta = new JTextField();
        txtFotoRuta.setEditable(false);
        btnSeleccionarFoto = new JButton("Seleccionar");
        txtDireccionIp = new JTextField();
        btnEnviarRegistro = new JButton("Enviar");
        txtPeerEndpoint = new JTextField();
        txtPeerEndpoint.setName("txtPeerEndpoint");
        txtPeerEndpoint.setBackground(BG_CARD);
        txtPeerEndpoint.setForeground(TEXT_PRIMARY);
        txtPeerEndpoint.setCaretColor(TEXT_PRIMARY);
        txtPeerEndpoint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        btnConectarServidor = new JButton("Conectar");
        btnConectarServidor.setName("btnConectarServidor");
        styleModernButton(btnConectarServidor, ACCENT_BLUE, ACCENT_BLUE_HOVER);

        lstConexiones = new JList<>(new DefaultListModel<>());
        lstConexiones.setBackground(BG_CARD);
        lstConexiones.setForeground(TEXT_PRIMARY);
        lstConexiones.setSelectionBackground(ACCENT_BLUE);
        lstConexiones.setSelectionForeground(Color.WHITE);
        lstConexiones.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lstConexiones.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        lstServidores = new JList<>(new DefaultListModel<>());
        lstServidores.setName("lstServidores");
        lstServidores.setBackground(BG_CARD);
        lstServidores.setForeground(TEXT_PRIMARY);
        lstServidores.setSelectionBackground(ACCENT_BLUE);
        lstServidores.setSelectionForeground(Color.WHITE);
        lstServidores.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lstServidores.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        btnCerrarConexion = new JButton("Cerrar Conexión");
        styleModernButton(btnCerrarConexion, ACCENT_BLUE, ACCENT_BLUE_HOVER);

        btnGenerarUsuarios = new JButton("Generar");
        styleModernButton(btnGenerarUsuarios, ACCENT_BLUE, ACCENT_BLUE_HOVER);
        
        btnGenerarCanales = new JButton("Generar");
        styleModernButton(btnGenerarCanales, ACCENT_BLUE, ACCENT_BLUE_HOVER);
        
        btnGenerarConectados = new JButton("Generar");
        styleModernButton(btnGenerarConectados, ACCENT_BLUE, ACCENT_BLUE_HOVER);
        
        btnGenerarLogs = new JButton("Generar");
        styleModernButton(btnGenerarLogs, ACCENT_BLUE, ACCENT_BLUE_HOVER);
        
        btnApagarServidor = new JButton("Apagar Servidor");
        styleDangerButton(btnApagarServidor);

        // Crear panel principal con fondo oscuro
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_PRIMARY);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        // Header
        JPanel header = createHeader();
        mainPanel.add(header, BorderLayout.NORTH);

        // Contenido principal en grid responsivo
        JPanel content = new JPanel(new GridLayout(1, 2, 20, 0));
        content.setBackground(BG_PRIMARY);
        content.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        // Panel izquierdo: Conexiones
        content.add(buildConexionesCard());

        // Panel derecho: Reportes + Control
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(BG_PRIMARY);
        rightPanel.add(buildPeersCard());
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(buildReportesCard());
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(buildControlCard());
        content.add(rightPanel);

        mainPanel.add(content, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JLabel title = new JLabel("Servidor de Mensajería");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(TEXT_PRIMARY);
        
        JLabel subtitle = new JLabel("Panel de administración y monitoreo");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_SECONDARY);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(BG_PRIMARY);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        header.add(titlePanel, BorderLayout.WEST);
        return header;
    }

    private JPanel buildConexionesCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 12));

        // Título de la sección
        JLabel sectionTitle = new JLabel("Conexiones Activas");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        sectionTitle.setForeground(TEXT_PRIMARY);

        JLabel maxLabel = new JLabel("Máximo: " + maxConnections + " conexiones");
        maxLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        maxLabel.setForeground(TEXT_SECONDARY);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_SECONDARY);
        headerPanel.add(sectionTitle, BorderLayout.WEST);
        headerPanel.add(maxLabel, BorderLayout.EAST);

        card.add(headerPanel, BorderLayout.NORTH);

        // Lista de conexiones
        lstConexiones.setName("lstConexiones");
        JScrollPane scrollPane = new JScrollPane(lstConexiones);
        scrollPane.setBackground(BG_CARD);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollPane.setPreferredSize(new Dimension(420, 400));
        card.add(scrollPane, BorderLayout.CENTER);

        // Botón de acción
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionPanel.setBackground(BG_SECONDARY);
        btnCerrarConexion.setName("btnCerrarConexion");
        actionPanel.add(btnCerrarConexion);
        card.add(actionPanel, BorderLayout.SOUTH);

        return card;
    }

    private JPanel buildReportesCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 16));

        // Título
        JLabel sectionTitle = new JLabel("Reportes del Sistema");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        sectionTitle.setForeground(TEXT_PRIMARY);
        card.add(sectionTitle, BorderLayout.NORTH);

        // Grid de reportes
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(BG_SECONDARY);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 0, 8, 0);

        // Usuarios registrados
        gbc.gridx = 0; gbc.gridy = 0;
        grid.add(createReportRow("Usuarios Registrados", "Total de usuarios en el sistema"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.insets = new Insets(8, 12, 8, 0);
        btnGenerarUsuarios.setName("btnGenerarUsuarios");
        grid.add(btnGenerarUsuarios, gbc);

        // Canales
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0; gbc.insets = new Insets(8, 0, 8, 0);
        grid.add(createReportRow("Canales", "Canales con usuarios vinculados"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.insets = new Insets(8, 12, 8, 0);
        btnGenerarCanales.setName("btnGenerarCanales");
        grid.add(btnGenerarCanales, gbc);

        // Conectados
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0; gbc.insets = new Insets(8, 0, 8, 0);
        grid.add(createReportRow("Usuarios Conectados", "Conexiones activas en tiempo real"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.insets = new Insets(8, 12, 8, 0);
        btnGenerarConectados.setName("btnGenerarConectados");
        grid.add(btnGenerarConectados, gbc);

        // Logs
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 1.0; gbc.insets = new Insets(8, 0, 8, 0);
        grid.add(createReportRow("Logs del Sistema", "Registro de eventos y actividades"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.insets = new Insets(8, 12, 8, 0);
        btnGenerarLogs.setName("btnGenerarLogs");
        grid.add(btnGenerarLogs, gbc);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildControlCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 16));

        JLabel sectionTitle = new JLabel("Control del Servidor");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        sectionTitle.setForeground(TEXT_PRIMARY);
        card.add(sectionTitle, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG_SECONDARY);
        
        JLabel warningLabel = new JLabel("<html><body style='width: 300px;'>Esta acción cerrará todas las conexiones activas y detendrá el servidor.</body></html>");
        warningLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        warningLabel.setForeground(TEXT_SECONDARY);
        warningLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        content.add(warningLabel, BorderLayout.NORTH);

        btnApagarServidor.setName("btnApagarServidor");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonPanel.setBackground(BG_SECONDARY);
        buttonPanel.add(btnApagarServidor);
        content.add(buttonPanel, BorderLayout.CENTER);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(BG_SECONDARY);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        return card;
    }

    private JPanel buildPeersCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 16));

        JLabel sectionTitle = new JLabel("Red de Servidores");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        sectionTitle.setForeground(TEXT_PRIMARY);

        JLabel sectionSubtitle = new JLabel("Conecta este nodo con otros servidores");
        sectionSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sectionSubtitle.setForeground(TEXT_SECONDARY);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(BG_SECONDARY);
        header.add(sectionTitle);
        header.add(Box.createVerticalStrut(4));
        header.add(sectionSubtitle);
        card.add(header, BorderLayout.NORTH);

        JPanel connectPanel = new JPanel(new BorderLayout(12, 0));
        connectPanel.setBackground(BG_SECONDARY);

        JPanel inputWrapper = new JPanel();
        inputWrapper.setBackground(BG_SECONDARY);
        inputWrapper.setLayout(new BoxLayout(inputWrapper, BoxLayout.Y_AXIS));

        JLabel inputLabel = new JLabel("Servidor (IP:Puerto)");
        inputLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        inputLabel.setForeground(TEXT_SECONDARY);
        inputLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtPeerEndpoint.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtPeerEndpoint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        inputWrapper.add(inputLabel);
        inputWrapper.add(Box.createVerticalStrut(6));
        inputWrapper.add(txtPeerEndpoint);

        connectPanel.add(inputWrapper, BorderLayout.CENTER);
        connectPanel.add(btnConectarServidor, BorderLayout.EAST);
        card.add(connectPanel, BorderLayout.CENTER);

        lstServidores.setName("lstServidores");
        JScrollPane scroll = new JScrollPane(lstServidores);
        scroll.setBackground(BG_CARD);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scroll.setPreferredSize(new Dimension(420, 180));

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBackground(BG_SECONDARY);
        listWrapper.add(scroll, BorderLayout.CENTER);

        JLabel listLabel = new JLabel("Servidores conectados");
        listLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        listLabel.setForeground(TEXT_SECONDARY);
        listLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 8, 0));
        listWrapper.add(listLabel, BorderLayout.NORTH);

        card.add(listWrapper, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createReportRow(String title, String description) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(BG_SECONDARY);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descLabel.setForeground(TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(titleLabel);
        row.add(Box.createVerticalStrut(2));
        row.add(descLabel);

        return row;
    }

    private void styleModernButton(JButton button, Color bgColor, Color hoverColor) {
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(hoverColor);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });
    }

    private void styleDangerButton(JButton button) {
        button.setBackground(ACCENT_RED);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(ACCENT_RED_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(ACCENT_RED);
            }
        });
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
    public JTextField getTxtPeerEndpoint() { return txtPeerEndpoint; }
    public JButton getBtnConectarServidor() { return btnConectarServidor; }
    public JList<String> getLstServidores() { return lstServidores; }
    public JButton getBtnCerrarConexion() { return btnCerrarConexion; }
    public JButton getBtnGenerarUsuarios() { return btnGenerarUsuarios; }
    public JButton getBtnGenerarCanales() { return btnGenerarCanales; }
    public JButton getBtnGenerarConectados() { return btnGenerarConectados; }
    public JButton getBtnGenerarLogs() { return btnGenerarLogs; }
    public JButton getBtnApagarServidor() { return btnApagarServidor; }
    
    /**
     * Muestra un diálogo moderno con el reporte y permite guardarlo.
     * Mantiene la separación de capas permitiendo que el controlador
     * use esta funcionalidad sin conocer la implementación específica.
     */
    public boolean mostrarDialogoReporte(String titulo, String contenido) {
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        java.awt.Frame frame = (window instanceof java.awt.Frame) ? (java.awt.Frame) window : null;
        return ModernReportDialog.showReportDialog(frame, titulo, contenido);
    }
}
