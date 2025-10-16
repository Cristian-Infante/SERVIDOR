package com.arquitectura.servicios;

import java.util.List;

import com.arquitectura.dto.AudioMetadataDto;
import com.arquitectura.dto.ChannelSummary;
import com.arquitectura.dto.LogEntryDto;
import com.arquitectura.dto.UserSummary;

public interface ReporteService {
    List<UserSummary> usuariosRegistrados(Long excluirUsuarioId);

    List<ChannelSummary> canalesConUsuarios();
    
    List<ChannelSummary> canalesAccesiblesParaUsuario(Long usuarioId);

    List<UserSummary> usuariosConectados(Long excluirUsuarioId);

    List<AudioMetadataDto> textoDeMensajesDeAudio();

    List<LogEntryDto> logs();
}
