package com.homekept.technician.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/staff/invite/accept}.
 */
public record StaffInviteAcceptRequest(
        @NotBlank(message = "token must not be blank")
        String token,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {}
