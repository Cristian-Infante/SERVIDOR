package com.arquitectura.entidades;

public class AudioMensaje extends Mensaje {
    private String rutaArchivo;
    private String mime;
    private int duracionSeg;

    public AudioMensaje() {
    }

    public AudioMensaje(String rutaArchivo, String mime, int duracionSeg) {
        this.rutaArchivo = rutaArchivo;
        this.mime = mime;
        this.duracionSeg = duracionSeg;
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

    public int getDuracionSeg() {
        return duracionSeg;
    }

    public void setDuracionSeg(int duracionSeg) {
        this.duracionSeg = duracionSeg;
    }
}

