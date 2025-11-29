package com.arquitectura.restapi;

import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Aplicación Spring Boot que expone una API REST con las métricas y logs del servidor.
 * Se ejecuta en paralelo con el servidor TCP principal.
 */
@SpringBootApplication
public class RestApiApplication {

    private static final Logger LOGGER = Logger.getLogger(RestApiApplication.class.getName());
    private static ConfigurableApplicationContext context;

    /**
     * Inicia la aplicación Spring Boot en un hilo separado.
     * El puerto se configura en application.properties (server.port=8089).
     * 
     * @param port Puerto (ignorado, se usa application.properties)
     * @param dataSource DataSource para acceder a la base de datos
     */
    public static void startAsync(int port, javax.sql.DataSource dataSource) {
        Thread restApiThread = new Thread(() -> {
            try {
                SpringApplication app = new SpringApplication(RestApiApplication.class);
                // No sobrescribir server.port, usar la configuración de application.properties
                app.setDefaultProperties(java.util.Map.of(
                    "spring.main.banner-mode", "off",
                    "logging.level.root", "WARN",
                    "logging.level.com.arquitectura.restapi", "INFO"
                ));
                
                // Registrar el DataSource como bean antes de iniciar
                app.addInitializers(applicationContext -> {
                    applicationContext.getBeanFactory().registerSingleton("dataSource", dataSource);
                });
                
                context = app.run();
                // Obtener el puerto real de Spring Boot
                String actualPort = context.getEnvironment().getProperty("server.port", "8089");
                LOGGER.info("REST API iniciada en el puerto " + actualPort);
            } catch (Exception e) {
                LOGGER.severe("Error al iniciar REST API: " + e.getMessage());
            }
        }, "rest-api-thread");
        
        restApiThread.setDaemon(false);
        restApiThread.start();
    }

    /**
     * Detiene la aplicación Spring Boot.
     */
    public static void stop() {
        if (context != null) {
            SpringApplication.exit(context);
            context = null;
            LOGGER.info("REST API detenida");
        }
    }

    /**
     * Método main para pruebas standalone (opcional).
     */
    public static void main(String[] args) {
        SpringApplication.run(RestApiApplication.class, args);
    }
}
