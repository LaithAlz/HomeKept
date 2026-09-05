package com.homekept.technician;

import com.homekept.identity.AuthService;
import com.homekept.identity.PasswordResetTokenService;
import com.homekept.identity.Role;
import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserStatus;
import com.homekept.technician.dto.AdminTechnicianListItem;
import com.homekept.technician.dto.CreateTechnicianRequest;
import com.homekept.technician.dto.CreateTechnicianResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin service for onboarding and managing technicians.
 *
 * <h2>Invite-by-email onboarding</h2>
 * <p>The admin supplies identity only (name, email, optional phone) — never a raw
 * {@code userId}. {@link #createProfile} creates the {@code User} (TECHNICIAN role,
 * {@code PENDING_ACTIVATION} status, an unusable random password), the
 * {@code technician_profile} row, mints a 7-day invite token, and queues the invite email,
 * all in one transaction. The invited person sets their own password via the staff-invite
 * accept link ({@code StaffInviteController}) — this mirrors the customer activation flow's
 * "invite before password" shape and matches the Jobber/Housecall Pro pattern (admin-set
 * temporary passwords are the anti-pattern this replaces).
 *
 * <p>{@code fullyLoadedHourlyCostCents}, {@code employeeStatus}, and {@code hireDate} are not
 * captured at invite time (they belong on a later technician-edit screen, not yet built) —
 * a new profile is created with all three left {@code null} (the
 * {@code fully_loaded_hourly_cost_cents} column is nullable in the V7 migration).
 *
 * <h2>Domain boundaries</h2>
 * <p>This service does NOT call the identity repository or entity directly. It stores
 * the {@code userId} as a bare foreign key. Cross-domain calls go only through identity's
 * services: {@link AuthService} (create/activate the user), {@link UserQueryService}
 * (identity lookups), {@link PasswordResetTokenService} (invite tokens, reused from the
 * forgot/reset-password flow — see that service's Javadoc for why).
 */
@Service
public class TechnicianAdminService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianAdminService.class);

    private final TechnicianProfileRepository technicianProfileRepository;
    private final UserQueryService userQueryService;
    private final AuthService authService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final StaffInviteNotifier staffInviteNotifier;

    public TechnicianAdminService(TechnicianProfileRepository technicianProfileRepository,
                                  UserQueryService userQueryService,
                                  AuthService authService,
                                  PasswordResetTokenService passwordResetTokenService,
                                  StaffInviteNotifier staffInviteNotifier) {
        this.technicianProfileRepository = technicianProfileRepository;
        this.userQueryService = userQueryService;
        this.authService = authService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.staffInviteNotifier = staffInviteNotifier;
    }

    /**
     * Invites a new technician: creates the {@code User} (TECHNICIAN, PENDING_ACTIVATION),
     * the {@code technician_profile}, mints an invite token, and queues the invite email —
     * all in one transaction.
     *
     * <p>409 if a user with this email already exists ({@link StaffEmailAlreadyExistsException}).
     *
     * @param request the invite identity data (name, email, optional phone)
     * @return the created profile + identity summary
     */
    @Transactional
    public CreateTechnicianResponse createProfile(CreateTechnicianRequest request) {
        if (userQueryService.existsByEmail(request.email())) {
            throw new StaffEmailAlreadyExistsException();
        }

        // Unusable password: a value nobody (including this process, after this call
        // returns) retains, so the account cannot authenticate before the invite is
        // accepted. PENDING_ACTIVATION independently blocks login regardless
        // (AuthService.login's status gate) — this closes the gap even if that ever changed.
        String unusablePassword = UUID.randomUUID().toString() + UUID.randomUUID();

        var user = authService.createUser(
                request.email(), unusablePassword, request.firstName(), request.lastName(),
                request.phone(), Role.TECHNICIAN, UserStatus.PENDING_ACTIVATION);

        TechnicianProfile profile = new TechnicianProfile(user.getId(), null, null, null);
        TechnicianProfile saved = technicianProfileRepository.save(profile);

        PasswordResetTokenService.MintResult mint =
                passwordResetTokenService.mintStaffInvite(user.getId());
        staffInviteNotifier.sendInvite(user.getEmail(), user.getFirstName(), mint.rawToken());

        log.info("staff_invite_created profileId={} userId={}", saved.getId(), user.getId());

        return new CreateTechnicianResponse(saved.getId(), user.getId(), user.getFirstName(),
                user.getLastName(), user.getEmail(), user.getStatus().name(), mint.createdAt());
    }

    /**
     * Resends the invite for an existing (typically still-pending) technician profile:
     * invalidates the user's prior unconsumed staff-invite tokens, then mints a fresh one and
     * queues a new invite email — in one transaction, so the old link stops working the
     * moment the new one is sent.
     *
     * <p><b>Resolve-before-touch:</b> the target user's identity and status are resolved and
     * checked BEFORE any token is invalidated or minted. Without this ordering, resending to
     * an already-{@code ACTIVE} technician (e.g. an admin clicking "Resend" on a roster row
     * rendered from a stale cached status) would mail that account a fresh, 7-day,
     * password-setting link — redeemable to take over the account. Only a still-eligible
     * {@code PENDING_ACTIVATION} {@code TECHNICIAN} may be re-invited.
     *
     * @param profileId the {@code technician_profile} id (the roster row's {@code id})
     * @throws TechnicianNotFoundException if no profile exists with this id
     * @throws TechnicianNotEligibleForInviteException if the linked user is not currently an
     *         eligible PENDING_ACTIVATION TECHNICIAN
     */
    @Transactional
    public void resendInvite(Long profileId) {
        TechnicianProfile profile = technicianProfileRepository.findById(profileId)
                .orElseThrow(() -> new TechnicianNotFoundException(
                        "No technician profile with id=" + profileId));

        Map<Long, UserQueryService.UserSummary> summaries =
                userQueryService.findSummariesByIds(List.of(profile.getUserId()));
        UserQueryService.UserSummary summary = summaries.get(profile.getUserId());

        if (summary == null
                || !Role.TECHNICIAN.name().equals(summary.role())
                || !UserStatus.PENDING_ACTIVATION.name().equals(summary.status())) {
            throw new TechnicianNotEligibleForInviteException(
                    "This technician is not eligible for a new invite.");
        }

        passwordResetTokenService.invalidateAllForUser(profile.getUserId());

        PasswordResetTokenService.MintResult mint =
                passwordResetTokenService.mintStaffInvite(profile.getUserId());
        staffInviteNotifier.sendInvite(summary.email(), summary.firstName(), mint.rawToken());

        log.info("staff_invite_resent profileId={} userId={}", profileId, profile.getUserId());
    }

    /**
     * Returns the full technician roster, newest first. Small dataset at MVP (see
     * {@link TechnicianProfile} — two rows at launch), so no pagination is offered.
     *
     * <p>Resolves name/email/role/status for each profile's {@code userId} in a single
     * batch call to {@link UserQueryService} (identity domain's service, not its
     * repository), and the latest invite timestamp in a single batch call to
     * {@link PasswordResetTokenService}. If the linked user is somehow missing, the identity
     * fields are null rather than dropping the row.
     */
    @Transactional(readOnly = true)
    public List<AdminTechnicianListItem> listTechnicians() {
        List<TechnicianProfile> profiles = technicianProfileRepository.findAllByOrderByIdDesc();

        List<Long> userIds = profiles.stream()
                .map(TechnicianProfile::getUserId)
                .collect(Collectors.toList());
        Map<Long, UserQueryService.UserSummary> summaries = userQueryService.findSummariesByIds(userIds);
        Map<Long, Instant> invitedAtByUserId = passwordResetTokenService.latestInviteAtByUserIds(userIds);

        return profiles.stream()
                .map(p -> toListItem(p, summaries.get(p.getUserId()), invitedAtByUserId.get(p.getUserId())))
                .collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AdminTechnicianListItem toListItem(TechnicianProfile p, UserQueryService.UserSummary summary,
            Instant invitedAt) {
        return new AdminTechnicianListItem(
                p.getId(),
                p.getUserId(),
                summary != null ? summary.firstName() : null,
                summary != null ? summary.lastName() : null,
                summary != null ? summary.email() : null,
                summary != null ? summary.role() : null,
                summary != null ? summary.status() : null,
                p.getEmployeeStatus(),
                p.getHireDate(),
                p.getFullyLoadedHourlyCostCents(),
                p.getCreatedAt(),
                invitedAt
        );
    }
}
