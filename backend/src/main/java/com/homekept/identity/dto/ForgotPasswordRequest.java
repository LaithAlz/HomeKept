package com.homekept.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/forgot.
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email
) {}
