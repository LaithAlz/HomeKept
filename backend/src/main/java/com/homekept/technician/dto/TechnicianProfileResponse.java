package com.homekept.technician.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for a {@code technician_profile} row.
 * No PII beyond userId and cost — role of the user is identity-domain's concern.
 */
public record TechnicianProfileResponse(
        Long id,
        Long userId,
        String employeeStatus,
        LocalDate hireDate,
        Integer fullyLoadedHourlyCostCents,
        Instant createdAt
) {}
