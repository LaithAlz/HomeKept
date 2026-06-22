package com.homekept.subscription;

/**
 * Seam for sending activation magic-link emails.
 *
 * <p>The real implementation (SendGrid) is built in the notification slice.
 * At MVP, {@link DefaultActivationNotifier} logs the would-be link.
 */
public interface ActivationNotifier {

    /**
     * Sends (or logs) the activation magic link to the prospective subscriber.
     *
     * @param email     recipient email address — not logged in properties
     * @param rawToken  the raw HMAC-signed activation token for the magic link
     * @param bookingId the booking id — safe to log (not PII per arch doc §5.2)
     */
    void sendActivationLink(String email, String rawToken, Long bookingId);
}
