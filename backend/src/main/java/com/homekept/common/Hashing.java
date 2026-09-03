package com.homekept.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared SHA-256 helper. Used for idempotency keys (Stripe) and token hashes (refresh,
 * password-reset, activation) — never for password storage, which goes through
 * {@code PasswordEncoder}.
 */
public final class Hashing {

    private Hashing() {
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of {@code input} (UTF-8 encoded).
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec — never thrown in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
