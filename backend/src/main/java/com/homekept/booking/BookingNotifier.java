package com.homekept.booking;

import java.time.Instant;

/**
 * Notification seam for booking-related emails.
 *
 * <p>{@link DefaultBookingNotifier} sends the real booking-confirmation email via the
 * notification domain's {@code EmailSender} (SendGrid) — see #11.
 */
public interface BookingNotifier {

    /**
     * Called after a new walk-through booking is persisted.
     * No PII in any log calls — only booking ID and city (per analytics rules).
     *
     * @param booking the newly created booking
     */
    void sendBookingConfirmation(WalkthroughBooking booking);

    /**
     * Sends the 24h-before walk-through reminder (#89), triggered by
     * {@code com.homekept.notification.ReminderScheduler} for CONFIRMED bookings.
     *
     * <p>Takes the fields needed to render the email directly (rather than a
     * {@link WalkthroughBooking}) — the scheduler lives in the notification domain and
     * resolves these via {@code BookingService.findConfirmedInWindow}, never this domain's
     * repository or entity.
     *
     * @param bookingId     the booking id (safe to log)
     * @param email         recipient email (not logged)
     * @param fullName      recipient's full name, split to a first name for the greeting
     * @param streetAddress property street address
     * @param city          property city
     * @param scheduledFor  the walk-through's scheduled time (UTC; rendered in the display zone)
     */
    void sendBookingReminder(Long bookingId, String email, String fullName,
            String streetAddress, String city, Instant scheduledFor);
}
