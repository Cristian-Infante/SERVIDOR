package com.arquitectura.servicios.impl;

import com.arquitectura.servicios.AudioStorageService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación del servicio de almacenamiento de audio
 * Guarda los archivos en el sistema de archivos del servidor
 */
public class AudioStorageServiceImpl implements AudioStorageService {
    
    private static final Logger LOGGER = Logger.getLogger(AudioStorageServiceImpl.class.getName());
    private static final String BASE_AUDIO_PATH = "media/audio/usuarios";
    
    @Override
    public String guardarAudio(String audioBase64, Long usuarioId, String mime) {
        if (audioBase64 == null || audioBase64.isEmpty()) {
            throw new IllegalArgumentException("El contenido del audio no puede estar vacío");
        }
        
        if (usuarioId == null) {
            throw new IllegalArgumentException("El ID de usuario es requerido");
        }
        
        try {
            // Decodificar el audio desde Base64
            byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
            
            // Crear directorio del usuario si no existe
            Path userDir = Paths.get(BASE_AUDIO_PATH, usuarioId.toString());
            Files.createDirectories(userDir);
            
            // Generar nombre único para el archivo
            String extension = getExtensionFromMime(mime);
            String nombreArchivo = "rec_" + System.currentTimeMillis() + extension;
            Path rutaCompleta = userDir.resolve(nombreArchivo);
            
            // Guardar el archivo
            Files.write(rutaCompleta, audioBytes);
            
            String rutaRelativa = BASE_AUDIO_PATH + "/" + usuarioId + "/" + nombreArchivo;
            LOGGER.info(() -> "Audio guardado exitosamente en: " + rutaRelativa + 
                             " (" + audioBytes.length + " bytes)");
            
            return rutaRelativa;
            
        } catch (IllegalArgumentException e) {
            LOGGER.warning(() -> "Error decodificando audio Base64: " + e.getMessage());
            throw new IllegalArgumentException("El audio Base64 es inválido", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error guardando archivo de audio", e);
            throw new IllegalStateException("No se pudo guardar el archivo de audio", e);
        }
    }
    
    @Override
    public boolean existeAudio(String rutaArchivo) {
        if (rutaArchivo == null || rutaArchivo.isEmpty()) {
            return false;
        }
        File file = new File(rutaArchivo);
        return file.exists() && file.isFile();
    }
    
    @Override
    public boolean eliminarAudio(String rutaArchivo) {
        if (rutaArchivo == null || rutaArchivo.isEmpty()) {
            return false;
        }
        try {
            Path path = Paths.get(rutaArchivo);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error eliminando archivo de audio: " + rutaArchivo, e);
            return false;
        }
    }

    @Override
    public String cargarAudioBase64(String rutaArchivo) {
        if (rutaArchivo == null || rutaArchivo.isBlank()) {
            throw new IllegalArgumentException("La ruta del audio no puede estar vacía");
        }
        Path path = Paths.get(rutaArchivo);
        try {
            if (!Files.exists(path)) {
                LOGGER.warning(() -> "Archivo de audio no encontrado: " + path.toAbsolutePath());
                return null;
            }

            byte[] audioBytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(audioBytes);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    () -> "Error leyendo archivo de audio " + path.toAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene la extensión del archivo según el tipo MIME
     */
    private String getExtensionFromMime(String mime) {
        if (mime == null) {
            return ".bin";
        }
        return switch (mime.toLowerCase()) {
            case "audio/wav", "audio/wave", "audio/x-wav" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "audio/webm" -> ".webm";
            case "audio/aac" -> ".aac";
            case "audio/m4a" -> ".m4a";
            default -> ".bin";
        };
    }
}

