package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/tech/visits/{id}/incomplete}.
 * Transitions the visit to INCOMPLETE and auto-creates a follow-up SCHEDULED visit.
 */
public record TechIncompleteVisitRequest(
        @NotBlank @Size(max = 2000) String reason
) {}
