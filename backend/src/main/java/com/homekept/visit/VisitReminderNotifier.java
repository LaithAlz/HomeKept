package com.homekept.visit;

import java.time.Instant;

/**
 * Notification seam for the 24h-before visit reminder (#89), triggered by
 * {@code com.homekept.notification.ReminderScheduler} for SCHEDULED visits.
 *
 * <p>Pattern: plain {@code @Component} default + later {@code @Primary} real impl — see
 * {@link VisitReportNotifier}.
 */
public interface VisitReminderNotifier {

    /**
     * Called for a SCHEDULED visit whose {@code scheduledFor} is within the reminder window.
     * Implementations resolve the recipient from {@code subscriberId} and skip silently (no
     * exception) if no recipient can be found — this is a best-effort send.
     *
     * <p>No PII in log calls — visit ID and subscriber ID only.
     *
     * @param visitId      the visit id
     * @param subscriberId the owning subscriber id, used to resolve the recipient
     * @param scheduledFor the visit's scheduled time (UTC; rendered in the display zone)
     */
    void sendVisitReminder(Long visitId, Long subscriberId, Instant scheduledFor);
}
