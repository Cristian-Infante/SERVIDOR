package com.arquitectura.repositorios.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Utility class that makes sure the minimum schema required by the
 * repositories exists before the application starts interacting with the
 * database. The statements are idempotent so they can be executed on every
 * boot without affecting existing data.
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    public static void ensureSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : schemaStatements()) {
                statement.executeUpdate(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialise database schema", e);
        }
    }

    private static List<String> schemaStatements() {
        return List.of(
                "CREATE TABLE IF NOT EXISTS clientes (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "usuario VARCHAR(120) NOT NULL," +
                        "email VARCHAR(180) NOT NULL," +
                        "contrasenia VARCHAR(128) NOT NULL," +
                        "foto LONGBLOB," +
                        "ip VARCHAR(64)," +
                        "estado TINYINT(1) DEFAULT 0," +
                        "UNIQUE KEY uk_clientes_usuario (usuario)," +
                        "UNIQUE KEY uk_clientes_email (email)" +
                        ")",
                "ALTER TABLE clientes ADD COLUMN IF NOT EXISTS foto LONGBLOB",
                "ALTER TABLE clientes ADD COLUMN IF NOT EXISTS ip VARCHAR(64)",
                "ALTER TABLE clientes ADD COLUMN IF NOT EXISTS estado TINYINT(1) DEFAULT 0",
                "CREATE TABLE IF NOT EXISTS canales (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "nombre VARCHAR(160) NOT NULL," +
                        "privado TINYINT(1) NOT NULL" +
                        ")",
                "ALTER TABLE canales ADD COLUMN IF NOT EXISTS privado TINYINT(1) NOT NULL DEFAULT 0",
                "CREATE TABLE IF NOT EXISTS canal_clientes (" +
                        "canal_id BIGINT NOT NULL," +
                        "cliente_id BIGINT NOT NULL," +
                        "PRIMARY KEY (canal_id, cliente_id)," +
                        "KEY idx_canal_clientes_canal (canal_id)," +
                        "KEY idx_canal_clientes_cliente (cliente_id)" +
                        ")",
                "CREATE TABLE IF NOT EXISTS mensajes (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "timestamp DATETIME NOT NULL," +
                        "tipo VARCHAR(32) NOT NULL," +
                        "emisor_id BIGINT NOT NULL," +
                        "receptor_id BIGINT NULL," +
                        "canal_id BIGINT NULL," +
                        "contenido TEXT NULL," +
                        "ruta_archivo VARCHAR(400) NULL," +
                        "mime VARCHAR(120) NULL," +
                        "duracion_seg INT NULL," +
                        "KEY idx_mensajes_canal (canal_id)," +
                        "KEY idx_mensajes_emisor (emisor_id)," +
                        "KEY idx_mensajes_receptor (receptor_id)" +
                        ")",
                "ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS ruta_archivo VARCHAR(400) NULL",
                "ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS mime VARCHAR(120) NULL",
                "ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS duracion_seg INT NULL",
                "CREATE TABLE IF NOT EXISTS logs (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "tipo TINYINT(1) NOT NULL," +
                        "detalle TEXT NOT NULL," +
                        "fecha_hora DATETIME NOT NULL" +
                        ")"
        );
    }
}

