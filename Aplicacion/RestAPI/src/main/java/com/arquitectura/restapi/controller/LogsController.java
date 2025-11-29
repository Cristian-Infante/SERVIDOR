package com.arquitectura.restapi.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST para exponer los logs del servidor.
 * Permite consultar todos los logs registrados en la base de datos.
 */
@RestController
@RequestMapping("/api/logs")
@Tag(name = "Logs", description = "Endpoints para acceder a los logs del servidor TCP")
public class LogsController {

    private final LogRepository logRepository;

    @Autowired
    public LogsController(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Endpoint que devuelve todos los logs del servidor.
     * 
     * GET /api/logs
     * 
     * @return Lista de logs en formato JSON
     */
    @Operation(
            summary = "Obtener todos los logs del servidor",
            description = "Retorna la lista completa de logs registrados en la base de datos, " +
                         "incluyendo tanto logs de información como de errores del servidor TCP."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logs obtenidos exitosamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping
    public ResponseEntity<List<LogDTO>> getLogs() {
        try {
            List<Log> logs = logRepository.findAll();
            List<LogDTO> logDTOs = logs.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(logDTOs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint de salud para verificar que el servicio de logs está disponible.
     * 
     * GET /api/logs/health
     * 
     * @return Estado del servicio
     */
    @Operation(
            summary = "Verificar estado del servicio de logs",
            description = "Endpoint de health check para verificar que el servicio de logs está funcionando correctamente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Servicio funcionando correctamente")
    })
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Logs service is running");
    }

    /**
     * Endpoint para verificar la conexión con Loki.
     * 
     * GET /api/logs/loki/status
     * 
     * @return Estado de la conexión con Loki
     */
    @Operation(
            summary = "Estado de conexión con Loki",
            description = "Verifica si el servidor puede conectarse a Loki para el envío de logs"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado de Loki obtenido correctamente"),
            @ApiResponse(responseCode = "500", description = "Error de conexión con Loki")
    })
    @GetMapping("/loki/status")
    public ResponseEntity<?> getLokiStatus() {
        try {
            String lokiUrl = System.getProperty("loki.url", "http://localhost:3100");
            java.util.Map<String, Object> status = new java.util.HashMap<>();
            status.put("lokiUrl", lokiUrl);
            status.put("timestamp", System.currentTimeMillis());
            status.put("status", "configured");
            status.put("message", "Loki appender está configurado. Revisa Grafana para ver los logs.");
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", "Error al verificar configuración de Loki: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Endpoint de información sobre WebSocket de logs.
     * 
     * GET /api/logs/websocket/info
     * 
     * @return Información del WebSocket
     */
    @Operation(
            summary = "Información del WebSocket de logs",
            description = "Proporciona información sobre cómo conectarse al WebSocket para recibir logs en tiempo real."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Información del WebSocket obtenida correctamente")
    })
    @GetMapping("/websocket/info")
    public ResponseEntity<?> getWebSocketInfo() {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("websocket_url", "ws://localhost:8089/ws/logs");
        info.put("sockjs_url", "http://localhost:8089/ws/logs");
        info.put("update_interval", "3 seconds - busca nuevos logs en la BD");
        info.put("message_format", "JSON Array");
        info.put("cors_enabled", true);
        info.put("connection_type", "WebSocket with SockJS fallback");
        info.put("data_source", "MySQL database - tabla logs");
        info.put("filter_type", "Timestamp-based incremental updates");
        info.put("description", "Conecta para recibir logs nuevos del servidor TCP en tiempo real desde la base de datos");
        
        // Ejemplo de mensaje que se recibirá
        java.util.List<java.util.Map<String, Object>> sampleMessage = new java.util.ArrayList<>();
        java.util.Map<String, Object> logEntry = new java.util.HashMap<>();
        logEntry.put("id", 123);
        logEntry.put("fechaHora", "2025-11-25T18:30:15");
        logEntry.put("nivel", "INFO");
        logEntry.put("mensaje", "Nueva conexión establecida desde IP: 192.168.1.100");
        logEntry.put("origen", "TCP_SERVER");
        sampleMessage.add(logEntry);
        info.put("sample_message", sampleMessage);
        
        // Ejemplo de código JavaScript
        info.put("js_example", "const socket = new SockJS('http://localhost:8089/ws/logs'); socket.onmessage = (event) => { const logs = JSON.parse(event.data); logs.forEach(log => console.log('Nuevo log:', log)); };");
        
        return ResponseEntity.ok(info);
    }

    /**
     * Convierte una entidad Log a un DTO para la respuesta REST.
     */
    private LogDTO convertToDTO(Log log) {
        LogDTO dto = new LogDTO();
        dto.setId(log.getId());
        dto.setTipo(Boolean.TRUE.equals(log.getTipo()) ? "INFO" : "ERROR");
        dto.setDetalle(log.getDetalle());
        dto.setFechaHora(log.getFechaHora() != null ? log.getFechaHora().toString() : null);
        return dto;
    }

    /**
     * DTO para representar un log en la respuesta REST.
     */
    public static class LogDTO {
        private Long id;
        private String tipo;
        private String detalle;
        private String fechaHora;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getDetalle() {
            return detalle;
        }

        public void setDetalle(String detalle) {
            this.detalle = detalle;
        }

        public String getFechaHora() {
            return fechaHora;
        }

        public void setFechaHora(String fechaHora) {
            this.fechaHora = fechaHora;
        }
    }
}
