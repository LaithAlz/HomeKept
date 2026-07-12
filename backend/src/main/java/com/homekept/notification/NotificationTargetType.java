package com.homekept.notification;

/**
 * The kind of entity a {@link NotificationLog} row's {@code targetId} points at (#89).
 *
 * <p>Values must exactly match the V10 migration's CHECK constraint on the
 * {@code target_type} column. {@code target_id} is a plain BIGINT with no FK — see the V10
 * migration comment for why (the log points at different parent tables depending on this
 * enum value, which a single FK cannot express).
 */
public enum NotificationTargetType {
    WALKTHROUGH_BOOKING,
    VISIT
}
