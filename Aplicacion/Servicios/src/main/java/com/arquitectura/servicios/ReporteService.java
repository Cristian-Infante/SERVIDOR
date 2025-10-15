package com.arquitectura.servicios;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Log;
import com.arquitectura.entidades.Mensaje;

import java.util.List;
import java.util.Map;

public interface ReporteService {

    List<Cliente> usuariosRegistrados();

    Map<Canal, List<Cliente>> canalesConUsuarios();

    List<Cliente> usuariosConectados();

    List<Mensaje> textoDeMensajesDeAudio();

    List<Log> logs();
}
