package com.homekept.subscription;

import org.springframework.stereotype.Component;

/**
 * State machine for {@link Subscriber} status transitions.
 *
 * <p>Every status write in the entire codebase routes through this class.
 * No direct status writes, ever.
 *
 * <p>Legal transitions (per arch doc §4.1):
 * <pre>
 *   PENDING_ACTIVATION → ACTIVE
 *   PENDING_ACTIVATION → CANCELLED   (token expired / never paid)
 *   ACTIVE → PAUSED
 *   ACTIVE → PAYMENT_ISSUE
 *   ACTIVE → CANCELLED
 *   PAUSED → ACTIVE
 *   PAUSED → CANCELLED
 *   PAYMENT_ISSUE → ACTIVE           (payment retry succeeded)
 *   PAYMENT_ISSUE → CANCELLED        (retry exhaustion)
 *   CANCELLED → (none — terminal)
 * </pre>
 */
@Component
public class SubscriberStateMachine {

    /**
     * Returns {@code true} if the transition from {@code from} to {@code to} is legal.
     *
     * @param from the current status
     * @param to   the desired next status
     * @return {@code true} if the transition is permitted
     */
    public boolean canTransition(SubscriberStatus from, SubscriberStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PENDING_ACTIVATION -> to == SubscriberStatus.ACTIVE
                                    || to == SubscriberStatus.CANCELLED;
            case ACTIVE             -> to == SubscriberStatus.PAUSED
                                    || to == SubscriberStatus.PAYMENT_ISSUE
                                    || to == SubscriberStatus.CANCELLED;
            case PAUSED             -> to == SubscriberStatus.ACTIVE
                                    || to == SubscriberStatus.CANCELLED;
            case PAYMENT_ISSUE      -> to == SubscriberStatus.ACTIVE
                                    || to == SubscriberStatus.CANCELLED;
            case CANCELLED          -> false; // terminal — no exit
        };
    }
}
