package com.arquitectura.gateway.service;

import com.arquitectura.gateway.model.AggregatedLogs;
import com.arquitectura.gateway.model.AggregatedMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Servicio para broadcast de datos a clientes WebSocket frontend.
 */
@Service
public class WebSocketBroadcastService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcastService.class);
    
    private final Set<WebSocketSession> metricsSessions = new CopyOnWriteArraySet<>();
    private final Set<WebSocketSession> logsSessions = new CopyOnWriteArraySet<>();
    private final Map<String, Set<WebSocketSession>> serverSpecificSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Registra una sesión para recibir métricas agregadas.
     */
    public void registerMetricsSession(WebSocketSession session) {
        metricsSessions.add(session);
        logger.info("Sesión registrada para métricas. Total sesiones: {}", metricsSessions.size());
    }

    /**
     * Registra una sesión para recibir logs agregados.
     */
    public void registerLogsSession(WebSocketSession session) {
        logsSessions.add(session);
        logger.info("Sesión registrada para logs. Total sesiones: {}", logsSessions.size());
    }

    /**
     * Registra una sesión para recibir datos de un servidor específico.
     */
    public void registerServerSpecificSession(WebSocketSession session, String serverId) {
        serverSpecificSessions.computeIfAbsent(serverId, k -> new CopyOnWriteArraySet<>()).add(session);
        logger.info("Sesión registrada para servidor {}. Total sesiones: {}", 
                   serverId, serverSpecificSessions.get(serverId).size());
    }

    /**
     * Desregistra una sesión de métricas.
     */
    public void unregisterMetricsSession(WebSocketSession session) {
        metricsSessions.remove(session);
        logger.info("Sesión desregistrada de métricas. Total sesiones: {}", metricsSessions.size());
    }

    /**
     * Desregistra una sesión de logs.
     */
    public void unregisterLogsSession(WebSocketSession session) {
        logsSessions.remove(session);
        logger.info("Sesión desregistrada de logs. Total sesiones: {}", logsSessions.size());
    }

    /**
     * Desregistra una sesión de un servidor específico.
     */
    public void unregisterServerSpecificSession(WebSocketSession session, String serverId) {
        Set<WebSocketSession> sessions = serverSpecificSessions.get(serverId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                serverSpecificSessions.remove(serverId);
            }
            logger.info("Sesión desregistrada del servidor {}. Sesiones restantes: {}", 
                       serverId, sessions.size());
        }
    }

    /**
     * Desregistra una sesión de todos los canales.
     */
    public void unregisterAllSessions(WebSocketSession session) {
        unregisterMetricsSession(session);
        unregisterLogsSession(session);
        
        // Remover de sesiones específicas de servidor
        serverSpecificSessions.forEach((serverId, sessions) -> {
            sessions.remove(session);
        });
        
        // Limpiar mapas vacíos
        serverSpecificSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Broadcast de métricas agregadas a todos los clientes.
     */
    public void broadcastMetrics(AggregatedMetrics metrics) {
        if (metricsSessions.isEmpty()) {
            logger.debug("No hay sesiones de métricas registradas para broadcast");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "aggregated_metrics");
            message.put("timestamp", System.currentTimeMillis());
            message.put("data", metrics);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            metricsSessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                        return false; // Mantener sesión
                    } else {
                        logger.debug("Removiendo sesión cerrada de métricas");
                        return true; // Remover sesión cerrada
                    }
                } catch (Exception e) {
                    logger.error("Error enviando métricas a sesión - {}", e.getMessage());
                    return true; // Remover sesión con error
                }
            });
            
            logger.debug("Métricas enviadas a {} sesiones", metricsSessions.size());
            
        } catch (Exception e) {
            logger.error("Error en broadcast de métricas - {}", e.getMessage());
        }
    }

    /**
     * Broadcast de logs agregados a todos los clientes.
     */
    public void broadcastLogs(AggregatedLogs logs) {
        if (logsSessions.isEmpty()) {
            logger.debug("No hay sesiones de logs registradas para broadcast");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "aggregated_logs");
            message.put("timestamp", System.currentTimeMillis());
            message.put("count", logs.getTotalLogsCount());
            message.put("data", logs);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            logsSessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                        return false; // Mantener sesión
                    } else {
                        logger.debug("Removiendo sesión cerrada de logs");
                        return true; // Remover sesión cerrada
                    }
                } catch (Exception e) {
                    logger.error("Error enviando logs a sesión - {}", e.getMessage());
                    return true; // Remover sesión con error
                }
            });
            
            logger.debug("Logs enviados a {} sesiones", logsSessions.size());
            
        } catch (Exception e) {
            logger.error("Error en broadcast de logs - {}", e.getMessage());
        }
    }

    /**
     * Broadcast de datos específicos de un servidor.
     */
    public void broadcastServerData(String serverId, Object data, String dataType) {
        Set<WebSocketSession> sessions = serverSpecificSessions.get(serverId);
        if (sessions == null || sessions.isEmpty()) {
            logger.debug("No hay sesiones registradas para servidor {}", serverId);
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "server_" + dataType);
            message.put("serverId", serverId);
            message.put("timestamp", System.currentTimeMillis());
            message.put("data", data);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                        return false; // Mantener sesión
                    } else {
                        logger.debug("Removiendo sesión cerrada del servidor {}", serverId);
                        return true; // Remover sesión cerrada
                    }
                } catch (Exception e) {
                    logger.error("Error enviando datos del servidor {} a sesión - {}", serverId, e.getMessage());
                    return true; // Remover sesión con error
                }
            });
            
            logger.debug("Datos del servidor {} enviados a {} sesiones", serverId, sessions.size());
            
        } catch (Exception e) {
            logger.error("Error en broadcast de datos del servidor {} - {}", serverId, e.getMessage());
        }
    }

    /**
     * Envía mensaje de conexión inicial a una sesión.
     */
    public void sendWelcomeMessage(WebSocketSession session, String connectionType) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "connection_established");
            message.put("connectionType", connectionType);
            message.put("timestamp", System.currentTimeMillis());
            message.put("message", "Conectado exitosamente al API Gateway");
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
            
            logger.debug("Mensaje de bienvenida enviado para conexión tipo: {}", connectionType);
            
        } catch (Exception e) {
            logger.error("Error enviando mensaje de bienvenida - {}", e.getMessage());
        }
    }

    /**
     * Obtiene estadísticas de sesiones activas.
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("metricsSessions", metricsSessions.size());
        stats.put("logsSessions", logsSessions.size());
        stats.put("serverSpecificSessions", serverSpecificSessions.size());
        
        Map<String, Integer> serverSessionCounts = new HashMap<>();
        serverSpecificSessions.forEach((serverId, sessions) -> {
            serverSessionCounts.put(serverId, sessions.size());
        });
        stats.put("sessionsByServer", serverSessionCounts);
        stats.put("lastUpdate", LocalDateTime.now());
        
        return stats;
    }
}