package com.arquitectura.repositorios.jdbc;

import javax.sql.DataSource;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clase para manejar migraciones de la base de datos
 * Ejecuta cambios incrementales en el esquema existente
 */
public final class DatabaseMigrations {

    private static final Logger LOGGER = Logger.getLogger(DatabaseMigrations.class.getName());

    private DatabaseMigrations() {
    }

    /**
     * Ejecuta todas las migraciones necesarias
     * @param dataSource conexión a la base de datos
     */
    public static void runMigrations(DataSource dataSource) {
        try {
            addTranscripcionColumn(dataSource);
            LOGGER.info("Migraciones de base de datos completadas exitosamente");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error ejecutando migraciones de base de datos", e);
        }
    }

    /**
     * Agrega la columna 'transcripcion' a la tabla mensajes si no existe
     */
    private static void addTranscripcionColumn(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            // Verificar si la columna ya existe
            if (columnExists(conn, "mensajes", "transcripcion")) {
                LOGGER.fine("La columna 'transcripcion' ya existe en la tabla 'mensajes'");
                return;
            }

            // Agregar la columna
            String sql = "ALTER TABLE mensajes ADD COLUMN transcripcion TEXT NULL AFTER duracion_seg";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                LOGGER.info("✓ Columna 'transcripcion' agregada a la tabla 'mensajes'");
            }
        } catch (SQLException e) {
            // Si el error es porque la columna ya existe, ignorarlo
            if (e.getMessage() != null && e.getMessage().contains("Duplicate column")) {
                LOGGER.fine("La columna 'transcripcion' ya existe (error ignorado)");
            } else {
                LOGGER.log(Level.WARNING, "Error agregando columna 'transcripcion'", e);
            }
        }
    }

    /**
     * Verifica si una columna existe en una tabla
     */
    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }
}

