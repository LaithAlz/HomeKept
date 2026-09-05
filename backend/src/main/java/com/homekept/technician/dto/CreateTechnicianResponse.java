package com.homekept.technician.dto;

import java.time.Instant;

/**
 * Response body for {@code POST /api/admin/technicians} (invite) — the created identity +
 * profile pair. No password, no role field (the role is always TECHNICIAN by construction),
 * and no cost/employee-status/hire-date fields, since those are not captured at invite time
 * (see {@link CreateTechnicianRequest}).
 */
public record CreateTechnicianResponse(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String userStatus,
        Instant invitedAt
) {}
