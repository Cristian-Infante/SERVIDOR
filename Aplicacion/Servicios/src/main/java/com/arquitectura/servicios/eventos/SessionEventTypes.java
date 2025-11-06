package com.arquitectura.servicios.eventos;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilidades para trabajar con {@link SessionEventType} cuando existen diferencias
 * entre versiones desplegadas del servidor dentro del clúster.
 *
 * <p>Algunos nodos pueden ejecutar binarios anteriores que todavía no definen
 * nuevos tipos de eventos. Acceder directamente a la constante del enum en esas
 * instancias provoca errores de carga de clases. Esta utilidad realiza la
 * resolución de manera segura, devolviendo {@code null} cuando el tipo no está
 * disponible en tiempo de ejecución.</p>
 */
public final class SessionEventTypes {

    private static final Logger LOGGER = Logger.getLogger(SessionEventTypes.class.getName());

    private static final String USER_REGISTERED_NAME = "USER_REGISTERED";
    private static final SessionEventType USER_REGISTERED_TYPE = resolve(USER_REGISTERED_NAME);

    private SessionEventTypes() {
        // Utility class
    }

    /**
     * Devuelve el tipo de evento {@code USER_REGISTERED} cuando está disponible.
     *
     * @return el tipo de evento o {@code null} si la constante no existe en esta versión.
     */
    public static SessionEventType userRegistered() {
        return USER_REGISTERED_TYPE;
    }

    /**
     * Verifica si el tipo proporcionado corresponde a {@code USER_REGISTERED} y
     * si la constante está soportada en la versión en ejecución.
     *
     * @param type tipo de evento a comprobar.
     * @return {@code true} cuando representa a {@code USER_REGISTERED}.
     */
    public static boolean isUserRegistered(SessionEventType type) {
        return USER_REGISTERED_TYPE != null && USER_REGISTERED_TYPE == type;
    }

    private static SessionEventType resolve(String name) {
        try {
            return SessionEventType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, () -> "SessionEventType " + name + " no disponible en esta versión del servidor");
            return null;
        }
    }
}
