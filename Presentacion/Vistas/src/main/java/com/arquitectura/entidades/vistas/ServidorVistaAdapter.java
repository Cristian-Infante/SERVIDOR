package com.arquitectura.entidades.vistas;

import com.arquitectura.controladores.ServidorView;

public class ServidorVistaAdapter extends ServidorVista implements ServidorView {
    
    public ServidorVistaAdapter() {
        super(); // Usa el constructor por defecto de ServidorVista
    }
    
    public ServidorVistaAdapter(int maxConnections) {
        super(maxConnections); // Usa el constructor con configuración
    }
    
    @Override
    public java.awt.Component asComponent() {
        return this;
    }
}
