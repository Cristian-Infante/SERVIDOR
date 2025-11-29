package com.arquitectura.gateway.controller;

import com.arquitectura.gateway.config.ServersConfiguration;
import com.arquitectura.gateway.service.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para gestionar información de servidores configurados.
 * Los servidores se definen en application.properties y se conectan automáticamente.
 */
@RestController
@RequestMapping("/api/servers")
@Tag(name = "Servidores", description = "Información sobre servidores REST monitoreados")
@CrossOrigin(origins = "*")
public class ServersController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServersController.class);
    
    @Autowired
    private ServersConfiguration serversConfig;
    
    @Autowired
    private WebSocketClientService webSocketClientService;

    @Operation(
        summary = "Obtener lista de servidores configurados",
        description = "Retorna la lista completa de servidores REST configurados en application.properties"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de servidores obtenida exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                        "success": true,
                        "totalServers": 1,
                        "enabledServers": 1,
                        "servers": [
                            {
                                "id": "rest-server-1",
                                "name": "Servidor REST Principal",
                                "host": "localhost",
                                "port": 8089,
                                "enabled": true,
                                "baseUrl": "http://localhost:8089"
                            }
                        ]
                    }
                    """)
            )
        )
    })
    @GetMapping("")
    public ResponseEntity<?> getServers() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalServers", serversConfig.getServers().size());
            response.put("configuredServers", serversConfig.getAllServers().size());
            response.put("servers", serversConfig.getServers());
            response.put("dataCollection", serversConfig.getDataCollection());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error obteniendo lista de servidores: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error interno del servidor");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Operation(
        summary = "Obtener estado de recolección de datos",
        description = "Retorna el estado actual de la recolección de datos de todos los servidores"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Estado de recolección obtenido exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                        "success": true,
                        "totalServers": 1,
                        "enabledServers": 1,
                        "activeCollections": 1,
                        "servers": {
                            "rest-server-1": {
                                "config": {
                                    "id": "rest-server-1",
                                    "name": "Servidor REST Principal",
                                    "host": "localhost",
                                    "port": 8089,
                                    "enabled": true
                                },
                                "collecting": true,
                                "status": {
                                    "serverId": "rest-server-1",
                                    "active": true,
                                    "lastSuccessfulMetrics": "2023-11-26T10:30:00",
                                    "lastSuccessfulLogs": "2023-11-26T10:30:00"
                                }
                            }
                        }
                    }
                    """)
            )
        )
    })
    @GetMapping("/status")
    public ResponseEntity<?> getServersStatus() {
        try {
            Map<String, Object> status = webSocketClientService.getServersStatus();
            status.put("success", true);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error obteniendo estado de servidores: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error interno del servidor");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Operation(
        summary = "Obtener información de un servidor específico",
        description = "Retorna la configuración y estado de un servidor específico por ID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Información del servidor obtenida exitosamente"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Servidor no encontrado"
        )
    })
    @GetMapping("/{serverId}")
    public ResponseEntity<?> getServerById(@PathVariable String serverId) {
        try {
            ServersConfiguration.ServerConfig server = serversConfig.findServerById(serverId);
            
            if (server == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Servidor no encontrado");
                error.put("serverId", serverId);
                
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("server", server);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error obteniendo servidor {}: {}", serverId, e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error interno del servidor");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Operation(
        summary = "Obtener configuración de recolección de datos",
        description = "Retorna la configuración actual para la recolección automática de datos"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Configuración obtenida exitosamente",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                        "success": true,
                        "dataCollection": {
                            "metricsInterval": 5000,
                            "logsInterval": 10000,
                            "autoStart": true
                        }
                    }
                    """)
            )
        )
    })
    @GetMapping("/config")
    public ResponseEntity<?> getDataCollectionConfig() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dataCollection", serversConfig.getDataCollection());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error obteniendo configuración: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error interno del servidor");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Operation(
        summary = "Iniciar recolección de datos",
        description = "Inicia la recolección automática de métricas y logs de todos los servidores habilitados"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Recolección iniciada exitosamente"
        )
    })
    @PostMapping("/start-collection")
    public ResponseEntity<?> startCollection() {
        try {
            for (ServersConfiguration.ServerConfig server : serversConfig.getAllServers()) {
                webSocketClientService.startCollectionForServer(server);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recolección iniciada para todos los servidores habilitados");
            response.put("serversStarted", serversConfig.getAllServers().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error iniciando recolección: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error iniciando recolección");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Operation(
        summary = "Detener recolección de datos",
        description = "Detiene la recolección automática de métricas y logs de todos los servidores"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Recolección detenida exitosamente"
        )
    })
    @PostMapping("/stop-collection")
    public ResponseEntity<?> stopCollection() {
        try {
            for (ServersConfiguration.ServerConfig server : serversConfig.getAllServers()) {
                webSocketClientService.stopCollectionForServer(server.getId());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recolección detenida para todos los servidores");
            response.put("serversStopped", serversConfig.getAllServers().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error deteniendo recolección: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error deteniendo recolección");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
}