package com.homekept.visit;

import com.homekept.config.AppProperties;
import com.homekept.identity.UserQueryService.UserContact;
import com.homekept.notification.EmailSender;
import com.homekept.notification.EmailTemplates;
import com.homekept.notification.RecipientResolver;
import com.homekept.notification.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Sends the "visit complete + report ready" email when a visit is completed.
 *
 * <p>Resolves the recipient via {@link RecipientResolver}; a missing recipient skips the
 * send (the visit is already completed — the email is best-effort). No PII is logged.
 */
@Component
public class DefaultVisitReportNotifier implements VisitReportNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultVisitReportNotifier.class);

    private final RecipientResolver recipientResolver;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public DefaultVisitReportNotifier(RecipientResolver recipientResolver,
                                      EmailSender emailSender,
                                      AppProperties appProperties) {
        this.recipientResolver = recipientResolver;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void sendVisitReport(Visit visit) {
        Optional<UserContact> contact = recipientResolver.forSubscriber(visit.getSubscriberId());
        if (contact.isEmpty()) {
            return;
        }
        String reportUrl = appProperties.frontendBaseUrl() + "/app";
        RenderedEmail rendered = EmailTemplates.visitComplete(contact.get().firstName(), reportUrl);
        emailSender.send(contact.get().email(), contact.get().firstName(),
                rendered.subject(), rendered.htmlBody());
        log.info("visit_report_email_dispatched visitId={} subscriberId={}",
                visit.getId(), visit.getSubscriberId());
    }
}
