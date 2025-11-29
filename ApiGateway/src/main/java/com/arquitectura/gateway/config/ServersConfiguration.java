package com.arquitectura.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de servidores REST que el Gateway debe monitorear.
 * Los servidores se definen en application.properties usando el prefijo gateway.servers
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class ServersConfiguration {

    private List<ServerConfig> servers = new ArrayList<>();
    private DataCollectionConfig dataCollection = new DataCollectionConfig();

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public DataCollectionConfig getDataCollection() {
        return dataCollection;
    }

    public void setDataCollection(DataCollectionConfig dataCollection) {
        this.dataCollection = dataCollection;
    }

    /**
     * Obtiene lista de todos los servidores configurados.
     */
    public List<ServerConfig> getAllServers() {
        return servers;
    }

    /**
     * Busca servidor por ID.
     */
    public ServerConfig findServerById(String id) {
        return servers.stream()
                .filter(server -> server.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Configuración individual de cada servidor.
     */
    public static class ServerConfig {
        private String id;
        private String name;
        private String host;
        private int port;

        // Constructores
        public ServerConfig() {}

        public ServerConfig(String id, String name, String host, int port) {
            this.id = id;
            this.name = name;
            this.host = host;
            this.port = port;
        }

        // Getters y setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }



        /**
         * Construye la URL base del servidor.
         */
        public String getBaseUrl() {
            return String.format("http://%s:%d", host, port);
        }

        /**
         * Construye la URL de métricas del servidor.
         */
        public String getMetricsUrl() {
            return getBaseUrl() + "/api/metrics/json";
        }

        /**
         * Construye la URL de logs del servidor.
         */
        public String getLogsUrl() {
            return getBaseUrl() + "/api/logs";
        }

        /**
         * Construye la URL de health check del servidor.
         */
        public String getHealthCheckUrl() {
            return getBaseUrl() + "/actuator/health";
        }

        @Override
        public String toString() {
            return String.format("ServerConfig{id='%s', name='%s', host='%s', port=%d}", 
                               id, name, host, port);
        }
    }

    /**
     * Configuración para la recolección de datos.
     */
    public static class DataCollectionConfig {
        private long metricsInterval = 5000; // ms
        private long logsInterval = 10000; // ms
        private boolean autoStart = true;

        public long getMetricsInterval() {
            return metricsInterval;
        }

        public void setMetricsInterval(long metricsInterval) {
            this.metricsInterval = metricsInterval;
        }

        public long getLogsInterval() {
            return logsInterval;
        }

        public void setLogsInterval(long logsInterval) {
            this.logsInterval = logsInterval;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        @Override
        public String toString() {
            return String.format("DataCollectionConfig{metricsInterval=%d, logsInterval=%d, autoStart=%s}", 
                               metricsInterval, logsInterval, autoStart);
        }
    }
}