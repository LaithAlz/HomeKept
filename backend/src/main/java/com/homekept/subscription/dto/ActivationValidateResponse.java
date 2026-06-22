package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code POST /api/activation/validate}.
 *
 * <p>On success: {@code { "valid": true, "bookingId": 123, "firstName": "Priya" }}.
 * On failure: {@code { "valid": false, "reason": "EXPIRED" | "USED" | "INVALID" }}.
 * Null fields are omitted from serialisation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivationValidateResponse(
        boolean valid,
        Long bookingId,
        String firstName,
        String reason
) {

    public static ActivationValidateResponse valid(Long bookingId, String firstName) {
        return new ActivationValidateResponse(true, bookingId, firstName, null);
    }

    public static ActivationValidateResponse invalid(String reason) {
        return new ActivationValidateResponse(false, null, null, reason);
    }
}
