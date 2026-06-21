package com.homekept.booking.dto;

import com.homekept.booking.WalkthroughBooking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Summary DTO returned in the admin booking pipeline list.
 * {@code GET /api/admin/bookings} — cursor-paginated, id-descending.
 */
public record AdminBookingListItem(
        Long id,
        String status,
        String fullName,
        String email,
        String phone,
        String city,
        String propertyType,
        LocalDate preferredWeek,
        String timeOfDay,
        List<String> dayPreferences,
        String leadSource,
        Instant scheduledFor,
        Instant createdAt
) {
    public static AdminBookingListItem from(WalkthroughBooking b) {
        return new AdminBookingListItem(
                b.getId(),
                b.getStatus().name(),
                b.getFullName(),
                b.getEmail(),
                b.getPhone(),
                b.getCity(),
                b.getPropertyType().name(),
                b.getPreferredWeek(),
                b.getTimeOfDay().name(),
                b.getDayPreferences().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.toList()),
                b.getLeadSource().name(),
                b.getScheduledFor(),
                b.getCreatedAt()
        );
    }
}
