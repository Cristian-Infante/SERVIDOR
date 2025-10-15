package com.arquitectura.configdb;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Centraliza la carga de configuraciones para la capa de datos.
 * Lee el archivo {@code properties/DB.properties} y expone utilitarios
 * para obtener un {@link javax.sql.DataSource} conectado a MySQL así como
 * acceso a otras claves requeridas por el servidor.
 */
public final class DBConfig {

    private static final String PROPERTIES_PATH = "properties/DB.properties";
    private static final Properties PROPERTIES = new Properties();
    private static volatile DataSource dataSource;

    static {
        load();
    }

    private DBConfig() {
    }

    private static void load() {
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(PROPERTIES_PATH)) {
            if (in == null) {
                throw new IllegalStateException("No se encontró el archivo de configuración: " + PROPERTIES_PATH);
            }
            PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la configuración de base de datos", e);
        }
    }

    /**
     * Obtiene un {@link DataSource} que utiliza {@link DriverManager} para producir conexiones.
     * La instancia se crea de forma perezosa y se reutiliza (singleton).
     *
     * @return DataSource configurado con los parámetros de MySQL.
     */
    public static DataSource getMySqlDataSource() {
        if (dataSource == null) {
            synchronized (DBConfig.class) {
                if (dataSource == null) {
                    dataSource = new DriverManagerDataSource(
                            require("mysql.url"),
                            require("mysql.user"),
                            require("mysql.password"),
                            PROPERTIES.getProperty("mysql.driver", "com.mysql.cj.jdbc.Driver")
                    );
                }
            }
        }
        return dataSource;
    }

    /**
     * Obtiene una propiedad arbitraria definida en el archivo de configuración.
     *
     * @param key clave solicitada.
     * @return valor encontrado o {@code null} si no existe.
     */
    public static String get(String key) {
        return PROPERTIES.getProperty(key);
    }

    /**
     * Obtiene una propiedad obligatoria, lanzando excepción en caso de ausencia.
     */
    public static String require(String key) {
        String value = get(key);
        if (Objects.requireNonNull(value, "No se encontró la propiedad: " + key).isEmpty()) {
            throw new IllegalStateException("La propiedad " + key + " no puede ser vacía");
        }
        return value;
    }

    /**
     * Implementación sencilla de {@link DataSource} basada en {@link DriverManager}.
     */
    private static final class DriverManagerDataSource implements DataSource {

        private final String url;
        private final String user;
        private final String password;

        private DriverManagerDataSource(String url, String user, String password, String driverClassName) {
            this.url = url;
            this.user = user;
            this.password = password;
            if (driverClassName != null && !driverClassName.isBlank()) {
                try {
                    Class.forName(driverClassName);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("No se pudo cargar el driver de base de datos", e);
                }
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException("unwrap no soportado");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("ParentLogger no soportado");
        }
    }
}
