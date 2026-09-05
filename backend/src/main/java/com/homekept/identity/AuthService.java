package com.homekept.identity;

import com.homekept.identity.dto.MeResponse;
import com.homekept.identity.exception.AuthenticationException;
import com.homekept.identity.exception.InvalidPasswordChangeRequestException;
import com.homekept.identity.exception.InvalidPasswordResetRequestException;
import com.homekept.identity.exception.InvalidPasswordResetTokenException;
import com.homekept.identity.exception.InvalidStaffInviteTokenException;
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
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordResetNotifier passwordResetNotifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter,
                       PasswordResetTokenService passwordResetTokenService,
                       PasswordResetNotifier passwordResetNotifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.passwordResetTokenService = passwordResetTokenService;
        this.passwordResetNotifier = passwordResetNotifier;
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
        return createUser(email, rawPassword, firstName, lastName, null, role, initialStatus);
    }

    /**
     * Same as {@link #createUser(String, String, String, String, Role, UserStatus)} but also
     * sets the optional phone number — used by the technician staff-invite flow
     * ({@code TechnicianAdminService}), which collects a phone at invite time.
     *
     * @param phone the user's phone number, or null/blank if not supplied
     */
    @Transactional
    public User createUser(String email, String rawPassword, String firstName, String lastName,
                           String phone, Role role, UserStatus initialStatus) {
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(email.strip().toLowerCase(java.util.Locale.ROOT),
                hash, firstName, lastName, role, initialStatus);
        user.setPhone(phone);
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
     * Requests a password reset for the given email. Always completes normally — the
     * controller returns 202 whether or not the email belongs to an account (no
     * enumeration, per api-contract.md).
     *
     * <p>Timing: when the email is unknown, {@link PasswordResetTokenService#mintDummy()}
     * runs the same nonce/HMAC computation as a real mint so the two branches' CPU cost is
     * close (same idea as the dummy bcrypt comparison in {@link #login}). This method does
     * <b>not</b> attempt to equalize wall-clock time on its own — a one-sided sleep here
     * would only work if the found-email branch's outbound SendGrid call actually costs
     * real network time, which it doesn't in the default (SendGrid-unconfigured) config
     * (#120), so a one-sided delay would invert the oracle instead of closing it. The
     * wall-clock gap is closed by {@link AuthController#forgot} padding BOTH branches to a
     * shared fixed response-time budget after this method returns (i.e. after this method's
     * {@code @Transactional} block has committed) — see that method's Javadoc for the budget
     * and its tradeoffs.
     *
     * @param email the email address submitted on the forgot-password form
     */
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.strip().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            passwordResetTokenService.mintDummy();
            return;
        }

        PasswordResetTokenService.MintResult mint = passwordResetTokenService.mint(user);
        passwordResetNotifier.sendResetLink(user.getEmail(), user.getFirstName(), mint.rawToken(), user.getId());
    }

    /**
     * Completes a password reset: validates and consumes the token, sets the new bcrypt
     * password, revokes all the user's refresh tokens, and signs the user back in.
     *
     * <p><b>Guards:</b>
     * <ol>
     *   <li>The token must be {@link TokenPurpose#PASSWORD_RESET} — a
     *       {@link TokenPurpose#STAFF_INVITE} token (or any other purpose that might exist in
     *       future) is rejected with the same generic {@code INVALID_TOKEN} as a malformed
     *       token, never distinguished. This is what closes the path where a staff invite
     *       token — long-lived (7 days) and mailed to an account that has no password set
     *       yet — could otherwise be redeemed here instead of at
     *       {@code /api/staff/invite/accept}.</li>
     *   <li>The resolved user must be {@link UserStatus#ACTIVE}. A password reset for an
     *       account that has never had a real password (still {@code PENDING_ACTIVATION}) or
     *       that has been {@code SUSPENDED} is meaningless — and, combined with the first
     *       guard, is the second half of the account-takeover path this method now closes:
     *       an admin resending a staff invite to the wrong (already-{@code ACTIVE}) account
     *       used to be the only thing standing between a stray token and a live session; now
     *       even a wrongly-issued {@code PASSWORD_RESET} token for a non-{@code ACTIVE}
     *       account is rejected outright. Every genuine customer is created {@code ACTIVE}
     *       (see {@code ActivationService.complete}), so this never fires for a real
     *       customer reset.</li>
     * </ol>
     * Both guards run AFTER {@code validateAndConsume} in this same {@code @Transactional}
     * method, so a rejection rolls back that consumption too — a rejected attempt never burns
     * the token (mirrors {@link #activateInvitedTechnician}'s exact pattern).
     *
     * @param rawToken    the raw reset token from the reset link
     * @param newPassword the new plaintext password (bcrypt-hashed here)
     * @return a fresh token pair for the now-signed-in user
     * @throws InvalidPasswordResetRequestException if the password is null or shorter than
     *         8 characters
     * @throws InvalidPasswordResetTokenException if the token is invalid, expired, consumed,
     *         of the wrong purpose, or resolves to a non-ACTIVE user
     */
    @Transactional
    public TokenPair resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordResetRequestException("Password must be at least 8 characters");
        }

        Long userId = passwordResetTokenService.validateAndConsume(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("INVALID"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidPasswordResetTokenException("INVALID");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAll(user.getId());

        return issueTokensFor(user);
    }

    /**
     * Changes the authenticated user's password: verifies the current password, requires
     * the new one to be at least 8 characters and different from the current password, sets
     * it, revokes all of the user's refresh tokens, and issues a fresh token pair so the
     * caller (who already proved possession of the account) stays signed in.
     *
     * <p>Any authenticated role may call this (not CUSTOMER-only) — every role authenticates
     * with a password.
     *
     * @param userId          the authenticated user's id (JWT principal)
     * @param currentPassword the password to verify against the stored hash
     * @param newPassword     the new plaintext password (bcrypt-hashed here)
     * @return a fresh token pair to set in cookies
     * @throws InvalidPasswordChangeRequestException if the current password is wrong, the
     *         new password is shorter than 8 characters, or it matches the current password
     */
    @Transactional
    public TokenPair changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidPasswordChangeRequestException("Current password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordChangeRequestException("New password must be at least 8 characters");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidPasswordChangeRequestException(
                    "New password must be different from your current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAll(user.getId());

        return issueTokensFor(user);
    }

    /**
     * Completes a staff (technician) invite acceptance: sets the chosen password and flips
     * the invited user from {@link UserStatus#PENDING_ACTIVATION} to {@link UserStatus#ACTIVE}.
     *
     * <p><b>Critical guard:</b> rejects any user who is not currently a {@link Role#TECHNICIAN}
     * in {@link UserStatus#PENDING_ACTIVATION}. Without this, a still-valid (unexpired,
     * unconsumed) invite token could be replayed to reactivate a SUSPENDED account or to
     * silently reset an already-ACTIVE technician's password outside the normal reset flow.
     * Called only after the caller ({@code StaffInviteService.accept}) has already validated
     * and consumed the invite token in the same {@code @Transactional} method — if this guard
     * throws, that token consumption is rolled back too, so a rejected attempt does not burn
     * a token that might otherwise have belonged to some other, unrelated flow.
     *
     * @param userId      the user id resolved from the (already-consumed) invite token
     * @param rawPassword the plaintext password chosen by the technician (bcrypt-hashed here)
     * @return the now-ACTIVE user
     * @throws InvalidStaffInviteTokenException if the user does not exist, is not a
     *         TECHNICIAN, or is not currently PENDING_ACTIVATION
     */
    @Transactional
    public User activateInvitedTechnician(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStaffInviteTokenException("INVALID"));
        if (user.getRole() != Role.TECHNICIAN || user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw new InvalidStaffInviteTokenException("INVALID");
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
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
