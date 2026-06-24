package com.homekept.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/admin/reschedule-requests/{id}/decline}.
 *
 * <p>A note is required — it explains to the customer why the reschedule could not be
 * accommodated and is stored on {@code reschedule_request.admin_note}.
 */
public record AdminDeclineRescheduleRequest(
        @NotBlank(message = "A note is required when declining")
        @Size(max = 1000, message = "Note must be at most 1000 characters")
        String adminNote
) {}
