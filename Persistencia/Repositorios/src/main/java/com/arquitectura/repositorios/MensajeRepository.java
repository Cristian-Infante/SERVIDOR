package com.arquitectura.repositorios;

import com.arquitectura.entidades.Mensaje;

import java.util.List;

/**
 * Operaciones de almacenamiento para mensajes en el sistema.
 */
public interface MensajeRepository {

    Mensaje save(Mensaje mensaje);

    List<Mensaje> findTextAudioLogs();

    List<Mensaje> findByCanal(Long canalId);

    List<Mensaje> findBetweenUsers(Long emisor, Long receptor);
}
