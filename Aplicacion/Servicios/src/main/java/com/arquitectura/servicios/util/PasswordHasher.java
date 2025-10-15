package com.arquitectura.servicios.util;

import com.arquitectura.configdb.DBConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utilitario para hashear contraseñas utilizando SHA-256 con un salt configurable.
 */
public final class PasswordHasher {

    private static final String DEFAULT_SALT = "chat-salt";

    private PasswordHasher() {
    }

    public static String hash(String plain) {
        DBConfig config = DBConfig.getInstance();
        String salt = config.getProperty("security.salt", DEFAULT_SALT);
        String toHash = salt + plain;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
