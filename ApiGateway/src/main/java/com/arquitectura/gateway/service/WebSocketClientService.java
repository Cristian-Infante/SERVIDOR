package com.arquitectura.gateway.service;

import com.arquitectura.gateway.config.ServersConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio para recolectar datos de servidores REST configurados estáticamente.
 * Se conecta automáticamente a los servidores al iniciar la aplicación.
 */
@Service
public class WebSocketClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientService.class);
    
    @Autowired
    private ServersConfiguration serversConfig;
    
    @Autowired
    private DataAggregationService aggregationService;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> metricsJobs = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> logsJobs = new ConcurrentHashMap<>();
    private final Map<String, ServerStatus> serverStatuses = new ConcurrentHashMap<>();
    
    /**
     * Inicia la recolección automática cuando la aplicación está lista.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAutomaticCollection() {
        if (!serversConfig.getDataCollection().isAutoStart()) {
            logger.info("Auto-start deshabilitado en configuración");
            return;
        }
        
        logger.info("Iniciando recolección automática de datos...");
        
        List<ServersConfiguration.ServerConfig> servers = serversConfig.getAllServers();
        for (ServersConfiguration.ServerConfig server : servers) {
            logger.info("Configurado servidor: {} - {}:{}", server.getName(), server.getHost(), server.getPort());
            startCollectionForServer(server);
        }
        
        logger.info("Recolección automática iniciada para {} servidores", servers.size());
    }
    
    /**
     * Inicia la recolección de datos para un servidor específico.
     */
    public void startCollectionForServer(ServersConfiguration.ServerConfig server) {
        try {
            // Verificar disponibilidad antes de iniciar
            if (!isServerAvailable(server)) {
                logger.warn("Servidor {} no disponible - no se puede iniciar recolección", server.getId());
                aggregationService.markServerInactive(server.getId());
                return;
            }
            
            logger.info("Iniciando recolección para servidor: {} ({}:{})", 
                       server.getName(), server.getHost(), server.getPort());
            
            // Inicializar estado del servidor
            serverStatuses.put(server.getId(), new ServerStatus(server.getId(), true));
            aggregationService.markServerActive(server.getId());
            
            // Programar recolección de métricas
            ScheduledFuture<?> metricsJob = scheduler.scheduleAtFixedRate(
                () -> collectMetricsForServer(server),
                0, // Iniciar inmediatamente
                serversConfig.getDataCollection().getMetricsInterval(),
                TimeUnit.MILLISECONDS
            );
            metricsJobs.put(server.getId(), metricsJob);
            
            // Recolectar logs históricos inmediatamente (una sola vez)
            collectHistoricalLogsForServer(server);
            
            // Programar recolección de logs nuevos (TODO: implementar diferenciación)
            ScheduledFuture<?> logsJob = scheduler.scheduleAtFixedRate(
                () -> collectNewLogsForServer(server),
                10000, // Iniciar después de obtener históricos
                serversConfig.getDataCollection().getLogsInterval(),
                TimeUnit.MILLISECONDS
            );
            logsJobs.put(server.getId(), logsJob);
            
            logger.info("Recolección iniciada exitosamente para servidor: {}", server.getId());
            
        } catch (Exception e) {
            logger.error("Error iniciando recolección para servidor {}: {}", server.getId(), e.getMessage());
        }
    }
    
    /**
     * Recolecta métricas de un servidor específico mediante REST API.
     */
    private void collectMetricsForServer(ServersConfiguration.ServerConfig server) {
        try {
            String url = server.getMetricsUrl();
            logger.debug("Recolectando métricas de: {}", url);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> metrics = response.getBody();
                
                // Agregar información del servidor
                metrics.put("server_id", server.getId());
                metrics.put("server_name", server.getName());
                
                // Enviar métricas al servicio de agregación
                aggregationService.addServerMetrics(server.getId(), metrics);
                
                // Actualizar estado del servidor
                ServerStatus status = serverStatuses.get(server.getId());
                if (status != null) {
                    status.updateLastSuccessfulMetrics();
                }
                
                logger.debug("Métricas recolectadas exitosamente para servidor: {}", server.getId());
                
            } else {
                logger.warn("Respuesta vacía o error HTTP para métricas del servidor {}: {}", 
                           server.getId(), response.getStatusCode());
                // Marcar servidor como inactivo
                aggregationService.markServerInactive(server.getId());
            }
            
        } catch (Exception e) {
            logger.error("Error recolectando métricas del servidor {}: {}", server.getId(), e.getMessage());
            // Marcar servidor como inactivo cuando falla la conexión
            aggregationService.markServerInactive(server.getId());
        }
    }
    
    /**
     * Recolecta logs históricos de un servidor al conectarse (una sola vez).
     */
    private void collectHistoricalLogsForServer(ServersConfiguration.ServerConfig server) {
        try {
            logger.info("Recolectando logs históricos del servidor {}", server.getId());
            
            String url = server.getLogsUrl();
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Object> logs = response.getBody();
                List<Map<String, Object>> processedLogs = processLogsResponse(server, logs);
                
                if (!processedLogs.isEmpty()) {
                    // Enviar logs históricos al servicio de agregación
                    aggregationService.addServerLogs(server.getId(), processedLogs);
                    logger.info("Logs históricos cargados para servidor {}: {} entradas", 
                               server.getId(), processedLogs.size());
                } else {
                    logger.info("No hay logs históricos para el servidor {}", server.getId());
                }
                
                // Actualizar estado del servidor
                ServerStatus status = serverStatuses.get(server.getId());
                if (status != null) {
                    status.updateLastSuccessfulLogs();
                }
                
            } else {
                logger.warn("Error obteniendo logs históricos del servidor {}: {}", 
                           server.getId(), response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error recolectando logs históricos del servidor {}: {}", 
                        server.getId(), e.getMessage());
        }
    }
    
    /**
     * Recolecta solo logs nuevos de un servidor (ejecución periódica).
     * TODO: Implementar lógica para obtener solo logs nuevos basándose en timestamp
     */
    private void collectNewLogsForServer(ServersConfiguration.ServerConfig server) {
        try {
            // Por ahora, no hacer nada para evitar duplicación de logs
            logger.debug("Verificando logs nuevos para servidor {} (implementación pendiente)", server.getId());
            
        } catch (Exception e) {
            logger.error("Error verificando logs nuevos del servidor {}: {}", 
                        server.getId(), e.getMessage());
        }
    }
    
    /**
     * Procesa la respuesta de logs del servidor REST.
     */
    private List<Map<String, Object>> processLogsResponse(ServersConfiguration.ServerConfig server, List<Object> logs) {
        List<Map<String, Object>> processedLogs = new ArrayList<>();
        
        for (Object logObj : logs) {
            if (logObj instanceof Map) {
                Map<String, Object> originalLog = (Map<String, Object>) logObj;
                Map<String, Object> processedLog = new HashMap<>();
                
                // Agregar información del servidor
                processedLog.put("server_id", server.getId());
                processedLog.put("server_name", server.getName());
                
                // Mapear campos del servidor REST al formato esperado
                processedLog.put("id", originalLog.get("id"));
                
                // Normalizar el nivel de log
                String originalLevel = (String) originalLog.get("tipo");
                String message = (String) originalLog.get("detalle");
                String normalizedLevel = normalizeLogLevel(originalLevel, message);
                
                processedLog.put("level", normalizedLevel);
                processedLog.put("message", message);
                processedLog.put("timestamp", originalLog.get("fechaHora"));
                processedLog.put("collection_timestamp", LocalDateTime.now().toString());
                
                processedLogs.add(processedLog);
            }
        }
        
        return processedLogs;
    }
    
    /**
     * Normaliza el nivel de log del servidor REST.
     */
    private String normalizeLogLevel(String originalLevel, String message) {
        if (originalLevel == null) return "INFO";
        
        // Convertir boolean a string si es necesario
        if ("true".equals(originalLevel) || "INFO".equals(originalLevel)) {
            return "INFO";
        } else if ("false".equals(originalLevel) || "ERROR".equals(originalLevel)) {
            return "ERROR";
        } else if ("WARN".equals(originalLevel) || "WARNING".equals(originalLevel)) {
            return "WARN";
        }
        
        return originalLevel.toUpperCase();
    }
    
    /**
     * Obtiene el estado actual de todos los servidores.
     */
    public Map<String, Object> getServersStatus() {
        Map<String, Object> status = new HashMap<>();
        
        List<ServersConfiguration.ServerConfig> allServers = serversConfig.getAllServers();
        int activeServers = 0;
        int inactiveServers = 0;
        
        Map<String, Object> serverDetails = new HashMap<>();
        for (ServersConfiguration.ServerConfig server : allServers) {
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("config", server);
            
            boolean isActive = metricsJobs.containsKey(server.getId()) && serverStatuses.containsKey(server.getId());
            serverInfo.put("collecting", isActive);
            serverInfo.put("status", isActive ? "ACTIVE" : "INACTIVE");
            serverInfo.put("lastCheck", LocalDateTime.now());
            
            if (isActive) {
                activeServers++;
                ServerStatus serverStatus = serverStatuses.get(server.getId());
                if (serverStatus != null) {
                    serverInfo.put("serverStatus", serverStatus);
                }
            } else {
                inactiveServers++;
            }
            
            serverDetails.put(server.getId(), serverInfo);
        }
        
        status.put("totalServers", allServers.size());
        status.put("activeServers", activeServers);
        status.put("inactiveServers", inactiveServers);
        status.put("activeCollections", metricsJobs.size());
        status.put("servers", serverDetails);
        status.put("success", true);
        
        return status;
    }
    
    /**
     * Detiene la recolección de datos para un servidor específico.
     */
    public void stopCollectionForServer(String serverId) {
        logger.info("Deteniendo recolección para servidor: {}", serverId);
        
        // Cancelar job de métricas
        ScheduledFuture<?> metricsJob = metricsJobs.remove(serverId);
        if (metricsJob != null) {
            metricsJob.cancel(true);
        }
        
        // Cancelar job de logs
        ScheduledFuture<?> logsJob = logsJobs.remove(serverId);
        if (logsJob != null) {
            logsJob.cancel(true);
        }
        
        // Remover estado del servidor
        serverStatuses.remove(serverId);
        
        // Marcar servidor como inactivo en agregación
        aggregationService.markServerInactive(serverId);
        
        logger.info("Recolección detenida para servidor: {}", serverId);
    }
    
    /**
     * Detiene toda la recolección al cerrar la aplicación.
     */
    public void shutdown() {
        logger.info("Deteniendo recolección de datos...");
        
        // Cancelar todos los jobs
        metricsJobs.values().forEach(job -> job.cancel(true));
        logsJobs.values().forEach(job -> job.cancel(true));
        
        // Limpiar mapas
        metricsJobs.clear();
        logsJobs.clear();
        serverStatuses.clear();
        
        // Shutdown del scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        logger.info("Recolección de datos detenida");
    }
    
    /**
     * Verifica si un servidor está disponible mediante health check.
     */
    private boolean isServerAvailable(ServersConfiguration.ServerConfig server) {
        try {
            String healthUrl = server.getHealthCheckUrl();
            logger.debug("Verificando disponibilidad de servidor: {}", healthUrl);
            
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Servidor {} está disponible", server.getId());
                return true;
            } else {
                logger.warn("Servidor {} no está disponible - HTTP {}", server.getId(), response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.warn("Servidor {} no disponible - {}", server.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Clase interna para el estado del servidor.
     */
    private static class ServerStatus {
        private final String serverId;
        private final LocalDateTime startedAt;
        private LocalDateTime lastSuccessfulMetrics;
        private LocalDateTime lastSuccessfulLogs;
        private boolean active;
        
        public ServerStatus(String serverId, boolean active) {
            this.serverId = serverId;
            this.active = active;
            this.startedAt = LocalDateTime.now();
        }
        
        public void updateLastSuccessfulMetrics() {
            this.lastSuccessfulMetrics = LocalDateTime.now();
        }
        
        public void updateLastSuccessfulLogs() {
            this.lastSuccessfulLogs = LocalDateTime.now();
        }
        
        // Getters
        public String getServerId() { return serverId; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getLastSuccessfulMetrics() { return lastSuccessfulMetrics; }
        public LocalDateTime getLastSuccessfulLogs() { return lastSuccessfulLogs; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}