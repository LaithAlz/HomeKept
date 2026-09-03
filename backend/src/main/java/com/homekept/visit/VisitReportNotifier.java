package com.homekept.visit;

/**
 * Notification seam for visit completion (the "visit report" email).
 *
 * <p>{@link DefaultVisitReportNotifier} sends the real email via {@code EmailSender}
 * (SendGrid).
 */
public interface VisitReportNotifier {

    /**
     * Called after a visit transitions to COMPLETED.
     *
     * <p>No PII in log calls — visit ID and subscriber ID only.
     *
     * @param visit the completed visit
     */
    void sendVisitReport(Visit visit);
}
