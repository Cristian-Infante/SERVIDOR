package com.arquitectura.restapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de Swagger/OpenAPI para la documentación de la REST API.
 * Proporciona una interfaz web para explorar y probar los endpoints disponibles.
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8089}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Servidor TCP - REST API")
                        .version("1.0.0")
                        .description("API REST para acceder a métricas de Prometheus y logs del servidor TCP. " +
                                   "Esta API complementa el servidor TCP principal proporcionando endpoints " +
                                   "para integración con herramientas de monitoreo como Grafana.")
                        .contact(new Contact()
                                .name("Equipo de Desarrollo")
                                .email("desarrollo@arquitectura.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor de desarrollo local")
                ));
    }
}