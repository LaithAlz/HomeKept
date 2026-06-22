package com.homekept.subscription;

/**
 * Published (in-process) after a subscriber transitions to ACTIVE and the activation
 * transaction has committed.
 *
 * <p>Listeners annotated with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} receive this event only
 * after the activation transaction commits successfully — so a scheduling failure can
 * never roll back the activation, and orphan visits are never created when activation
 * itself rolls back (e.g. concurrent-duplicate webhook).
 *
 * <p>Carries only the subscriber id (no mutable entity reference) so that the listener
 * re-loads a fresh entity in its own transaction rather than operating on a stale
 * detached object.
 *
 * @param subscriberId the HomeKept subscriber id
 */
public record SubscriberActivatedEvent(Long subscriberId) {}
