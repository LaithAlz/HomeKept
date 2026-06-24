package com.homekept.subscription;

/**
 * Notifies a subscriber that an invoice payment failed (Stripe will retry). Fired from
 * {@code invoice.payment_failed}. The real implementation sends a "update your payment
 * method" email; it is best-effort and must never break webhook processing.
 */
public interface PaymentFailedNotifier {

    void onPaymentFailed(Long subscriberId);
}
