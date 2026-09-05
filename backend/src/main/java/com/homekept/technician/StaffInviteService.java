package com.homekept.technician;

import com.homekept.identity.AuthService;
import com.homekept.identity.PasswordResetTokenService;
import com.homekept.identity.Role;
import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserStatus;
import com.homekept.identity.exception.InvalidPasswordResetTokenException;
import com.homekept.identity.exception.InvalidStaffInviteTokenException;
import com.homekept.technician.dto.StaffInviteValidateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the staff (technician) invite acceptance flow — the TECHNICIAN-role
 * counterpart of the customer magic-link activation flow ({@code ActivationService}).
 *
 * <p>Reuses the identity domain's {@link PasswordResetTokenService} rather than a dedicated
 * token table (no migration needed for this issue). Because that table is shared with the
 * customer forgot/reset-password flow (and has no "purpose" column), a resolved token's user
 * is always re-checked here for {@code role == TECHNICIAN} and
 * {@code status == PENDING_ACTIVATION} before this service treats it as a legitimate, still
 * -open staff invite. Without that check, a stray password-reset token for some unrelated
 * account (or a technician's own token from *before* they accepted, replayed after
 * suspension) would otherwise also resolve here.
 *
 * <p>Domain crossings go only through service interfaces — {@link PasswordResetTokenService},
 * {@link AuthService}, {@link UserQueryService} — never identity's repository or
 * {@code User} entity directly.
 */
@Service
public class StaffInviteService {

    private final PasswordResetTokenService passwordResetTokenService;
    private final AuthService authService;
    private final UserQueryService userQueryService;

    public StaffInviteService(PasswordResetTokenService passwordResetTokenService,
                              AuthService authService,
                              UserQueryService userQueryService) {
        this.passwordResetTokenService = passwordResetTokenService;
        this.authService = authService;
        this.userQueryService = userQueryService;
    }

    /**
     * Validates a staff-invite token without consuming it. Returns the invited technician's
     * first name on success, or a safe reason label on failure — never the email or role.
     *
     * @param rawToken the raw token from the invite link
     * @return validate response (valid+firstName or invalid+reason)
     */
    @Transactional(readOnly = true)
    public StaffInviteValidateResponse validate(String rawToken) {
        PasswordResetTokenService.ValidationResult result = passwordResetTokenService.validate(rawToken);
        if (!result.valid()) {
            return StaffInviteValidateResponse.invalid(result.reason());
        }
        String firstName = pendingTechnicianFirstName(result.userId());
        if (firstName == null) {
            // Either no such user, or the resolved user isn't an eligible pending technician
            // (see class Javadoc). Collapse into the same generic INVALID — never leak that a
            // token exists for a different kind of account.
            return StaffInviteValidateResponse.invalid("INVALID");
        }
        return StaffInviteValidateResponse.valid(firstName);
    }

    /**
     * Completes the staff-invite acceptance in one transaction: validates and consumes the
     * token, then sets the password and flips the technician to ACTIVE.
     *
     * <p>Note: {@link PasswordResetTokenService#validateAndConsume} is called first (so
     * single-use is DB-enforced the same way as the customer reset flow), and
     * {@link AuthService#activateInvitedTechnician} is called second, which independently
     * re-checks role/status. If that second check throws, the whole {@code @Transactional}
     * method rolls back — including the token consumption already flushed by the first call
     * — so a rejected attempt (e.g. against a SUSPENDED account) does not leave the token
     * burned.
     *
     * @param rawToken    the raw token from the invite link
     * @param rawPassword the plaintext password chosen by the technician
     * @return the result (userId + fresh auth tokens) for the controller to set cookies with
     * @throws InvalidStaffInviteRequestException if the password is null or shorter than 8
     *         characters
     * @throws InvalidStaffInviteTokenException   if the token is invalid, expired, already
     *         consumed, or the resolved user is not an eligible PENDING_ACTIVATION TECHNICIAN
     */
    @Transactional
    public AcceptResult accept(String rawToken, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new InvalidStaffInviteRequestException("Password must be at least 8 characters");
        }

        Long userId;
        try {
            userId = passwordResetTokenService.validateAndConsume(rawToken);
        } catch (InvalidPasswordResetTokenException e) {
            // Re-thrown under the staff-invite-specific exception so the error copy reads
            // correctly for this flow ("Staff invite link..." not "Password reset link...").
            throw new InvalidStaffInviteTokenException("INVALID");
        }

        var activated = authService.activateInvitedTechnician(userId, rawPassword);
        AuthService.TokenPair tokens = authService.issueTokensFor(activated);

        return new AcceptResult(activated.getId(), tokens.accessToken(), tokens.refreshToken());
    }

    private String pendingTechnicianFirstName(Long userId) {
        Map<Long, UserQueryService.UserSummary> summaries =
                userQueryService.findSummariesByIds(List.of(userId));
        UserQueryService.UserSummary summary = summaries.get(userId);
        if (summary == null
                || !Role.TECHNICIAN.name().equals(summary.role())
                || !UserStatus.PENDING_ACTIVATION.name().equals(summary.status())) {
            return null;
        }
        return summary.firstName();
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /** Carries the result of a successful accept back to the controller layer. */
    public record AcceptResult(Long userId, String accessToken, String refreshToken) {}
}
