package com.homekept.identity;

import com.homekept.config.AppProperties;
import com.homekept.identity.exception.InvalidPasswordResetTokenException;
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
 * Mints, validates, and consumes password reset tokens for the forgot/reset password flow.
 * Mirrors {@code ActivationTokenService} — see its Javadoc for the general HMAC scheme.
 *
 * <h2>Token structure</h2>
 * <p>The raw token placed in the reset link is a Base64-URL encoded string of:
 * <pre>
 *   HMAC-SHA256(key, "userId=123&nonce=abc&exp=1234567890")
 *   encoded as: base64url(payload) + "." + base64url(hmac)
 * </pre>
 * where {@code payload = "userId=<id>&nonce=<hex>&exp=<epochSeconds>"}.
 *
 * <p>The HMAC signing key is the JWT signing key (same reuse rationale as
 * {@code ActivationTokenService} — no additional secret to manage at MVP).
 *
 * <p>Only the SHA-256 hash of the raw token is stored in {@code password_reset_tokens.token_hash}.
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
public class PasswordResetTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    static final long TOKEN_TTL_SECONDS = 30L * 60; // 30 minutes

    // Bounds for the enumeration-timing compensation delay in mintDummy() — chosen to overlap
    // the found-email branch's observed DB-insert + synchronous SendGrid-send cost (~100-300ms).
    // Public (like ForgotPasswordRateLimiter.MAX_ATTEMPTS) so tests can assert against the real
    // bound instead of a hardcoded duplicate that could silently drift out of sync.
    public static final long MIN_DUMMY_DELAY_MS = 100;
    public static final long MAX_DUMMY_DELAY_MS = 300;

    private final PasswordResetTokenRepository tokenRepository;
    private final byte[] signingKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository,
                                     AppProperties appProperties) {
        this.tokenRepository = tokenRepository;
        String key = appProperties.jwt().signingKey();
        this.signingKeyBytes = (key != null ? key : "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Mints a new password reset token for the given user.
     * Stores the SHA-256 hash in the DB and returns the raw token (for the reset link).
     *
     * @param user the user requesting a password reset
     * @return the raw token string to embed in the reset URL (never stored)
     */
    @Transactional
    public MintResult mint(User user) {
        String nonce = generateNonce();
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);
        long expEpoch = expiresAt.getEpochSecond();

        String payload = "userId=" + user.getId() + "&nonce=" + nonce + "&exp=" + expEpoch;
        String rawToken = buildSignedToken(payload);
        String hash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, hash, expiresAt);
        PasswordResetToken saved = tokenRepository.save(token);

        return new MintResult(saved.getId(), rawToken);
    }

    /**
     * Performs the same nonce-generation and HMAC computation as {@link #mint} but persists
     * nothing, then blocks the calling thread for a bounded, randomized delay. Called on the
     * "email not found" branch of forgot-password so that branch's total response time
     * overlaps the "email found" branch's — the same enumeration-timing idea as
     * {@code AuthService}'s dummy bcrypt comparison on unknown-email login.
     *
     * <p>The found-email branch does a DB insert plus a synchronous outbound SendGrid call
     * (roughly {@value #MIN_DUMMY_DELAY_MS}-{@value #MAX_DUMMY_DELAY_MS}ms); the not-found
     * branch's HMAC computation alone takes only a few ms, which previously leaked account
     * existence via wall-clock timing. The jittered (not constant) sleep below closes that
     * gap by making the two branches' response-time distributions overlap.
     *
     * <p><b>Tradeoff:</b> this ties up a request-handling thread for up to
     * {@value #MAX_DUMMY_DELAY_MS}ms per unknown-email request. The proper fix is to dispatch
     * the reset email asynchronously (arch doc Stage 2 note on {@code SendGridEmailSender},
     * shared infra with #89) so neither branch blocks the request thread on network I/O; that
     * infra isn't in place yet, so this bounded sleep is a stopgap, not the final fix.
     */
    public void mintDummy() {
        String nonce = generateNonce();
        long expEpoch = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = "userId=0&nonce=" + nonce + "&exp=" + expEpoch;
        buildSignedToken(payload);
        sleepJittered();
    }

    /**
     * Validates a raw reset token without consuming it.
     * Returns a {@link ValidationResult} describing validity, user id, and reason if invalid.
     *
     * @param rawToken the raw token from the reset link
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
        PasswordResetToken token = tokenRepository.findByTokenHash(hash).orElse(null);
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

        return ValidationResult.valid(fields.userId(), token.getId());
    }

    /**
     * Validates and consumes the token (sets {@code consumed_at}), then invalidates every
     * other outstanding reset token belonging to the same user, so a successful reset retires
     * all of that user's live reset links, not just the one used.
     * MUST be called within the same transaction as the password update.
     *
     * @param rawToken the raw token from the reset link
     * @return the resolved user id
     * @throws InvalidPasswordResetTokenException if the token is invalid, expired, or consumed
     */
    @Transactional
    public Long validateAndConsume(String rawToken) {
        // Stateless checks first (HMAC signature + payload expiry + existence + not-yet-consumed).
        ValidationResult result = validate(rawToken);
        if (!result.valid()) {
            throw new InvalidPasswordResetTokenException(result.reason());
        }

        // Atomic single-use gate: only one concurrent caller can flip consumed_at from NULL.
        // The loser of the race updates 0 rows and is rejected — single-use is DB-enforced,
        // not dependent on a read-then-write window.
        Instant now = Instant.now();
        String hash = sha256Hex(rawToken);
        int updated = tokenRepository.consumeIfUnconsumed(hash, now);
        if (updated == 0) {
            throw new InvalidPasswordResetTokenException("USED");
        }

        // Retire any other still-outstanding reset tokens for this user (#115 finding 3):
        // otherwise an earlier, unexpired reset link would stay valid for up to 30 minutes
        // after the password has already been changed via this one.
        tokenRepository.consumeAllUnconsumedForUser(result.userId(), now);

        return result.userId();
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
            Long userId = null;
            long expEpoch = 0;
            for (String part : payload.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                if ("userId".equals(kv[0])) userId = Long.parseLong(kv[1]);
                if ("exp".equals(kv[0])) expEpoch = Long.parseLong(kv[1]);
            }
            if (userId == null || expEpoch == 0) return null;
            return new PayloadFields(userId, expEpoch);
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

    /**
     * Sleeps the current thread for a randomized (jittered) duration in
     * {@code [MIN_DUMMY_DELAY_MS, MAX_DUMMY_DELAY_MS]}. See {@link #mintDummy()} for why this
     * exists and its tradeoff.
     */
    private void sleepJittered() {
        try {
            Thread.sleep(computeJitteredDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Computes (without sleeping) a randomized delay in
     * {@code [MIN_DUMMY_DELAY_MS, MAX_DUMMY_DELAY_MS]}. Package-private so unit tests can
     * assert on the distribution directly without paying the real sleep cost. A constant
     * delay would itself become a distinguishing signal once an attacker samples enough
     * requests, so the delay is uniformly randomized rather than fixed.
     */
    long computeJitteredDelayMs() {
        long range = MAX_DUMMY_DELAY_MS - MIN_DUMMY_DELAY_MS + 1;
        return MIN_DUMMY_DELAY_MS + Math.floorMod(secureRandom.nextLong(), range);
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

    public record ValidationResult(boolean valid, Long userId, Long tokenId, String reason) {
        static ValidationResult valid(Long userId, Long tokenId) {
            return new ValidationResult(true, userId, tokenId, null);
        }
        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, null, null, reason);
        }
    }

    private record PayloadFields(long userId, long expEpoch) {}
}
