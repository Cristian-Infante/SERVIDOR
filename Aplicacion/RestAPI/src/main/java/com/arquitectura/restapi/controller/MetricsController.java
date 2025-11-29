package com.arquitectura.restapi.controller;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST para exponer las métricas de Prometheus.
 * Las métricas son las mismas que ya se están recolectando en ServerMetrics
 * y se exponen en formato Prometheus para Grafana.
 */
@RestController
@RequestMapping("/api/metrics")
@Tag(name = "Métricas", description = "Endpoints para acceder a las métricas del servidor TCP")
public class MetricsController {

    /**
     * Endpoint que expone todas las métricas en formato Prometheus.
     * 
     * GET /api/metrics
     * 
     * @return Las métricas en formato texto plano compatible con Prometheus
     */
    @Operation(
            summary = "Obtener métricas de Prometheus",
            description = "Retorna todas las métricas del servidor TCP en formato compatible con Prometheus/Grafana. " +
                         "Incluye métricas de conexiones activas, mensajes procesados, tiempo de respuesta, etc."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Métricas obtenidas exitosamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping(produces = TextFormat.CONTENT_TYPE_004)
    public ResponseEntity<String> getMetrics() {
        try {
            Writer writer = new StringWriter();
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(TextFormat.CONTENT_TYPE_004));
            
            return new ResponseEntity<>(writer.toString(), headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar métricas: " + e.getMessage());
        }
    }

    /**
     * Endpoint de salud simplificado para verificar que el servicio de métricas está disponible.
     * 
     * GET /api/metrics/health
     * 
     * @return Estado del servicio
     */
    @Operation(
            summary = "Verificar estado del servicio de métricas",
            description = "Endpoint de health check para verificar que el servicio de métricas está funcionando correctamente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Servicio funcionando correctamente")
    })
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Metrics service is running");
    }

    /**
     * Endpoint de debug para verificar configuración de WebSocket
     */
    @GetMapping("/websocket/debug")
    public ResponseEntity<?> debugWebSocket() {
        java.util.Map<String, Object> debug = new java.util.HashMap<>();
        debug.put("server_port", "8089");
        debug.put("websocket_endpoint", "/ws/metrics");
        debug.put("sockjs_enabled", true);
        debug.put("cors_enabled", true);
        debug.put("full_url", "http://localhost:8089/ws/metrics");
        debug.put("test_sockjs", "new SockJS('http://localhost:8089/ws/metrics')");
        debug.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(debug);
    }

    /**
     * Endpoint que devuelve métricas en formato JSON para integración con API Gateway.
     * 
     * GET /api/metrics/json
     * 
     * @return Métricas en formato JSON
     */
    @Operation(
            summary = "Obtener métricas en formato JSON",
            description = "Retorna las métricas del servidor en formato JSON para integración con API Gateway."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Métricas JSON obtenidas exitosamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/json")
    public ResponseEntity<?> getMetricsAsJson() {
        try {
            // Simular métricas del servidor (en un caso real, obtener de Prometheus o métricas reales)
            java.util.Map<String, Object> metrics = new java.util.HashMap<>();
            
            // Métricas básicas del servidor
            metrics.put("connections", getCurrentConnections());
            metrics.put("response_time_ms", getAverageResponseTime());
            metrics.put("memory_usage_percent", getMemoryUsagePercent());
            metrics.put("cpu_usage_percent", getCpuUsagePercent());
            metrics.put("requests_per_second", getRequestsPerSecond());
            metrics.put("errors_count", getErrorsCount());
            metrics.put("uptime_seconds", getUptimeSeconds());
            metrics.put("timestamp", System.currentTimeMillis());
            metrics.put("server_id", "localhost:8089");
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", "Error al obtener métricas: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // Métodos helper para obtener métricas (simuladas por ahora)
    private int getCurrentConnections() {
        return 45 + (int)(Math.random() * 50);
    }
    
    private int getAverageResponseTime() {
        return 20 + (int)(Math.random() * 100);
    }
    
    private int getMemoryUsagePercent() {
        return 50 + (int)(Math.random() * 40);
    }
    
    private int getCpuUsagePercent() {
        return 10 + (int)(Math.random() * 80);
    }
    
    private int getRequestsPerSecond() {
        return 100 + (int)(Math.random() * 200);
    }
    
    private int getErrorsCount() {
        return (int)(Math.random() * 5);
    }
    
    private long getUptimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * Endpoint de información sobre WebSocket de métricas.
     * 
     * GET /api/metrics/websocket/info
     * 
     * @return Información del WebSocket
     */
    @Operation(
            summary = "Información del WebSocket de métricas",
            description = "Proporciona información sobre cómo conectarse al WebSocket para recibir métricas en tiempo real."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Información del WebSocket obtenida correctamente")
    })
    @GetMapping("/websocket/info")
    public ResponseEntity<?> getWebSocketInfo() {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("websocket_url", "ws://localhost:8089/ws/metrics");
        info.put("sockjs_url", "http://localhost:8089/ws/metrics");
        info.put("update_interval", "5 seconds");
        info.put("message_format", "JSON");
        info.put("cors_enabled", true);
        info.put("connection_type", "WebSocket with SockJS fallback");
        info.put("description", "Conecta para recibir métricas de Prometheus en tiempo real del servidor TCP");
        
        // Ejemplo de mensaje que se recibirá
        java.util.Map<String, Object> sampleMessage = new java.util.HashMap<>();
        sampleMessage.put("timestamp", "2025-11-25T18:30:00Z");
        sampleMessage.put("tcp_server_connections_active", 5);
        sampleMessage.put("tcp_server_total_connections", 150);
        sampleMessage.put("tcp_server_bytes_sent", 1024000);
        sampleMessage.put("tcp_server_bytes_received", 2048000);
        info.put("sample_message", sampleMessage);
        
        // Ejemplo de código JavaScript
        info.put("js_example", "const socket = new SockJS('http://localhost:8089/ws/metrics'); socket.onmessage = (event) => { const metrics = JSON.parse(event.data); console.log('Métricas recibidas:', metrics); };");
        
        return ResponseEntity.ok(info);
    }
}
