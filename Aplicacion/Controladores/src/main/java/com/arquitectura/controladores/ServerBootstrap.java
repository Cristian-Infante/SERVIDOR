package com.arquitectura.controladores;

import com.arquitectura.configdb.DBConfig;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.repositorios.jdbc.JdbcCanalRepository;
import com.arquitectura.repositorios.jdbc.JdbcClienteRepository;
import com.arquitectura.repositorios.jdbc.JdbcLogRepository;
import com.arquitectura.repositorios.jdbc.JdbcMensajeRepository;
import com.arquitectura.servicios.*;
import com.arquitectura.servicios.conexiones.ConnectionGateway;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.impl.*;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada del servidor TCP.
 */
public class ServerBootstrap {

    private static final Logger LOGGER = Logger.getLogger(ServerBootstrap.class.getName());

    private final int port;
    private final int maxConnections;
    private final ConnectionHandlerPool handlerPool;
    private final ConnectionRegistry connectionRegistry;
    private final ExecutorService executorService;
    private final RegistroService registroService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final SessionEventBus eventBus;

    public ServerBootstrap(int port, int maxConnections, ConnectionHandlerPool handlerPool,
                           ConnectionRegistry connectionRegistry, ExecutorService executorService,
                           RegistroService registroService, ReporteService reporteService,
                           ConexionService conexionService, SessionEventBus eventBus) {
        this.port = port;
        this.maxConnections = maxConnections;
        this.handlerPool = Objects.requireNonNull(handlerPool);
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry);
        this.executorService = Objects.requireNonNull(executorService);
        this.registroService = Objects.requireNonNull(registroService);
        this.reporteService = Objects.requireNonNull(reporteService);
        this.conexionService = Objects.requireNonNull(conexionService);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    public void start() throws IOException {
        LOGGER.log(Level.INFO, "Servidor iniciando en puerto {0} con capacidad {1}", new Object[]{port, maxConnections});
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ConnectionHandler handler = handlerPool.borrow();
                if (handler == null) {
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        writer.write("{\"error\":\"capacity reached\"}\n");
                        writer.flush();
                    }
                    socket.close();
                    continue;
                }
                handler.attach(socket, handlerPool);
                executorService.submit(handler);
            }
        }
    }

    public static ServerBootstrap createDefault() {
        configureLogging();
        int port = DBConfig.getIntProperty("server.port", 5050);
        int maxConnections = DBConfig.getIntProperty("server.maxConnections", 5);
        DataSource dataSource = DBConfig.getDataSource();

        ClienteRepository clienteRepository = new JdbcClienteRepository(dataSource);
        CanalRepository canalRepository = new JdbcCanalRepository(dataSource);
        MensajeRepository mensajeRepository = new JdbcMensajeRepository(dataSource);
        LogRepository logRepository = new JdbcLogRepository(dataSource);

        SessionEventBus eventBus = new SessionEventBus();
        ConnectionRegistry registry = new ConnectionRegistry(canalRepository);
        ConnectionGateway gateway = registry;

        RegistroService registroService = new RegistroServiceImpl(clienteRepository, eventBus);
        CanalService canalService = new CanalServiceImpl(canalRepository, clienteRepository, eventBus);
        MensajeriaService mensajeriaService = new MensajeriaServiceImpl(mensajeRepository, clienteRepository, canalRepository, logRepository, eventBus);
        ReporteService reporteService = new ReporteServiceImpl(clienteRepository, canalRepository, mensajeRepository, logRepository);
        ConexionService conexionService = new ConexionServiceImpl(gateway, eventBus);

        ConnectionHandlerPool pool = new ConnectionHandlerPool(maxConnections, registroService, canalService, mensajeriaService, reporteService, conexionService, registry, eventBus);
        ExecutorService executor = Executors.newCachedThreadPool();

        eventBus.subscribe(new LogSubscriber());

        return new ServerBootstrap(port, maxConnections, pool, registry, executor,
                registroService, reporteService, conexionService, eventBus);
    }

    private static void configureLogging() {
        String level = DBConfig.getProperty("log.level", "INFO");
        Level logLevel = Level.parse(level);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(logLevel);
        for (var handler : rootLogger.getHandlers()) {
            handler.setLevel(logLevel);
        }
    }

    public static void main(String[] args) throws IOException {
        ServerBootstrap bootstrap = createDefault();
        bootstrap.start();
    }

    public RegistroService getRegistroService() {
        return registroService;
    }

    public ReporteService getReporteService() {
        return reporteService;
    }

    public ConexionService getConexionService() {
        return conexionService;
    }

    public SessionEventBus getEventBus() {
        return eventBus;
    }
}
