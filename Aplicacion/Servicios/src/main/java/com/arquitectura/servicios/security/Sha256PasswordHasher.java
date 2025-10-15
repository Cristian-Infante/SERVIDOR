package com.arquitectura.servicios.security;

import com.arquitectura.configdb.DBConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256PasswordHasher implements PasswordHasher {

    private final String salt;

    public Sha256PasswordHasher(DBConfig config) {
        String configuredSalt = config.getProperty("security.salt");
        this.salt = configuredSalt != null ? configuredSalt : "chat-academico";
    }

    @Override
    public String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((rawPassword + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
