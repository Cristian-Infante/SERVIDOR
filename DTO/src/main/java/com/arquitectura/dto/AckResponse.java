package com.arquitectura.dto;

public class AckResponse {

    private String message;

    public AckResponse() {
    }

    public AckResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
