package com.arquitectura.repositorios;

import com.arquitectura.entidades.Mensaje;

import java.util.List;

public interface MensajeRepository {
    Mensaje save(Mensaje mensaje);

    List<Mensaje> findTextAudioLogs();

    List<Mensaje> findByCanal(Long canalId);

    List<Mensaje> findBetweenUsers(Long emisor, Long receptor);
    
    /**
     * Encuentra todos los mensajes donde el usuario es emisor o receptor
     * @param usuarioId ID del usuario
     * @return Lista de mensajes ordenados por timestamp
     */
    List<Mensaje> findAllByUser(Long usuarioId);

    /**
     * Obtiene todos los mensajes registrados en la base de datos ordenados por identificador.
     */
    List<Mensaje> findAllOrdered();
}
