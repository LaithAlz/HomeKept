package com.homekept.booking;

/**
 * Notification seam for booking-related emails.
 *
 * <p>The MVP default implementation ({@link DefaultBookingNotifier}) logs a placeholder
 * message. The notification slice will later register a {@code @Primary} real
 * implementation backed by SendGrid — the plain {@code @Component} default bean will
 * then be superseded without any code change here.
 *
 * <p>Pattern: plain {@code @Component} default + later {@code @Primary} real impl.
 * Do NOT use {@code @ConditionalOnMissingBean} on a component-scanned bean.
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
