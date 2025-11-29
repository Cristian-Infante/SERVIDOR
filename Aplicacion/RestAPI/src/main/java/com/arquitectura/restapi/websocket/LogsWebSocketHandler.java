package com.arquitectura.restapi.websocket;

import com.arquitectura.entidades.Log;
import com.arquitectura.repositorios.LogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handler de WebSocket para streaming de logs en tiempo real.
 * Envía los logs nuevos cada 3 segundos a todos los clientes conectados.
 */
@Component
public class LogsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = Logger.getLogger(LogsWebSocketHandler.class.getName());
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;
    private LocalDateTime lastLogTime = LocalDateTime.now().minusHours(1);

    public LogsWebSocketHandler(LogRepository logRepository) {
        this.logRepository = logRepository;
        this.objectMapper = new ObjectMapper();
        
        // Inicializar con timestamp muy antiguo para capturar todos los logs inicialmente
        this.lastLogTime = LocalDateTime.now().minusDays(1);
        
        LOGGER.info("LogsWebSocketHandler inicializado. Buscará logs después de: " + this.lastLogTime);
        
        // Iniciar el envío periódico de logs nuevos
        scheduler.scheduleAtFixedRate(this::broadcastNewLogs, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        LOGGER.info("Cliente conectado a WebSocket de logs. Total clientes: " + sessions.size());
        
        // Enviar TODOS los logs existentes al conectarse
        sendAllExistingLogsToSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        LOGGER.info("Cliente desconectado de WebSocket de logs. Total clientes: " + sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        LOGGER.warning("Error en WebSocket de logs: " + exception.getMessage());
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        if ("GET_ALL_LOGS".equals(payload)) {
            sendAllExistingLogsToSession(session);
        } else if ("PING".equals(payload)) {
            session.sendMessage(new TextMessage("PONG"));
        }
    }

    /**
     * Envía logs nuevos a todos los clientes conectados.
     */
    private void broadcastNewLogs() {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            List<Log> newLogs = getNewLogs();
            
            if (newLogs != null && !newLogs.isEmpty()) {
                String jsonMessage = createLogsMessage(newLogs, "new_logs");
                
                LOGGER.info("Enviando " + newLogs.size() + " logs nuevos a " + sessions.size() + " clientes");
                
                // Enviar a todos los clientes conectados
                broadcastMessage(jsonMessage);
                
                // Actualizar timestamp del último log procesado de forma thread-safe
                synchronized(this) {
                    newLogs.stream()
                           .map(Log::getFechaHora)
                           .max(LocalDateTime::compareTo)
                           .ifPresent(maxTime -> {
                               if (maxTime.isAfter(lastLogTime)) {
                                   lastLogTime = maxTime;
                               }
                           });
                }
            }
            // No enviar mensajes informativos constantemente si no hay logs nuevos
            // Solo logs cuando realmente hay contenido nuevo
        } catch (Exception e) {
            LOGGER.severe("Error obteniendo logs nuevos: " + e.getMessage());
        }
    }

    /**
     * Envía todos los logs existentes a una sesión específica al conectarse.
     */
    private void sendAllExistingLogsToSession(WebSocketSession session) {
        try {
            List<Log> allLogs = getAllLogs();
            if (allLogs != null && !allLogs.isEmpty()) {
                String jsonMessage = createLogsMessage(allLogs, "existing_logs");
                session.sendMessage(new TextMessage(jsonMessage));
                
                LOGGER.info("Enviados " + allLogs.size() + " logs existentes al cliente recién conectado");
                
                // Actualizar lastLogTime al timestamp del log más reciente
                allLogs.stream()
                       .map(Log::getFechaHora)
                       .max(LocalDateTime::compareTo)
                       .ifPresent(maxTime -> {
                           synchronized(this) {
                               if (maxTime.isAfter(lastLogTime)) {
                                   lastLogTime = maxTime;
                                   LOGGER.info("Actualizado lastLogTime a: " + lastLogTime);
                               }
                           }
                       });
            } else {
                // Si no hay logs, enviar mensaje informativo
                String infoMessage = createInfoMessage("No hay logs almacenados en la base de datos");
                session.sendMessage(new TextMessage(infoMessage));
                LOGGER.info("No hay logs existentes para enviar al cliente");
            }
        } catch (Exception e) {
            LOGGER.warning("Error enviando logs existentes a sesión: " + e.getMessage());
        }
    }

    /**
     * Obtiene logs nuevos desde el último timestamp procesado.
     */
    private List<Log> getNewLogs() {
        return logRepository.findByFechaHoraAfter(lastLogTime);
    }



    /**
     * Crea un mensaje JSON con los logs y metadata.
     */
    private String createLogsMessage(List<Log> logs, String messageType) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", messageType);
            message.put("timestamp", System.currentTimeMillis());
            message.put("count", logs.size());
            
            // Convertir logs a DTOs
            List<Map<String, Object>> logDTOs = logs.stream()
                    .map(this::convertToDTO)
                    .toList();
            
            message.put("logs", logDTOs);
            
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOGGER.severe("Error creando mensaje de logs: " + e.getMessage());
            return "{\"type\":\"error\",\"message\":\"Error procesando logs\"}";
        }
    }

    /**
     * Convierte un Log a DTO para JSON.
     */
    private Map<String, Object> convertToDTO(Log log) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", log.getId());
        dto.put("tipo", Boolean.TRUE.equals(log.getTipo()) ? "INFO" : "ERROR");
        dto.put("detalle", log.getDetalle());
        dto.put("fechaHora", log.getFechaHora() != null ? log.getFechaHora().toString() : null);
        return dto;
    }
    
    /**
     * Obtiene todos los logs de la base de datos.
     */
    private List<Log> getAllLogs() {
        try {
            return logRepository.findAll();
        } catch (Exception e) {
            LOGGER.warning("Error obteniendo todos los logs: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Crea un mensaje informativo para el cliente.
     */
    private String createInfoMessage(String message) {
        try {
            Map<String, Object> infoMessage = new HashMap<>();
            infoMessage.put("type", "info");
            infoMessage.put("timestamp", System.currentTimeMillis());
            infoMessage.put("message", message);
            return objectMapper.writeValueAsString(infoMessage);
        } catch (Exception e) {
            LOGGER.severe("Error creando mensaje informativo: " + e.getMessage());
            return "{\"type\":\"error\",\"message\":\"Error procesando mensaje\"}";
        }
    }
    
    /**
     * Envía un mensaje a todos los clientes conectados.
     */
    private void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    LOGGER.warning("Error enviando mensaje a cliente: " + e.getMessage());
                    sessions.remove(session);
                }
            }
        }
    }
}