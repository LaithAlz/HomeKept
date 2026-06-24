package com.homekept.visit;

/**
 * Lifecycle of a customer {@link RescheduleRequest}.
 *
 * <p>PENDING on creation; an admin moves it to CONFIRMED (the visit was rescheduled to
 * one of the proposed times) or DECLINED (could not be accommodated). Both are terminal.
 */
public enum RescheduleRequestStatus {
    PENDING,
    CONFIRMED,
    DECLINED
}
