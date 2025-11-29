package com.arquitectura.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API Gateway para agregación de múltiples servidores TCP.
 * 
 * Funcionalidades principales:
 * - Conexión a múltiples servidores TCP mediante WebSockets
 * - Agregación de métricas y logs de todos los servidores
 * - Distribución de datos a frontends mediante WebSockets
 * - Gestión de conexiones dinámicas por IP:Puerto
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}