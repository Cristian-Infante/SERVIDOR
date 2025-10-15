package com.arquitectura.servicios.impl;

import com.arquitectura.dto.AudioMetadataDto;
import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;
import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.ReporteService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ReporteServiceImpl implements ReporteService {

    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final LogRepository logRepository;

    public ReporteServiceImpl(ClienteRepository clienteRepository,
                              CanalRepository canalRepository,
                              MensajeRepository mensajeRepository,
                              LogRepository logRepository) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
    }

    @Override
    public List<UserSummary> usuariosRegistrados() {
        return clienteRepository.all().stream()
                .map(cli -> new UserSummary(cli.getId(), cli.getNombreDeUsuario(), cli.getEmail(), Boolean.TRUE.equals(cli.getEstado())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChannelSummary> canalesConUsuarios() {
        return canalRepository.findAll().stream()
                .map(canal -> {
                    ChannelSummary summary = new ChannelSummary(canal.getId(), canal.getNombre(), Boolean.TRUE.equals(canal.getPrivado()));
                    List<UserSummary> usuarios = canalRepository.findUsers(canal.getId()).stream()
                            .map(cli -> new UserSummary(cli.getId(), cli.getNombreDeUsuario(), cli.getEmail(), Boolean.TRUE.equals(cli.getEstado())))
                            .collect(Collectors.toCollection(ArrayList::new));
                    summary.setUsuarios(usuarios);
                    return summary;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<UserSummary> usuariosConectados() {
        return clienteRepository.findConnected().stream()
                .map(cli -> new UserSummary(cli.getId(), cli.getNombreDeUsuario(), cli.getEmail(), true))
                .collect(Collectors.toList());
    }

    @Override
    public List<AudioMetadataDto> textoDeMensajesDeAudio() {
        List<AudioMetadataDto> audios = new ArrayList<>();
        for (Mensaje mensaje : mensajeRepository.findTextAudioLogs()) {
            if (mensaje instanceof AudioMensaje audio) {
                AudioMetadataDto dto = new AudioMetadataDto();
                dto.setMensajeId(mensaje.getId());
                dto.setEmisorId(mensaje.getEmisor());
                dto.setReceptorId(mensaje.getReceptor());
                dto.setCanalId(mensaje.getCanalId());
                dto.setRutaArchivo(audio.getRutaArchivo());
                dto.setMime(audio.getMime());
                dto.setDuracionSeg(audio.getDuracionSeg());
                audios.add(dto);
            } else if (mensaje instanceof ArchivoMensaje archivo) {
                AudioMetadataDto dto = new AudioMetadataDto();
                dto.setMensajeId(mensaje.getId());
                dto.setEmisorId(mensaje.getEmisor());
                dto.setReceptorId(mensaje.getReceptor());
                dto.setCanalId(mensaje.getCanalId());
                dto.setRutaArchivo(archivo.getRutaArchivo());
                dto.setMime(archivo.getMime());
                audios.add(dto);
            }
        }
        return audios;
    }

    @Override
    public List<LogEntryDto> logs() {
        return logRepository.findAll().stream()
                .map(log -> new LogEntryDto(log.getId(), Boolean.TRUE.equals(log.getTipo()), log.getDetalle(), log.getFechaHora()))
                .collect(Collectors.toList());
    }
}
