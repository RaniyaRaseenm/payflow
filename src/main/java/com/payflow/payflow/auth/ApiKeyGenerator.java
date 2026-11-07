package com.payflow.payflow.auth;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class ApiKeyGenerator {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();

    public record GeneratedKey(String fullKey, String prefix) { }

    public GeneratedKey generate() {
        String prefix = randomString(8);
        String secret = randomString(32);
        return new GeneratedKey("pf_live_" + prefix + "_" + secret, prefix);
    }

    /** Returns the 8-char prefix, or null if the key is not in our format. */
    public static String extractPrefix(String fullKey) {
        if (fullKey == null) return null;
        String[] parts = fullKey.split("_");
        if (parts.length != 4 || !parts[0].equals("pf") || !parts[1].equals("live")
                || parts[2].length() != 8 || parts[3].length() != 32) {
            return null;
        }
        return parts[2];
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}