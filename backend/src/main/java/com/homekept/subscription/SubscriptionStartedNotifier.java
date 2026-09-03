package com.homekept.subscription;

/**
 * Seam for sending "subscription started" notifications.
 *
 * <p>Called by {@link StripeWebhookService} when a {@code checkout.session.completed}
 * event (mode=subscription) successfully activates a subscriber.
 *
 * <p>{@link DefaultSubscriptionStartedNotifier} sends the real welcome email via
 * {@code EmailSender} (SendGrid).
 */
public interface SubscriptionStartedNotifier {

    /**
     * Notifies the subscriber that their subscription has been activated.
     *
     * @param subscriberId the HomeKept subscriber id — safe to log (not PII)
     * @param planCode     the plan code string (ESSENTIAL / COMPLETE / PREMIER)
     */
    void onSubscriptionStarted(Long subscriberId, String planCode);
}
