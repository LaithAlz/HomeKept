package com.homekept.subscription;

/**
 * Thrown when a founding-rate checkout is requested but either:
 * <ul>
 *   <li>All 15 founding-member slots are taken, or</li>
 *   <li>The requested plan does not offer a founding rate.</li>
 * </ul>
 *
 * <p>Mapped to HTTP 409 in {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class FoundingRateExhaustedException extends RuntimeException {

    public FoundingRateExhaustedException(String message) {
        super(message);
    }
}
