package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.*;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.MensajeriaService;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class MensajeriaServiceImpl implements MensajeriaService {

    private final MensajeRepository mensajeRepository;
    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final LogRepository logRepository;
    private final SessionEventBus eventBus;

    public MensajeriaServiceImpl(MensajeRepository mensajeRepository,
                                 ClienteRepository clienteRepository,
                                 CanalRepository canalRepository,
                                 LogRepository logRepository,
                                 SessionEventBus eventBus) {
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository);
        this.clienteRepository = Objects.requireNonNull(clienteRepository);
        this.canalRepository = Objects.requireNonNull(canalRepository);
        this.logRepository = Objects.requireNonNull(logRepository);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public Mensaje enviarMensajeATexto(Long emisorId, Long receptorId, String contenido) {
        validarUsuario(emisorId);
        validarUsuario(receptorId);
        TextoMensaje mensaje = MensajeFactory.crearMensajeTexto(contenido, emisorId, receptorId, null);
        Mensaje guardado = mensajeRepository.save(mensaje);
        registrarLog("Mensaje directo enviado de " + emisorId + " a " + receptorId);
        eventBus.publish(new SessionEvent(SessionEvent.Type.MESSAGE_SENT, Map.of(
                "tipo", "TEXTO",
                "mensajeId", guardado.getId(),
                "emisor", emisorId,
                "receptor", receptorId
        )));
        return guardado;
    }

    @Override
    public Mensaje enviarMensajeACanalTexto(Long emisorId, Long canalId, String contenido) {
        validarUsuario(emisorId);
        canalRepository.findById(canalId).orElseThrow(() -> new IllegalArgumentException("Canal no encontrado"));
        TextoMensaje mensaje = MensajeFactory.crearMensajeTexto(contenido, emisorId, null, canalId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        registrarLog("Mensaje a canal " + canalId + " enviado por " + emisorId);
        eventBus.publish(new SessionEvent(SessionEvent.Type.MESSAGE_SENT, Map.of(
                "tipo", "TEXTO",
                "mensajeId", guardado.getId(),
                "emisor", emisorId,
                "canal", canalId
        )));
        return guardado;
    }

    @Override
    public Mensaje enviarMensajeAudio(Long emisorId, Long receptorId, Long canalId, String ruta, String mime, int duracion) {
        validarUsuario(emisorId);
        if (canalId != null) {
            canalRepository.findById(canalId).orElseThrow(() -> new IllegalArgumentException("Canal no encontrado"));
        }
        if (receptorId != null) {
            validarUsuario(receptorId);
        }
        AudioMensaje mensaje = MensajeFactory.crearMensajeAudio(ruta, duracion, mime, emisorId, receptorId, canalId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        registrarLog("Audio enviado por " + emisorId);
        eventBus.publish(new SessionEvent(SessionEvent.Type.AUDIO_SENT, Map.of(
                "mensajeId", guardado.getId(),
                "ruta", ruta,
                "mime", mime
        )));
        return guardado;
    }

    @Override
    public Mensaje enviarMensajeArchivo(Long emisorId, Long receptorId, Long canalId, String ruta, String mime) {
        validarUsuario(emisorId);
        if (canalId != null) {
            canalRepository.findById(canalId).orElseThrow(() -> new IllegalArgumentException("Canal no encontrado"));
        }
        if (receptorId != null) {
            validarUsuario(receptorId);
        }
        ArchivoMensaje mensaje = MensajeFactory.crearMensajeArchivo(ruta, mime, emisorId, receptorId, canalId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        registrarLog("Archivo enviado por " + emisorId);
        eventBus.publish(new SessionEvent(SessionEvent.Type.MESSAGE_SENT, Map.of(
                "tipo", "ARCHIVO",
                "mensajeId", guardado.getId()
        )));
        return guardado;
    }

    private void validarUsuario(Long id) {
        clienteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
    }

    private void registrarLog(String detalle) {
        Log log = new Log();
        log.setDetalle(detalle);
        log.setTipo(Boolean.TRUE);
        log.setFechaHora(LocalDateTime.now());
        logRepository.append(log);
    }
}
