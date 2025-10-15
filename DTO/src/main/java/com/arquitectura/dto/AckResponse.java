package com.arquitectura.dto;

public class AckResponse {
    private boolean ok;
    private String message;

    public static AckResponse ok(String message) {
        AckResponse response = new AckResponse();
        response.setOk(true);
        response.setMessage(message);
        return response;
    }

    public static AckResponse fail(String message) {
        AckResponse response = new AckResponse();
        response.setOk(false);
        response.setMessage(message);
        return response;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
