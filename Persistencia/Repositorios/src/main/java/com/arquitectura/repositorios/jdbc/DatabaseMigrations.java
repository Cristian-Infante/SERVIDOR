package com.arquitectura.repositorios.jdbc;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Locale;
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

            ensureUuidAutoGeneration(conn);
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

    private static void ensureUuidAutoGeneration(Connection conn) throws SQLException {
        ColumnInfo columnInfo = fetchColumnInfo(conn, "canales", "uuid");
        if (columnInfo == null) {
            return;
        }

        boolean columnReady = !columnInfo.nullable && columnInfo.hasUuidDefault;
        if (columnReady) {
            return;
        }

        boolean altered = tryAlterColumnWithDefault(conn);
        if (altered) {
            return;
        }

        tryForceNotNull(conn);
        ensureUuidInsertTrigger(conn);
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

    private static ColumnInfo fetchColumnInfo(Connection conn, String tableName, String columnName) throws SQLException {
        String schema = resolveCurrentSchema(conn);
        if (schema == null) {
            return null;
        }
        String sql = "SELECT IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ps.setString(3, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String nullable = rs.getString("IS_NULLABLE");
                String columnDefault = rs.getString("COLUMN_DEFAULT");
                boolean isNullable = nullable != null && nullable.equalsIgnoreCase("YES");
                boolean hasUuidDefault = columnDefault != null && columnDefault.toUpperCase(Locale.ROOT).contains("UUID");
                return new ColumnInfo(isNullable, hasUuidDefault);
            }
        }
    }

    private static String resolveCurrentSchema(Connection conn) throws SQLException {
        String catalog = conn.getCatalog();
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    private static boolean tryAlterColumnWithDefault(Connection conn) {
        DatabaseMetaData meta;
        try {
            meta = conn.getMetaData();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "No se pudo determinar versión de base de datos", e);
            return false;
        }

        int major;
        int minor;
        try {
            major = meta.getDatabaseMajorVersion();
            minor = meta.getDatabaseMinorVersion();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "No se pudo leer versión de base de datos", e);
            return false;
        }

        boolean supportsExpressions = major > 8 || (major == 8 && minor >= 13);
        if (!supportsExpressions) {
            return false;
        }

        String alterSql = "ALTER TABLE canales MODIFY uuid CHAR(36) NOT NULL DEFAULT (UUID())";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(alterSql);
            LOGGER.info("✓ Columna 'canales.uuid' configurada con DEFAULT (UUID()) y NOT NULL");
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "No se pudo aplicar DEFAULT (UUID()) directamente", e);
            return false;
        }
    }

    private static void tryForceNotNull(Connection conn) {
        String alterSql = "ALTER TABLE canales MODIFY uuid CHAR(36) NOT NULL";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(alterSql);
            LOGGER.info("✓ Columna 'canales.uuid' establecida como NOT NULL");
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "No se modificó nulabilidad de 'canales.uuid'", e);
        }
    }

    private static void ensureUuidInsertTrigger(Connection conn) throws SQLException {
        if (triggerExists(conn, "bi_canales_uuid_autofill")) {
            return;
        }
        String triggerSql = "CREATE TRIGGER bi_canales_uuid_autofill BEFORE INSERT ON canales " +
                "FOR EACH ROW SET NEW.uuid = IFNULL(NULLIF(NEW.uuid, ''), UUID())";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(triggerSql);
            LOGGER.info("✓ Trigger 'bi_canales_uuid_autofill' creado para rellenar UUID en inserciones");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "No se pudo crear trigger para autogenerar UUID en 'canales'", e);
        }
    }

    private static boolean triggerExists(Connection conn, String triggerName) throws SQLException {
        String schema = resolveCurrentSchema(conn);
        if (schema == null) {
            return false;
        }
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA = ? AND TRIGGER_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, triggerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private record ColumnInfo(boolean nullable, boolean hasUuidDefault) {
    }
}

