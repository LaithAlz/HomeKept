package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/app/todos}.
 * No PII — body is customer-written free text (not logged, not sent to analytics).
 */
public record AppCreateTodoRequest(
        @NotBlank(message = "body is required")
        @Size(max = 1000, message = "body must be at most 1000 characters")
        String body
) {}
