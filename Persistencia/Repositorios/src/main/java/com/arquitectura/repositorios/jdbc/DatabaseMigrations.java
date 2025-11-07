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
            ensureCanalesUuidColumn(dataSource);
            addTranscripcionColumn(dataSource);
            LOGGER.info("Migraciones de base de datos completadas exitosamente");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error ejecutando migraciones de base de datos", e);
            throw new IllegalStateException("Fallo en migraciones de base de datos", e);
        }
    }

    private static void ensureCanalesUuidColumn(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!columnExists(conn, "canales", "uuid")) {
                String sql = "ALTER TABLE canales ADD COLUMN uuid CHAR(36) NULL AFTER id";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    LOGGER.info("✓ Columna 'uuid' agregada a la tabla 'canales'");
                }
            }

            int updated = populateMissingChannelUuids(conn);
            if (updated > 0) {
                LOGGER.info(() -> "✓ " + updated + " canal(es) existentes recibieron UUID generado");
            }

            ensureUuidUniqueIndex(conn);
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

    private static int populateMissingChannelUuids(Connection conn) throws SQLException {
        final String sql = "UPDATE canales SET uuid = UUID() WHERE uuid IS NULL OR uuid = '' LIMIT ?";
        int totalUpdated = 0;
        int batchSize = 500;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            while (true) {
                stmt.setInt(1, batchSize);
                try {
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        break;
                    }
                    totalUpdated += updated;
                } catch (SQLTransactionRollbackException e) {
                    if (batchSize == 1) {
                        throw e;
                    }
                    batchSize = Math.max(1, batchSize / 2);
                    continue;
                }
            }
        }

        return totalUpdated;
    }

    private static void ensureUuidUniqueIndex(Connection conn) throws SQLException {
        if (indexExists(conn, "canales", "uq_canales_uuid")) {
            return;
        }
        String sql = "ALTER TABLE canales ADD UNIQUE KEY uq_canales_uuid (uuid)";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            LOGGER.info("✓ Índice único 'uq_canales_uuid' creado en 'canales'");
        } catch (SQLException e) {
            // Si el índice ya existe o hay datos duplicados se registrará la causa para diagnóstico
            LOGGER.log(Level.WARNING, "No se pudo crear índice único para 'canales.uuid'", e);
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

    private static boolean indexExists(Connection conn, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String existing = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existing)) {
                    return true;
                }
            }
        }
        return false;
    }
}

