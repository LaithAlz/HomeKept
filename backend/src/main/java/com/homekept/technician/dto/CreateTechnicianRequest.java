package com.homekept.technician.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/technicians} — invite a new technician by
 * identity only (name, email, optional phone). The account is created
 * {@code PENDING_ACTIVATION} with an unusable random password; the invited person sets
 * their own password via the staff-invite accept link ({@code StaffInviteController}). The
 * role is always server-set to TECHNICIAN — never read from this request.
 *
 * <p>{@code fullyLoadedHourlyCostCents}, {@code employeeStatus}, and {@code hireDate} are
 * deliberately not on this form — they belong on a later technician-edit screen (not yet
 * built), not the invite step.
 */
public record CreateTechnicianRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        @Size(max = 30, message = "Phone must be 30 characters or fewer")
        String phone
) {}
