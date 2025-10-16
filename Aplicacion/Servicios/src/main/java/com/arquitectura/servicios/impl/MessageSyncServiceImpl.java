package com.arquitectura.servicios.impl;

import com.arquitectura.dto.MessageSyncResponse;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.MessageSyncService;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Implementación del servicio de sincronización de mensajes
 */
public class MessageSyncServiceImpl implements MessageSyncService {
    
    private static final Logger LOGGER = Logger.getLogger(MessageSyncServiceImpl.class.getName());
    
    private final MensajeRepository mensajeRepository;
    
    public MessageSyncServiceImpl(MensajeRepository mensajeRepository) {
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
    }
    
    @Override
    public MessageSyncResponse sincronizarMensajes(Long usuarioId) {
        LOGGER.info(() -> "Sincronizando mensajes para usuario: " + usuarioId);
        
        try {
            // Obtener todos los mensajes del usuario (enviados y recibidos)
            List<Mensaje> mensajes = mensajeRepository.findAllByUser(usuarioId);
            
            LOGGER.info(() -> "Encontrados " + mensajes.size() + " mensajes para usuario " + usuarioId);
            
            // Convertir a List<Object> para evitar problemas de serialización
            List<Object> mensajesObj = mensajes.stream()
                    .map(mensaje -> (Object) mensaje)
                    .toList();
            
            return new MessageSyncResponse(mensajesObj);
            
        } catch (Exception e) {
            LOGGER.warning(() -> "Error sincronizando mensajes para usuario " + usuarioId + ": " + e.getMessage());
            // Retornar respuesta vacía en caso de error
            return new MessageSyncResponse(List.of());
        }
    }
}
