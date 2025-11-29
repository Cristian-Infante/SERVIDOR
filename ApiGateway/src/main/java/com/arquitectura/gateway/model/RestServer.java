package com.arquitectura.gateway.model;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Representa un servidor REST conectado al gateway.
 * 
 * Cada servidor expone una API REST que el gateway consume
 * para obtener datos, métricas y establecer conexiones WebSocket.
 */
public class RestServer {
    
    private String id;
    private String host;
    private int restApiPort;
    private String status;
    private LocalDateTime connectedAt;
    private LocalDateTime lastHeartbeat;
    private String version;
    public RestServer() {}

    public RestServer(String host, int restApiPort) {
        this.host = host;
        this.restApiPort = restApiPort;
        this.id = generateId(host, restApiPort);
        this.status = ServerStatus.DISCONNECTED.name();
        this.connectedAt = LocalDateTime.now();
    }

    private String generateId(String host, int port) {
        return String.format("%s:%d", host, port);
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getRestApiPort() { return restApiPort; }
    public void setRestApiPort(int restApiPort) { this.restApiPort = restApiPort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }



    // URLs de WebSocket
    public String getMetricsWebSocketUrl() {
        return String.format("http://%s:%d/ws/metrics", host, restApiPort);
    }

    public String getLogsWebSocketUrl() {
        return String.format("http://%s:%d/ws/logs", host, restApiPort);
    }

    public String getHealthCheckUrl() {
        return String.format("http://%s:%d/actuator/health", host, restApiPort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestServer restServer = (RestServer) o;
        return Objects.equals(id, restServer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("RestServer{id='%s', host='%s', port=%d, status='%s'}", 
                           id, host, restApiPort, status);
    }

    public enum ServerStatus {
        CONNECTED, DISCONNECTED, CONNECTING, ERROR
    }
}