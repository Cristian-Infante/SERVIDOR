package com.arquitectura.servicios.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centraliza todas las metricas de observabilidad del servidor y expone
 * un endpoint HTTP en formato Prometheus para ser consumido por Prometheus/Grafana.
 */
public final class ServerMetrics {

    private static final Logger LOGGER = Logger.getLogger(ServerMetrics.class.getName());

    private static volatile HTTPServer httpServer;
    private static volatile ScheduledExecutorService systemMetricsExecutor;

    // --- TCP / comandos cliente ---

    private static final Gauge tcpActiveConnections = Gauge.build()
        .name("chat_tcp_active_connections")
        .help("NÃºmero de conexiones TCP activas (sesiones registradas).")
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
        .help("Latencia de procesamiento de comandos (desde recepciÃ³n hasta respuesta).")
        .labelNames("command")
        .buckets(0.001, 0.005, 0.01, 0.025, 0.05,
                 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        .register();

    private static final Histogram serverResponseTime = Histogram.build()
        .name("chat_server_response_time_seconds")
        .help("Tiempo de respuesta del servidor (desde lectura del comando hasta envio de respuesta).")
        .buckets(0.001, 0.005, 0.01, 0.025, 0.05,
                 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        .register();

    public static Histogram.Timer startServerResponseTimer() {

        return serverResponseTime.startTimer();

    }



    public static void observeServerResponse(Histogram.Timer timer) {

        if (timer != null) {

            timer.observeDuration();

        }

    }



    // --- AutenticaciÃ³n / sesiones ---

    private static final Counter loginAttempts = Counter.build()
        .name("chat_login_attempts_total")
        .help("Intentos de autenticaciÃ³n (LOGIN) por resultado.")
        .labelNames("result")
        .register();

    private static final Gauge authenticatedSessions = Gauge.build()
        .name("chat_authenticated_sessions")
        .help("NÃºmero de sesiones autenticadas activas.")
        .register();

    private static final Counter businessErrors = Counter.build()
        .name("chat_business_errors_total")
        .help("Respuestas de negocio con ERROR hacia el cliente.")
        .labelNames("reason")
        .register();

    // --- Audio / carga de archivos ---

    private static final Counter audioUploads = Counter.build()
        .name("chat_audio_uploads_total")
        .help("NÃºmero de comandos UPLOAD_AUDIO procesados exitosamente.")
        .register();

    private static final Histogram audioUploadSizeBytes = Histogram.build()
        .name("chat_audio_upload_size_bytes")
        .help("TamaÃ±o de los audios subidos en bytes.")
        .buckets(1024, 4096, 16384, 65536, 262144, 1048576, 4194304)
        .register();

    // --- SincronizaciÃ³n de mensajes ---

    private static final Histogram messageSyncBacklog = Histogram.build()
        .name("chat_message_sync_backlog_messages")
        .help("Cantidad de mensajes devueltos en MESSAGE_SYNC tras login.")
        .buckets(0, 10, 50, 100, 200, 500, 1000, 2000)
        .register();

    private static final Histogram messageSyncDuration = Histogram.build()
        .name("chat_message_sync_duration_seconds")
        .help("Tiempo dedicado a construir la respuesta de sincronizaciÃ³n de mensajes.")
        .buckets(0.01, 0.05, 0.1, 0.25, 0.5,
                 1.0, 2.0, 5.0, 10.0, 20.0)
        .register();

    // --- Eventos de mensajerÃ­a tiempo real ---

    private static final Counter realtimeEvents = Counter.build()
        .name("chat_realtime_events_total")
        .help("Eventos de tiempo real enviados a clientes (NEW_MESSAGE, INVITE_*, USER_STATUS_CHANGED, etc.).")
        .labelNames("event")
        .register();

    private static final Counter audioMessages = Counter.build()
        .name("chat_audio_messages_total")
        .help("Mensajes de audio enviados (incluye directos y de canal).")
        .register();

    // --- Metricas P2P entre servidores ---

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

    // --- Recursos del sistema ---

    private static final Gauge systemCpuUsagePercent = Gauge.build()
        .name("chat_system_cpu_usage_percent")
        .help("Uso de CPU del proceso del servidor (%) segun OperatingSystemMXBean.")
        .register();

    private static final Gauge systemMemoryUsagePercent = Gauge.build()
        .name("chat_system_memory_usage_percent")
        .help("Porcentaje de memoria fisica utilizada (best effort desde OperatingSystemMXBean).")
        .register();

    private static final Gauge systemMemoryUsedBytes = Gauge.build()
        .name("chat_system_memory_used_bytes")
        .help("Memoria fisica usada (bytes).")
        .register();

    private ServerMetrics() {
    }

    /**
     * Arranca el servidor HTTP de metricas Prometheus en el puerto indicado.
     * Es idempotente: llamar mÃºltiples veces reutiliza la misma instancia.
     */
    public static synchronized void startMetricsServer(int port) {
        if (httpServer != null) {
            return;
        }
        try {
            httpServer = new HTTPServer(port);
            startSystemMetricsCollector();

            LOGGER.info(() -> "Servidor de metricas Prometheus escuchando en puerto " + port);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar el servidor de metricas en el puerto " + port, e);
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

    // --- AutenticaciÃ³n / sesiones ---

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

    // --- SincronizaciÃ³n de mensajes ---

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
        // Reemplazar caracteres problemÃ¡ticos para etiquetas
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        if (normalized.isEmpty()) {
            return "unknown";
        }
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    private static void startSystemMetricsCollector() {
        if (systemMetricsExecutor != null) {
            return;
        }
        systemMetricsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-system-collector");
            t.setDaemon(true);
            return t;
        });
        systemMetricsExecutor.scheduleAtFixedRate(ServerMetrics::collectSystemMetrics, 0, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void collectSystemMetrics() {
        try {
            double cpu = readCpuUsagePercent();
            if (cpu >= 0) {
                systemCpuUsagePercent.set(cpu);
            }

            MemoryStats stats = readMemoryStats();
            if (stats.usedBytes >= 0) {
                systemMemoryUsedBytes.set(stats.usedBytes);
            }
            if (stats.usagePercent >= 0) {
                systemMemoryUsagePercent.set(stats.usagePercent);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo recolectar metricas de sistema", e);
        }
    }

    private static double readCpuUsagePercent() {
        try {
            var osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                double processLoad = sunOs.getProcessCpuLoad();
                if (processLoad >= 0) {
                    return processLoad * 100.0;
                }
                double systemLoad = sunOs.getCpuLoad();
                if (systemLoad >= 0) {
                    return systemLoad * 100.0;
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static MemoryStats readMemoryStats() {
        long used = -1;
        double percent = -1;
        try {
            var osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                long total = sunOs.getTotalMemorySize();
                long free = sunOs.getFreeMemorySize();
                if (total > 0) {
                    used = total - free;
                    percent = used * 100.0 / total;
                }
            } else {
                Runtime rt = Runtime.getRuntime();
                used = rt.totalMemory() - rt.freeMemory();
                long total = rt.totalMemory();
                if (total > 0) {
                    percent = used * 100.0 / total;
                }
            }
        } catch (Exception ignored) {
        }
        return new MemoryStats(used, percent);
    }

    private record MemoryStats(long usedBytes, double usagePercent) {}
}
