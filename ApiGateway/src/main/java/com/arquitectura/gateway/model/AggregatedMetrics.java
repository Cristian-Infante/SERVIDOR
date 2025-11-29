package com.arquitectura.gateway.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Métricas agregadas de múltiples servidores TCP.
 */
public class AggregatedMetrics {
    
    private LocalDateTime timestamp;
    private Map<String, ServerMetrics> serverMetrics;
    private GlobalMetrics globalMetrics;
    
    public AggregatedMetrics() {
        this.timestamp = LocalDateTime.now();
        this.serverMetrics = new ConcurrentHashMap<>();
        this.globalMetrics = new GlobalMetrics();
    }

    public static class ServerMetrics {
        private String serverId;
        private LocalDateTime lastUpdate;
        private Map<String, Object> metrics;
        private String status;

        public ServerMetrics() {
            this.metrics = new ConcurrentHashMap<>();
            this.lastUpdate = LocalDateTime.now();
        }

        public ServerMetrics(String serverId) {
            this();
            this.serverId = serverId;
        }

        // Getters y Setters
        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public LocalDateTime getLastUpdate() { return lastUpdate; }
        public void setLastUpdate(LocalDateTime lastUpdate) { this.lastUpdate = lastUpdate; }

        public Map<String, Object> getMetrics() { return metrics; }
        public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class GlobalMetrics {
        private int totalServers;
        private int connectedServers;
        private int disconnectedServers;
        private long totalConnections;
        private long totalMessages;
        private long totalErrors;

        // Getters y Setters
        public int getTotalServers() { return totalServers; }
        public void setTotalServers(int totalServers) { this.totalServers = totalServers; }

        public int getConnectedServers() { return connectedServers; }
        public void setConnectedServers(int connectedServers) { this.connectedServers = connectedServers; }

        public int getDisconnectedServers() { return disconnectedServers; }
        public void setDisconnectedServers(int disconnectedServers) { this.disconnectedServers = disconnectedServers; }

        public long getTotalConnections() { return totalConnections; }
        public void setTotalConnections(long totalConnections) { this.totalConnections = totalConnections; }

        public long getTotalMessages() { return totalMessages; }
        public void setTotalMessages(long totalMessages) { this.totalMessages = totalMessages; }

        public long getTotalErrors() { return totalErrors; }
        public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }
    }

    // Getters y Setters principales
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, ServerMetrics> getServerMetrics() { return serverMetrics; }
    public void setServerMetrics(Map<String, ServerMetrics> serverMetrics) { this.serverMetrics = serverMetrics; }

    public GlobalMetrics getGlobalMetrics() { return globalMetrics; }
    public void setGlobalMetrics(GlobalMetrics globalMetrics) { this.globalMetrics = globalMetrics; }

    // Métodos utilitarios
    public void addServerMetrics(String serverId, Map<String, Object> metrics) {
        ServerMetrics serverMetric = this.serverMetrics.computeIfAbsent(serverId, ServerMetrics::new);
        serverMetric.setMetrics(metrics);
        serverMetric.setLastUpdate(LocalDateTime.now());
        serverMetric.setStatus("ACTIVE");
        updateGlobalMetrics();
    }

    public void markServerActive(String serverId) {
        ServerMetrics serverMetric = this.serverMetrics.computeIfAbsent(serverId, ServerMetrics::new);
        serverMetric.setStatus("ACTIVE");
        serverMetric.setLastUpdate(LocalDateTime.now());
        updateGlobalMetrics();
    }

    public void markServerInactive(String serverId) {
        ServerMetrics serverMetric = this.serverMetrics.get(serverId);
        if (serverMetric != null) {
            serverMetric.setStatus("INACTIVE");
            updateGlobalMetrics();
        }
    }

    private void updateGlobalMetrics() {
        globalMetrics.setTotalServers(serverMetrics.size());
        
        long connected = serverMetrics.values().stream()
                .filter(sm -> "ACTIVE".equals(sm.getStatus()))
                .count();
        
        globalMetrics.setConnectedServers((int) connected);
        globalMetrics.setDisconnectedServers((int) (serverMetrics.size() - connected));

        // Sumar métricas de todos los servidores
        long totalConnections = serverMetrics.values().stream()
                .filter(sm -> "ACTIVE".equals(sm.getStatus()))
                .mapToLong(sm -> {
                    Object connections = sm.getMetrics().get("tcp_server_connections_active");
                    return connections instanceof Number ? ((Number) connections).longValue() : 0L;
                })
                .sum();
        
        globalMetrics.setTotalConnections(totalConnections);
    }
}