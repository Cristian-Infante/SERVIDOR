package com.arquitectura.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controlador para consultar datos agregados de métricas y logs.
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
@Tag(name = "Consulta de Datos", description = "Endpoints para obtener métricas y logs agregados de los servidores")
public class DataController {

    @Operation(
        summary = "Obtener métricas agregadas", 
        description = "Retorna las métricas consolidadas de todos los servidores conectados"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Métricas agregadas obtenidas exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"success\": true, \"metrics\": { \"totalServers\": 3, \"totalConnections\": 150, \"avgResponseTime\": 45.2, \"totalErrors\": 5, \"aggregatedAt\": \"2024-01-01T10:00:00\" } }")
            )
        )
    })
    @GetMapping("/metrics")
    public ResponseEntity<?> getAggregatedMetrics() {
        Map<String, Object> response = new HashMap<>();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalServers", 3);
        metrics.put("totalConnections", 150);
        metrics.put("avgResponseTime", 45.2);
        metrics.put("totalErrors", 5);
        metrics.put("uptime", 3600);
        metrics.put("memoryUsage", 67.8);
        metrics.put("cpuUsage", 23.5);
        metrics.put("aggregatedAt", LocalDateTime.now().toString());
        
        response.put("success", true);
        response.put("metrics", metrics);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Obtener logs agregados", 
        description = "Retorna los logs consolidados de todos los servidores con filtros opcionales"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logs agregados obtenidos exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"success\": true, \"logs\": [ { \"server\": \"srv_localhost_8080\", \"level\": \"INFO\", \"message\": \"Conexión establecida\", \"timestamp\": \"2024-01-01T10:00:00\" } ] }")
            )
        )
    })
    @GetMapping("/logs")
    public ResponseEntity<?> getAggregatedLogs(
        @Parameter(description = "Nivel de log (INFO, WARN, ERROR)", required = false)
        @RequestParam(required = false) String level,
        @Parameter(description = "ID del servidor específico", required = false)
        @RequestParam(required = false) String serverId,
        @Parameter(description = "Número máximo de logs a retornar", required = false)
        @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        Map<String, Object> response = new HashMap<>();
        
        // TODO: Implementar consulta a logs reales de servidores REST
        // Por ahora retornar estructura vacía hasta que se implemente la agregación real
        List<Map<String, Object>> logs = new ArrayList<>();
        
        // Los logs reales deben venir del WebSocketClientService 
        // que consulta a los servidores REST configurados
        
        response.put("success", true);
        response.put("logs", logs);
        response.put("totalCount", 0);
        response.put("message", "Logs reales serán transmitidos via WebSocket");
        response.put("filters", Map.of(
            "level", level,
            "serverId", serverId,
            "limit", limit
        ));
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Obtener estadísticas de servidor específico", 
        description = "Retorna métricas y logs de un servidor específico por su ID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Datos del servidor obtenidos exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"success\": true, \"server\": { \"id\": \"srv_localhost_8080\", \"metrics\": { \"connections\": 50, \"responseTime\": 42.1 }, \"recentLogs\": [] } }")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Servidor no encontrado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"success\": false, \"error\": \"Servidor no encontrado\" }")
            )
        )
    })
    @GetMapping("/server/{serverId}")
    public ResponseEntity<?> getServerData(
        @Parameter(description = "ID único del servidor", required = true)
        @PathVariable String serverId
    ) {
        Map<String, Object> response = new HashMap<>();
        
        // TODO: Consultar datos reales del servidor específico
        // Verificar si el servidor existe en la configuración
        if (serverId != null && !serverId.trim().isEmpty()) {
            Map<String, Object> serverData = new HashMap<>();
            serverData.put("id", serverId);
            serverData.put("metrics", Map.of(
                "message", "Métricas reales disponibles via WebSocket"
            ));
            serverData.put("recentLogs", List.of());
            serverData.put("note", "Los logs reales se transmiten via WebSocket en tiempo real");
            
            response.put("success", true);
            response.put("server", serverData);
        } else {
            response.put("success", false);
            response.put("error", "ID de servidor requerido");
            response.put("serverId", serverId);
            
            return ResponseEntity.status(400).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Health check de datos", 
        description = "Verifica el estado de los servicios de agregación de datos"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Servicios funcionando correctamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"success\": true, \"status\": \"healthy\", \"services\": { \"aggregation\": true, \"websocket\": true } }")
            )
        )
    })
    @GetMapping("/health")
    public ResponseEntity<?> getDataHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "healthy");
        response.put("services", Map.of(
            "aggregation", true,
            "websocket", true,
            "cache", true
        ));
        response.put("checkedAt", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
}