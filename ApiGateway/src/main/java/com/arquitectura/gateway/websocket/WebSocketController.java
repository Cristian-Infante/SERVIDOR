package com.arquitectura.gateway.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador WebSocket para manejar comunicación en tiempo real.
 * Recibe suscripciones y envía datos agregados a los clientes conectados.
 */
@Controller
@CrossOrigin(origins = "*")
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Maneja suscripciones a métricas agregadas.
     * Frontend envía: /app/subscribe-metrics
     * Respuesta automática: /topic/metrics
     */
    @MessageMapping("/subscribe-metrics")
    @SendTo("/topic/metrics")
    public Map<String, Object> subscribeToMetrics() {
        logger.info("Cliente suscrito a métricas agregadas");
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "metrics");
        response.put("message", "Suscripción a métricas exitosa");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return response;
    }

    /**
     * Maneja suscripciones a logs agregados.
     * Frontend envía: /app/subscribe-logs  
     * Respuesta automática: /topic/logs
     */
    @MessageMapping("/subscribe-logs")
    @SendTo("/topic/logs")
    public Map<String, Object> subscribeToLogs() {
        logger.info("Cliente suscrito a logs agregados");
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "logs");
        response.put("message", "Suscripción a logs exitosa");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return response;
    }

    /**
     * Envía métricas agregadas a todos los clientes suscritos.
     * Llamado internamente por el servicio de agregación.
     */
    public void broadcastMetrics(Map<String, Object> metrics) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "metrics-update");
            message.put("data", metrics);
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/metrics", message);
            logger.debug("Métricas enviadas a clientes WebSocket: {} servidores activos", 
                        metrics.getOrDefault("totalServers", 0));
            
        } catch (Exception e) {
            logger.error("Error enviando métricas por WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Envía logs agregados a todos los clientes suscritos.
     * Llamado internamente por el servicio de agregación.
     */
    public void broadcastLogs(List<Map<String, Object>> logs) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "logs-update");
            message.put("data", logs);
            message.put("count", logs.size());
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/logs", message);
            logger.debug("Logs enviados a clientes WebSocket: {} entradas", logs.size());
            
        } catch (Exception e) {
            logger.error("Error enviando logs por WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Envía notificación cuando un servidor se conecta.
     */
    public void broadcastServerConnected(String serverId, String host, int port) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "server-connected");
            message.put("serverId", serverId);
            message.put("host", host);
            message.put("port", port);
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/servers", message);
            logger.info("Notificación de servidor conectado enviada: {}", serverId);
            
        } catch (Exception e) {
            logger.error("Error enviando notificación de conexión: {}", e.getMessage());
        }
    }

    /**
     * Envía notificación cuando un servidor se desconecta.
     */
    public void broadcastServerDisconnected(String serverId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "server-disconnected");
            message.put("serverId", serverId);
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/servers", message);
            logger.info("Notificación de servidor desconectado enviada: {}", serverId);
            
        } catch (Exception e) {
            logger.error("Error enviando notificación de desconexión: {}", e.getMessage());
        }
    }
}