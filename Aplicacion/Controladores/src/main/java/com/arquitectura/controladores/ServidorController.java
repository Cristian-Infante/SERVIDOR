package com.arquitectura.controladores;

import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;
import com.arquitectura.entidades.vistas.ServidorVista;

import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServidorController implements SessionObserver {

    private final ServidorVista vista;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;
    private final DefaultListModel<String> conexionesModel;
    private final Map<String, String> mapping = new HashMap<>();

    public ServidorController(ServidorVista vista,
                              ReporteService reporteService,
                              ConexionService conexionService,
                              SessionEventBus eventBus) {
        this.vista = vista;
        this.reporteService = reporteService;
        this.conexionService = conexionService;
        this.eventBus = eventBus;
        this.conexionesModel = (DefaultListModel<String>) vista.getLstConexiones().getModel();
        wire();
        this.eventBus.subscribe(this);
        refreshConexiones();
    }

    private void wire() {
        vista.getBtnGenerarUsuarios().addActionListener(this::mostrarUsuarios);
        vista.getBtnGenerarCanales().addActionListener(this::mostrarCanales);
        vista.getBtnGenerarConectados().addActionListener(this::mostrarConectados);
        vista.getBtnGenerarLogs().addActionListener(this::mostrarLogs);
        vista.getBtnCerrarConexion().addActionListener(this::cerrarSeleccionada);
    }

    private void mostrarUsuarios(ActionEvent e) {
        List<UserSummary> usuarios = reporteService.usuariosRegistrados();
        String mensaje = usuarios.stream()
                .map(u -> u.getId() + " - " + u.getUsuario() + " (" + (u.isConectado() ? "conectado" : "desconectado") + ")")
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista, mensaje.isBlank() ? "Sin usuarios" : mensaje, "Usuarios registrados", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarCanales(ActionEvent e) {
        List<ChannelSummary> canales = reporteService.canalesConUsuarios();
        String mensaje = canales.stream()
                .map(c -> c.getId() + " - " + c.getNombre() + " -> " + c.getUsuarios().size() + " usuarios")
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista, mensaje.isBlank() ? "Sin canales" : mensaje, "Canales", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarConectados(ActionEvent e) {
        List<UserSummary> conectados = reporteService.usuariosConectados();
        String mensaje = conectados.stream()
                .map(u -> u.getId() + " - " + u.getUsuario())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista, mensaje.isBlank() ? "Sin usuarios conectados" : mensaje, "Conectados", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarLogs(ActionEvent e) {
        List<LogEntryDto> logs = reporteService.logs();
        String mensaje = logs.stream()
                .map(l -> l.getFechaHora() + " - " + l.getDetalle())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista, mensaje.isBlank() ? "Sin logs" : mensaje, "Logs", JOptionPane.INFORMATION_MESSAGE);
    }

    private void cerrarSeleccionada(ActionEvent e) {
        String value = vista.getLstConexiones().getSelectedValue();
        if (value == null) {
            return;
        }
        String sessionId = mapping.get(value);
        if (sessionId != null) {
            conexionService.cerrarConexion(sessionId);
        }
    }

    private void refreshConexiones() {
        SwingUtilities.invokeLater(() -> {
            conexionesModel.clear();
            mapping.clear();
            for (SessionDescriptor descriptor : conexionService.sesionesActivas()) {
                String text = descriptor.getSessionId() + " - " + descriptor.getUsuario();
                conexionesModel.addElement(text);
                mapping.put(text, descriptor.getSessionId());
            }
        });
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event.getType() == SessionEventType.LOGIN || event.getType() == SessionEventType.LOGOUT) {
            refreshConexiones();
        }
    }
}
