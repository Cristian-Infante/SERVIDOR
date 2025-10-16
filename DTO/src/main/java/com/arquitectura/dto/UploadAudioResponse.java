package com.arquitectura.dto;

/**
 * Respuesta al subir un archivo de audio
 * Contiene la ruta donde se guardó el archivo en el servidor
 */
public class UploadAudioResponse {
    
    private String rutaArchivo;  // Ruta donde se guardó el archivo
    private String mensaje;       // Mensaje informativo
    private boolean exito;        // Si la subida fue exitosa
    
    public UploadAudioResponse() {
    }
    
    public UploadAudioResponse(boolean exito, String rutaArchivo, String mensaje) {
        this.exito = exito;
        this.rutaArchivo = rutaArchivo;
        this.mensaje = mensaje;
    }
    
    public String getRutaArchivo() {
        return rutaArchivo;
    }
    
    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }
    
    public String getMensaje() {
        return mensaje;
    }
    
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
    
    public boolean isExito() {
        return exito;
    }
    
    public void setExito(boolean exito) {
        this.exito = exito;
    }
}

