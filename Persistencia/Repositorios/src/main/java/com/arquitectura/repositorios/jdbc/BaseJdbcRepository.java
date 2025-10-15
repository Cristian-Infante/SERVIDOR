package com.arquitectura.repositorios.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Clase base reutilizable que facilita el acceso a {@link Connection}.
 */
public abstract class BaseJdbcRepository {

    private final DataSource dataSource;

    protected BaseJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
