package com.homekept.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link ActivationNotifier}.
 *
 * <p>Logs the would-be action without sending real email or exposing PII.
 * The notification slice (SendGrid) replaces this with the real implementation.
 *
 * <p>No PII in log properties — only booking_id (an internal ID, not PII per arch doc §5.2).
 * The email address is NOT logged.
 */
@Component
public class DefaultActivationNotifier implements ActivationNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultActivationNotifier.class);

    @Override
    public void sendActivationLink(String email, String rawToken, Long bookingId) {
        // STUB: notification slice replaces this with real SendGrid email.
        // No PII in log — booking_id only.
        log.info("STUB would send activation link bookingId={}", bookingId);
    }
}
