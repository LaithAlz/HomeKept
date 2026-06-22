package com.homekept.visit.dto;

import java.time.Instant;
import java.util.List;

/**
 * A single visit on the technician's day sheet.
 *
 * <p>{@code accessNotes}: decrypted plaintext access notes for the property.
 * ONLY returned on the day sheet for the assigned technician. NEVER logged.
 * NEVER returned on any other endpoint.
 *
 * <p>The DTO itself does not enforce the "never log" rule — that's the responsibility
 * of every logging call-site that touches this object. The field name is deliberately
 * named to make it obvious what it contains.
 */
public record TechVisitListItem(
        Long id,
        String name,
        Instant scheduledFor,
        int durationMinutes,
        String status,
        String type,

        // Property fields (address for navigation)
        String streetAddress,
        String unit,
        String city,
        String postalCode,

        /**
         * Decrypted access notes — lockbox codes, alarm codes, etc.
         * ONLY on the day sheet for the assigned tech. NEVER log this value.
         */
        String accessNotes,

        List<VisitServiceItem> services
) {}
