package com.arquitectura.dto;

/**
 * Request para subir un archivo de audio al servidor
 * El cliente envía el contenido del audio en base64
 */
public class UploadAudioRequest {
    
    private String audioBase64;  // Contenido del audio codificado en Base64
    private String mime;          // Tipo MIME (audio/wav, audio/mpeg, etc.)
    private Integer duracionSeg;  // Duración en segundos
    private String nombreArchivo; // Nombre original del archivo (opcional)
    
    public String getAudioBase64() {
        return audioBase64;
    }
    
    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }
    
    public String getMime() {
        return mime;
    }
    
    public void setMime(String mime) {
        this.mime = mime;
    }
    
    public Integer getDuracionSeg() {
        return duracionSeg;
    }
    
    public void setDuracionSeg(Integer duracionSeg) {
        this.duracionSeg = duracionSeg;
    }
    
    public String getNombreArchivo() {
        return nombreArchivo;
    }
    
    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }
}

