package com.arquitectura.servicios.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centraliza todas las métricas de observabilidad del servidor y expone
 * un endpoint HTTP en formato Prometheus para ser consumido por Prometheus/Grafana.
 */
public final class ServerMetrics {

    private static final Logger LOGGER = Logger.getLogger(ServerMetrics.class.getName());

    private static volatile HTTPServer httpServer;

    // --- TCP / comandos cliente ---

    private static final Gauge tcpActiveConnections = Gauge.build()
        .name("chat_tcp_active_connections")
        .help("Número de conexiones TCP activas (sesiones registradas).")
        .register();

    private static final Counter tcpConnectionEvents = Counter.build()
        .name("chat_tcp_connection_events_total")
        .help("Eventos de ciclo de vida de conexiones TCP.")
        .labelNames("event")
        .register();

    private static final Counter tcpSocketErrors = Counter.build()
        .name("chat_tcp_socket_errors_total")
        .help("Errores de socket en el servidor/handler TCP.")
        .labelNames("phase", "exception")
        .register();

    private static final Counter commandsTotal = Counter.build()
        .name("chat_commands_total")
        .help("Comandos procesados por el servidor por tipo y resultado.")
        .labelNames("command", "result")
        .register();

    private static final Counter commandErrors = Counter.build()
        .name("chat_command_errors_total")
        .help("Errores en el procesamiento de comandos por tipo.")
        .labelNames("command", "type")
        .register();

