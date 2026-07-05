package com.homekept.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code GET /api/app/account} — the customer app's settings/profile page.
 *
 * <p>Bundles identity ({@code firstName}/{@code lastName}/{@code email}) with the service
 * property's address so the settings page can render both without two round trips. Name and
 * email duplicate a subset of {@code GET /api/auth/me}; that is deliberate for this endpoint.
 *
 * <p><strong>Never includes decrypted access notes</strong> — those are technician-only
 * (see {@link com.homekept.property.PropertyService#decryptAccessNotes}). {@code unit} is
 * {@code null} when the property has no unit number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppAccountResponse(
        String firstName,
        String lastName,
        String email,
        String streetAddress,
        String unit,
        String city,
        String postalCode
) {}
