package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/tech/visits/{id}/incomplete}.
 * Transitions the visit to INCOMPLETE and auto-creates a follow-up SCHEDULED visit.
 */
public record TechIncompleteVisitRequest(
        @NotBlank String reason
) {}
