package com.homekept.technician.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/staff/invite/validate}.
 */
public record StaffInviteValidateRequest(
        @NotBlank(message = "token must not be blank")
        String token
) {}
