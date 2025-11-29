package com.arquitectura.configdb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;

/**
 * Centralised configuration helper that reads the <code>properties/database.properties</code> file
 * from the classpath and exposes typed accessors used by the data access layers.
 */
public final class DBConfig {

    private static final Logger LOGGER = Logger.getLogger(DBConfig.class.getName());
    private static final String DATABASE_CONFIG_PATH = "/properties/database.properties";
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
        try (InputStream in = DBConfig.class.getResourceAsStream(DATABASE_CONFIG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Configuration file not found at " + DATABASE_CONFIG_PATH);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load database configuration properties", e);
        }
    }

    private DataSource buildMySqlDataSource() {
        MysqlConnectionPoolDataSource ds = new MysqlConnectionPoolDataSource();
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
}
