package com.homekept.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link BookingNotifier}.
 *
 * <p>Logs a placeholder line in lieu of a real email send. No secrets, no SendGrid
 * dependency — this is the MVP stand-in. The notification slice supersedes this by
 * registering a {@code @Primary} real implementation.
 *
 * <p>No PII is logged — only the booking ID (internal) is referenced, per CLAUDE.md.
 *
 * copy-guardian: log message below ("would send booking confirmation" — internal log,
 * not customer-visible, but noted for completeness).
 */
@Component
public class DefaultBookingNotifier implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultBookingNotifier.class);

    @Override
    public void sendBookingConfirmation(WalkthroughBooking booking) {
        // No real email is sent here. The notification slice registers a @Primary
        // implementation when SendGrid is available.
        log.info("would send booking confirmation for booking id={}", booking.getId());
    }
}
