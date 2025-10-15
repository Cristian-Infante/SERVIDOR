package com.arquitectura.repositorios;

import com.arquitectura.entidades.Log;

import java.util.List;

public interface LogRepository {
    void append(Log log);

    List<Log> findAll();
}
