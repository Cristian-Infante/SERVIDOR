package com.arquitectura.entidades;

import java.time.LocalDateTime;

/**
 * Patrón Factory para crear instancias de diferentes tipos de mensajes.
 * Facilita la creación de TextoMensaje, AudioMensaje y ArchivoMensaje.
 */
public class MensajeFactory {

    /**
     * Tipos de mensajes soportados por la factory
     */
    public enum TipoMensaje {
        TEXTO,
        AUDIO,
        ARCHIVO
    }

    /**
     * Crea un mensaje de texto
     * 
     * @param contenido El contenido del mensaje de texto
     * @return Una instancia de TextoMensaje
     */
    public static TextoMensaje crearMensajeTexto(String contenido) {
        TextoMensaje mensaje = new TextoMensaje();
        mensaje.setContenido(contenido);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("TEXTO");
        return mensaje;
    }

    /**
     * Crea un mensaje de texto con emisor y receptor
     * 
     * @param contenido El contenido del mensaje
     * @param emisorId ID del usuario emisor
     * @param receptorId ID del usuario receptor (null si es a un canal)
     * @param canalId ID del canal (null si es a un usuario)
     * @return Una instancia de TextoMensaje configurada
     */
    public static TextoMensaje crearMensajeTexto(String contenido, Long emisorId, Long receptorId, Long canalId) {
        TextoMensaje mensaje = new TextoMensaje();
        mensaje.setContenido(contenido);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("TEXTO");
        mensaje.setEmisor(emisorId);
        mensaje.setReceptor(receptorId);
        mensaje.setCanalId(canalId);
        return mensaje;
    }

    /**
     * Crea un mensaje de audio
     * 
     * @param rutaArchivo Ruta donde se almacenó el archivo de audio
     * @param duracionSeg Duración del audio en segundos
     * @param mime Tipo MIME del archivo (ej: "audio/mpeg", "audio/wav")
     * @return Una instancia de AudioMensaje
     */
    public static AudioMensaje crearMensajeAudio(String rutaArchivo, int duracionSeg, String mime) {
        AudioMensaje mensaje = new AudioMensaje();
        mensaje.setRutaArchivo(rutaArchivo);
        mensaje.setDuracionSeg(duracionSeg);
        mensaje.setMime(mime);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("AUDIO");
        return mensaje;
    }

    /**
     * Crea un mensaje de audio con emisor y receptor
     * 
     * @param rutaArchivo Ruta del archivo de audio
     * @param duracionSeg Duración en segundos
     * @param mime Tipo MIME
     * @param emisorId ID del emisor
     * @param receptorId ID del receptor (null si es a un canal)
     * @param canalId ID del canal (null si es a un usuario)
     * @return Una instancia de AudioMensaje configurada
     */
    public static AudioMensaje crearMensajeAudio(String rutaArchivo, int duracionSeg, String mime, 
                                                  Long emisorId, Long receptorId, Long canalId) {
        AudioMensaje mensaje = new AudioMensaje();
        mensaje.setRutaArchivo(rutaArchivo);
        mensaje.setDuracionSeg(duracionSeg);
        mensaje.setMime(mime);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("AUDIO");
        mensaje.setEmisor(emisorId);
        mensaje.setReceptor(receptorId);
        mensaje.setCanalId(canalId);
        return mensaje;
    }

    /**
     * Crea un mensaje de archivo
     * 
     * @param rutaArchivo Ruta donde se almacenó el archivo
     * @param mime Tipo MIME del archivo
     * @return Una instancia de ArchivoMensaje
     */
    public static ArchivoMensaje crearMensajeArchivo(String rutaArchivo, String mime) {
        ArchivoMensaje mensaje = new ArchivoMensaje();
        mensaje.setRutaArchivo(rutaArchivo);
        mensaje.setMime(mime);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("ARCHIVO");
        return mensaje;
    }

    /**
     * Crea un mensaje de archivo con emisor y receptor
     * 
     * @param rutaArchivo Ruta del archivo
     * @param mime Tipo MIME
     * @param emisorId ID del emisor
     * @param receptorId ID del receptor (null si es a un canal)
     * @param canalId ID del canal (null si es a un usuario)
     * @return Una instancia de ArchivoMensaje configurada
     */
    public static ArchivoMensaje crearMensajeArchivo(String rutaArchivo, String mime, 
                                                      Long emisorId, Long receptorId, Long canalId) {
        ArchivoMensaje mensaje = new ArchivoMensaje();
        mensaje.setRutaArchivo(rutaArchivo);
        mensaje.setMime(mime);
        mensaje.setTimeStamp(LocalDateTime.now());
        mensaje.setTipo("ARCHIVO");
        mensaje.setEmisor(emisorId);
        mensaje.setReceptor(receptorId);
        mensaje.setCanalId(canalId);
        return mensaje;
    }

    /**
     * Crea un mensaje según el tipo especificado (método genérico)
     * 
     * @param tipo Tipo de mensaje a crear
     * @param datos Datos específicos según el tipo (contenido, ruta, etc.)
     * @return Una instancia del tipo de mensaje solicitado
     */
    public static Mensaje crearMensaje(TipoMensaje tipo, Object... datos) {
        switch (tipo) {
            case TEXTO:
                String contenido = datos.length > 0 ? (String) datos[0] : "";
                return crearMensajeTexto(contenido);
                
            case AUDIO:
                String rutaAudio = datos.length > 0 ? (String) datos[0] : "";
                int duracion = datos.length > 1 ? (Integer) datos[1] : 0;
                String mimeAudio = datos.length > 2 ? (String) datos[2] : "audio/mpeg";
                return crearMensajeAudio(rutaAudio, duracion, mimeAudio);
                
            case ARCHIVO:
                String rutaArchivo = datos.length > 0 ? (String) datos[0] : "";
                String mimeArchivo = datos.length > 1 ? (String) datos[1] : "application/octet-stream";
                return crearMensajeArchivo(rutaArchivo, mimeArchivo);
                
            default:
                throw new IllegalArgumentException("Tipo de mensaje no soportado: " + tipo);
        }
    }
}
