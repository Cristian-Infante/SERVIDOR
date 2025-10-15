package com.arquitectura.servicios;

import com.arquitectura.entidades.Mensaje;

public interface MensajeriaService {

    Mensaje enviarMensajeATexto(Long emisorId, Long receptorId, String contenido);

    Mensaje enviarMensajeACanalTexto(Long emisorId, Long canalId, String contenido);

    Mensaje enviarMensajeAudio(Long emisorId, Long receptorId, Long canalId, String ruta, String mime, int duracion);

    Mensaje enviarMensajeArchivo(Long emisorId, Long receptorId, Long canalId, String ruta, String mime);
}
