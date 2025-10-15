package com.arquitectura.servicios;

import com.arquitectura.dto.MessageRequest;
import com.arquitectura.entidades.Mensaje;

public interface MensajeriaService {
    Mensaje enviarMensajeAUsuario(MessageRequest request);

    Mensaje enviarMensajeACanal(MessageRequest request);
}
