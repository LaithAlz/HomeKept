package com.homekept.identity;

import com.homekept.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
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
 * <p>Header/payload JSON is built and parsed via the shared Jackson {@link ObjectMapper}.
 * Claim order is fixed ({@code sub}, {@code email}, {@code role}, {@code iat}, {@code exp})
 * via insertion-ordered maps, matching the wire format previously produced by hand.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ALGORITHM = "HmacSHA256";

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
    private final ObjectMapper objectMapper;
    private final String headerB64;

    public JwtService(AppProperties appProperties, ObjectMapper objectMapper) {
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
        this.objectMapper = objectMapper;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        this.headerB64 = base64UrlEncode(
                objectMapper.writeValueAsString(header).getBytes(StandardCharsets.UTF_8));
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
        String signingInput = headerB64 + "." + encodedPayload;
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
            if (claims == null) {
                return Optional.empty();
            }
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
     * Builds the JWT payload JSON from the fixed set of claims this service uses, via the
     * shared Jackson {@link ObjectMapper}. Insertion order is fixed ({@code sub}, {@code email},
     * {@code role}, {@code iat}, {@code exp}) to match the historical hand-rolled wire format.
     *
     * <p>Package-private so the exact wire format can be pinned in {@code JwtServiceTest}.
     */
    String buildPayloadJson(String sub, String email, String role, long iat, long exp) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("iat", iat);
        claims.put("exp", exp);
        return objectMapper.writeValueAsString(claims);
    }

    /**
     * Parses the JWT payload JSON into the fixed claim shape this service issues, via the
     * shared Jackson {@link ObjectMapper}. Returns {@code null} (rejected) if the payload
     * is not a JSON object, or if any expected claim is missing or has an unexpected type:
     * {@code sub}/{@code email}/{@code role} must be JSON strings, {@code iat}/{@code exp}
     * must be JSON integral numbers.
     */
    private Map<String, Object> parsePayloadJson(String json) {
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject()) {
            return null;
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        for (String field : new String[] {"sub", "email", "role"}) {
            JsonNode value = root.get(field);
            if (value == null || !value.isTextual()) {
                return null;
            }
            claims.put(field, value.asString());
        }
        for (String field : new String[] {"iat", "exp"}) {
            JsonNode value = root.get(field);
            if (value == null || !value.isIntegralNumber()) {
                return null;
            }
            claims.put(field, value.asLong());
        }
        return claims;
    }
}
