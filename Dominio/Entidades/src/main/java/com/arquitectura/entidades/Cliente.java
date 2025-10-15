package com.arquitectura.entidades;

public class Cliente {
    private Long id;
    private String nombreDeUsuario;
    private String email;
    private String contrasenia;
    private byte[] foto;
    private String ip;
    private Boolean estado;

    public Cliente() {
    }

    public Cliente(Long id, String nombreDeUsuario, String email, String contrasenia, byte[] foto, String ip, Boolean estado) {
        this.id = id;
        this.nombreDeUsuario = nombreDeUsuario;
        this.email = email;
        this.contrasenia = contrasenia;
        this.foto = foto;
        this.ip = ip;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreDeUsuario() {
        return nombreDeUsuario;
    }

    public void setNombreDeUsuario(String nombreDeUsuario) {
        this.nombreDeUsuario = nombreDeUsuario;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContrasenia() {
        return contrasenia;
    }

    public void setContrasenia(String contrasenia) {
        this.contrasenia = contrasenia;
    }

    public byte[] getFoto() {
        return foto;
    }

    public void setFoto(byte[] foto) {
        this.foto = foto;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Boolean getEstado() {
        return estado;
    }

    public void setEstado(Boolean estado) {
        this.estado = estado;
    }
}
