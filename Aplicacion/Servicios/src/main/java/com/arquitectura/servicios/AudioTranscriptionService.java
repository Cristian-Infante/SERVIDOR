package com.arquitectura.servicios;

/**
 * Servicio para transcribir archivos de audio a texto
 */
public interface AudioTranscriptionService {
    
    /**
     * Transcribe un archivo de audio a texto en español
     * 
     * @param audioFilePath Ruta del archivo de audio
     * @return Texto transcrito del audio
     */
    String transcribir(String audioFilePath);
}
