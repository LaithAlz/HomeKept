package com.homekept.visit.dto;

/**
 * Response after a technician starts a visit ({@code POST /api/tech/visits/{id}/start}).
 */
public record TechStartVisitResponse(
        Long id,
        String status
) {}
