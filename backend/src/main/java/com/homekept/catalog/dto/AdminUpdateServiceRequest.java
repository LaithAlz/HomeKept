package com.homekept.catalog.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/admin/services/{id}}.
 *
 * <p>All fields are optional/nullable — PATCH semantics match
 * {@code AdminUpdateSkuRequest}/{@code PropertyService.updateSkuSheet}: a field omitted (or
 * explicitly {@code null}) leaves the corresponding column unchanged. There is currently no
 * way to clear {@code aLaCartePriceCents} back to {@code null} once set — same known
 * limitation as the SKU sheet fields.
 *
 * <p>This endpoint never touches {@code active} — archiving/restoring is a separate,
 * explicit action ({@code POST /api/admin/services/{id}/archive} /
 * {@code .../restore}), never a hard delete, per the founder's scope decision.
 *
 * <p>{@code category}/{@code tierClass} use {@code @Pattern} rather than binding straight
 * to the enum, so a bad value (when present) is a 400 {@code VALIDATION_FAILED} instead of
 * a raw Jackson enum-coercion error; {@code @Pattern} treats {@code null} as valid, so
 * omitting the field is unaffected.
 */
public record AdminUpdateServiceRequest(
        @Size(max = 200) String name,

        @Pattern(regexp = "HVAC|PLUMBING|EXTERIOR|SMART_HOME",
                 message = "category must be one of HVAC, PLUMBING, EXTERIOR, SMART_HOME")
        String category,

        @Pattern(regexp = "BASIC|MEDIUM|PREMIUM",
                 message = "tierClass must be one of BASIC, MEDIUM, PREMIUM")
        String tierClass,

        @Positive(message = "defaultDurationMinutes must be positive")
        Integer defaultDurationMinutes,

        @Positive(message = "aLaCartePriceCents must be positive")
        Integer aLaCartePriceCents,

        String description,

        Boolean isFreeWithEveryVisit
) {}
