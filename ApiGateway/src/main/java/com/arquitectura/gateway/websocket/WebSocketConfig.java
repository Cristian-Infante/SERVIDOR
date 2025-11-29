package com.arquitectura.gateway.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * Configuración de WebSocket para el API Gateway.
 * Permite comunicación bidireccional en tiempo real con clientes frontend.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilitar broker simple para enviar mensajes a suscriptores
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefijo para mensajes enviados desde el cliente al servidor
        config.setApplicationDestinationPrefixes("/app");
        
        // Prefijo para mensajes de usuario específico
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint principal para conexiones WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Endpoint específico para métricas agregadas
        registry.addEndpoint("/ws/metrics")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Endpoint específico para logs agregados
        registry.addEndpoint("/ws/logs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}