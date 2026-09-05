package com.homekept.identity;

import com.homekept.common.Hashing;
import com.homekept.config.AppProperties;
import com.homekept.identity.exception.InvalidPasswordResetTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mints, validates, and consumes password reset tokens for the forgot/reset password flow.
 * Also reused (with a longer, caller-supplied TTL) by the technician staff-invite flow —
 * see {@link #mint(User, Duration)}'s Javadoc — rather than standing up a second token table.
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

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final byte[] signingKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository,
                                     UserRepository userRepository,
                                     AppProperties appProperties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        String key = appProperties.jwt().signingKey();
        this.signingKeyBytes = (key != null ? key : "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Mints a new password reset token for the given user, with the standard 30-minute
     * forgot/reset-password expiry.
     *
     * @param user the user requesting a password reset
     * @return the raw token string to embed in the reset URL (never stored)
     */
    @Transactional
    public MintResult mint(User user) {
        return mint(user, Duration.ofSeconds(TOKEN_TTL_SECONDS));
    }

    /**
     * Mints a new token for the given user with a caller-supplied time-to-live. Stores the
     * SHA-256 hash in the DB and returns the raw token.
     *
     * <p>This table is shared by two purposes with different lifetimes: the customer
     * forgot/reset-password flow ({@link #mint(User)}, 30 minutes) and the staff (technician)
     * invite flow ({@code TechnicianAdminService}, 7 days — same expiry as the customer
     * activation magic link). There is no "purpose" column; callers that need to distinguish
     * the two (e.g. the staff-invite validate/accept endpoints) do so by also checking the
     * resolved user's role and status — see {@code StaffInviteService}.
     *
     * @param user the user this token is minted for
     * @param ttl  how long the token remains valid
     * @return the raw token string to embed in the link (never stored)
     */
    @Transactional
    public MintResult mint(User user, Duration ttl) {
        String nonce = generateNonce();
        Instant expiresAt = Instant.now().plus(ttl);
        long expEpoch = expiresAt.getEpochSecond();

        String payload = "userId=" + user.getId() + "&nonce=" + nonce + "&exp=" + expEpoch;
        String rawToken = buildSignedToken(payload);
        String hash = Hashing.sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, hash, expiresAt);
        PasswordResetToken saved = tokenRepository.save(token);

        return new MintResult(saved.getId(), rawToken, saved.getCreatedAt());
    }

    /**
     * Mints a new token for a user identified only by id, with a caller-supplied
     * time-to-live. Convenience overload for cross-domain callers (e.g. the technician
     * domain's staff-invite flow) that have a bare {@code userId} rather than a loaded
     * {@link User} entity — keeps {@link User}/{@link UserRepository} from crossing the
     * identity domain boundary.
     *
     * @param userId the id of the user this token is minted for
     * @param ttl    how long the token remains valid
     * @return the raw token string to embed in the link (never stored)
     * @throws IllegalStateException if no such user exists
     */
    @Transactional
    public MintResult mint(Long userId, Duration ttl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        return mint(user, ttl);
    }

    /**
     * Performs the same nonce-generation and HMAC computation as {@link #mint} but persists
     * nothing. Called on the "email not found" branch of forgot-password so that branch's
     * CPU cost is closer to the "email found" branch's cost — the same enumeration-timing
     * idea as {@code AuthService}'s dummy bcrypt comparison on unknown-email login.
     *
     * <p>This closes only the CPU-time gap. The wall-clock gap (the found-email branch's DB
     * insert plus outbound SendGrid attempt vs. this branch's few-ms HMAC computation) is
     * closed separately by a fixed-budget response-time pad applied to BOTH branches in
     * {@link AuthController#forgot}, after this method's caller returns — see that method's
     * Javadoc. Deliberately no sleep lives here: this method still runs inside
     * {@link AuthService#forgotPassword}'s {@code @Transactional} block, and a sleep inside a
     * transaction would pin a pooled DB connection for its duration (a DoS amplifier) — the
     * padding must happen only once the transaction has committed and released its connection.
     */
    public void mintDummy() {
        String nonce = generateNonce();
        long expEpoch = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = "userId=0&nonce=" + nonce + "&exp=" + expEpoch;
        buildSignedToken(payload);
    }

    /**
     * Validates a raw token without consuming it. Public (unlike a purely-internal helper)
     * because the staff-invite validate endpoint needs a non-consuming check too — see
     * {@code StaffInviteService.validate}, which additionally checks the resolved user's role
     * and status before treating this as a legitimate invite (see {@link #mint(User, Duration)}
     * Javadoc on why the shared table needs that extra check).
     *
     * <p>Returns a {@link ValidationResult} describing validity, user id, and reason if invalid.
     *
     * @param rawToken the raw token from the link
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
        String hash = Hashing.sha256Hex(rawToken);
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

        return ValidationResult.valid(fields.userId());
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
        String hash = Hashing.sha256Hex(rawToken);
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

    /**
     * Invalidates every outstanding (unconsumed) token belonging to a user, by marking them
     * consumed. Used by the technician admin resend-invite flow to burn a prior unconsumed
     * invite link before minting a fresh one, so the old link stops working.
     *
     * @param userId the user whose outstanding tokens should be invalidated
     */
    @Transactional
    public void invalidateAllForUser(Long userId) {
        tokenRepository.consumeAllUnconsumedForUser(userId, Instant.now());
    }

    /**
     * Returns the most recent token-mint timestamp for each of the given user ids, for the
     * technician admin roster's {@code invitedAt} column (mirrors
     * {@code ActivationTokenService.latestInviteAtByBookingIds}).
     *
     * @param userIds the user ids to resolve; may be empty
     * @return a map from user id to the latest token's {@code createdAt}; user ids with no
     *         token row are simply absent from the map (never mapped to null). Empty input
     *         returns an empty map without querying the database.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> latestInviteAtByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return tokenRepository.findLatestCreatedAtByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        PasswordResetTokenRepository.LatestInviteAt::getUserId,
                        PasswordResetTokenRepository.LatestInviteAt::getLatestCreatedAt));
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

    // ── Result types ──────────────────────────────────────────────────────────

    public record MintResult(Long tokenId, String rawToken, Instant createdAt) {}

    public record ValidationResult(boolean valid, Long userId, String reason) {
        static ValidationResult valid(Long userId) {
            return new ValidationResult(true, userId, null);
        }
        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, null, reason);
        }
    }

    private record PayloadFields(long userId, long expEpoch) {}
}
