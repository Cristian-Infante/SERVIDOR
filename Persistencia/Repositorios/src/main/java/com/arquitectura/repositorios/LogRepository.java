package com.arquitectura.repositorios;

import com.arquitectura.entidades.Log;

import java.util.List;

/**
 * Repositorio encargado de la persistencia de {@link Log}.
 */
public interface LogRepository {

    void append(Log log);

    List<Log> findAll();
}
