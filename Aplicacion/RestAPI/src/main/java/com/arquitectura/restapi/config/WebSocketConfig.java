package com.arquitectura.restapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.arquitectura.restapi.websocket.LogsWebSocketHandler;
import com.arquitectura.restapi.websocket.MetricsWebSocketHandler;

/**
 * Configuración de WebSocket para streaming de métricas y logs en tiempo real.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricsWebSocketHandler metricsHandler;
    private final LogsWebSocketHandler logsHandler;

    public WebSocketConfig(MetricsWebSocketHandler metricsHandler, LogsWebSocketHandler logsHandler) {
        this.metricsHandler = metricsHandler;
        this.logsHandler = logsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // WebSocket para métricas en tiempo real
        registry.addHandler(metricsHandler, "/ws/metrics")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // WebSocket para logs en tiempo real  
        registry.addHandler(logsHandler, "/ws/logs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}