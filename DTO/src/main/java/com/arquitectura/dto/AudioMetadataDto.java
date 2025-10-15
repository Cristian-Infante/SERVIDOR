package com.arquitectura.dto;

public class AudioMetadataDto {

    private Long mensajeId;
    private Long emisorId;
    private Long receptorId;
    private Long canalId;
    private String ruta;
    private String mime;
    private Integer duracion;

    public Long getMensajeId() {
        return mensajeId;
    }

    public void setMensajeId(Long mensajeId) {
        this.mensajeId = mensajeId;
    }

    public Long getEmisorId() {
        return emisorId;
    }

    public void setEmisorId(Long emisorId) {
        this.emisorId = emisorId;
    }

    public Long getReceptorId() {
        return receptorId;
    }

    public void setReceptorId(Long receptorId) {
        this.receptorId = receptorId;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public String getRutaArchivo() {
        return ruta;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.ruta = rutaArchivo;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public Integer getDuracion() {
        return duracion;
    }

    public void setDuracion(Integer duracion) {
        this.duracion = duracion;
    }

    public Integer getDuracionSeg() {
        return duracion;
    }

    public void setDuracionSeg(Integer duracionSeg) {
        this.duracion = duracionSeg;
    }
}
