package com.homekept.notification;

/**
 * The kind of reminder recorded in {@code notification_log} (#89).
 *
 * <p>Values must exactly match the V10 migration's CHECK constraint on the
 * {@code notification_type} column. Do not rename or add a value here without a
 * corresponding migration (founder, hand-write) — {@code ddl-auto: validate} does not
 * enforce CHECK constraints, but an unmapped value would fail the insert at the database.
 */
public enum NotificationType {
    WALKTHROUGH_REMINDER_24H,
    VISIT_REMINDER_24H
}
