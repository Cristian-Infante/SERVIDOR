package com.arquitectura.bootstrap;

import com.arquitectura.configdb.DBConfig;
import com.arquitectura.controladores.LogSubscriber;
import com.arquitectura.controladores.ServidorController;
import com.arquitectura.controladores.ServidorView;
import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.repositorios.jdbc.DatabaseInitializer;
import com.arquitectura.repositorios.jdbc.JdbcCanalRepository;
import com.arquitectura.repositorios.jdbc.JdbcClienteRepository;
import com.arquitectura.repositorios.jdbc.JdbcLogRepository;
import com.arquitectura.repositorios.jdbc.JdbcMensajeRepository;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.impl.CanalServiceImpl;
import com.arquitectura.servicios.impl.ConexionServiceImpl;
import com.arquitectura.servicios.impl.MensajeriaServiceImpl;
import com.arquitectura.servicios.impl.RegistroServiceImpl;
import com.arquitectura.servicios.impl.ReporteServiceImpl;
import com.arquitectura.servicios.security.PasswordHasher;
import com.arquitectura.servicios.security.Sha256PasswordHasher;

import javax.sql.DataSource;

/**
 * Manual dependency injection helper for the Swing application. It wires the
 * repositories, services and event bus needed by the controller and ensures the
 * database schema is available before the UI interacts with it.
 */
public final class ServidorApplication {

    private final SessionEventBus eventBus;
    private final RegistroService registroService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final ConnectionRegistry connectionRegistry;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;

    public ServidorApplication() {
        DBConfig config = DBConfig.getInstance();
        DataSource dataSource = config.getMySqlDataSource();
        DatabaseInitializer.ensureSchema(dataSource);

        ClienteRepository clienteRepository = new JdbcClienteRepository(dataSource);
        CanalRepository canalRepository = new JdbcCanalRepository(dataSource);
        MensajeRepository mensajeRepository = new JdbcMensajeRepository(dataSource);
        LogRepository logRepository = new JdbcLogRepository(dataSource);

        this.eventBus = new SessionEventBus();
        this.connectionRegistry = new ConnectionRegistry();
        new com.arquitectura.servicios.eventos.LogSubscriber(logRepository, eventBus);
        eventBus.subscribe(new LogSubscriber());

        PasswordHasher passwordHasher = new Sha256PasswordHasher(config);
        this.registroService = new RegistroServiceImpl(clienteRepository, passwordHasher);
        this.reporteService = new ReporteServiceImpl(clienteRepository, canalRepository, mensajeRepository, logRepository);
        this.conexionService = new ConexionServiceImpl(connectionRegistry, clienteRepository, eventBus);
        this.canalService = new CanalServiceImpl(canalRepository, clienteRepository, eventBus);
        this.mensajeriaService = new MensajeriaServiceImpl(mensajeRepository, logRepository, connectionRegistry, eventBus);
    }

    public ServidorController createServidorController(ServidorView view) {
        return new ServidorController(view, registroService, reporteService, conexionService, eventBus);
    }

    public ConnectionRegistry getConnectionRegistry() {
        return connectionRegistry;
    }

    public SessionEventBus getEventBus() {
        return eventBus;
    }

    public CanalService getCanalService() {
        return canalService;
    }

    public MensajeriaService getMensajeriaService() {
        return mensajeriaService;
    }
}
