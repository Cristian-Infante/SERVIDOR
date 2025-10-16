package com.arquitectura.servicios.impl;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.vosk.Model;
import org.vosk.Recognizer;

import com.arquitectura.servicios.AudioTranscriptionService;

/**
 * Implementación del servicio de transcripción usando Vosk
 * Requiere modelo de Vosk en español descargado en la carpeta models/
 */
public class VoskTranscriptionService implements AudioTranscriptionService {
    
    private static final Logger LOGGER = Logger.getLogger(VoskTranscriptionService.class.getName());
    private static final String MODEL_PATH = "models/vosk-model-small-es-0.42";
    private Model model;
    
    public VoskTranscriptionService() {
        try {
            File modelFile = new File(MODEL_PATH);
            if (modelFile.exists()) {
                this.model = new Model(MODEL_PATH);
                LOGGER.info("Modelo Vosk cargado correctamente desde: " + MODEL_PATH);
            } else {
                LOGGER.warning("Modelo Vosk no encontrado en: " + MODEL_PATH + 
                        ". La transcripción devolverá placeholder. " +
                        "Descarga el modelo desde: https://alphacephei.com/vosk/models");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando modelo Vosk", e);
        }
    }
    
    @Override
    public String transcribir(String audioFilePath) {
        // Si no hay modelo, retornar placeholder
        if (model == null) {
            return "[Transcripción automática no disponible - modelo no encontrado]";
        }
        
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            LOGGER.warning("Archivo de audio no encontrado: " + audioFilePath);
            return "[Audio no encontrado]";
        }
        
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat format = ais.getFormat();
            
            // Vosk requiere 16kHz mono
            if (format.getSampleRate() != 16000 || format.getChannels() != 1) {
                LOGGER.warning("Formato de audio no compatible. Requiere 16kHz mono.");
                return "[Formato de audio no compatible - requiere 16kHz mono]";
            }
            
            try (Recognizer recognizer = new Recognizer(model, 16000)) {
                StringBuilder transcripcion = new StringBuilder();
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = ais.read(buffer)) != -1) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult();
                        transcripcion.append(extractText(result)).append(" ");
                    }
                }
                
                // Obtener el resultado final
                String finalResult = recognizer.getFinalResult();
                transcripcion.append(extractText(finalResult));
                
                String texto = transcripcion.toString().trim();
                return texto.isEmpty() ? "[Sin contenido de voz detectado]" : texto;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error transcribiendo audio: " + audioFilePath, e);
            return "[Error en transcripción: " + e.getMessage() + "]";
        }
    }
    
    private String extractText(String jsonResult) {
        // Extrae el texto del JSON {"text": "..."}
        try {
            int start = jsonResult.indexOf("\"text\"");
            if (start == -1) return "";
            start = jsonResult.indexOf(":", start) + 1;
            int end = jsonResult.indexOf("\"", start + 1);
            if (end == -1) return "";
            return jsonResult.substring(start + 1, end).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
