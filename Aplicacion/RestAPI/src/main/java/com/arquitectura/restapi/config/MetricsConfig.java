package com.arquitectura.restapi.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.exporter.common.TextFormat;

/**
 * Configuración para integrar las métricas del servidor TCP con Spring Boot Actuator.
 * Sobrescribe el endpoint /actuator/prometheus para incluir métricas del servidor TCP.
 */
@Configuration
public class MetricsConfig {

    /**
     * Controlador que sobrescribe el endpoint /actuator/prometheus estándar
     * para incluir las métricas del servidor TCP
     */
    @RestController
    public static class CustomPrometheusController {
        
        private final HttpClient httpClient;
        private final PrometheusScrapeEndpoint prometheusScrapeEndpoint;
        
        public CustomPrometheusController(PrometheusScrapeEndpoint prometheusScrapeEndpoint) {
            this.prometheusScrapeEndpoint = prometheusScrapeEndpoint;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        }
        
        @GetMapping(value = "/actuator/prometheus", produces = TextFormat.CONTENT_TYPE_004)
        public ResponseEntity<String> prometheus() {
            try {
                // Obtener métricas básicas de Spring Boot usando el endpoint original
                org.springframework.boot.actuate.endpoint.web.WebEndpointResponse<String> response = 
                    prometheusScrapeEndpoint.scrape(
                        org.springframework.boot.actuate.metrics.export.prometheus.TextOutputFormat.CONTENT_TYPE_004, 
                        java.util.Set.of());
                String springBootMetrics = response.getBody();
                
                // Obtener métricas personalizadas del servidor TCP
                String tcpMetrics = getTcpServerMetrics();
                
                // Combinar ambas métricas
                String combinedMetrics;
                if (tcpMetrics != null && !tcpMetrics.isEmpty()) {
                    combinedMetrics = springBootMetrics + "\n" + tcpMetrics;
                } else {
                    combinedMetrics = springBootMetrics;
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(TextFormat.CONTENT_TYPE_004));
                
                return new ResponseEntity<>(combinedMetrics, headers, HttpStatus.OK);
            } catch (Exception e) {
                System.err.println("Error al generar métricas combinadas: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("# Error al obtener métricas\n");
            }
        }
        
        private String getTcpServerMetrics() {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5100/metrics"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    System.err.println("Error HTTP " + response.statusCode() + " al obtener métricas TCP");
                    return "";
                }
            } catch (Exception e) {
                System.err.println("Error conectando al servidor TCP de métricas: " + e.getMessage());
                return "";
            }
        }
    }
}