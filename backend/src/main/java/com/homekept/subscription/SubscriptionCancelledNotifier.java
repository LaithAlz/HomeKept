package com.homekept.subscription;

/**
 * Notifies a subscriber that their subscription has been cancelled (terminal). Fired from
 * {@code customer.subscription.deleted} — covers both portal cancellations and self-serve
 * cancel-at-period-end. Best-effort; must never break webhook processing.
 */
public interface SubscriptionCancelledNotifier {

    void onSubscriptionCancelled(Long subscriberId);
}
