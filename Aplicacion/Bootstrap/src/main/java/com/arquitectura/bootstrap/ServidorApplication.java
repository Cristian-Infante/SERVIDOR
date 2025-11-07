package com.arquitectura.bootstrap;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.AudioTranscriptionService;
import com.arquitectura.servicios.MessageSyncService;
import com.arquitectura.servicios.impl.AudioStorageServiceImpl;
import com.arquitectura.servicios.impl.MessageSyncServiceImpl;
import com.arquitectura.servicios.impl.VoskTranscriptionService;

import com.arquitectura.bootstrap.config.ServerConfig;
import com.arquitectura.configdb.DBConfig;
import com.arquitectura.controladores.LogSubscriber;
import com.arquitectura.controladores.ServidorController;
import com.arquitectura.controladores.ServidorView;
import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.controladores.p2p.ClusterInvitationReplicationListener;
import com.arquitectura.controladores.p2p.ClusterUserRegistrationListener;
import com.arquitectura.controladores.p2p.DatabaseSyncCoordinator;
import com.arquitectura.controladores.p2p.ServerPeerManager;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.InvitacionRepository;
import com.arquitectura.repositorios.InvitacionRepositoryImpl;
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
import com.arquitectura.servicios.eventos.InvitationNotificationService;
import com.arquitectura.servicios.eventos.MessageNotificationService;
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
    private final ServerPeerManager peerManager;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;
    private final AudioStorageService audioStorageService;
    private final MessageSyncService messageSyncService;
    private final TCPServer tcpServer;

    public ServidorApplication() {
        ServerConfig serverConfig = ServerConfig.getInstance();
        DBConfig config = DBConfig.getInstance();
        DataSource dataSource = config.getMySqlDataSource();
        DatabaseInitializer.ensureSchema(dataSource);

        ClienteRepository clienteRepository = new JdbcClienteRepository(dataSource);
        CanalRepository canalRepository = new JdbcCanalRepository(dataSource);
        MensajeRepository mensajeRepository = new JdbcMensajeRepository(dataSource);
        LogRepository logRepository = new JdbcLogRepository(dataSource);
        InvitacionRepository invitacionRepository = new InvitacionRepositoryImpl();

        // Limpiar estados de conexión del inicio anterior
        clienteRepository.disconnectAll();

        this.eventBus = new SessionEventBus();
        this.connectionRegistry = new ConnectionRegistry(eventBus, serverConfig.getServerId());
        DatabaseSyncCoordinator databaseSyncCoordinator = new DatabaseSyncCoordinator(
            clienteRepository,
            canalRepository,
            mensajeRepository,
            dataSource
        );
        this.peerManager = new ServerPeerManager(
            serverConfig.getServerId(),
            serverConfig.getPeerPort(),
            serverConfig.getPeerEndpoints(),
            connectionRegistry,
            databaseSyncCoordinator,
            clienteRepository
        );
        connectionRegistry.setPeerManager(peerManager);
        new ClusterUserRegistrationListener(peerManager, eventBus);
        new ClusterInvitationReplicationListener(peerManager, invitacionRepository, eventBus);
        new com.arquitectura.servicios.eventos.LogSubscriber(logRepository, clienteRepository, canalRepository, eventBus);
        eventBus.subscribe(new LogSubscriber());
        
        // Servicios para notificar eventos en tiempo real a los clientes
        new MessageNotificationService(connectionRegistry, canalRepository, clienteRepository, eventBus);
        new InvitationNotificationService(connectionRegistry, canalRepository, clienteRepository, invitacionRepository, eventBus);

        PasswordHasher passwordHasher = new Sha256PasswordHasher(config);
        RegistroServiceImpl registroServiceImpl = new RegistroServiceImpl(clienteRepository, passwordHasher, eventBus);
        this.registroService = registroServiceImpl;
        this.reporteService = new ReporteServiceImpl(clienteRepository, canalRepository, mensajeRepository, logRepository);
        this.conexionService = new ConexionServiceImpl(connectionRegistry, clienteRepository, eventBus);
        this.canalService = new CanalServiceImpl(canalRepository, clienteRepository, invitacionRepository, eventBus);
        
        // Establecer referencia circular después de crear ambos servicios
        registroServiceImpl.setConexionService(conexionService);

        // Instanciar el servicio de transcripción de audio (Vosk)
        AudioTranscriptionService transcriptionService = new VoskTranscriptionService();
        // Instanciar el servicio de almacenamiento de audio
        this.audioStorageService = new AudioStorageServiceImpl();
        // Instanciar el servicio de sincronización de mensajes
        this.messageSyncService = new MessageSyncServiceImpl(mensajeRepository, clienteRepository, canalRepository, audioStorageService);
        this.mensajeriaService = new MensajeriaServiceImpl(mensajeRepository, logRepository, connectionRegistry, eventBus, transcriptionService, audioStorageService);

        // Iniciar servidor TCP
        this.tcpServer = new TCPServer(registroService, canalService, mensajeriaService, reporteService, conexionService, audioStorageService, messageSyncService, eventBus, connectionRegistry);
        this.peerManager.start();
        this.tcpServer.start();
    }

    public ServidorController createServidorController(ServidorView view) {
        return new ServidorController(view, registroService, reporteService, conexionService, eventBus, peerManager);
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

    public TCPServer getTcpServer() {
        return tcpServer;
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

    public int getMaxConnections() {
        return ServerConfig.getInstance().getMaxConnections();
    }

    public ServerPeerManager getPeerManager() {
        return peerManager;
    }
}
