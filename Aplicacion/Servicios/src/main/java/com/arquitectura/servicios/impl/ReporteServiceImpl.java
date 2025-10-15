package com.arquitectura.servicios.impl;

import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Log;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.ReporteService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReporteServiceImpl implements ReporteService {

    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final LogRepository logRepository;

    public ReporteServiceImpl(ClienteRepository clienteRepository,
                              CanalRepository canalRepository,
                              MensajeRepository mensajeRepository,
                              LogRepository logRepository) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository);
        this.canalRepository = Objects.requireNonNull(canalRepository);
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository);
        this.logRepository = Objects.requireNonNull(logRepository);
    }

    @Override
    public List<Cliente> usuariosRegistrados() {
        return clienteRepository.all();
    }

    @Override
    public Map<Canal, List<Cliente>> canalesConUsuarios() {
        Map<Canal, List<Cliente>> resultado = new LinkedHashMap<>();
        for (Canal canal : canalRepository.findAll()) {
            resultado.put(canal, canalRepository.findUsers(canal.getId()));
        }
        return resultado;
    }

    @Override
    public List<Cliente> usuariosConectados() {
        return clienteRepository.findConnected();
    }

    @Override
    public List<Mensaje> textoDeMensajesDeAudio() {
        return mensajeRepository.findTextAudioLogs();
    }

    @Override
    public List<Log> logs() {
        return logRepository.findAll();
    }
}
