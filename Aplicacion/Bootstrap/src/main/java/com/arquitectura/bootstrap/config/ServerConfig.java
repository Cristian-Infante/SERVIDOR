package com.arquitectura.bootstrap.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Centralised configuration helper that reads the <code>properties/server.properties</code> file
 * from the classpath and exposes typed accessors for server, storage, logging and security configuration.
 */
public final class ServerConfig {

    private static final Logger LOGGER = Logger.getLogger(ServerConfig.class.getName());
    private static final String CONFIG_PATH = "/properties/server.properties";
    private static final ServerConfig INSTANCE = new ServerConfig();

    private final Properties properties = new Properties();

    private ServerConfig() {
        loadProperties();
    }

    public static ServerConfig getInstance() {
        return INSTANCE;
    }

    private void loadProperties() {
        try (InputStream in = ServerConfig.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Configuration file not found at " + CONFIG_PATH);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load server configuration properties", e);
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid integer for {0}: {1}", new Object[]{key, raw});
            return defaultValue;
        }
    }

    public int getServerPort() {
        return getIntProperty("server.port", 5050);
    }

    public int getMaxConnections() {
        return getIntProperty("server.maxConnections", 5);
    }

    public int getPeerPort() {
        return getIntProperty("server.peerPort", getServerPort() + 1000);
    }

    public int getMetricsPort() {
        return getIntProperty("metrics.port", getServerPort() + 100);
    }

    public String getServerId() {
        return getProperty("server.id", "server-" + getServerPort());
    }

    public List<String> getPeerEndpoints() {
        String raw = getProperty("server.peers", "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public String getAudioDirectory() {
        return getProperty("storage.audioDir", "/var/chat/audio");
    }

    public String getSecuritySalt() {
        return getProperty("security.salt", "default-salt");
    }

    public String getGrafanaUrl() {
        return getProperty("grafana.url", "http://localhost:3000");
    }

    public Level getLogLevel() {
        String level = properties.getProperty("log.level");
        if (Objects.isNull(level)) {
            return Level.INFO;
        }
        try {
            return Level.parse(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Invalid log level {0}, defaulting to INFO", level);
            return Level.INFO;
        }
    }
}
