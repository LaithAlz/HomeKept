package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/tech/visits/{id}/flags}.
 */
public record TechCreateFlagRequest(
        @NotBlank @Size(max = 2000) String body,

        @NotNull
        @Pattern(regexp = "INFO|ATTENTION|URGENT",
                 message = "severity must be INFO, ATTENTION, or URGENT")
        String severity,

        /** Optional R2 storage key for a photo attached to the flag. Nullable. */
        @Size(max = 1024) String photoStorageKey
) {}
