package com.homekept.identity;

import com.homekept.identity.dto.MeResponse;
import com.homekept.identity.exception.AuthenticationException;
import com.homekept.identity.exception.RateLimitExceededException;
import com.homekept.identity.exception.TokenException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates login, token refresh, logout, and identity fetching.
 * All credential failures return the same generic exception to prevent user enumeration.
 */
@Service
public class AuthService {

    /**
     * Dummy bcrypt hash used as the comparison target when the requested email does not
     * exist, so the unknown-email and found-user branches take ~the same wall-clock time
     * (timing-oracle prevention). Computed at construction from the injected encoder — never
     * a hardcoded literal — so no bcrypt hash is committed to source. Its source password is
     * irrelevant; a real password will never match it.
     */
    private final String dummyBcryptHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        // Compute the timing-equalizer hash once at startup (cost-12, same as real hashes).
        this.dummyBcryptHash = passwordEncoder.encode("timing-equalizer-not-a-credential");
    }

    /**
     * Result of a successful login or refresh — holds the tokens to set in cookies.
     */
    public record TokenPair(String accessToken, String refreshToken) {}

    /**
     * Validates credentials and issues both tokens.
     *
     * <p>Timing: bcrypt runs in both the found-user and unknown-email branches so
     * response time does not reveal whether the email exists (timing oracle prevention).
     *
     * @throws RateLimitExceededException if the email has exceeded the rate limit
     * @throws AuthenticationException    if the credentials are invalid (same message
     *                                    whether the email doesn't exist, password is wrong,
     *                                    or the account is not ACTIVE — no status enumeration)
     */
    @Transactional
    public TokenPair login(String email, String password) {
        if (!rateLimiter.tryConsume(email)) {
            throw new RateLimitExceededException();
        }

        // Normalize email the same way the rate limiter and repository lookup do.
        String normalizedEmail = email.strip().toLowerCase(java.util.Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            // Run a dummy bcrypt comparison to match the timing of the password-check branch.
            passwordEncoder.matches(password, dummyBcryptHash);
            throw new AuthenticationException();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException();
        }

        // Reject non-ACTIVE users with the same generic error to avoid status enumeration.
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException();
        }

        rateLimiter.reset(email);
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = refreshTokenService.createToken(user);
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Rotates the refresh token and issues a new access token.
     * If the user's status is no longer ACTIVE, all tokens are revoked and 401 is returned.
     * The old refresh token is revoked; the new pair is returned.
     * rotate() validates, revokes-old, and creates-new in one operation.
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        // getUserForToken validates and returns the user without revoking the token.
        User user = refreshTokenService.getUserForToken(rawRefreshToken);

        // If the user has been suspended or deactivated, revoke their tokens and reject.
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenService.revokeAll(user.getId());
            throw new TokenException(TokenException.Reason.REVOKED);
        }

        // rotate() validates again (may detect a concurrent revocation), revokes old, issues new.
        String newRefreshToken = refreshTokenService.rotate(rawRefreshToken);
        String newAccessToken = jwtService.issueAccessToken(user);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    /**
     * Revokes all refresh tokens for the given user (logout via access token).
     */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAll(userId);
    }

    /**
     * Resolves the user from a raw refresh token and revokes all their tokens.
     * Used by the logout endpoint when the caller no longer has a valid access token.
     * Throws {@link TokenException} if the refresh token is unknown, expired, or revoked —
     * the controller silently swallows that exception.
     */
    @Transactional
    public void logoutViaRefreshToken(String rawRefreshToken) {
        User user = refreshTokenService.getUserForToken(rawRefreshToken);
        refreshTokenService.revokeAll(user.getId());
    }

    /**
     * Creates a new user account during the activation flow.
     *
     * <p>Called only by the activation orchestrator — this is the single permitted crossing
     * from the subscription domain into identity. Callers in other domains must go through this
     * method, never reach {@code UserRepository} directly.
     *
     * @param email        user email (must be unique — service layer should guard uniqueness)
     * @param rawPassword  plaintext password (bcrypt-hashed here, never stored raw)
     * @param firstName    user first name
     * @param lastName     user last name
     * @param role         the role to assign
     * @param initialStatus the initial {@link UserStatus}
     * @return the persisted {@link User}
     */
    @Transactional
    public User createUser(String email, String rawPassword, String firstName, String lastName,
                           Role role, UserStatus initialStatus) {
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(email.strip().toLowerCase(java.util.Locale.ROOT),
                hash, firstName, lastName, role, initialStatus);
        return userRepository.save(user);
    }

    /**
     * Issues a JWT access token and a fresh refresh token for an already-authenticated
     * (or just-created) user without requiring their password.
     *
     * <p>Used by the activation flow immediately after account creation so the
     * subscriber is signed in without a second login round-trip.
     *
     * @param user the persisted user to issue tokens for
     * @return a token pair to set in cookies
     */
    @Transactional
    public TokenPair issueTokensFor(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = refreshTokenService.createToken(user);
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Returns the public profile for the authenticated user.
     */
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        return MeResponse.from(user);
    }
}
