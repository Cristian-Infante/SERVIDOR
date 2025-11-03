package com.arquitectura.servicios.eventos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.arquitectura.dto.RealtimeMessageDto;
import com.arquitectura.dto.UserSummary;
import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.servicios.conexion.ConnectionGateway;

/**
 * Servicio que escucha eventos de mensajes y notifica a los clientes correspondientes
 */
public class MessageNotificationService implements SessionObserver {
    
    private static final Logger LOGGER = Logger.getLogger(MessageNotificationService.class.getName());
    
    private final ConnectionGateway connectionGateway;
    private final CanalRepository canalRepository;
    private final ClienteRepository clienteRepository;

    public MessageNotificationService(ConnectionGateway connectionGateway,
                                    CanalRepository canalRepository,
                                    ClienteRepository clienteRepository,
                                    SessionEventBus eventBus) {
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
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
            RealtimeMessageDto dto = construirEventoMensaje(mensaje, "NEW_MESSAGE");

            // Enviar el mensaje a todas las sesiones del receptor
            connectionGateway.sendToUser(mensaje.getReceptor(), dto);
            LOGGER.fine(() -> "Mensaje privado notificado al usuario " + mensaje.getReceptor());

            // Entregar también a todas las sesiones del emisor (por ejemplo, cuando tiene múltiples conexiones abiertas)
            if (mensaje.getEmisor() != null && !mensaje.getEmisor().equals(mensaje.getReceptor())) {
                connectionGateway.sendToUser(mensaje.getEmisor(), dto);
                LOGGER.fine(() -> "Mensaje privado replicado al emisor " + mensaje.getEmisor());
            }
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
            RealtimeMessageDto dto = construirEventoMensaje(mensaje, "NEW_CHANNEL_MESSAGE");
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
                        // No reenviar al emisor en este ciclo; se maneja más abajo
                        continue;
                    }
                    connectionGateway.sendToUser(cliente.getId(), dto);
                    enviados++;
                    // Mostrar solo el ID del receptor
                    receptores.append(String.format("[ID:%d] ", cliente.getId()));
                }

                // Replicar también al emisor en todas sus sesiones activas
                if (mensaje.getEmisor() != null) {
                    connectionGateway.sendToUser(mensaje.getEmisor(), dto);
                    enviados++;
                    receptores.append(String.format("[ID:%d] (emisor) ", mensaje.getEmisor()));
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

    private RealtimeMessageDto construirEventoMensaje(Mensaje mensaje, String tipoEvento) {
        RealtimeMessageDto dto = new RealtimeMessageDto();
        dto.setEvento(tipoEvento);
        dto.setId(mensaje.getId());
        dto.setTipoMensaje(mensaje.getTipo());
        dto.setTimestamp(mensaje.getTimeStamp());
        dto.setEmisorId(mensaje.getEmisor());
        dto.setEmisorNombre(obtenerNombreUsuario(mensaje.getEmisor()));
        dto.setReceptorId(mensaje.getReceptor());
        if (mensaje.getReceptor() != null) {
            dto.setReceptorNombre(obtenerNombreUsuario(mensaje.getReceptor()));
        }
        dto.setCanalId(mensaje.getCanalId());
        if (mensaje.getCanalId() != null) {
            dto.setCanalNombre(obtenerNombreCanal(mensaje.getCanalId()));
            dto.setCanalMiembros(obtenerMiembrosCanal(mensaje.getCanalId()));
        }
        dto.setTipoConversacion(determinarTipoConversacion(mensaje));
        dto.setContenido(construirContenido(mensaje));
        return dto;
    }

    private String determinarTipoConversacion(Mensaje mensaje) {
        if (mensaje.getCanalId() != null) {
            return "CANAL";
        }
        if (mensaje.getReceptor() != null) {
            return "DIRECTO";
        }
        return "DESCONOCIDO";
    }

    private String obtenerNombreUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return clienteRepository.findById(usuarioId)
                .map(com.arquitectura.entidades.Cliente::getNombreDeUsuario)
                .orElse("Desconocido");
    }

    private String obtenerNombreCanal(Long canalId) {
        if (canalId == null) {
            return null;
        }
        return canalRepository.findById(canalId)
                .map(com.arquitectura.entidades.Canal::getNombre)
                .orElse("Canal " + canalId);
    }

    private List<UserSummary> obtenerMiembrosCanal(Long canalId) {
        if (canalId == null) {
            return new ArrayList<>();
        }
        var miembros = canalRepository.findUsers(canalId);
        List<UserSummary> summaries = new ArrayList<>();
        for (var cliente : miembros) {
            if (cliente == null) {
                continue;
            }
            summaries.add(new UserSummary(
                    cliente.getId(),
                    cliente.getNombreDeUsuario(),
                    cliente.getEmail(),
                    Boolean.TRUE.equals(cliente.getEstado())));
        }
        return summaries;
    }

    private java.util.Map<String, Object> construirContenido(Mensaje mensaje) {
        java.util.Map<String, Object> contenido = new java.util.LinkedHashMap<>();
        if (mensaje instanceof TextoMensaje texto) {
            contenido.put("contenido", texto.getContenido());
        } else if (mensaje instanceof AudioMensaje audio) {
            contenido.put("rutaArchivo", audio.getRutaArchivo());
            contenido.put("mime", audio.getMime());
            contenido.put("duracionSeg", audio.getDuracionSeg());
            contenido.put("transcripcion", audio.getTranscripcion());
        } else if (mensaje instanceof ArchivoMensaje archivo) {
            contenido.put("rutaArchivo", archivo.getRutaArchivo());
            contenido.put("mime", archivo.getMime());
        }
        return contenido;
    }
}
