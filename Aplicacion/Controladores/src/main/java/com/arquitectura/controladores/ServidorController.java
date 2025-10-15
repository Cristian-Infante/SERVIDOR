package com.arquitectura.controladores;

import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.conexion.SessionDescriptor;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServidorController implements SessionObserver {

    private final ServidorView vista;
    private final RegistroService registroService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;
    private final DefaultListModel<String> conexionesModel;
    private final Map<String, String> mapping = new HashMap<>();

    public ServidorController(ServidorView vista,
                              RegistroService registroService,
                              ReporteService reporteService,
                              ConexionService conexionService,
                              SessionEventBus eventBus) {
        this.vista = vista;
        this.registroService = registroService;
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
        vista.getBtnEnviarRegistro().addActionListener(this::registrarCliente);
        vista.getBtnSeleccionarFoto().addActionListener(this::seleccionarFoto);
    }

    private void mostrarUsuarios(ActionEvent e) {
        List<UserSummary> usuarios = reporteService.usuariosRegistrados();
        String mensaje = usuarios.stream()
                .map(u -> u.getId() + " - " + u.getUsuario() + " (" + (u.isConectado() ? "conectado" : "desconectado") + ")")
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin usuarios" : mensaje, "Usuarios registrados", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarCanales(ActionEvent e) {
        List<ChannelSummary> canales = reporteService.canalesConUsuarios();
        String mensaje = canales.stream()
                .map(c -> c.getId() + " - " + c.getNombre() + " -> " + c.getUsuarios().size() + " usuarios")
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin canales" : mensaje, "Canales", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarConectados(ActionEvent e) {
        List<UserSummary> conectados = reporteService.usuariosConectados();
        String mensaje = conectados.stream()
                .map(u -> u.getId() + " - " + u.getUsuario())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin usuarios conectados" : mensaje, "Conectados", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarLogs(ActionEvent e) {
        List<LogEntryDto> logs = reporteService.logs();
        String mensaje = logs.stream()
                .map(l -> l.getFechaHora() + " - " + l.getDetalle())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin logs" : mensaje, "Logs", JOptionPane.INFORMATION_MESSAGE);
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

    private void registrarCliente(ActionEvent e) {
        char[] contraseniaChars = vista.getTxtContrasena().getPassword();
        try {
            String email = vista.getTxtEmail().getText().trim();
            String usuario = vista.getTxtUsuario().getText().trim();
            String contrasenia = new String(contraseniaChars);
            String ip = vista.getTxtDireccionIp().getText().trim();
            String rutaFoto = vista.getTxtFotoRuta().getText().trim();

            if (email.isBlank() || usuario.isBlank() || contrasenia.isBlank() || ip.isBlank()) {
                throw new IllegalArgumentException("Todos los campos obligatorios deben completarse");
            }

            byte[] foto = loadFoto(rutaFoto);
            var cliente = registroService.registrarCliente(usuario, email, contrasenia, foto, ip);
            JOptionPane.showMessageDialog(vista.asComponent(),
                    "Cliente registrado con ID " + cliente.getId(),
                    "Registro", JOptionPane.INFORMATION_MESSAGE);
            limpiarRegistro();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(vista.asComponent(), ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(contraseniaChars, '\0');
        }
    }

    private void seleccionarFoto(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(vista.asComponent());
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            vista.getTxtFotoRuta().setText(selected.getAbsolutePath());
        }
    }

    private byte[] loadFoto(String ruta) throws IOException {
        if (ruta == null || ruta.isBlank()) {
            return new byte[0];
        }
        Path path = Path.of(ruta);
        if (!Files.exists(path)) {
            throw new IOException("La ruta de la foto no existe");
        }
        return Files.readAllBytes(path);
    }

    private void limpiarRegistro() {
        vista.getTxtEmail().setText("");
        vista.getTxtUsuario().setText("");
        vista.getTxtContrasena().setText("");
        vista.getTxtFotoRuta().setText("");
        vista.getTxtDireccionIp().setText("");
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
