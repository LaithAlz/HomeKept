package com.homekept.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/reset.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "token must not be blank")
        String token,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {}
