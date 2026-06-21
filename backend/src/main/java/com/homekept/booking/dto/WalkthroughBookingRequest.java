package com.homekept.booking.dto;

import com.homekept.booking.BookingDayOfWeek;
import com.homekept.booking.PropertyType;
import com.homekept.booking.TimeOfDay;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for {@code POST /api/bookings/walkthrough}.
 * See api-contract.md for the exact field spec.
 *
 * <p>CASL rule: {@code contactConsent} must be {@code true} — a missing or false value
 * is a 400 (see {@link #isContactConsent()}).
 *
 * <p>Customer-visible validation messages are flagged for copy-guardian.
 */
public record WalkthroughBookingRequest(

        // copy-guardian: validation message below
        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must be 200 characters or fewer")
        String fullName,

        // copy-guardian: validation message below
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        // copy-guardian: validation message below
        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must be 30 characters or fewer")
        String phone,

        // copy-guardian: validation message below
        @NotBlank(message = "Street address is required")
        @Size(max = 255, message = "Street address must be 255 characters or fewer")
        String streetAddress,

        // copy-guardian: validation message below
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be 100 characters or fewer")
        String city,

        // copy-guardian: validation message below
        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must be 20 characters or fewer")
        String postalCode,

        // Optional
        Integer yearBuilt,

        // Optional — validated by the service against allowed set (not Bean Validation:
        // enum-style validation via @Pattern is fragile for nullable fields; service checks it).
        String squareFootageRange,

        // copy-guardian: validation message below
        @NotNull(message = "Property type is required")
        PropertyType propertyType,

        // copy-guardian: validation message below
        @NotNull(message = "Preferred week is required")
        LocalDate preferredWeek,

        // copy-guardian: validation message below
        @NotNull(message = "Time of day is required")
        TimeOfDay timeOfDay,

        // Optional — list of day codes; may be null or empty (max 7: one per day of week)
        // copy-guardian: validation message below
        @Size(max = 7, message = "Day preferences must not exceed 7 entries")
        List<BookingDayOfWeek> dayPreferences,

        // Optional
        @Size(max = 2000, message = "Notes must be 2000 characters or fewer")
        String notes,

        // Optional — defaults to WEBSITE_DIRECT in the service
        String leadSource,

        // CASL: required true.
        // copy-guardian: validation message on isContactConsent below
        Boolean contactConsent,

        // Optional — anonymous PostHog ID for funnel stitching (arch §5.7)
        @Size(max = 255, message = "posthogDistinctId must be 255 characters or fewer")
        String posthogDistinctId

) {
    /**
     * CASL consent check: {@code contactConsent} must be {@code true}.
     * A missing (null) or explicit false value is rejected with 400.
     *
     * <p>Method name {@code isContactConsent} makes Bean Validation derive the
     * property node as {@code contactConsent}, so the error map key matches the
     * request field name the frontend expects.
     *
     * copy-guardian: message below
     */
    @AssertTrue(message = "Contact consent is required to proceed")
    public boolean isContactConsent() {
        return Boolean.TRUE.equals(contactConsent);
    }
}
