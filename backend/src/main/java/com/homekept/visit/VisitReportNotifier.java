package com.homekept.visit;

/**
 * Notification seam for visit completion (the "visit report" email).
 *
 * <p>The MVP default implementation ({@link DefaultVisitReportNotifier}) logs a
 * placeholder. The notification slice will later register a {@code @Primary} real
 * implementation backed by SendGrid — no code change required here.
 *
 * <p>Pattern: plain {@code @Component} default + later {@code @Primary} real impl.
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
