package com.arquitectura.entidades.vistas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ModernReportDialog extends JDialog {
    // Colores modernos (mismos que ServidorVista)
    private static final Color BG_PRIMARY = new Color(17, 24, 39);
    private static final Color BG_SECONDARY = new Color(31, 41, 55);
    private static final Color BG_CARD = new Color(55, 65, 81);
    private static final Color TEXT_PRIMARY = new Color(243, 244, 246);
    private static final Color TEXT_SECONDARY = new Color(156, 163, 175);
    private static final Color ACCENT_BLUE = new Color(59, 130, 246);
    private static final Color ACCENT_BLUE_HOVER = new Color(37, 99, 235);
    private static final Color BORDER_COLOR = new Color(75, 85, 99);

    private boolean saveRequested = false;

    public ModernReportDialog(Frame parent, String title, String content) {
        super(parent, title, true);
        initComponents(content);
    }

    private void initComponents(String content) {
        setSize(700, 550);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_PRIMARY);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Header con título
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_PRIMARY);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JLabel titleLabel = new JLabel(getTitle());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Área de texto con el contenido
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setBackground(BG_CARD);
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setCaretColor(TEXT_PRIMARY);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBackground(BG_CARD);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.getViewport().setBackground(BG_CARD);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Panel de botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(BG_PRIMARY);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));

        JButton btnSave = createModernButton("Guardar como archivo", ACCENT_BLUE, ACCENT_BLUE_HOVER);
        btnSave.addActionListener(e -> {
            saveRequested = true;
            dispose();
        });

        JButton btnClose = createModernButton("Cerrar", BG_CARD, new Color(75, 85, 99));
        btnClose.addActionListener(e -> {
            saveRequested = false;
            dispose();
        });

        buttonPanel.add(btnSave);
        buttonPanel.add(btnClose);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JButton createModernButton(String text, Color bgColor, Color hoverColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(hoverColor);
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    public boolean isSaveRequested() {
        return saveRequested;
    }

    /**
     * Muestra el diálogo y retorna true si el usuario quiere guardar el reporte
     */
    public static boolean showReportDialog(Frame parent, String title, String content) {
        ModernReportDialog dialog = new ModernReportDialog(parent, title, content);
        dialog.setVisible(true);
        return dialog.isSaveRequested();
    }
}
