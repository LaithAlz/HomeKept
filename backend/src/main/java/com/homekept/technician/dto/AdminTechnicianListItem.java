package com.homekept.technician.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Admin roster item for {@code GET /api/admin/technicians}.
 *
 * <p>{@code firstName}, {@code lastName}, {@code email}, {@code role}, and
 * {@code userStatus} come from the identity domain via
 * {@code UserQueryService.findSummariesByIds} (the technician domain never touches the
 * {@code users} table directly). They are {@code null} only if the linked user has been
 * deleted out from under the profile, which should not happen in normal operation.
 *
 * <p>This is internal staff data shown only to ADMIN users — not customer PII, so it is
 * not subject to the no-name rule that applies to the subscriber-facing admin lists.
 *
 * <p>{@code invitedAt} is the most recently minted staff-invite token's {@code createdAt}
 * for this user, resolved from the (shared) {@code password_reset_tokens} table via
 * {@code PasswordResetTokenService.latestInviteAtByUserIds} — {@code null} until an invite
 * has ever been sent for this profile, else the latest invite/resend timestamp. Mirrors the
 * walk-through pipeline's {@code invitedAt} semantics (resolved server-side, never a
 * frontend-only flag).
 */
public record AdminTechnicianListItem(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String role,          // identity Role name, e.g. "TECHNICIAN"
        String userStatus,    // identity UserStatus name, e.g. "ACTIVE"
        String employeeStatus,
        LocalDate hireDate,
        Integer fullyLoadedHourlyCostCents, // integer cents; nullable
        Instant createdAt,
        Instant invitedAt
) {}
