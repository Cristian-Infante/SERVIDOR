package com.arquitectura.dto;

import java.util.ArrayList;
import java.util.List;

public class ChannelSummary {

    private Long id;
    private String nombre;
    private boolean privado;
    private List<UserSummary> usuarios = new ArrayList<>();

    public ChannelSummary() {
    }

    public ChannelSummary(Long id, String nombre, boolean privado) {
        this.id = id;
        this.nombre = nombre;
        this.privado = privado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isPrivado() {
        return privado;
    }

    public void setPrivado(boolean privado) {
        this.privado = privado;
    }

    public List<UserSummary> getUsuarios() {
        return usuarios;
    }

    public void setUsuarios(List<UserSummary> usuarios) {
        this.usuarios = usuarios;
    }
}
