package com.arquitectura.bootstrap;

import com.arquitectura.bootstrap.config.ServerConfig;
import com.arquitectura.controladores.conexion.ConnectionHandler;
import com.arquitectura.controladores.conexion.ConnectionHandlerPool;
import com.arquitectura.controladores.conexion.ConnectionRegistry;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.CanalService;
import com.arquitectura.servicios.ConexionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.MessageSyncService;
import com.arquitectura.servicios.RegistroService;
import com.arquitectura.servicios.ReporteService;
import com.arquitectura.servicios.eventos.SessionEventBus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP Server that listens for client connections and delegates handling
 * to ConnectionHandler instances from a pool.
 */
public class TCPServer implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TCPServer.class.getName());

    private final int port;
    private final int maxConnections;
    private final RegistroService registroService;
    private final CanalService canalService;
    private final MensajeriaService mensajeriaService;
    private final ReporteService reporteService;
    private final ConexionService conexionService;
    private final AudioStorageService audioStorageService;
    private final MessageSyncService messageSyncService;
    private final SessionEventBus eventBus;
    private final ConnectionRegistry registry;
    private final ConnectionHandlerPool pool;
    private final ExecutorService executor;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public TCPServer(RegistroService registroService,
                     CanalService canalService,
                     MensajeriaService mensajeriaService,
                     ReporteService reporteService,
                     ConexionService conexionService,
                     AudioStorageService audioStorageService,
                     MessageSyncService messageSyncService,
                     SessionEventBus eventBus,
                     ConnectionRegistry registry) {
        ServerConfig config = ServerConfig.getInstance();
        this.port = config.getServerPort();
        this.maxConnections = config.getMaxConnections();
        this.registroService = registroService;
        this.canalService = canalService;
        this.mensajeriaService = mensajeriaService;
        this.reporteService = reporteService;
        this.conexionService = conexionService;
        this.audioStorageService = audioStorageService;
        this.messageSyncService = messageSyncService;
        this.eventBus = eventBus;
        this.registry = registry;
        this.pool = new ConnectionHandlerPool(10, () -> new ConnectionHandler(
            registroService, canalService, mensajeriaService, reporteService, conexionService, audioStorageService, messageSyncService, eventBus, registry
        ));
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            
            String hostAddress = getServerAddress();
            LOGGER.log(Level.INFO, "Servidor TCP iniciado en {0}:{1}", new Object[]{hostAddress, port});
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getRemoteSocketAddress().toString();
                    
                    // Verificar si se alcanzó el límite de conexiones
                    int currentConnections = registry.getTotalConnections();
                    if (currentConnections >= maxConnections) {
                        LOGGER.log(Level.WARNING, 
                            "Conexión rechazada desde {0} - Límite alcanzado ({1}/{2} conexiones)", 
                            new Object[]{clientAddress, currentConnections, maxConnections});
                        
                        try {
                            // Notificar al cliente antes de cerrar
                            var writer = new java.io.BufferedWriter(
                                new java.io.OutputStreamWriter(clientSocket.getOutputStream(), 
                                java.nio.charset.StandardCharsets.UTF_8));
                            writer.write("{\"command\":\"ERROR\",\"payload\":{\"error\":\"Servidor lleno. Máximo de conexiones alcanzado.\"}}\n");
                            writer.flush();
                        } catch (IOException ignored) {
                        }
                        
                        clientSocket.close();
                        continue;
                    }
                    
                    LOGGER.log(Level.INFO, "Nueva conexión desde {0} ({1}/{2} conexiones)", 
                        new Object[]{clientAddress, currentConnections + 1, maxConnections});
                    
                    ConnectionHandler handler = pool.acquire(clientSocket);
                    if (handler != null) {
                        executor.execute(handler);
                    } else {
                        LOGGER.log(Level.WARNING, "Pool agotado, rechazando conexión");
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Error aceptando conexión", e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error iniciando servidor TCP", e);
        } finally {
            shutdown();
        }
    }

    public void start() {
        Thread serverThread = new Thread(this, "TCP-Server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private String getServerAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.log(Level.WARNING, "No se pudo obtener la dirección IP local", e);
            return "localhost";
        }
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error cerrando ServerSocket", e);
            }
        }
        executor.shutdown();
        LOGGER.log(Level.INFO, "Servidor TCP detenido");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
