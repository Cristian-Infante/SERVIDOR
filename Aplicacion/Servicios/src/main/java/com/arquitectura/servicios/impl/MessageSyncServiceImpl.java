package com.arquitectura.servicios.impl;

import com.arquitectura.dto.MessageSyncResponse;
import com.arquitectura.dto.SyncedMessageDto;
import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.MessageSyncService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Implementación del servicio de sincronización de mensajes
 */
public class MessageSyncServiceImpl implements MessageSyncService {

    private static final Logger LOGGER = Logger.getLogger(MessageSyncServiceImpl.class.getName());

    private final MensajeRepository mensajeRepository;
    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;

    public MessageSyncServiceImpl(MensajeRepository mensajeRepository,
                                  ClienteRepository clienteRepository,
                                  CanalRepository canalRepository) {
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
    }

    @Override
    public MessageSyncResponse sincronizarMensajes(Long usuarioId) {
        LOGGER.info(() -> "Sincronizando mensajes para usuario: " + usuarioId);

        try {
            // Obtener todos los mensajes del usuario (enviados y recibidos)
            List<Mensaje> mensajes = mensajeRepository.findAllByUser(usuarioId);

            LOGGER.info(() -> "Encontrados " + mensajes.size() + " mensajes para usuario " + usuarioId);

            Map<Long, String> cacheUsuarios = new HashMap<>();
            Map<Long, String> cacheCanales = new HashMap<>();
            List<SyncedMessageDto> mensajesDto = new ArrayList<>(mensajes.size());

            for (Mensaje mensaje : mensajes) {
                mensajesDto.add(construirDto(mensaje, cacheUsuarios, cacheCanales));
            }

            return new MessageSyncResponse(mensajesDto);

        } catch (Exception e) {
            LOGGER.warning(() -> "Error sincronizando mensajes para usuario " + usuarioId + ": " + e.getMessage());
            // Retornar respuesta vacía en caso de error
            return new MessageSyncResponse(List.of());
        }
    }

    private SyncedMessageDto construirDto(Mensaje mensaje,
                                          Map<Long, String> cacheUsuarios,
                                          Map<Long, String> cacheCanales) {
        SyncedMessageDto dto = new SyncedMessageDto();
        dto.setId(mensaje.getId());
        dto.setTipoMensaje(mensaje.getTipo());
        dto.setTimestamp(mensaje.getTimeStamp());
        dto.setEmisorId(mensaje.getEmisor());
        dto.setEmisorNombre(obtenerNombreUsuario(mensaje.getEmisor(), cacheUsuarios));
        dto.setReceptorId(mensaje.getReceptor());
        if (mensaje.getReceptor() != null) {
            dto.setReceptorNombre(obtenerNombreUsuario(mensaje.getReceptor(), cacheUsuarios));
        }
        dto.setCanalId(mensaje.getCanalId());
        if (mensaje.getCanalId() != null) {
            dto.setCanalNombre(obtenerNombreCanal(mensaje.getCanalId(), cacheCanales));
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

    private Map<String, Object> construirContenido(Mensaje mensaje) {
        Map<String, Object> contenido = new LinkedHashMap<>();
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

    private String obtenerNombreUsuario(Long usuarioId, Map<Long, String> cacheUsuarios) {
        if (usuarioId == null) {
            return null;
        }
        return cacheUsuarios.computeIfAbsent(usuarioId, id ->
            clienteRepository.findById(id)
                .map(com.arquitectura.entidades.Cliente::getNombreDeUsuario)
                .orElse("Desconocido"));
    }

    private String obtenerNombreCanal(Long canalId, Map<Long, String> cacheCanales) {
        if (canalId == null) {
            return null;
        }
        return cacheCanales.computeIfAbsent(canalId, id ->
            canalRepository.findById(id)
                .map(com.arquitectura.entidades.Canal::getNombre)
                .orElse("Desconocido"));
    }
}
