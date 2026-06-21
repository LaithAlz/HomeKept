package com.homekept.identity;

import com.homekept.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and validates JWT access tokens using HMAC-SHA256 (HS256).
 *
 * <p>Implemented with standard Java {@link Mac} — no third-party JWT library required.
 * The JWT structure is: base64url(header) . base64url(payload) . base64url(signature)
 * where the signature covers the first two parts.
 *
 * <p>Claims issued:
 * <ul>
 *   <li>{@code sub} — user id (String, per JWT spec)</li>
 *   <li>{@code email} — user email</li>
 *   <li>{@code role} — user role</li>
 *   <li>{@code iat} — issued-at (epoch seconds)</li>
 *   <li>{@code exp} — expiry (epoch seconds)</li>
 * </ul>
 *
 * <p>JSON serialisation/deserialisation is done with a minimal hand-rolled parser that
 * only handles the fixed claim types this service writes — no reflection, no dependencies.
 * This approach is safe because we control both the write and read side.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER_B64 = base64UrlEncode(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    /**
     * The well-known dev sentinel value set in application.yml.
     * The startup guard rejects this key when dev-mode=false.
     */
    static final String DEV_SENTINEL_KEY =
            "dev-sentinel-key-do-not-use-in-production-replace-me!!";

    private final byte[] signingKey;
    private final long accessTokenExpirySeconds;
    private final boolean devMode;
    private final String rawKey;

    public JwtService(AppProperties appProperties) {
        String key = appProperties.jwt().signingKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY must not be blank. "
                    + "Set a ≥32-byte random value or set APP_DEV_MODE=true for local development.");
        }
        this.rawKey = key;
        this.signingKey = key.getBytes(StandardCharsets.UTF_8);
        this.accessTokenExpirySeconds = appProperties.jwt().accessTokenExpirySeconds();
        this.devMode = appProperties.devMode();
    }

    /**
     * Startup guard: rejects insecure key configurations in non-dev mode.
     * Checked at bean initialization so the application fails fast rather than
     * running with a forgeable signing key.
     *
     * <p>Rules (all applied when dev-mode=false):
     * <ul>
     *   <li>Key must be non-blank (already checked in constructor).</li>
     *   <li>Key UTF-8 length must be ≥32 bytes (256 bits for HS256).</li>
     *   <li>Key must not equal the well-known dev sentinel value.</li>
     * </ul>
     */
    @PostConstruct
    void validateKeyStrength() {
        if (devMode) {
            log.warn("Running in dev-mode — JWT signing key strength checks are relaxed. "
                     + "NEVER set APP_DEV_MODE=true in production.");
            return;
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is too short (" + keyBytes.length + " bytes). "
                    + "HS256 requires ≥32 bytes (256 bits). "
                    + "Generate one with: openssl rand -hex 32");
        }
        if (DEV_SENTINEL_KEY.equals(rawKey)) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is set to the well-known dev sentinel value. "
                    + "This key is public and must never be used in production. "
                    + "Set a real random key with: openssl rand -hex 32");
        }
    }

    /**
     * Issues a signed access token for the given user.
     */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        long iat = now.getEpochSecond();
        long exp = iat + accessTokenExpirySeconds;
        String payload = buildPayloadJson(
                String.valueOf(user.getId()), user.getEmail(), user.getRole().name(), iat, exp);
        String encodedPayload = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER_B64 + "." + encodedPayload;
        String signature = computeSignature(signingInput);
        return signingInput + "." + signature;
    }

    /**
     * Validates the token signature and expiry, returns the claims if valid.
     *
     * @param token compact JWT string
     * @return claims map ({@code sub}, {@code email}, {@code role}, {@code iat}, {@code exp}),
     *         or empty if the token is missing, malformed, signature-invalid, or expired
     */
    public Optional<Map<String, Object>> validateAndParseClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = computeSignature(signingInput);
        if (!constantTimeEquals(expectedSig, parts[2])) {
            return Optional.empty();
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            Map<String, Object> claims = parsePayloadJson(payloadJson);
            Number exp = (Number) claims.get("exp");
            if (exp == null || Instant.now().getEpochSecond() > exp.longValue()) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            log.debug("JWT parse failed", e);
            return Optional.empty();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String computeSignature(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
            byte[] sigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(sigBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute JWT signature", e);
        }
    }

    /** Constant-time byte comparison to prevent timing attacks on signature validation. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Builds the JWT payload JSON from the fixed set of claims this service uses.
     * Values are safely escaped (no user-controlled characters can break the JSON structure
     * because email is validated by the DB unique constraint and role comes from a controlled enum).
     */
    private String buildPayloadJson(String sub, String email, String role, long iat, long exp) {
        return "{" +
               "\"sub\":\"" + escapeJsonString(sub) + "\"," +
               "\"email\":\"" + escapeJsonString(email) + "\"," +
               "\"role\":\"" + escapeJsonString(role) + "\"," +
               "\"iat\":" + iat + "," +
               "\"exp\":" + exp +
               "}";
    }

    /**
     * Minimal JSON parser for the fixed payload format produced by {@link #buildPayloadJson}.
     * Handles exactly: string values for {@code sub}, {@code email}, {@code role};
     * numeric values for {@code iat}, {@code exp}.
     */
    private Map<String, Object> parsePayloadJson(String json) {
        Map<String, Object> claims = new HashMap<>();
        // Strip outer braces
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        // Split on top-level commas (safe since none of our string values contain commas or nested objects)
        String[] pairs = content.split(",(?=\")");
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx < 0) continue;
            String rawKey = pair.substring(0, colonIdx).trim().replace("\"", "");
            String rawVal = pair.substring(colonIdx + 1).trim();
            if (rawVal.startsWith("\"")) {
                // String value
                claims.put(rawKey, rawVal.substring(1, rawVal.length() - 1));
            } else {
                // Numeric value
                try {
                    claims.put(rawKey, Long.parseLong(rawVal));
                } catch (NumberFormatException e) {
                    claims.put(rawKey, rawVal);
                }
            }
        }
        return claims;
    }

    /** Escapes backslash and double-quote characters for safe embedding in JSON strings. */
    private String escapeJsonString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
