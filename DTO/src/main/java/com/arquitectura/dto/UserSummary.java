package com.arquitectura.dto;

public class UserSummary {

    private Long id;
    private String usuario;
    private String email;
    private boolean conectado;

    public UserSummary() {
    }

    public UserSummary(Long id, String usuario, String email, boolean conectado) {
        this.id = id;
        this.usuario = usuario;
        this.email = email;
        this.conectado = conectado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isConectado() {
        return conectado;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }
}
