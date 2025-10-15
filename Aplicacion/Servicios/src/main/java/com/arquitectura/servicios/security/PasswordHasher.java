package com.arquitectura.servicios.security;

public interface PasswordHasher {
    String hash(String rawPassword);
}
