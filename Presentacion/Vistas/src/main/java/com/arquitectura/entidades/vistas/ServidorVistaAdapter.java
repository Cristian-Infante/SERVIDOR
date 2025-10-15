package com.arquitectura.entidades.vistas;

import com.arquitectura.controladores.ServidorView;

public class ServidorVistaAdapter extends ServidorVista implements ServidorView {
    @Override
    public java.awt.Component asComponent() {
        return this;
    }
}
