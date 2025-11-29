package com.arquitectura.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de Swagger/OpenAPI para el API Gateway.
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8090}")
    private String serverPort;

    @Value("${server.servlet.context-path:/gateway}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(createApiInfo())
                .servers(createServers())
                .tags(createTags());
    }

    private Info createApiInfo() {
        return new Info()
                .title("TCP Servers API Gateway")
                .description("""
                    API Gateway para la gestión y agregación de múltiples servidores TCP.
                    
                    ## Funcionalidades principales:
                    
                    - **Gestión de Conexiones**: Conectar y desconectar servidores TCP dinámicamente
                    - **Agregación de Datos**: Consolidar métricas y logs de múltiples servidores en tiempo real
                    - **WebSockets**: Distribución de datos agregados a frontends mediante WebSockets
                    - **Monitoreo**: Health checks automáticos y estadísticas de conexión
                    
                    ## WebSocket Endpoints:
                    
                    - `ws://localhost:8090/gateway/ws/metrics-aggregated` - Métricas agregadas
                    - `ws://localhost:8090/gateway/ws/logs-aggregated` - Logs agregados  
                    - `ws://localhost:8090/gateway/ws/server/{serverId}` - Datos específicos de servidor
                    
                    ## Flujo de trabajo:
                    
                    1. **Registrar servidor**: POST `/api/servers/connect` con IP y puerto
                    2. **Verificar conexión**: GET `/api/servers` para ver servidores activos
                    3. **Consumir datos**: Conectar WebSocket para recibir datos en tiempo real
                    4. **Desconectar**: DELETE `/api/servers/disconnect` cuando sea necesario
                    """)
                .version("1.0.0")
                .contact(createContact())
                .license(createLicense());
    }

    private Contact createContact() {
        return new Contact()
                .name("Equipo de Arquitectura")
                .email("arquitectura@empresa.com")
                .url("https://github.com/Cristian-Infante/SERVIDOR");
    }

    private License createLicense() {
        return new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");
    }

    private List<Server> createServers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort + contextPath)
                        .description("Servidor de desarrollo local"),
                new Server()
                        .url("http://api-gateway:8090/gateway")
                        .description("Servidor Docker interno")
        );
    }

    private List<Tag> createTags() {
        return List.of(
                new Tag()
                        .name("Gestión de Servidores")
                        .description("Endpoints para conectar, desconectar y gestionar servidores TCP"),
                new Tag()
                        .name("Consulta de Datos")
                        .description("Endpoints para consultar métricas, logs y estadísticas agregadas"),
                new Tag()
                        .name("Monitoreo")
                        .description("Endpoints para health checks y estadísticas del sistema"),
                new Tag()
                        .name("WebSocket Info")
                        .description("Información sobre conexiones WebSocket disponibles")
        );
    }
}