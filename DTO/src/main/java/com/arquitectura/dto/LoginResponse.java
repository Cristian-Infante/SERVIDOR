package com.arquitectura.dto;

public class LoginResponse extends AckResponse {

    private String fotoBase64;

    public LoginResponse() {
    }

    public LoginResponse(String message, String fotoBase64) {
        super(true, message);
        this.fotoBase64 = fotoBase64;
    }

    public LoginResponse(boolean success, String message, String fotoBase64) {
        super(success, message);
        this.fotoBase64 = fotoBase64;
    }

    public String getFotoBase64() {
        return fotoBase64;
    }

    public void setFotoBase64(String fotoBase64) {
        this.fotoBase64 = fotoBase64;
    }
}
