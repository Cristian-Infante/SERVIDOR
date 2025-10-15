package com.arquitectura.entidades;

public class TextoMensaje extends Mensaje {
    private String contenido;

    public TextoMensaje() {
    }

    public TextoMensaje(String contenido) {
        this.contenido = contenido;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }
}

