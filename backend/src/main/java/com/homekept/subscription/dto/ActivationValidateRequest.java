package com.homekept.subscription.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/activation/validate}.
 */
public record ActivationValidateRequest(
        @NotBlank(message = "token must not be blank")
        String token
) {}
