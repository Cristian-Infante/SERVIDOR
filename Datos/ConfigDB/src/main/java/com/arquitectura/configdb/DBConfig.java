package com.arquitectura.configdb;

import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de acceso centralizado para la configuración del servidor.
 *
 * <p>Lee el archivo {@code properties/DB.properties} y expone un {@link DataSource}
 * para interactuar con MySQL. También expone utilitarios para obtener claves
 * arbitrarias usadas por otras capas (puertos del servidor, rutas de
 * almacenamiento, niveles de logging, etc.).</p>
 */
public final class DBConfig {

    private static final Logger LOGGER = Logger.getLogger(DBConfig.class.getName());
    private static final String PROPERTIES_PATH = "/properties/DB.properties";
    private static final Properties PROPERTIES = new Properties();
    private static DataSource dataSource;

    static {
        load();
    }

    private DBConfig() {
    }

    private static void load() {
        try (InputStream inputStream = DBConfig.class.getResourceAsStream(PROPERTIES_PATH)) {
            if (Objects.isNull(inputStream)) {
                throw new IllegalStateException("No se encontró el archivo de propiedades " + PROPERTIES_PATH);
            }
            PROPERTIES.load(inputStream);
            LOGGER.log(Level.CONFIG, "Propiedades de configuración cargadas ({0})", PROPERTIES.size());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron cargar las propiedades", e);
        }
    }

    /**
     * Expone una instancia reutilizable de {@link DataSource} inicializada a partir de la configuración.
     *
     * @return datasource configurado hacia MySQL
     */
    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            MysqlDataSource mysqlDataSource = new MysqlDataSource();
            mysqlDataSource.setUrl(requireProperty("mysql.url"));
            mysqlDataSource.setUser(requireProperty("mysql.user"));
            mysqlDataSource.setPassword(requireProperty("mysql.password"));
            dataSource = mysqlDataSource;
        }
        return dataSource;
    }

    /**
     * Obtiene una propiedad como {@link String} validando que exista.
     */
    public static String requireProperty(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("No se encontró la clave de configuración: " + key);
        }
        return value;
    }

    /**
     * Obtiene una propiedad como {@link int}.
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    /**
     * Obtiene una propiedad como {@link long}.
     */
    public static long getLongProperty(String key, long defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }

    /**
     * Obtiene una propiedad como {@link boolean}.
     */
    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Devuelve una propiedad como {@link String} permitiendo valor por defecto.
     */
    public static String getProperty(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value == null ? defaultValue : value;
    }
}
