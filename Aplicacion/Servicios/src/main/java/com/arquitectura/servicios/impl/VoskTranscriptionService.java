package com.arquitectura.servicios.impl;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
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
        
        try (AudioInputStream originalStream = AudioSystem.getAudioInputStream(audioFile)) {
            AudioInputStream preparedStream = prepareStreamForVosk(originalStream);
            if (preparedStream == null) {
                return "[Formato de audio no compatible - requiere PCM 16kHz mono]";
            }

            try (AudioInputStream stream = preparedStream;
                 Recognizer recognizer = new Recognizer(model, 16000)) {
                StringBuilder transcripcion = new StringBuilder();
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Nota: ignoramos los resultados parciales porque suelen repetir el contenido
                // previo y generan transcripciones con palabras duplicadas.
                while ((bytesRead = stream.read(buffer)) != -1) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult();
                        appendIfNotBlank(transcripcion, extractText(result));
                    }
                }

                // Obtener el resultado final que pueda quedar pendiente al terminar el audio
                String finalText = extractText(recognizer.getFinalResult());
                appendIfNotBlank(transcripcion, finalText);

                String texto = transcripcion.toString().trim();
                return texto.isEmpty() ? "[Sin contenido de voz detectado]" : texto;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error transcribiendo audio: " + audioFilePath, e);
            return "[Error en transcripción: " + e.getMessage() + "]";
        }
    }

    private AudioInputStream prepareStreamForVosk(AudioInputStream source) {
        try {
            AudioInputStream currentStream = source;
            AudioFormat format = currentStream.getFormat();

            if (!Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.getSampleSizeInBits() != 16) {
                AudioFormat pcmFormat = new AudioFormat(
                        Encoding.PCM_SIGNED,
                        format.getSampleRate(),
                        16,
                        format.getChannels(),
                        Math.max(1, format.getChannels()) * 2,
                        format.getSampleRate(),
                        false);
                if (!AudioSystem.isConversionSupported(pcmFormat, format)) {
                    final AudioFormat formatSnapshot = format;
                    LOGGER.warning(() -> "No se puede convertir el audio a PCM_SIGNED 16 bits desde formato: " + formatSnapshot);
                    return null;
                }
                currentStream = AudioSystem.getAudioInputStream(pcmFormat, currentStream);
                format = currentStream.getFormat();
            }

            if (format.getChannels() != 1) {
                int frameSize = Math.max(1, format.getSampleSizeInBits() / 8);
                AudioFormat monoFormat = new AudioFormat(
                        format.getEncoding(),
                        format.getSampleRate(),
                        format.getSampleSizeInBits(),
                        1,
                        frameSize,
                        format.getSampleRate(),
                        format.isBigEndian());
                if (!AudioSystem.isConversionSupported(monoFormat, format)) {
                    final AudioFormat formatSnapshot = format;
                    LOGGER.warning(() -> "No se puede convertir el audio a mono desde formato: " + formatSnapshot);
                    return null;
                }
                currentStream = AudioSystem.getAudioInputStream(monoFormat, currentStream);
                format = currentStream.getFormat();
            }

            if (format.getSampleRate() != 16000f) {
                int frameSize = Math.max(1, format.getSampleSizeInBits() / 8) * format.getChannels();
                AudioFormat targetSampleRate = new AudioFormat(
                        format.getEncoding(),
                        16000f,
                        format.getSampleSizeInBits(),
                        format.getChannels(),
                        frameSize,
                        16000f,
                        format.isBigEndian());
                if (!AudioSystem.isConversionSupported(targetSampleRate, format)) {
                    final AudioFormat formatSnapshot = format;
                    LOGGER.warning(() -> "No se puede convertir el audio a 16kHz desde formato: " + formatSnapshot);
                    return null;
                }
                currentStream = AudioSystem.getAudioInputStream(targetSampleRate, currentStream);
                format = currentStream.getFormat();
            }

            if (format.isBigEndian()) {
                int frameSize = Math.max(1, format.getSampleSizeInBits() / 8) * format.getChannels();
                AudioFormat littleEndian = new AudioFormat(
                        format.getEncoding(),
                        format.getSampleRate(),
                        format.getSampleSizeInBits(),
                        format.getChannels(),
                        frameSize,
                        format.getSampleRate(),
                        false);
                if (!AudioSystem.isConversionSupported(littleEndian, format)) {
                    final AudioFormat formatSnapshot = format;
                    LOGGER.warning(() -> "No se puede convertir el audio a little-endian desde formato: " + formatSnapshot);
                    return null;
                }
                currentStream = AudioSystem.getAudioInputStream(littleEndian, currentStream);
            }

            return currentStream;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error preparando audio para Vosk", e);
            return null;
        }
    }

    private void appendIfNotBlank(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text.trim());
        }
    }

    private String extractText(String jsonResult) {
        // Extrae el texto del JSON {"text": "..."}
        try {
            if (jsonResult == null || jsonResult.isBlank()) {
                return "";
            }

            int start = jsonResult.indexOf("\"text\"");
            if (start == -1) {
                start = jsonResult.indexOf("\"partial\"");
            }
            if (start == -1) return "";

            int colon = jsonResult.indexOf(":", start);
            if (colon == -1) return "";

            int firstQuote = jsonResult.indexOf('"', colon + 1);
            if (firstQuote == -1) return "";

            int secondQuote = jsonResult.indexOf('"', firstQuote + 1);
            if (secondQuote == -1) return "";

            return jsonResult.substring(firstQuote + 1, secondQuote).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
