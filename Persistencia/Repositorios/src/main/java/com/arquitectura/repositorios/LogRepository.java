package com.arquitectura.repositorios;

import java.time.LocalDateTime;
import java.util.List;

import com.arquitectura.entidades.Log;

public interface LogRepository {
    void append(Log log);

    List<Log> findAll();
    
    List<Log> findByFechaHoraAfter(LocalDateTime fechaHora);
}
