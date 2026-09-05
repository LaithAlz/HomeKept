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
 * Mints, validates, and consumes tokens in {@code password_reset_tokens} — used by both the
 * customer forgot/reset-password flow and the technician staff-invite flow (V13 migration
 * added {@link TokenPurpose} to separate them; see that enum's Javadoc for why one table).
 * Mirrors {@code ActivationTokenService} — see its Javadoc for the general HMAC scheme.
 *
 * <h2>Token structure</h2>
 * <p>The raw token placed in the link is a Base64-URL encoded string of:
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
 * <h2>Minting: two shapes only</h2>
 * <p>{@link #mint(User)} (customer reset, 30 minutes, {@code PASSWORD_RESET}) and
 * {@link #mintStaffInvite(Long)} (staff invite, 7 days, {@code STAFF_INVITE}) are the only
 * public entry points. Neither lets a caller choose an arbitrary purpose/TTL combination for
 * an arbitrary user — {@code mintStaffInvite} owns its own 7-day lifetime internally. This
 * closes the path where a caller bug (e.g. resending an invite to the wrong account) could
 * mint a long-lived, broadly-redeemable token.
 *
 * <h2>Validation rules</h2>
 * <ol>
 *   <li>HMAC signature must verify (integrity + authenticity).</li>
 *   <li>The stored row must exist for the exact (hash, purpose) pair — a hash that exists
 *       under a different purpose is indistinguishable from a hash that does not exist.</li>
 *   <li>Token must not be consumed ({@code consumed_at} must be null).</li>
 *   <li>Token must not be expired ({@code expires_at} must be in the future).</li>
 * </ol>
 */
@Service
public class PasswordResetTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    static final long TOKEN_TTL_SECONDS = 30L * 60; // 30 minutes (PASSWORD_RESET)
    static final Duration STAFF_INVITE_TTL = Duration.ofDays(7); // STAFF_INVITE

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
     * Mints a new password-reset token ({@link TokenPurpose#PASSWORD_RESET}) for the given
     * user, with the standard 30-minute expiry.
     *
     * @param user the user requesting a password reset
     * @return the raw token string to embed in the reset URL (never stored)
     */
    @Transactional
    public MintResult mint(User user) {
        return mint(user, Duration.ofSeconds(TOKEN_TTL_SECONDS), TokenPurpose.PASSWORD_RESET);
    }

    /**
     * Mints a new staff-invite token ({@link TokenPurpose#STAFF_INVITE}) for the given user
     * id, with a fixed 7-day expiry that this method owns — the caller (the technician
     * admin flow) cannot request a different lifetime or purpose. Resolves the {@link User}
     * internally so cross-domain callers never need to load one themselves.
     *
     * @param userId the id of the technician this invite is for
     * @return the raw token string to embed in the invite link (never stored)
     * @throws IllegalStateException if no such user exists
     */
    @Transactional
    public MintResult mintStaffInvite(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        return mint(user, STAFF_INVITE_TTL, TokenPurpose.STAFF_INVITE);
    }

    /**
     * Core mint, parameterized by TTL and purpose. Deliberately private: the only public
     * shapes are {@link #mint(User)} and {@link #mintStaffInvite(Long)} — see class Javadoc.
     */
    private MintResult mint(User user, Duration ttl, TokenPurpose purpose) {
        String nonce = generateNonce();
        Instant expiresAt = Instant.now().plus(ttl);
        long expEpoch = expiresAt.getEpochSecond();

        String payload = "userId=" + user.getId() + "&nonce=" + nonce + "&exp=" + expEpoch;
        String rawToken = buildSignedToken(payload);
        String hash = Hashing.sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, hash, expiresAt, purpose);
        PasswordResetToken saved = tokenRepository.save(token);

        return new MintResult(saved.getId(), rawToken, saved.getCreatedAt());
    }

    /**
     * Performs the same nonce-generation and HMAC computation as {@link #mint(User)} but
     * persists nothing. Called on the "email not found" branch of forgot-password so that
     * branch's CPU cost is closer to the "email found" branch's cost — the same
     * enumeration-timing idea as {@code AuthService}'s dummy bcrypt comparison on
     * unknown-email login.
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
     * Validates a raw token without consuming it, scoped to a single purpose. Public (unlike
     * a purely-internal helper) because the staff-invite validate endpoint needs a
     * non-consuming check too — see {@code StaffInviteService.validate}.
     *
     * <p>The purpose-scoped lookup (rather than look-up-then-check-purpose) is what makes a
     * wrong-purpose token report the same {@code "INVALID"} as a token that does not exist at
     * all — see class Javadoc. {@code EXPIRED}/{@code USED} are only ever returned for a
     * token that really is the requested purpose; api-contract.md documents this precisely.
     *
     * @param rawToken the raw token from the link
     * @param purpose  the purpose this token must have to be considered at all
     * @return validation outcome
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(String rawToken, TokenPurpose purpose) {
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

        // 3. Look up in DB by (hash, purpose) — a wrong-purpose match is treated as absent,
        // BEFORE any expiry/consumed check runs, so a wrong-purpose token can never reach
        // (and therefore never reveal) an EXPIRED or USED reason.
        String hash = Hashing.sha256Hex(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHashAndPurpose(hash, purpose).orElse(null);
        if (token == null) {
            return ValidationResult.invalid("INVALID");
        }

        // 4. Check consumed
        if (token.isConsumed()) {
            return ValidationResult.invalid("USED");
        }

        // 5. Check expiry (the stored row, not the payload's embedded copy — they are set
        // from the same instant at mint time, so checking the DB row is sufficient and is
        // the single source of truth).
        if (token.isExpired()) {
            return ValidationResult.invalid("EXPIRED");
        }

        return ValidationResult.valid(fields.userId());
    }

    /**
     * Validates and consumes the token (sets {@code consumed_at}) for the given purpose, then
     * invalidates every other outstanding token of that SAME purpose belonging to the same
     * user, so a successful reset/accept retires all of that user's live links for that flow,
     * not just the one used — without touching a token from the other flow (e.g. a password
     * reset the same person separately requested).
     * MUST be called within the same transaction as the resulting state change.
     *
     * @param rawToken the raw token from the link
     * @param purpose  the purpose this token must have to be considered at all
     * @return the resolved user id
     * @throws InvalidPasswordResetTokenException if the token is invalid, expired, consumed,
     *         or of the wrong purpose (all indistinguishable as "INVALID")
     */
    @Transactional
    public Long validateAndConsume(String rawToken, TokenPurpose purpose) {
        // Stateless checks first (HMAC signature + purpose-scoped lookup + not-yet-consumed
        // + not-expired).
        ValidationResult result = validate(rawToken, purpose);
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

        // Retire any other still-outstanding SAME-purpose tokens for this user (#115 finding
        // 3, extended to be purpose-scoped): otherwise an earlier, unexpired link for this
        // same flow would stay valid after this one has already been redeemed. Scoped so a
        // password reset never burns a staff invite for the same person, and vice versa.
        tokenRepository.consumeAllUnconsumedForUserAndPurpose(result.userId(), purpose, now);

        return result.userId();
    }

    /**
     * Invalidates every outstanding (unconsumed) {@link TokenPurpose#STAFF_INVITE} token
     * belonging to a user, by marking them consumed. Used by the technician admin
     * resend-invite flow to burn a prior unconsumed invite link before minting a fresh one,
     * so the old link stops working. Deliberately scoped to {@code STAFF_INVITE} only — a
     * password-reset token the same person separately requested is never touched.
     *
     * @param userId the user whose outstanding staff-invite tokens should be invalidated
     */
    @Transactional
    public void invalidateAllForUser(Long userId) {
        tokenRepository.consumeAllUnconsumedForUserAndPurpose(userId, TokenPurpose.STAFF_INVITE, Instant.now());
    }

    /**
     * Returns the most recent {@link TokenPurpose#STAFF_INVITE} mint timestamp for each of
     * the given user ids, for the technician admin roster's {@code invitedAt} column (mirrors
     * {@code ActivationTokenService.latestInviteAtByBookingIds}). Scoped to {@code
     * STAFF_INVITE} so a technician's own password reset never shows up on the roster as an
     * invite that was never sent.
     *
     * @param userIds the user ids to resolve; may be empty
     * @return a map from user id to the latest staff-invite token's {@code createdAt}; user
     *         ids with no staff-invite token row are simply absent from the map (never mapped
     *         to null). Empty input returns an empty map without querying the database.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> latestInviteAtByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return tokenRepository.findLatestCreatedAtByUserIdInAndPurpose(userIds, TokenPurpose.STAFF_INVITE).stream()
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
