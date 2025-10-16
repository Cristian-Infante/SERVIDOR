package com.arquitectura.servicios.eventos;

import java.util.Objects;
import java.util.logging.Logger;

import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.servicios.conexion.ConnectionGateway;

/**
 * Servicio que escucha eventos de mensajes y notifica a los clientes correspondientes
 */
public class MessageNotificationService implements SessionObserver {
    
    private static final Logger LOGGER = Logger.getLogger(MessageNotificationService.class.getName());
    
    private final ConnectionGateway connectionGateway;
    private final CanalRepository canalRepository;
    
    public MessageNotificationService(ConnectionGateway connectionGateway, 
                                    CanalRepository canalRepository,
                                    SessionEventBus eventBus) {
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        eventBus.subscribe(this);
    }
    
    @Override
    public void onEvent(SessionEvent event) {
        switch (event.getType()) {
            case NEW_MESSAGE -> notificarNuevoMensajePrivado(event);
            case NEW_CHANNEL_MESSAGE -> {
                // Agregar un pequeño delay para que el LogSubscriber procese primero
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                notificarNuevoMensajeCanal(event);
            }
        }
    }
    
    private void notificarNuevoMensajePrivado(SessionEvent event) {
        if (!(event.getPayload() instanceof Mensaje mensaje)) {
            return;
        }
        
        if (mensaje.getReceptor() == null) {
            return;
        }
        
        try {
            // Enviar el mensaje al receptor específico
            connectionGateway.sendToUser(mensaje.getReceptor(), mensaje);
            LOGGER.fine(() -> "Mensaje privado notificado al usuario " + mensaje.getReceptor());
        } catch (Exception e) {
            LOGGER.warning(() -> "Error notificando mensaje privado: " + e.getMessage());
        }
    }
    
    private void notificarNuevoMensajeCanal(SessionEvent event) {
        if (!(event.getPayload() instanceof Mensaje mensaje)) {
            LOGGER.warning("NEW_CHANNEL_MESSAGE: payload no es un Mensaje - " + event.getPayload());
            return;
        }
        
        if (mensaje.getCanalId() == null) {
            LOGGER.warning("NEW_CHANNEL_MESSAGE: mensaje sin canalId - " + mensaje.getId());
            return;
        }
        
        try {
            // Log detallado del mensaje que se va a enviar
            LOGGER.info(String.format(
                "NEW_CHANNEL_MESSAGE - Enviando mensaje ID:%d, Tipo:%s, Emisor:%d, Canal:%d", 
                mensaje.getId(), mensaje.getTipo(), mensaje.getEmisor(), mensaje.getCanalId()));

            if (mensaje instanceof com.arquitectura.entidades.TextoMensaje texto) {
                LOGGER.info("   Contenido: \"" + texto.getContenido() + "\"");
            } else if (mensaje instanceof com.arquitectura.entidades.AudioMensaje audio) {
                LOGGER.info("   Audio: " + audio.getRutaArchivo() + 
                             " (transcripción: \"" + audio.getTranscripcion() + "\")");
            }

            // Obtener información del canal
            String canalInfo = canalRepository.findById(mensaje.getCanalId())
                .map(c -> "'" + c.getNombre() + "'")
                .orElse("Canal ID " + mensaje.getCanalId());

            LOGGER.info("   Enviando a canal: " + canalInfo);

            // Enviar el mensaje a todos los miembros del canal (excluyendo al emisor)
            try {
                var miembros = canalRepository.findUsers(mensaje.getCanalId());
                int enviados = 0;
                StringBuilder receptores = new StringBuilder();
                for (var cliente : miembros) {
                    if (cliente == null || cliente.getId() == null) continue;
                    if (cliente.getId().equals(mensaje.getEmisor())) {
                        // No reenviar al emisor
                        continue;
                    }
                    connectionGateway.sendToUser(cliente.getId(), mensaje);
                    enviados++;
                    // Mostrar solo el ID del receptor
                    receptores.append(String.format("[ID:%d] ", cliente.getId()));
                }
                LOGGER.info(String.format("Mensaje de canal entregado a %d receptor(es) en canal %d. Receptores: %s",
                        enviados, mensaje.getCanalId(), receptores.toString().trim()));
            } catch (Exception e) {
                LOGGER.warning("Error entregando mensaje por miembros de canal: " + e.getMessage());
                throw e;
            }
            
        } catch (Exception e) {
            LOGGER.severe(() -> "❌ Error notificando mensaje de canal ID " + mensaje.getCanalId() + 
                               " - Mensaje ID " + mensaje.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
