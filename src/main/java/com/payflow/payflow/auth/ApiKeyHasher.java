package com.payflow.payflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ApiKeyHasher {

    private final String pepper;

    public ApiKeyHasher(@Value("${payflow.security.api-key-pepper}") String pepper) {
        this.pepper = pepper;
    }

    public String hash(String fullKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((pepper + fullKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matches(String fullKey, String expectedHash) {
        return MessageDigest.isEqual(
                hash(fullKey).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
