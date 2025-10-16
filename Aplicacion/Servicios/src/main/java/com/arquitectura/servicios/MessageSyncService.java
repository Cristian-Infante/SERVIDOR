package com.arquitectura.servicios;

import com.arquitectura.dto.MessageSyncResponse;

/**
 * Servicio para sincronizar mensajes del usuario al hacer login
 */
public interface MessageSyncService {
    
    /**
     * Obtiene todos los mensajes del usuario para sincronización
     * Incluye mensajes enviados y recibidos (privados y de canales)
     * 
     * @param usuarioId ID del usuario que hizo login
     * @return Respuesta con todos los mensajes del usuario
     */
    MessageSyncResponse sincronizarMensajes(Long usuarioId);
}
