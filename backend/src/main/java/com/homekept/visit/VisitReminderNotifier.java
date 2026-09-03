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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Sends the "visit is coming up" 24h-before reminder email (#89), triggered by
 * {@code com.homekept.notification.ReminderScheduler} for SCHEDULED visits.
 *
 * <p>Resolves the recipient via {@link RecipientResolver}; a missing recipient skips the
 * send (best-effort, mirrors {@link VisitReportNotifier}). No PII is logged.
 */
@Component
public class VisitReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(VisitReminderNotifier.class);

    /** Renders a reminder's scheduled time, e.g. "Tuesday, July 14 at 2:00 PM". */
    private static final DateTimeFormatter WHEN_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.ENGLISH);

    private final RecipientResolver recipientResolver;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final ZoneId renderZoneId;

    public VisitReminderNotifier(RecipientResolver recipientResolver,
                                        EmailSender emailSender,
                                        AppProperties appProperties,
                                        ZoneId renderZoneId) {
        this.recipientResolver = recipientResolver;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
        this.renderZoneId = renderZoneId;
    }

    /**
     * Called for a SCHEDULED visit whose {@code scheduledFor} is within the reminder window.
     * Skips silently (no exception) if no recipient can be found — this is a best-effort send.
     *
     * <p>No PII in log calls — visit ID and subscriber ID only.
     *
     * @param visitId      the visit id
     * @param subscriberId the owning subscriber id, used to resolve the recipient
     * @param scheduledFor the visit's scheduled time (UTC; rendered in the display zone)
     */
    public void sendVisitReminder(Long visitId, Long subscriberId, Instant scheduledFor) {
        Optional<UserContact> contact = recipientResolver.forSubscriber(subscriberId);
        if (contact.isEmpty()) {
            return;
        }
        String whenLabel = WHEN_FORMAT.format(scheduledFor.atZone(renderZoneId));
        String dashboardUrl = appProperties.frontendBaseUrl() + "/app";
        RenderedEmail rendered = EmailTemplates.visitReminder(contact.get().firstName(), whenLabel, dashboardUrl);
        emailSender.send(contact.get().email(), contact.get().firstName(),
                rendered.subject(), rendered.htmlBody());
        log.info("visit_reminder_email_dispatched visitId={} subscriberId={}", visitId, subscriberId);
    }
}
