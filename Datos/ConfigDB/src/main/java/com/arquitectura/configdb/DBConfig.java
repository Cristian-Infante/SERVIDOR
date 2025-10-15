package com.arquitectura.configdb;

import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralised configuration helper that reads the <code>properties/DB.properties</code> file
 * from the classpath and exposes typed accessors used by the infrastructure layers.
 */
public final class DBConfig {

    private static final Logger LOGGER = Logger.getLogger(DBConfig.class.getName());
    private static final String CONFIG_PATH = "/properties/DB.properties";
    private static final DBConfig INSTANCE = new DBConfig();

    private final Properties properties = new Properties();
    private final DataSource dataSource;

    private DBConfig() {
        loadProperties();
        this.dataSource = buildMySqlDataSource();
    }

    public static DBConfig getInstance() {
        return INSTANCE;
    }

    private void loadProperties() {
        try (InputStream in = DBConfig.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Configuration file not found at " + CONFIG_PATH);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load configuration properties", e);
        }
    }

    private DataSource buildMySqlDataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setURL(require("mysql.url"));
        ds.setUser(require("mysql.user"));
        ds.setPassword(require("mysql.password"));
        Optional.ofNullable(properties.getProperty("mysql.driver"))
                .ifPresent(driver -> {
                    try {
                        Class.forName(driver);
                    } catch (ClassNotFoundException e) {
                        LOGGER.log(Level.WARNING, "JDBC driver not found: {0}", driver);
                    }
                });
        return ds;
    }

    private String require(String key) {
        return Optional.ofNullable(properties.getProperty(key))
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Missing property: " + key));
    }

    public DataSource getMySqlDataSource() {
        return dataSource;
    }

    public static String requireProperty(String key) {
        return INSTANCE.require(key);
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
