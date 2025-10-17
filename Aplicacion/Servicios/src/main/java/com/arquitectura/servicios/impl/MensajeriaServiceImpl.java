package com.arquitectura.servicios.impl;

import com.arquitectura.dto.MessageRequest;
import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Log;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.MensajeFactory;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.AudioStorageService;
import com.arquitectura.servicios.AudioTranscriptionService;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.conexion.ConnectionGateway;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;
import com.arquitectura.servicios.eventos.SessionObserver;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public class MensajeriaServiceImpl implements MensajeriaService, SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(MensajeriaServiceImpl.class.getName());

    private final MensajeRepository mensajeRepository;
    private final LogRepository logRepository;
    private final ConnectionGateway connectionGateway;
    private final SessionEventBus eventBus;
    private final AudioTranscriptionService transcriptionService;
    private final AudioStorageService audioStorageService;

    public MensajeriaServiceImpl(MensajeRepository mensajeRepository,
                                 LogRepository logRepository,
                                 ConnectionGateway connectionGateway,
                                 SessionEventBus eventBus,
                                 AudioTranscriptionService transcriptionService,
                                 AudioStorageService audioStorageService) {
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        this.connectionGateway = Objects.requireNonNull(connectionGateway, "connectionGateway");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService");
        this.audioStorageService = Objects.requireNonNull(audioStorageService, "audioStorageService");
        this.eventBus.subscribe(this);
    }

    @Override
    public Mensaje enviarMensajeAUsuario(MessageRequest request) {
        Mensaje mensaje = construirMensaje(request, false);
        Mensaje saved = mensajeRepository.save(mensaje);
        registrarLog(saved);
        
        // Eventos para logs y estadísticas
        eventBus.publish(new SessionEvent(SessionEventType.MESSAGE_SENT, null, request.getEmisor(), saved));
        if (saved instanceof AudioMensaje) {
            eventBus.publish(new SessionEvent(SessionEventType.AUDIO_SENT, null, request.getEmisor(), saved));
        }
        
        // Evento específico para notificar al receptor
        eventBus.publish(new SessionEvent(SessionEventType.NEW_MESSAGE, null, saved.getReceptor(), saved));
        
        return saved;
    }

    @Override
    public Mensaje enviarMensajeACanal(MessageRequest request) {
        Mensaje mensaje = construirMensaje(request, true);
        Mensaje saved = mensajeRepository.save(mensaje);
        registrarLog(saved);
        
        // Eventos para logs y estadísticas
        eventBus.publish(new SessionEvent(SessionEventType.MESSAGE_SENT, null, request.getEmisor(), saved));
        if (saved instanceof AudioMensaje) {
            eventBus.publish(new SessionEvent(SessionEventType.AUDIO_SENT, null, request.getEmisor(), saved));
        }
        
        // Evento específico para notificar a los miembros del canal
        eventBus.publish(new SessionEvent(SessionEventType.NEW_CHANNEL_MESSAGE, null, request.getEmisor(), saved));
        
        return saved;
    }

    private Mensaje construirMensaje(MessageRequest request, boolean esCanal) {
        String tipo = request.getTipo() != null ? request.getTipo().toUpperCase(Locale.ROOT) : "TEXTO";
        Long receptor = esCanal ? null : request.getReceptor();
        Long canalId = esCanal ? request.getCanalId() : null;
        Mensaje mensaje;
        switch (tipo) {
            case "AUDIO" -> {
                AudioMensaje audioMensaje = MensajeFactory.crearMensajeAudio(
                        request.getRutaArchivo(),
                        request.getDuracionSeg() != null ? request.getDuracionSeg() : 0,
                        request.getMime(),
                        request.getEmisor(),
                        receptor,
                        canalId);
                // Transcribir el audio automáticamente
                try {
                    String transcripcion = transcriptionService.transcribir(request.getRutaArchivo());
                    audioMensaje.setTranscripcion(transcripcion);
                    LOGGER.info(() -> "Audio transcrito: " + transcripcion);
                } catch (Exception e) {
                    LOGGER.warning(() -> "Error transcribiendo audio: " + e.getMessage());
                    audioMensaje.setTranscripcion("[Error al transcribir audio]");
                }
                if (audioMensaje.getRutaArchivo() != null && !audioMensaje.getRutaArchivo().isBlank()) {
                    try {
                        String audioBase64 = audioStorageService.cargarAudioBase64(audioMensaje.getRutaArchivo());
                        audioMensaje.setAudioBase64(audioBase64);
                    } catch (Exception e) {
                        LOGGER.warning(() -> "No se pudo adjuntar contenido de audio: " + e.getMessage());
                        audioMensaje.setAudioBase64(null);
                    }
                } else {
                    LOGGER.warning("Ruta de audio vacía, no se adjunta contenido codificado");
                    audioMensaje.setAudioBase64(null);
                }
                mensaje = audioMensaje;
            }
            case "ARCHIVO" -> mensaje = MensajeFactory.crearMensajeArchivo(
                    request.getRutaArchivo(),
                    request.getMime(),
                    request.getEmisor(),
                    receptor,
                    canalId);
            default -> mensaje = MensajeFactory.crearMensajeTexto(
                    request.getContenido(),
                    request.getEmisor(),
                    receptor,
                    canalId);
        }
        return mensaje;
    }

    private void registrarLog(Mensaje saved) {
        Log log = new Log();
        log.setTipo(Boolean.TRUE);
        log.setDetalle(describirMensaje(saved));
        log.setFechaHora(LocalDateTime.now());
        logRepository.append(log);
    }

    private String describirMensaje(Mensaje saved) {
        if (saved instanceof AudioMensaje audio) {
            return "Audio enviado a " + destino(saved) + " archivo=" + audio.getRutaArchivo();
        }
        if (saved instanceof ArchivoMensaje archivo) {
            return "Archivo enviado a " + destino(saved) + " archivo=" + archivo.getRutaArchivo();
        }
        return "Texto enviado a " + destino(saved);
    }

    private String destino(Mensaje mensaje) {
        if (mensaje.getCanalId() != null) {
            return "canal " + mensaje.getCanalId();
        }
        return "usuario " + mensaje.getReceptor();
    }

    private void entregar(Mensaje mensaje) {
        if (mensaje.getCanalId() != null) {
            connectionGateway.sendToChannel(mensaje.getCanalId(), mensaje);
        } else if (mensaje.getReceptor() != null) {
            connectionGateway.sendToUser(mensaje.getReceptor(), mensaje);
        } else {
            connectionGateway.broadcast(mensaje);
        }
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event.getType() == SessionEventType.MESSAGE_SENT && event.getSessionId() != null) {
            if (event.getPayload() instanceof Mensaje mensaje) {
                LOGGER.fine(() -> "Despachando mensaje recibido del bus para sesión " + event.getSessionId());
                entregar(mensaje);
            }
        } else if (event.getType() == SessionEventType.AUDIO_SENT && event.getSessionId() != null) {
            if (event.getPayload() instanceof Mensaje mensaje) {
                entregar(mensaje);
            }
        }
    }
}
