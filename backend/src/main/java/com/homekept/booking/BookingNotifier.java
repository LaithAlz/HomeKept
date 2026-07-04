package com.homekept.booking;

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
}
