package com.arquitectura.servicios.security;

public interface PasswordHasher {
    String hash(String rawPassword);

    default boolean matches(String rawPassword, String hashedPassword) {
        return hash(rawPassword).equals(hashedPassword);
    }
}
