package com.arquitectura.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración de caché usando Caffeine.
 * 
 * Proporciona caché para:
 * - Datos de servidores
 * - Métricas agregadas
 * - Logs temporales
 * - Estado de conexiones
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configuración del administrador de caché Caffeine.
     * 
     * @return CacheManager configurado con Caffeine
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configuración global de Caffeine
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats());
        
        // Cachés específicos (usando Arrays.asList para crear una colección)
        cacheManager.setCacheNames(java.util.Arrays.asList("serverData", "metrics", "logs", "connections"));
        
        return cacheManager;
    }

    /**
     * Configuración específica de Caffeine para diferentes tipos de caché.
     * 
     * @return Caffeine builder configurado
     */
    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats();
    }
}