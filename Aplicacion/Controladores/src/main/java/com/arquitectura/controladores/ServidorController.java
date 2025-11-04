package com.arquitectura.controladores;

import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;
import com.arquitectura.controladores.p2p.ServerPeerManager;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServidorController implements SessionObserver, ServerPeerManager.PeerStatusListener {

    private final ServidorView vista;
    private final RegistroService registroService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;
    private final DefaultListModel<String> conexionesModel;
    private final DefaultListModel<String> servidoresModel;
    private final ServerPeerManager peerManager;
    private final Map<String, String> mapping = new HashMap<>();

    public ServidorController(ServidorView vista,
                              RegistroService registroService,
                              ReporteService reporteService,
                              ConexionService conexionService,
                              SessionEventBus eventBus,
                              ServerPeerManager peerManager) {
        this.vista = vista;
        this.registroService = registroService;
        this.reporteService = reporteService;
        this.conexionService = conexionService;
        this.eventBus = eventBus;
        this.peerManager = peerManager;
        this.conexionesModel = (DefaultListModel<String>) vista.getLstConexiones().getModel();
        this.servidoresModel = (DefaultListModel<String>) vista.getLstServidores().getModel();
        wire();
        this.eventBus.subscribe(this);
        this.peerManager.addPeerStatusListener(this);
        refreshConexiones();
        refreshServidores();
    }

    private void wire() {
        vista.getBtnGenerarUsuarios().addActionListener(this::mostrarUsuarios);
        vista.getBtnGenerarCanales().addActionListener(this::mostrarCanales);
        vista.getBtnGenerarConectados().addActionListener(this::mostrarConectados);
        vista.getBtnGenerarLogs().addActionListener(this::mostrarLogs);
        vista.getBtnCerrarConexion().addActionListener(this::cerrarSeleccionada);
        vista.getBtnEnviarRegistro().addActionListener(this::registrarCliente);
        vista.getBtnSeleccionarFoto().addActionListener(this::seleccionarFoto);
        vista.getBtnApagarServidor().addActionListener(this::apagarServidor);
        vista.getBtnConectarServidor().addActionListener(this::conectarServidor);
    }

    private void mostrarUsuarios(ActionEvent e) {
        List<UserSummary> usuarios = reporteService.usuariosRegistrados(null);
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== REPORTE DE USUARIOS REGISTRADOS ===\n");
        contenido.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        contenido.append("Total de usuarios: ").append(usuarios.size()).append("\n\n");
        
        for (UserSummary u : usuarios) {
            contenido.append(String.format("ID: %d | Usuario: %s | Email: %s | Estado: %s\n",
                    u.getId(), u.getUsuario(), u.getEmail(), 
                    u.isConectado() ? "Conectado" : "Desconectado"));
        }
        
        mostrarYGuardarReporte("Usuarios Registrados", contenido.toString(), "reporte_usuarios.txt");
    }

    private void mostrarCanales(ActionEvent e) {
        List<ChannelSummary> canales = reporteService.canalesConUsuarios();
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== REPORTE DE CANALES ===\n");
        contenido.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        contenido.append("Total de canales: ").append(canales.size()).append("\n\n");
        
        for (ChannelSummary c : canales) {
            contenido.append(String.format("ID: %d | Nombre: %s | Tipo: %s | Miembros: %d\n",
                    c.getId(), c.getNombre(), 
                    c.isPrivado() ? "Privado" : "Público",
                    c.getUsuarios().size()));
            
            if (!c.getUsuarios().isEmpty()) {
                contenido.append("  Usuarios:\n");
                for (UserSummary u : c.getUsuarios()) {
                    contenido.append(String.format("    - %s (%s)\n", 
                            u.getUsuario(), 
                            u.isConectado() ? "conectado" : "desconectado"));
                }
            }
            contenido.append("\n");
        }
        
        mostrarYGuardarReporte("Canales", contenido.toString(), "reporte_canales.txt");
    }

    private void mostrarConectados(ActionEvent e) {
        List<UserSummary> conectados = reporteService.usuariosConectados(null);
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== REPORTE DE USUARIOS CONECTADOS ===\n");
        contenido.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        contenido.append("Total conectados: ").append(conectados.size()).append("\n\n");
        
        for (UserSummary u : conectados) {
            contenido.append(String.format("ID: %d | Usuario: %s | Email: %s\n",
                    u.getId(), u.getUsuario(), u.getEmail()));
        }
        
        mostrarYGuardarReporte("Usuarios Conectados", contenido.toString(), "reporte_conectados.txt");
    }

    private void mostrarLogs(ActionEvent e) {
        List<LogEntryDto> logs = reporteService.logs();
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== REPORTE DE LOGS DEL SISTEMA ===\n");
        contenido.append("Fecha del reporte: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        contenido.append("Total de registros: ").append(logs.size()).append("\n\n");
        
        for (LogEntryDto l : logs) {
            contenido.append(String.format("[%s] %s\n", l.getFechaHora(), l.getDetalle()));
        }
        
        mostrarYGuardarReporte("Logs del Sistema", contenido.toString(), "reporte_logs.txt");
    }

    /**
     * Muestra el reporte en un diálogo y ofrece la opción de guardarlo en un archivo.
     */
    private void mostrarYGuardarReporte(String titulo, String contenido, String nombreSugerido) {
        // Usar la interfaz de la vista para mostrar el diálogo moderno
        boolean deseaGuardar = vista.mostrarDialogoReporte(
                titulo, 
                contenido.isBlank() ? "Sin datos para mostrar" : contenido
        );
        
        // Si el usuario eligió guardar
        if (deseaGuardar) {
            guardarReporte(contenido, nombreSugerido);
        }
    }

    /**
     * Permite al usuario seleccionar dónde guardar el reporte y lo guarda en formato .txt
     */
    private void guardarReporte(String contenido, String nombreSugerido) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar reporte");
        fileChooser.setSelectedFile(new File(nombreSugerido));
        
        // Filtro para archivos .txt
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Archivos de texto (*.txt)", "txt");
        fileChooser.setFileFilter(filter);
        
        int resultado = fileChooser.showSaveDialog(vista.asComponent());
        
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            
            // Asegurar que tenga extensión .txt
            if (!archivo.getName().toLowerCase().endsWith(".txt")) {
                archivo = new File(archivo.getAbsolutePath() + ".txt");
            }
            
            try (PrintWriter writer = new PrintWriter(archivo, StandardCharsets.UTF_8)) {
                writer.print(contenido);
                JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "Reporte guardado exitosamente en:\n" + archivo.getAbsolutePath(),
                        "Guardado exitoso",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "Error al guardar el archivo: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void cerrarSeleccionada(ActionEvent e) {
        String value = vista.getLstConexiones().getSelectedValue();
        if (value == null) {
            JOptionPane.showMessageDialog(
                    vista.asComponent(),
                    "Por favor selecciona una conexión de la lista",
                    "Advertencia",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(
                vista.asComponent(),
                "¿Está seguro de que desea cerrar la conexión:\n" + value + "?",
                "Confirmar cierre de conexión",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            String sessionId = mapping.get(value);
            if (sessionId != null) {
                conexionService.cerrarConexion(sessionId);
                JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "Conexión cerrada exitosamente",
                        "Información",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
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

    private void refreshServidores() {
        SwingUtilities.invokeLater(() -> {
            servidoresModel.clear();
            for (String serverId : peerManager.connectedPeerIds()) {
                servidoresModel.addElement(serverId);
            }
        });
    }

    private void conectarServidor(ActionEvent e) {
        String endpoint = vista.getTxtPeerEndpoint().getText().trim();
        if (endpoint.isBlank()) {
            JOptionPane.showMessageDialog(
                    vista.asComponent(),
                    "Ingresa la dirección del servidor en formato IP:PUERTO",
                    "Información",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int idx = endpoint.lastIndexOf(':');
        if (idx <= 0 || idx == endpoint.length() - 1) {
            JOptionPane.showMessageDialog(
                    vista.asComponent(),
                    "Formato inválido. Utiliza IP:PUERTO",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String host = endpoint.substring(0, idx);
        int port;
        try {
            port = Integer.parseInt(endpoint.substring(idx + 1));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    vista.asComponent(),
                    "El puerto debe ser un número válido",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        vista.getBtnConectarServidor().setEnabled(false);
        new Thread(() -> {
            try {
                peerManager.connectToPeer(host, port);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            vista.asComponent(),
                            "Conexión establecida correctamente",
                            "Servidores",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    vista.getTxtPeerEndpoint().setText("");
                    refreshServidores();
                });
            } catch (IllegalArgumentException | IllegalStateException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                ));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "No se pudo conectar con el servidor: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                ));
            } finally {
                SwingUtilities.invokeLater(() -> vista.getBtnConectarServidor().setEnabled(true));
            }
        }, "PeerConnector-UI").start();
    }

    private void apagarServidor(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(
                vista.asComponent(),
                "¿Está seguro de que desea apagar el servidor?\nTodos los clientes serán desconectados.",
                "Confirmar apagado",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Notificar a todos los clientes que el servidor se está apagando
                conexionService.notificarApagado("El servidor se está apagando. Serás desconectado.");
                
                // Dar tiempo para que lleguen las notificaciones
                Thread.sleep(500);
                
                JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "Notificaciones enviadas.\nCerrando servidor...",
                        "Servidor",
                        JOptionPane.INFORMATION_MESSAGE
                );
                
                // Cerrar la aplicación
                System.exit(0);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        vista.asComponent(),
                        "Error al apagar el servidor: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    @Override
    public void onEvent(SessionEvent event) {
        // Actualizar la lista de conexiones cuando:
        // - Se establece una nueva conexión TCP
        // - Se cierra una conexión TCP
        // - Un usuario hace LOGIN
        // - Un usuario hace LOGOUT
        if (event.getType() == SessionEventType.LOGIN ||
            event.getType() == SessionEventType.LOGOUT ||
            event.getType() == SessionEventType.TCP_CONNECTED ||
            event.getType() == SessionEventType.TCP_DISCONNECTED) {
            refreshConexiones();
        }
    }

    @Override
    public void onPeerConnected(String serverId) {
        if (serverId == null) {
            return;
        }
        refreshServidores();
    }

    @Override
    public void onPeerDisconnected(String serverId) {
        if (serverId == null) {
            return;
        }
        refreshServidores();
    }
}
