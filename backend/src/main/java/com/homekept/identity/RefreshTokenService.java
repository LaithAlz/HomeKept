package com.homekept.identity;

import com.homekept.config.AppProperties;
import com.homekept.identity.exception.TokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manages opaque refresh tokens. Responsibilities:
 * <ul>
 *   <li>Generate: 256-bit random token, store only its SHA-256 hash.</li>
 *   <li>Rotate: validate → revoke old → issue new (token reuse is detected).</li>
 *   <li>Revoke all: on logout.</li>
 * </ul>
 *
 * <p>The raw token value is returned to the caller exactly once (to set in the cookie)
 * and never stored or logged.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokenRepository;
    private final long refreshTokenExpirySeconds;

    public RefreshTokenService(RefreshTokenRepository tokenRepository,
                               AppProperties appProperties) {
        this.tokenRepository = tokenRepository;
        this.refreshTokenExpirySeconds = appProperties.jwt().refreshTokenExpirySeconds();
    }

    /**
     * Creates a new refresh token for the given user.
     *
     * @param user the authenticated user
     * @return the raw opaque token (caller sets this in the cookie; it is NEVER stored)
     */
    @Transactional
    public String createToken(User user) {
        String rawToken = generateRawToken();
        String hash = sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds);
        tokenRepository.save(new RefreshToken(user, hash, expiresAt));
        return rawToken;
    }

    /**
     * Rotates a refresh token: validates the provided raw token, revokes the old record,
     * and issues a new one.
     *
     * @param rawToken the raw token from the incoming cookie
     * @return the raw new token (caller sets this in the replacement cookie)
     * @throws TokenException if the token is unknown, expired, or already revoked
     */
    @Transactional
    public String rotate(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken existing = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenException(TokenException.Reason.NOT_FOUND));

        if (existing.isRevoked()) {
            // Possible replay attack — revoke the entire family to be safe.
            tokenRepository.revokeAllByUserId(existing.getUser().getId(), Instant.now());
            throw new TokenException(TokenException.Reason.REVOKED);
        }
        if (existing.isExpired()) {
            throw new TokenException(TokenException.Reason.EXPIRED);
        }

        existing.revoke();
        tokenRepository.save(existing);

        return createToken(existing.getUser());
    }

    /**
     * Returns the user associated with a valid refresh token (used by {@code /auth/refresh}
     * to know which user to issue a new access token for).
     *
     * @param rawToken the raw token from the incoming cookie
     * @return the owning user
     * @throws TokenException if the token is unknown, expired, or revoked
     */
    @Transactional(readOnly = true)
    public User getUserForToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenException(TokenException.Reason.NOT_FOUND));
        if (!token.isValid()) {
            throw new TokenException(
                    token.isRevoked() ? TokenException.Reason.REVOKED : TokenException.Reason.EXPIRED);
        }
        return token.getUser();
    }

    /**
     * Revokes all refresh tokens for a user (on logout).
     */
    @Transactional
    public void revokeAll(Long userId) {
        tokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    /**
     * Generates a cryptographically secure random 256-bit token, base64-URL-encoded.
     * The returned string is the value placed in the cookie and NEVER persisted.
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex digest. This is what gets persisted in the database.
     * Package-accessible for testing and for the token lookup logic.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
