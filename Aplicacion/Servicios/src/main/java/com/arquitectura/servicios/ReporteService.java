package com.arquitectura.servicios;

import com.arquitectura.dto.AudioMetadataDto;
import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;

import java.util.List;

public interface ReporteService {
    List<UserSummary> usuariosRegistrados();

    List<ChannelSummary> canalesConUsuarios();

    List<UserSummary> usuariosConectados();

    List<AudioMetadataDto> textoDeMensajesDeAudio();

    List<LogEntryDto> logs();
}