    private static final Histogram commandLatency = Histogram.build()
        .name("chat_command_latency_seconds")
        .help("Latencia de procesamiento de comandos (desde recepción hasta respuesta).")
        .labelNames("command")
        .buckets(0.001, 0.005, 0.01, 0.025, 0.05,
                 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        .register();

    // --- Autenticación / sesiones ---

    private static final Counter loginAttempts = Counter.build()
        .name("chat_login_attempts_total")
        .help("Intentos de autenticación (LOGIN) por resultado.")
        .labelNames("result")
        .register();

    private static final Gauge authenticatedSessions = Gauge.build()
        .name("chat_authenticated_sessions")
        .help("Número de sesiones autenticadas activas.")
        .register();

    private static final Counter businessErrors = Counter.build()
        .name("chat_business_errors_total")
        .help("Respuestas de negocio con ERROR hacia el cliente.")
        .labelNames("reason")
        .register();

    // --- Audio / carga de archivos ---

    private static final Counter audioUploads = Counter.build()
        .name("chat_audio_uploads_total")
        .help("Número de comandos UPLOAD_AUDIO procesados exitosamente.")
        .register();

    private static final Histogram audioUploadSizeBytes = Histogram.build()
        .name("chat_audio_upload_size_bytes")
        .help("Tamaño de los audios subidos en bytes.")
        .buckets(1024, 4096, 16384, 65536, 262144, 1048576, 4194304)
        .register();

    // --- Sincronización de mensajes ---

    private static final Histogram messageSyncBacklog = Histogram.build()
        .name("chat_message_sync_backlog_messages")
        .help("Cantidad de mensajes devueltos en MESSAGE_SYNC tras login.")
        .buckets(0, 10, 50, 100, 200, 500, 1000, 2000)
        .register();

    private static final Histogram messageSyncDuration = Histogram.build()
        .name("chat_message_sync_duration_seconds")
        .help("Tiempo dedicado a construir la respuesta de sincronización de mensajes.")
        .buckets(0.01, 0.05, 0.1, 0.25, 0.5,
                 1.0, 2.0, 5.0, 10.0, 20.0)
        .register();

    // --- Eventos de mensajería tiempo real ---

    private static final Counter realtimeEvents = Counter.build()
        .name("chat_realtime_events_total")
        .help("Eventos de tiempo real enviados a clientes (NEW_MESSAGE, INVITE_*, USER_STATUS_CHANGED, etc.).")
        .labelNames("event")
        .register();

    private static final Counter audioMessages = Counter.build()
        .name("chat_audio_messages_total")
        .help("Mensajes de audio enviados (incluye directos y de canal).")
        .register();

    // --- Métricas P2P entre servidores ---

    private static final Gauge p2pConnectedPeers = Gauge.build()
        .name("chat_p2p_connected_peers")
        .help("Peers P2P actualmente conectados.")
        .register();

    private static final Counter p2pConnectionFailures = Counter.build()
        .name("chat_p2p_connection_failures_total")
        .help("Fallos al establecer conexiones P2P.")
        .labelNames("phase")
        .register();

    private static final Counter p2pEvents = Counter.build()
        .name("chat_p2p_events_total")
        .help("Mensajes P2P enviados por tipo.")
        .labelNames("type")
        .register();

    private static final Histogram p2pRouteHops = Histogram.build()
        .name("chat_p2p_route_hops")
        .help("Longitud de la ruta (hops) en mensajes P2P dirigidos.")
        .labelNames("type")
        .buckets(1, 2, 3, 4, 5, 8, 12, 16)
        .register();

    private ServerMetrics() {
    }

    /**
     * Arranca el servidor HTTP de métricas Prometheus en el puerto indicado.
     * Es idempotente: llamar múltiples veces reutiliza la misma instancia.
     */
    public static synchronized void startMetricsServer(int port) {
        if (httpServer != null) {
            return;
        }
        try {
            httpServer = new HTTPServer(port);
            LOGGER.info(() -> "Servidor de métricas Prometheus escuchando en puerto " + port);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar el servidor de métricas en el puerto " + port, e);
        }
    }

    // --- TCP / conexiones ---

    public static void onTcpSessionRegistered() {
        tcpActiveConnections.inc();
        tcpConnectionEvents.labels("registered").inc();
    }

    public static void onTcpSessionUnregistered() {
        tcpActiveConnections.dec();
        tcpConnectionEvents.labels("unregistered").inc();
    }

    public static void onTcpConnectionRejected(String reason) {
        String label = normalizeLabel(reason);
        tcpConnectionEvents.labels(label).inc();
    }

    public static void onTcpSocketError(String phase, Exception exception) {
        String exName = exception != null ? exception.getClass().getSimpleName() : "Unknown";
        String phaseLabel = normalizeLabel(phase);
        tcpSocketErrors.labels(phaseLabel, exName).inc();
    }

    // --- Comandos ---

    public static Histogram.Timer startCommandTimer(String command) {
        String cmd = normalizeCommand(command);
        return commandLatency.labels(cmd).startTimer();
    }

    public static void finishCommand(String command, String result, Histogram.Timer timer) {
        String cmd = normalizeCommand(command);
        String res = normalizeLabel(result);
        commandsTotal.labels(cmd, res).inc();
        if (timer != null) {
            timer.observeDuration();
        }
    }

    public static void recordCommandError(String command, String type) {
        String cmd = normalizeCommand(command);
        String errorType = normalizeLabel(type);
        commandErrors.labels(cmd, errorType).inc();
    }

    // --- Autenticación / sesiones ---

    public static void recordLoginSuccess() {
        loginAttempts.labels("success").inc();
    }

    public static void recordLoginFailure() {
        loginAttempts.labels("failure").inc();
    }

    public static void onSessionAuthenticated() {
        authenticatedSessions.inc();
    }

    public static void onSessionLogout() {
        authenticatedSessions.dec();
    }

    public static void recordBusinessError(String reason) {
        String label = normalizeLabel(reason);
        businessErrors.labels(label).inc();
    }

    // --- Audio ---

    public static void recordAudioUpload(long sizeBytes) {
        audioUploads.inc();
        if (sizeBytes > 0) {
            audioUploadSizeBytes.observe(sizeBytes);
        }
    }

    public static void recordAudioMessage() {
        audioMessages.inc();
    }

    // --- Sincronización de mensajes ---

    public static Histogram.Timer startMessageSyncTimer() {
        return messageSyncDuration.startTimer();
    }

    public static void observeMessageSyncDuration(Histogram.Timer timer) {
        if (timer != null) {
            timer.observeDuration();
        }
    }

    public static void observeMessageSyncBacklog(int totalMessages) {
        messageSyncBacklog.observe(totalMessages);
    }

    // --- Eventos tiempo real ---

    public static void recordRealtimeEvent(String eventName) {
        String evt = eventName != null ? eventName.trim() : "UNKNOWN";
        if (evt.isEmpty()) {
            evt = "UNKNOWN";
        }
        realtimeEvents.labels(evt).inc();
    }

    // --- P2P ---

    public static void updateConnectedPeers(int count) {
        p2pConnectedPeers.set(count);
    }

    public static void recordP2PConnectionFailure(String phase) {
        String label = normalizeLabel(phase);
        p2pConnectionFailures.labels(label).inc();
    }

    public static void recordP2PEvent(String type) {
        String t = type != null ? type.trim() : "UNKNOWN";
        if (t.isEmpty()) {
            t = "UNKNOWN";
        }
        p2pEvents.labels(t).inc();
    }

    public static void observeP2PRoute(String type, int hops) {
        if (hops <= 0) {
            return;
        }
        String t = type != null ? type.trim() : "UNKNOWN";
        if (t.isEmpty()) {
            t = "UNKNOWN";
        }
        p2pRouteHops.labels(t).observe(hops);
    }

    // --- Utilidades ---

    private static String normalizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "UNKNOWN";
        }
        return command.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        // Reemplazar caracteres problemáticos para etiquetas
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        if (normalized.isEmpty()) {
            return "unknown";
        }
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }
}
