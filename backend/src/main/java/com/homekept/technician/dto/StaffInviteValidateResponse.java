package com.homekept.technician.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code POST /api/staff/invite/validate}.
 *
 * <p>On success: {@code { "valid": true, "firstName": "Priya" }} — first name only, never
 * the email or role (mirrors {@code ActivationValidateResponse}).
 * On failure: {@code { "valid": false, "reason": "EXPIRED" | "USED" | "INVALID" }}.
 * Null fields are omitted from serialisation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffInviteValidateResponse(
        boolean valid,
        String firstName,
        String reason
) {

    public static StaffInviteValidateResponse valid(String firstName) {
        return new StaffInviteValidateResponse(true, firstName, null);
    }

    public static StaffInviteValidateResponse invalid(String reason) {
        return new StaffInviteValidateResponse(false, null, reason);
    }
}
