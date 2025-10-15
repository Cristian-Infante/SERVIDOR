package com.arquitectura.dto;

/**
 * Representa la solicitud de envío de un mensaje desde la capa de presentación hacia
 * los servicios de mensajería. Agrupa los datos necesarios para construir diferentes
 * tipos de mensajes (texto, audio o archivo) manteniendo un contrato estable entre
 * módulos.
 */
public class MessageRequest {

    private String tipo;
    private String contenido;
    private String rutaArchivo;
    private String mime;
    private Integer duracionSeg;
    private Long emisor;
    private Long receptor;
    private Long canalId;

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
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

    public Integer getDuracionSeg() {
        return duracionSeg;
    }

    public void setDuracionSeg(Integer duracionSeg) {
        this.duracionSeg = duracionSeg;
    }

    public Long getEmisor() {
        return emisor;
    }

    public void setEmisor(Long emisor) {
        this.emisor = emisor;
    }

    public Long getEmisorId() {
        return emisor;
    }

    public void setEmisorId(Long emisorId) {
        this.emisor = emisorId;
    }

    public Long getReceptor() {
        return receptor;
    }

    public void setReceptor(Long receptor) {
        this.receptor = receptor;
    }

    public Long getReceptorId() {
        return receptor;
    }

    public void setReceptorId(Long receptorId) {
        this.receptor = receptorId;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }
}
