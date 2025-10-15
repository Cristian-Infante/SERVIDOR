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
import com.arquitectura.entidades.vistas.ServidorVistaAdapter;
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

    private DBConfig config;
    private DataSource dataSource;
    private ClienteRepository clienteRepository;
    private CanalRepository canalRepository;
    private MensajeRepository mensajeRepository;
    private LogRepository logRepository;
    private SessionEventBus eventBus;
    private ConnectionRegistry registry;
    private ConnectionHandlerPool pool;
    private RegistroService registroService;
    private CanalService canalService;
    private MensajeriaService mensajeriaService;
    private ReporteService reporteService;
    private ConexionService conexionService;
    private boolean initialized;
    private int port;
    private int maxConnections;
    private boolean launchViewOnStart = true;

    public static void main(String[] args) {
        MainServidor.main(args);
    }

    public static ServerBootstrap createDefault() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.launchViewOnStart = false;
        bootstrap.ensureInitialized();
        return bootstrap;
    }

    public void start() throws IOException {
        ensureInitialized();

        if (launchViewOnStart) {
            launchUi(reporteService, conexionService, eventBus);
        }

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

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        this.config = DBConfig.getInstance();
        configureLogging(config);

        this.dataSource = config.getMySqlDataSource();
        this.clienteRepository = new JdbcClienteRepository(dataSource);
        this.canalRepository = new JdbcCanalRepository(dataSource);
        this.mensajeRepository = new JdbcMensajeRepository(dataSource);
        this.logRepository = new JdbcLogRepository(dataSource);

        this.eventBus = new SessionEventBus();
        this.registry = new ConnectionRegistry();
        ConnectionGateway gateway = registry;
        PasswordHasher hasher = new Sha256PasswordHasher(config);

        this.registroService = new RegistroServiceImpl(clienteRepository, hasher);
        this.canalService = new CanalServiceImpl(canalRepository, clienteRepository, eventBus);
        this.mensajeriaService = new MensajeriaServiceImpl(mensajeRepository, logRepository, gateway, eventBus);
        this.reporteService = new ReporteServiceImpl(clienteRepository, canalRepository, mensajeRepository, logRepository);
        this.conexionService = new ConexionServiceImpl(gateway, clienteRepository, eventBus);

        new LogSubscriber(logRepository, eventBus);

        this.port = config.getIntProperty("server.port", 5050);
        this.maxConnections = config.getIntProperty("server.maxConnections", 5);

        this.pool = new ConnectionHandlerPool(maxConnections,
                () -> new ConnectionHandler(registroService, canalService, mensajeriaService, reporteService, conexionService, eventBus, registry));
        this.initialized = true;
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
            ServidorVistaAdapter vista = new ServidorVistaAdapter();
            new ServidorController(vista, registroService, reporteService, conexionService, eventBus);
            vista.setVisible(true);
        });
    }

    public RegistroService getRegistroService() {
        ensureInitialized();
        return registroService;
    }

    public ReporteService getReporteService() {
        ensureInitialized();
        return reporteService;
    }

    public ConexionService getConexionService() {
        ensureInitialized();
        return conexionService;
    }

    public SessionEventBus getEventBus() {
        ensureInitialized();
        return eventBus;
    }
}
