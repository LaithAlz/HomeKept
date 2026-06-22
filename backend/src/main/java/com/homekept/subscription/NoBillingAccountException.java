package com.homekept.subscription;

/**
 * Thrown when a billing-portal session is requested but the subscriber has no Stripe
 * customer id yet (checkout has not been completed).
 *
 * <p>Mapped to HTTP 409 in {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class NoBillingAccountException extends RuntimeException {

    public NoBillingAccountException(String message) {
        super(message);
    }
}
