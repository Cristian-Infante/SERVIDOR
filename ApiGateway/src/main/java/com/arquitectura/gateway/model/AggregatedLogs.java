package com.arquitectura.gateway.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs agregados de múltiples servidores TCP.
 */
public class AggregatedLogs {
    
    private LocalDateTime timestamp;
    private Map<String, List<ServerLog>> logsByServer;
    private List<ServerLog> recentLogs;
    private int maxLogsPerServer;
    private int maxRecentLogs;

    public AggregatedLogs() {
        this.timestamp = LocalDateTime.now();
        this.logsByServer = new ConcurrentHashMap<>();
        this.recentLogs = new ArrayList<>();
        this.maxLogsPerServer = 100;
        this.maxRecentLogs = 500;
    }

    public static class ServerLog {
        private String serverId;
        private Long logId;
        private String tipo;
        private String detalle;
        private LocalDateTime fechaHora;
        private LocalDateTime receivedAt;

        public ServerLog() {
            this.receivedAt = LocalDateTime.now();
        }

        public ServerLog(String serverId, Map<String, Object> logData) {
            this();
            this.serverId = serverId;
            
            if (logData.get("id") != null) {
                this.logId = Long.valueOf(logData.get("id").toString());
            }
            
            this.tipo = logData.get("tipo") != null ? logData.get("tipo").toString() : "INFO";
            this.detalle = logData.get("detalle") != null ? logData.get("detalle").toString() : "";
            
            if (logData.get("fechaHora") != null) {
                try {
                    this.fechaHora = LocalDateTime.parse(logData.get("fechaHora").toString());
                } catch (Exception e) {
                    this.fechaHora = LocalDateTime.now();
                }
            } else {
                this.fechaHora = LocalDateTime.now();
            }
        }

        // Getters y Setters
        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }

        public Long getLogId() { return logId; }
        public void setLogId(Long logId) { this.logId = logId; }

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public String getDetalle() { return detalle; }
        public void setDetalle(String detalle) { this.detalle = detalle; }

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public LocalDateTime getFechaHora() { return fechaHora; }
        public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public LocalDateTime getReceivedAt() { return receivedAt; }
        public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    }

    // Getters y Setters principales
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, List<ServerLog>> getLogsByServer() { return logsByServer; }
    public void setLogsByServer(Map<String, List<ServerLog>> logsByServer) { this.logsByServer = logsByServer; }

    public List<ServerLog> getRecentLogs() { return recentLogs; }
    public void setRecentLogs(List<ServerLog> recentLogs) { this.recentLogs = recentLogs; }

    public int getMaxLogsPerServer() { return maxLogsPerServer; }
    public void setMaxLogsPerServer(int maxLogsPerServer) { this.maxLogsPerServer = maxLogsPerServer; }

    public int getMaxRecentLogs() { return maxRecentLogs; }
    public void setMaxRecentLogs(int maxRecentLogs) { this.maxRecentLogs = maxRecentLogs; }

    // Métodos utilitarios
    public synchronized void addLogs(String serverId, List<Map<String, Object>> logs) {
        List<ServerLog> serverLogs = logsByServer.computeIfAbsent(serverId, k -> new ArrayList<>());
        
        for (Map<String, Object> logData : logs) {
            ServerLog serverLog = new ServerLog(serverId, logData);
            
            // Agregar a logs del servidor específico
            serverLogs.add(serverLog);
            if (serverLogs.size() > maxLogsPerServer) {
                serverLogs.remove(0); // Remover el más antiguo
            }
            
            // Agregar a logs recientes globales
            recentLogs.add(serverLog);
        }
        
        // Mantener solo los logs recientes más nuevos
        if (recentLogs.size() > maxRecentLogs) {
            recentLogs.sort((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()));
            recentLogs = new ArrayList<>(recentLogs.subList(0, maxRecentLogs));
        }
        
        this.timestamp = LocalDateTime.now();
    }

    public List<ServerLog> getLogsForServer(String serverId) {
        return logsByServer.getOrDefault(serverId, new ArrayList<>());
    }

    public List<ServerLog> getRecentLogsForServer(String serverId, int limit) {
        return logsByServer.getOrDefault(serverId, new ArrayList<>())
                .stream()
                .sorted((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()))
                .limit(limit)
                .toList();
    }

    public int getTotalLogsCount() {
        return logsByServer.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public long getErrorLogsCount() {
        return recentLogs.stream()
                .filter(log -> "ERROR".equalsIgnoreCase(log.getTipo()))
                .count();
    }
    
    /**
     * Obtiene todos los logs en formato compatible con frontend.
     */
    public List<Map<String, Object>> getAllLogs() {
        List<Map<String, Object>> allLogs = new ArrayList<>();
        
        for (ServerLog log : recentLogs) {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("server_id", log.getServerId());
            logMap.put("id", log.getLogId());
            logMap.put("level", log.getTipo());
            logMap.put("message", log.getDetalle());
            logMap.put("timestamp", log.getFechaHora().toString());
            logMap.put("receivedAt", log.getReceivedAt().toString());
            allLogs.add(logMap);
        }
        
        return allLogs;
    }
}