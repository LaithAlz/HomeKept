package com.homekept.subscription;

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
 * Sends the welcome email when a subscriber activates (PENDING_ACTIVATION → ACTIVE).
 *
 * <p>Called by {@link StripeWebhookService} when a {@code checkout.session.completed}
 * event (mode=subscription) successfully activates a subscriber.
 *
 * <p>Resolves the recipient via {@link RecipientResolver}; if it can't be resolved the send
 * is skipped (the activation has already succeeded — a missing email must not undo it).
 * No PII is logged.
 */
@Component
public class SubscriptionStartedNotifier {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionStartedNotifier.class);

    private final RecipientResolver recipientResolver;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public SubscriptionStartedNotifier(RecipientResolver recipientResolver,
                                              EmailSender emailSender,
                                              AppProperties appProperties) {
        this.recipientResolver = recipientResolver;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    /**
     * Notifies the subscriber that their subscription has been activated.
     *
     * @param subscriberId the HomeKept subscriber id — safe to log (not PII)
     * @param planCode     the plan code string (COMPLETE / PREMIER)
     */
    public void onSubscriptionStarted(Long subscriberId, String planCode) {
        Optional<UserContact> contact = recipientResolver.forSubscriber(subscriberId);
        if (contact.isEmpty()) {
            return;
        }
        String dashboardUrl = appProperties.frontendBaseUrl() + "/app";
        RenderedEmail rendered = EmailTemplates.welcome(contact.get().firstName(), dashboardUrl);
        emailSender.send(contact.get().email(), contact.get().firstName(),
                rendered.subject(), rendered.htmlBody());
        log.info("welcome_email_dispatched subscriberId={} planCode={}", subscriberId, planCode);
    }
}
