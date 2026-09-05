package com.homekept.subscription.dto;

/**
 * Response for {@code GET /api/app/billing/payment-method} — the default card on file.
 *
 * <p>Deliberately minimal: never a full PAN, a Stripe payment method id, or a fingerprint.
 * {@code brand}/{@code last4}/{@code expMonth}/{@code expYear} are exactly what Stripe's
 * own card-on-file UI shows, nothing more.
 */
public record AppPaymentMethodResponse(
        String brand,
        String last4,
        int expMonth,
        int expYear
) {}
