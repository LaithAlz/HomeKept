package com.homekept.booking.dto;

/**
 * Response body for {@code POST /api/bookings/walkthrough} (201 Created).
 * Per api-contract.md: {@code { "id": 123, "status": "PENDING" }}.
 */
public record WalkthroughBookingResponse(Long id, String status) {}
