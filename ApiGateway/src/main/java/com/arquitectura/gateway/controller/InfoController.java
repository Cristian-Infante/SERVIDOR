package com.arquitectura.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para información sobre WebSockets y documentación adicional del API Gateway.
 */
@RestController
@RequestMapping("/api/info")
@CrossOrigin(origins = "*")
@Tag(name = "WebSocket Info", description = "Información sobre conexiones WebSocket disponibles y documentación")
public class InfoController {

    @Operation(
        summary = "Información de WebSockets", 
        description = "Obtiene la lista de endpoints WebSocket disponibles para conexión"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de endpoints WebSocket",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"websockets\": [ { \"endpoint\": \"/ws/metrics-aggregated\", \"description\": \"Métricas agregadas de todos los servidores\" }, { \"endpoint\": \"/ws/logs-aggregated\", \"description\": \"Logs agregados de todos los servidores\" } ] }")
            )
        )
    })
    @GetMapping("/websockets")
    public ResponseEntity<?> getWebSocketInfo() {
        Map<String, Object> websockets = new HashMap<>();
        websockets.put("websockets", List.of(
            Map.of("endpoint", "/ws/metrics-aggregated", 
                   "description", "Métricas agregadas de todos los servidores",
                   "url", "ws://localhost:8090/gateway/ws/metrics-aggregated"),
            Map.of("endpoint", "/ws/logs-aggregated", 
                   "description", "Logs agregados de todos los servidores", 
                   "url", "ws://localhost:8090/gateway/ws/logs-aggregated"),
            Map.of("endpoint", "/ws/server/{serverId}", 
                   "description", "Datos específicos de un servidor", 
                   "url", "ws://localhost:8090/gateway/ws/server/{serverId}")
        ));
        
        return ResponseEntity.ok(websockets);
    }

    @Operation(
        summary = "Estadísticas del Gateway", 
        description = "Obtiene estadísticas generales del API Gateway"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Estadísticas del sistema",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"version\": \"1.0.0\", \"uptime\": 3600, \"features\": [\"TCP Connection Management\", \"Data Aggregation\", \"WebSocket Broadcasting\"] }")
            )
        )
    })
    @GetMapping("/status")
    public ResponseEntity<?> getGatewayStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("version", "1.0.0");
        status.put("status", "running");
        status.put("uptime", System.currentTimeMillis() / 1000);
        status.put("features", List.of(
            "TCP Connection Management",
            "Real-time Data Aggregation", 
            "WebSocket Broadcasting",
            "Health Monitoring",
            "Multi-server Support"
        ));
        
        return ResponseEntity.ok(status);
    }

    @Operation(
        summary = "Documentación de uso", 
        description = "Guía básica para usar el API Gateway"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Guía de uso del API",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{ \"steps\": [ \"1. Conectar servidor con POST /api/servers/connect\", \"2. Verificar conexión con GET /api/servers\", \"3. Conectar WebSocket para datos en tiempo real\" ] }")
            )
        )
    })
    @GetMapping("/usage")
    public ResponseEntity<?> getUsageGuide() {
        Map<String, Object> guide = new HashMap<>();
        guide.put("steps", List.of(
            "1. Conectar servidor: POST /api/servers/connect con host y puerto",
            "2. Verificar conexión: GET /api/servers para ver servidores activos", 
            "3. Conectar WebSocket: ws://localhost:8090/gateway/ws/metrics-aggregated",
            "4. Monitorear datos: Recibir métricas y logs en tiempo real",
            "5. Desconectar: DELETE /api/servers/disconnect cuando sea necesario"
        ));
        
        guide.put("websocket_examples", Map.of(
            "connect", "const ws = new WebSocket('ws://localhost:8090/gateway/ws/metrics-aggregated');",
            "listen", "ws.onmessage = (event) => { const data = JSON.parse(event.data); console.log(data); };"
        ));
        
        return ResponseEntity.ok(guide);
    }
}