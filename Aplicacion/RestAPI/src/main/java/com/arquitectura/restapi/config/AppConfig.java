package com.arquitectura.restapi.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arquitectura.repositorios.LogRepository;

/**
 * Configuración de beans para la inyección de dependencias en Spring Boot.
 * Proporciona las instancias de repositorios necesarias para los controladores REST.
 * Spring Boot creará automáticamente el DataSource usando application.properties.
 */
@Configuration
@EnableAutoConfiguration
public class AppConfig {

    @Bean
    public LogRepository logRepository(DataSource dataSource) {
        return new com.arquitectura.repositorios.jdbc.JdbcLogRepository(dataSource);
    }
}
