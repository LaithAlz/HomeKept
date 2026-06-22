package com.homekept.subscription;

import com.homekept.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mints, validates, and consumes activation tokens for the magic-link flow.
 *
 * <h2>Token structure</h2>
 * <p>The raw token placed in the magic link is a Base64-URL encoded string of:
 * <pre>
 *   HMAC-SHA256(key, "bookingId=123&nonce=abc&exp=1234567890")
 *   encoded as: base64url(payload) + "." + base64url(hmac)
 * </pre>
 * where {@code payload = "bookingId=<id>&nonce=<hex>&exp=<epochSeconds>"}.
 *
 * <p>The HMAC signing key is the JWT signing key (reusing the same env var ensures
 * there is no additional secret to manage at MVP).
 *
 * <p>Only the SHA-256 hash of the raw token is stored in {@code activation_token.token_hash}.
 *
 * <h2>Validation rules</h2>
 * <ol>
 *   <li>HMAC signature must verify (integrity + authenticity).</li>
 *   <li>{@code exp} must be in the future (not expired).</li>
 *   <li>Token must not be consumed ({@code consumed_at} must be null).</li>
 *   <li>Token hash must exist in the database (not forged/unknown).</li>
 * </ol>
 */
@Service
public class ActivationTokenService {

    private static final Logger log = LoggerFactory.getLogger(ActivationTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    static final long TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60; // 7 days

    private final ActivationTokenRepository tokenRepository;
    private final byte[] signingKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public ActivationTokenService(ActivationTokenRepository tokenRepository,
                                  AppProperties appProperties) {
        this.tokenRepository = tokenRepository;
        String key = appProperties.jwt().signingKey();
        this.signingKeyBytes = (key != null ? key : "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Mints a new activation token for the given booking.
     * Stores the SHA-256 hash in the DB and returns the raw token (for the magic link).
     *
     * @param bookingId the walk-through booking id
     * @return the raw token string to embed in the activation URL (never stored)
     */
    @Transactional
    public MintResult mint(Long bookingId) {
        String nonce = generateNonce();
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);
        long expEpoch = expiresAt.getEpochSecond();

        String payload = "bookingId=" + bookingId + "&nonce=" + nonce + "&exp=" + expEpoch;
        String rawToken = buildSignedToken(payload);
        String hash = sha256Hex(rawToken);

        ActivationToken token = new ActivationToken(bookingId, hash, expiresAt);
        ActivationToken saved = tokenRepository.save(token);

        return new MintResult(saved.getId(), rawToken);
    }

    /**
     * Validates a raw activation token without consuming it.
     * Returns a {@link ValidationResult} describing validity, booking id, and reason if invalid.
     *
     * @param rawToken the raw token from the magic link
     * @return validation outcome
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return ValidationResult.invalid("INVALID");
        }

        // 1. Verify HMAC signature and extract payload
        String payload = verifyAndExtractPayload(rawToken);
        if (payload == null) {
            return ValidationResult.invalid("INVALID");
        }

        // 2. Parse payload fields
        PayloadFields fields = parsePayload(payload);
        if (fields == null) {
            return ValidationResult.invalid("INVALID");
        }

        // 3. Check expiry from the payload
        if (Instant.now().getEpochSecond() > fields.expEpoch()) {
            return ValidationResult.invalid("EXPIRED");
        }

        // 4. Look up in DB by hash
        String hash = sha256Hex(rawToken);
        ActivationToken token = tokenRepository.findByTokenHash(hash).orElse(null);
        if (token == null) {
            return ValidationResult.invalid("INVALID");
        }

        // 5. Check consumed
        if (token.isConsumed()) {
            return ValidationResult.invalid("USED");
        }

        // 6. Double-check DB expiry
        if (token.isExpired()) {
            return ValidationResult.invalid("EXPIRED");
        }

        return ValidationResult.valid(fields.bookingId(), token.getId());
    }

    /**
     * Validates and consumes the token (sets {@code consumed_at}).
     * MUST be called within the same transaction as the activation completion.
     *
     * @param rawToken the raw token from the magic link
     * @return the resolved booking id
     * @throws InvalidActivationTokenException if the token is invalid, expired, or consumed
     */
    @Transactional
    public Long validateAndConsume(String rawToken) {
        // Stateless checks first (HMAC signature + payload expiry + existence + not-yet-consumed).
        ValidationResult result = validate(rawToken);
        if (!result.valid()) {
            throw new InvalidActivationTokenException(result.reason());
        }

        // Atomic single-use gate: only one concurrent caller can flip consumed_at from NULL.
        // The loser of the race updates 0 rows and is rejected — single-use is DB-enforced,
        // not dependent on a read-then-write window.
        String hash = sha256Hex(rawToken);
        int updated = tokenRepository.consumeIfUnconsumed(hash, Instant.now());
        if (updated == 0) {
            throw new InvalidActivationTokenException("USED");
        }

        return result.bookingId();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSignedToken(String payload) {
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String hmac = computeHmac(encodedPayload);
        return encodedPayload + "." + hmac;
    }

    private String verifyAndExtractPayload(String rawToken) {
        String[] parts = rawToken.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }
        String encodedPayload = parts[0];
        String providedHmac = parts[1];
        String expectedHmac = computeHmac(encodedPayload);

        // Constant-time comparison
        if (!constantTimeEquals(expectedHmac, providedHmac)) {
            return null;
        }

        try {
            return new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PayloadFields parsePayload(String payload) {
        try {
            Long bookingId = null;
            long expEpoch = 0;
            for (String part : payload.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                if ("bookingId".equals(kv[0])) bookingId = Long.parseLong(kv[1]);
                if ("exp".equals(kv[0])) expEpoch = Long.parseLong(kv[1]);
            }
            if (bookingId == null || expEpoch == 0) return null;
            return new PayloadFields(bookingId, expEpoch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String computeHmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKeyBytes, HMAC_ALGORITHM));
            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

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

    private String generateNonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public record MintResult(Long tokenId, String rawToken) {}

    public record ValidationResult(boolean valid, Long bookingId, Long tokenId, String reason) {
        static ValidationResult valid(Long bookingId, Long tokenId) {
            return new ValidationResult(true, bookingId, tokenId, null);
        }
        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, null, null, reason);
        }
    }

    private record PayloadFields(long bookingId, long expEpoch) {}
}
