package com.arquitectura.dto;

/**
 * Notificación del servidor para eventos importantes
 */
public class ServerNotification {
    
    private String tipo;      // KICKED, SERVER_SHUTDOWN, etc.
    private String mensaje;   // Mensaje descriptivo
    private String razon;     // Razón (opcional)
    
    public ServerNotification() {
    }
    
    public ServerNotification(String tipo, String mensaje) {
        this.tipo = tipo;
        this.mensaje = mensaje;
    }
    
    public ServerNotification(String tipo, String mensaje, String razon) {
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.razon = razon;
    }
    
    public String getTipo() {
        return tipo;
    }
    
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
    
    public String getMensaje() {
        return mensaje;
    }
    
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
    
    public String getRazon() {
        return razon;
    }
    
    public void setRazon(String razon) {
        this.razon = razon;
    }
}

