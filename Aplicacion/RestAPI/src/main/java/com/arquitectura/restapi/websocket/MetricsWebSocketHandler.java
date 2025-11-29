package com.arquitectura.restapi.websocket;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler de WebSocket para streaming de métricas en tiempo real.
 * Envía las métricas de Prometheus cada 5 segundos a todos los clientes conectados.
 */
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = Logger.getLogger(MetricsWebSocketHandler.class.getName());
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public MetricsWebSocketHandler() {
        // Iniciar el envío periódico de métricas
        scheduler.scheduleAtFixedRate(this::broadcastMetrics, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        LOGGER.info("Cliente conectado a WebSocket de métricas. Total clientes: " + sessions.size());
        
        // Enviar métricas inmediatamente al conectarse
        sendMetricsToSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        LOGGER.info("Cliente desconectado de WebSocket de métricas. Total clientes: " + sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        LOGGER.warning("Error en WebSocket de métricas: " + exception.getMessage());
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Manejar comandos del cliente si es necesario
        String payload = message.getPayload();
        
        if ("GET_METRICS".equals(payload)) {
            sendMetricsToSession(session);
        } else if ("PING".equals(payload)) {
            session.sendMessage(new TextMessage("PONG"));
        }
    }

    /**
     * Envía métricas a todos los clientes conectados.
     */
    private void broadcastMetrics() {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            String metrics = getPrometheusMetrics();
            String jsonMessage = createMetricsMessage(metrics);
            
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(jsonMessage));
                    } catch (IOException e) {
                        LOGGER.warning("Error enviando métricas a cliente: " + e.getMessage());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error generando métricas: " + e.getMessage());
        }
    }

    /**
     * Envía métricas a una sesión específica.
     */
    private void sendMetricsToSession(WebSocketSession session) {
        try {
            String metrics = getPrometheusMetrics();
            String jsonMessage = createMetricsMessage(metrics);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            LOGGER.warning("Error enviando métricas a sesión: " + e.getMessage());
        }
    }

    /**
     * Obtiene las métricas de Prometheus desde el servidor HTTP en puerto 5100.
     */
    private String getPrometheusMetrics() throws IOException {
        try {
            // Hacer petición HTTP al servidor Prometheus del TCP Server
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:5100/metrics"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                LOGGER.warning("Error obteniendo métricas de Prometheus: HTTP " + response.statusCode());
                return generatePlaceholderMetrics();
            }
        } catch (Exception e) {
            LOGGER.warning("No se pudo conectar al servidor TCP en puerto 5100: " + e.getMessage());
            return generatePlaceholderMetrics();
        }
    }
    
    /**
     * Genera métricas de placeholder cuando no se puede conectar al servidor TCP.
     */
    private String generatePlaceholderMetrics() {
        return "# HELP tcp_server_status Status of TCP server connection\n" +
               "# TYPE tcp_server_status gauge\n" +
               "tcp_server_status 0\n" +
               "# HELP rest_api_timestamp Current timestamp from REST API\n" +
               "# TYPE rest_api_timestamp counter\n" +
               "rest_api_timestamp " + System.currentTimeMillis() + "\n";
    }

    /**
     * Crea un mensaje JSON con las métricas y timestamp.
     */
    private String createMetricsMessage(String metrics) {
        return String.format(
            "{\"type\":\"metrics\",\"timestamp\":%d,\"data\":\"%s\"}",
            System.currentTimeMillis(),
            metrics.replace("\"", "\\\"").replace("\n", "\\n")
        );
    }
}
