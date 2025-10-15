package com.arquitectura.entidades;

public class ArchivoMensaje extends Mensaje {
    private String rutaArchivo;
    private String mime;

    public ArchivoMensaje() {
    }

    public ArchivoMensaje(String rutaArchivo, String mime) {
        this.rutaArchivo = rutaArchivo;
        this.mime = mime;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
