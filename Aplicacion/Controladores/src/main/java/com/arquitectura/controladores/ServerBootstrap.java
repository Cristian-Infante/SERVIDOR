package com.arquitectura.controladores;

import com.arquitectura.configdb.DBConfig;
import com.arquitectura.controladores.conexion.ConnectionHandler;
import com.arquitectura.controladores.conexion.ConnectionHandlerPool;
import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.repositorios.jdbc.JdbcCanalRepository;
import com.arquitectura.repositorios.jdbc.JdbcClienteRepository;
import com.arquitectura.repositorios.jdbc.JdbcLogRepository;
import com.arquitectura.repositorios.jdbc.JdbcMensajeRepository;
import com.arquitectura.entidades.vistas.ServidorVista;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.eventos.LogSubscriber;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.impl.CanalServiceImpl;
import com.arquitectura.servicios.impl.ConexionServiceImpl;
import com.arquitectura.servicios.impl.MensajeriaServiceImpl;
import com.arquitectura.servicios.impl.RegistroServiceImpl;
import com.arquitectura.servicios.impl.ReporteServiceImpl;
import com.arquitectura.servicios.security.PasswordHasher;
import com.arquitectura.servicios.security.Sha256PasswordHasher;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

public class ServerBootstrap {

    private static final Logger LOGGER = Logger.getLogger(ServerBootstrap.class.getName());

    public static void main(String[] args) throws IOException {
        new ServerBootstrap().start();
    }

    public void start() throws IOException {
        DBConfig config = DBConfig.getInstance();
        configureLogging(config);

        DataSource dataSource = config.getMySqlDataSource();
        ClienteRepository clienteRepository = new JdbcClienteRepository(dataSource);
        CanalRepository canalRepository = new JdbcCanalRepository(dataSource);
        MensajeRepository mensajeRepository = new JdbcMensajeRepository(dataSource);
        LogRepository logRepository = new JdbcLogRepository(dataSource);

        SessionEventBus eventBus = new SessionEventBus();
        ConnectionRegistry registry = new ConnectionRegistry();
        ConnectionGateway gateway = registry;
        PasswordHasher hasher = new Sha256PasswordHasher(config);

        RegistroService registroService = new RegistroServiceImpl(clienteRepository, hasher);
        CanalService canalService = new CanalServiceImpl(canalRepository, clienteRepository, eventBus);
        MensajeriaService mensajeriaService = new MensajeriaServiceImpl(mensajeRepository, logRepository, gateway, eventBus);
        ReporteService reporteService = new ReporteServiceImpl(clienteRepository, canalRepository, mensajeRepository, logRepository);
        ConexionService conexionService = new ConexionServiceImpl(gateway, clienteRepository, eventBus);

        new LogSubscriber(logRepository, eventBus);

        launchUi(reporteService, conexionService, eventBus);

        int port = config.getIntProperty("server.port", 5050);
        int maxConnections = config.getIntProperty("server.maxConnections", 5);

        ConnectionHandlerPool pool = new ConnectionHandlerPool(maxConnections,
                () -> new ConnectionHandler(registroService, canalService, mensajeriaService, reporteService, conexionService, eventBus, registry));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info(() -> "Servidor iniciado en puerto " + port + " capacidad " + maxConnections);
            while (true) {
                Socket socket = serverSocket.accept();
                ConnectionHandler handler = pool.acquire(socket);
                if (handler == null) {
                    reject(socket);
                    continue;
                }
                new Thread(handler).start();
            }
        }
    }

    private void configureLogging(DBConfig config) {
        Level level = config.getLogLevel();
        Logger root = Logger.getLogger("");
        root.setLevel(level);
    }

    private void reject(Socket socket) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("capacity reached\n");
            writer.flush();
        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void launchUi(ReporteService reporteService, ConexionService conexionService, SessionEventBus eventBus) {
        SwingUtilities.invokeLater(() -> {
            ServidorVista vista = new ServidorVista();
            new ServidorController(vista, reporteService, conexionService, eventBus);
            vista.setVisible(true);
        });
    }
}
