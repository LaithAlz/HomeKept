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
 *
 * <p>{@code todos}: the subscriber's "your list" items folded into THIS visit
 * ({@code TodoItem.visitId == this visit's id}), regardless of current status (so a
 * tech can see already-DONE/DECLINED items too). The tech app targets
 * {@code PATCH /api/tech/todos/{id}} using {@link TodoResponse#id}.
 *
 * <p>{@code flags}: OPEN {@link com.homekept.visit.Flag}s on this visit's subscriber — prior observations
 * that haven't been folded into a visit yet, shown here for context per the
 * observe → flag loop (flags are subscriber-scoped, not visit-scoped, so the same
 * open flag may appear on more than one of a subscriber's visits until resolved).
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

        List<VisitServiceItem> services,
        List<TodoResponse> todos,
        List<FlagResponse> flags
) {}
