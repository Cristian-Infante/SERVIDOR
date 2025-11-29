package com.arquitectura.gateway.service;

import com.arquitectura.gateway.model.AggregatedLogs;
import com.arquitectura.gateway.model.AggregatedMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para agregar datos de múltiples servidores TCP.
 */
@Service
public class DataAggregationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataAggregationService.class);
    
    private final AtomicReference<AggregatedMetrics> currentMetrics = new AtomicReference<>(new AggregatedMetrics());
    private final AtomicReference<AggregatedLogs> currentLogs = new AtomicReference<>(new AggregatedLogs());
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Agrega métricas de un servidor específico.
     */
    public void addServerMetrics(String serverId, Map<String, Object> metrics) {
        try {
            logger.debug("Agregando métricas del servidor {}: {} campos", serverId, metrics.size());
            
            AggregatedMetrics aggregated = currentMetrics.get();
            aggregated.addServerMetrics(serverId, metrics);
            aggregated.setTimestamp(LocalDateTime.now());
            
            // Actualizar referencia atómica
            currentMetrics.set(aggregated);
            
            // Notificar a clientes WebSocket mediante STOMP
            Map<String, Object> message = new HashMap<>();
            message.put("type", "metrics-update");
            message.put("data", aggregated);
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/metrics", message);
            
            logger.info("Métricas REALES agregadas y enviadas para servidor {} - CPU: {}%, Memoria: {}%", 
                       serverId, 
                       metrics.get("cpu_usage_percent"), 
                       metrics.get("memory_usage_percent"));
            
        } catch (Exception e) {
            logger.error("Error agregando métricas del servidor {} - {}", serverId, e.getMessage());
        }
    }

    /**
     * Agrega logs de un servidor específico - Solo envía logs nuevos incrementalmente.
     */
    public void addServerLogs(String serverId, List<Map<String, Object>> logs) {
        try {
            if (logs == null || logs.isEmpty()) {
                logger.debug("No hay logs para agregar del servidor {}", serverId);
                return;
            }
            
            logger.debug("Agregando {} logs del servidor {}", logs.size(), serverId);
            
            AggregatedLogs aggregated = currentLogs.get();
            aggregated.addLogs(serverId, logs);
            
            // Actualizar referencia atómica
            currentLogs.set(aggregated);
            
            // Enviar logs incrementalmente - solo los nuevos
            sendLogsUpdate(logs, "incremental");
            
            logger.debug("Logs agregados exitosamente para servidor {}: {} entradas", serverId, logs.size());
            
        } catch (Exception e) {
            logger.error("Error agregando logs del servidor {} - {}", serverId, e.getMessage());
        }
    }
    
    /**
     * Envía logs históricos completos al conectarse un nuevo cliente.
     */
    public void sendHistoricalLogs() {
        try {
            AggregatedLogs aggregated = currentLogs.get();
            List<Map<String, Object>> allLogs = aggregated.getAllLogs();
            
            if (allLogs != null && !allLogs.isEmpty()) {
                sendLogsUpdate(allLogs, "historical");
                logger.info("Enviados {} logs históricos a nuevo cliente", allLogs.size());
            } else {
                logger.debug("No hay logs históricos para enviar");
            }
        } catch (Exception e) {
            logger.error("Error enviando logs históricos: {}", e.getMessage());
        }
    }
    
    /**
     * Método privado para enviar actualizaciones de logs via WebSocket.
     */
    private void sendLogsUpdate(List<Map<String, Object>> logs, String updateType) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "logs-update");
            message.put("updateType", updateType); // "incremental" o "historical"
            message.put("entries", logs); // Cambiar "data" por "entries" para consistencia
            message.put("count", logs.size());
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/logs", message);
            
            logger.debug("Enviados {} logs via WebSocket ({})", logs.size(), updateType);
            
        } catch (Exception e) {
            logger.error("Error enviando logs via WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Marca un servidor como activo en las métricas.
     */
    public void markServerActive(String serverId) {
        try {
            logger.info("Marcando servidor {} como activo", serverId);
            
            AggregatedMetrics aggregated = currentMetrics.get();
            aggregated.markServerActive(serverId);
            aggregated.setTimestamp(LocalDateTime.now());
            
            currentMetrics.set(aggregated);
            
        } catch (Exception e) {
            logger.error("Error marcando servidor {} como activo - {}", serverId, e.getMessage());
        }
    }

    /**
     * Marca un servidor como inactivo en las métricas.
     */
    public void markServerInactive(String serverId) {
        try {
            logger.info("Marcando servidor {} como inactivo", serverId);
            
            AggregatedMetrics aggregated = currentMetrics.get();
            aggregated.markServerInactive(serverId);
            aggregated.setTimestamp(LocalDateTime.now());
            
            currentMetrics.set(aggregated);
            
            // Notificar cambio de estado mediante STOMP
            Map<String, Object> message = new HashMap<>();
            message.put("type", "metrics-update");
            message.put("data", aggregated);
            message.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/metrics", message);
            
        } catch (Exception e) {
            logger.error("Error marcando servidor {} como inactivo - {}", serverId, e.getMessage());
        }
    }

    /**
     * Obtiene las métricas agregadas actuales.
     */
    public AggregatedMetrics getCurrentMetrics() {
        return currentMetrics.get();
    }

    /**
     * Obtiene los logs agregados actuales.
     */
    public AggregatedLogs getCurrentLogs() {
        return currentLogs.get();
    }

    /**
     * Obtiene métricas de un servidor específico.
     */
    @Cacheable(value = "serverMetrics", key = "#serverId")
    public AggregatedMetrics.ServerMetrics getServerMetrics(String serverId) {
        AggregatedMetrics aggregated = currentMetrics.get();
        return aggregated.getServerMetrics().get(serverId);
    }

    /**
     * Obtiene logs de un servidor específico.
     */
    @Cacheable(value = "serverLogs", key = "#serverId")
    public List<AggregatedLogs.ServerLog> getServerLogs(String serverId) {
        AggregatedLogs aggregated = currentLogs.get();
        return aggregated.getLogsForServer(serverId);
    }

    /**
     * Obtiene logs recientes de un servidor específico.
     */
    public List<AggregatedLogs.ServerLog> getRecentServerLogs(String serverId, int limit) {
        AggregatedLogs aggregated = currentLogs.get();
        return aggregated.getRecentLogsForServer(serverId, limit);
    }

    /**
     * Obtiene estadísticas globales de métricas.
     */
    public Map<String, Object> getGlobalMetricsStats() {
        AggregatedMetrics aggregated = currentMetrics.get();
        AggregatedMetrics.GlobalMetrics global = aggregated.getGlobalMetrics();
        
        return Map.of(
            "totalServers", global.getTotalServers(),
            "connectedServers", global.getConnectedServers(),
            "disconnectedServers", global.getDisconnectedServers(),
            "totalConnections", global.getTotalConnections(),
            "totalMessages", global.getTotalMessages(),
            "totalErrors", global.getTotalErrors(),
            "lastUpdate", aggregated.getTimestamp()
        );
    }

    /**
     * Obtiene estadísticas globales de logs.
     */
    public Map<String, Object> getGlobalLogsStats() {
        AggregatedLogs aggregated = currentLogs.get();
        
        return Map.of(
            "totalLogs", aggregated.getTotalLogsCount(),
            "errorLogs", aggregated.getErrorLogsCount(),
            "serversWithLogs", aggregated.getLogsByServer().size(),
            "recentLogsCount", aggregated.getRecentLogs().size(),
            "lastUpdate", aggregated.getTimestamp()
        );
    }

    /**
     * Limpia datos antiguos de servidores desconectados.
     */
    public void cleanupInactiveServers(List<String> activeServerIds) {
        try {
            logger.debug("Limpiando datos de servidores inactivos. Activos: {}", activeServerIds.size());
            
            AggregatedMetrics metrics = currentMetrics.get();
            AggregatedLogs logs = currentLogs.get();
            
            // Limpiar métricas de servidores no activos
            metrics.getServerMetrics().keySet().removeIf(serverId -> !activeServerIds.contains(serverId));
            
            // Limpiar logs de servidores no activos (mantener logs recientes por un tiempo)
            // Solo removemos si el servidor ha estado inactivo por más de 1 hora
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            logs.getLogsByServer().entrySet().removeIf(entry -> {
                String serverId = entry.getKey();
                if (!activeServerIds.contains(serverId)) {
                    // Verificar si tiene logs recientes
                    boolean hasRecentLogs = entry.getValue().stream()
                            .anyMatch(log -> log.getReceivedAt().isAfter(oneHourAgo));
                    return !hasRecentLogs; // Remover si no tiene logs recientes
                }
                return false;
            });
            
            currentMetrics.set(metrics);
            currentLogs.set(logs);
            
            logger.debug("Limpieza completada");
            
        } catch (Exception e) {
            logger.error("Error en limpieza de servidores inactivos - {}", e.getMessage());
        }
    }
}