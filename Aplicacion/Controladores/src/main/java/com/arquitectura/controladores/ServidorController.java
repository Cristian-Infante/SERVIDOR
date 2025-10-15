package com.arquitectura.controladores;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Log;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionObserver;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServidorController {

    private static final Logger LOGGER = Logger.getLogger(ServidorController.class.getName());

    private final ServidorView vista;
    private final RegistroService registroService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;

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
        initListeners();
    }

    private void initListeners() {
        vista.getBtnSeleccionarFoto().addActionListener(this::seleccionarFoto);
        vista.getBtnEnviarRegistro().addActionListener(this::registrarUsuario);
        vista.getBtnGenerarUsuarios().addActionListener(e -> mostrarUsuarios(reporteService.usuariosRegistrados()));
        vista.getBtnGenerarCanales().addActionListener(e -> mostrarCanales(reporteService.canalesConUsuarios()));
        vista.getBtnGenerarConectados().addActionListener(e -> mostrarUsuarios(reporteService.usuariosConectados()));
        vista.getBtnGenerarLogs().addActionListener(e -> mostrarLogs(reporteService.logs()));
        vista.getBtnCerrarConexion().addActionListener(this::cerrarConexionSeleccionada);

        SessionObserver observer = event -> {
            if (event.getType() == SessionEvent.Type.LOGIN) {
                actualizarLista(event, true);
            } else if (event.getType() == SessionEvent.Type.LOGOUT) {
                actualizarLista(event, false);
            }
        };
        eventBus.subscribe(observer);
    }

    private void seleccionarFoto(ActionEvent actionEvent) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(vista.asComponent()) == JFileChooser.APPROVE_OPTION) {
            vista.getTxtFotoRuta().setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void registrarUsuario(ActionEvent e) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String usuario = vista.getTxtUsuario().getText();
                String email = vista.getTxtEmail().getText();
                String password = new String(vista.getTxtContrasena().getPassword());
                String ip = vista.getTxtDireccionIp().getText();
                byte[] foto = null;
                if (!vista.getTxtFotoRuta().getText().isBlank()) {
                    foto = Files.readAllBytes(Path.of(vista.getTxtFotoRuta().getText()));
                }
                registroService.registrar(usuario, email, password, foto, ip);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(vista.asComponent(), "Usuario registrado"));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error leyendo foto", ex);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(vista.asComponent(), "Error leyendo foto"));
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error registrando usuario", ex);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(vista.asComponent(), ex.getMessage()));
            }
        });
    }

    private void mostrarUsuarios(List<Cliente> clientes) {
        String mensaje = clientes.stream()
                .map(cliente -> cliente.getId() + " - " + cliente.getNombreDeUsuario() + " (" + cliente.getEmail() + ")")
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin datos" : mensaje);
    }

    private void mostrarCanales(Map<Canal, List<Cliente>> canales) {
        StringBuilder sb = new StringBuilder();
        canales.forEach((canal, usuarios) -> {
            sb.append("Canal ").append(canal.getNombre()).append(" -> ");
            sb.append(usuarios.stream().map(Cliente::getNombreDeUsuario).collect(Collectors.joining(", ")));
            sb.append('\n');
        });
        JOptionPane.showMessageDialog(vista.asComponent(), sb.length() == 0 ? "Sin datos" : sb.toString());
    }

    private void mostrarLogs(List<Log> logs) {
        String mensaje = logs.stream()
                .map(log -> log.getFechaHora() + " - " + log.getDetalle())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(vista.asComponent(), mensaje.isBlank() ? "Sin datos" : mensaje);
    }

    private void cerrarConexionSeleccionada(ActionEvent actionEvent) {
        String selected = vista.getLstConexiones().getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(vista.asComponent(), "Selecciona un usuario");
            return;
        }
        Long id = Long.parseLong(selected.split(":")[0]);
        conexionService.cerrarConexion(id);
    }

    @SuppressWarnings("unchecked")
    private void actualizarLista(SessionEvent event, boolean agregar) {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<String> model = (DefaultListModel<String>) vista.getLstConexiones().getModel();
            Object id = event.getPayload().get("clienteId");
            Object usuario = event.getPayload().getOrDefault("usuario", "");
            String entry = id + ":" + usuario;
            if (agregar) {
                model.addElement(entry);
            } else {
                for (int i = 0; i < model.size(); i++) {
                    if (model.get(i).startsWith(id + ":")) {
                        model.remove(i);
                        break;
                    }
                }
            }
        });
    }
}
