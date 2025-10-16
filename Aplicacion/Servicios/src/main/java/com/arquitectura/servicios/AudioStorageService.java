package com.arquitectura.servicios;

/**
 * Servicio para almacenar archivos de audio en el servidor
 */
public interface AudioStorageService {
    
    /**
     * Guarda un archivo de audio en el servidor
     * 
     * @param audioBase64 Contenido del audio codificado en Base64
     * @param usuarioId ID del usuario que sube el audio
     * @param mime Tipo MIME del audio
     * @return Ruta del archivo guardado
     * @throws IllegalArgumentException si los datos son inválidos
     */
    String guardarAudio(String audioBase64, Long usuarioId, String mime);
    
    /**
     * Verifica si un archivo de audio existe
     * 
     * @param rutaArchivo Ruta del archivo
     * @return true si existe, false en caso contrario
     */
    boolean existeAudio(String rutaArchivo);
    
    /**
     * Elimina un archivo de audio
     * 
     * @param rutaArchivo Ruta del archivo a eliminar
     * @return true si se eliminó correctamente
     */
    boolean eliminarAudio(String rutaArchivo);
}

