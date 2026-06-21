package com.homekept.booking.dto;

import com.homekept.booking.WalkthroughBooking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full detail DTO returned by {@code PATCH /api/admin/bookings/{id}} after a successful update.
 */
public record AdminBookingDetail(
        Long id,
        String status,
        String fullName,
        String email,
        String phone,
        String streetAddress,
        String city,
        String postalCode,
        Integer yearBuilt,
        String squareFootageRange,
        String propertyType,
        LocalDate preferredWeek,
        String timeOfDay,
        List<String> dayPreferences,
        String notes,
        String leadSource,
        Instant scheduledFor,
        Instant performedAt,
        Instant contactConsentAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminBookingDetail from(WalkthroughBooking b) {
        return new AdminBookingDetail(
                b.getId(),
                b.getStatus().name(),
                b.getFullName(),
                b.getEmail(),
                b.getPhone(),
                b.getStreetAddress(),
                b.getCity(),
                b.getPostalCode(),
                b.getYearBuilt(),
                b.getSquareFootageRange(),
                b.getPropertyType().name(),
                b.getPreferredWeek(),
                b.getTimeOfDay().name(),
                b.getDayPreferences().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.toList()),
                b.getNotes(),
                b.getLeadSource().name(),
                b.getScheduledFor(),
                b.getPerformedAt(),
                b.getContactConsentAt(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
