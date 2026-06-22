package com.homekept.visit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link VisitReportNotifier}.
 *
 * <p>Logs a placeholder line in lieu of a real email send. No secrets, no SendGrid
 * dependency — this is the MVP stand-in. The notification slice supersedes this by
 * registering a {@code @Primary} real implementation.
 *
 * <p>No PII is logged — only visit ID and subscriber ID.
 *
 * <p>copy-guardian: log message below is internal only, not customer-visible.
 */
@Component
public class DefaultVisitReportNotifier implements VisitReportNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultVisitReportNotifier.class);

    @Override
    public void sendVisitReport(Visit visit) {
        // STUB: real SendGrid email is the notification slice (#notification-issue).
        // This log is the only action taken at MVP.
        log.info("would send visit_report visitId={} subscriberId={}",
                visit.getId(), visit.getSubscriberId());
    }
}
