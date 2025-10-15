package com.arquitectura.dto;

public class AckResponse {

    private boolean success;
    private String message;

    public AckResponse() {
    }

    public AckResponse(String message) {
        this(true, message);
    }

    public AckResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AckResponse ok(String message) {
        return new AckResponse(true, message);
    }

    public static AckResponse fail(String message) {
        return new AckResponse(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
