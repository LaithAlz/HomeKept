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
 * Sends the cancellation-confirmation email on {@code customer.subscription.deleted}.
 * Best-effort; a missing recipient or send failure never breaks webhook processing.
 */
@Component
public class DefaultSubscriptionCancelledNotifier implements SubscriptionCancelledNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionCancelledNotifier.class);

    private final RecipientResolver recipientResolver;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public DefaultSubscriptionCancelledNotifier(RecipientResolver recipientResolver,
                                                EmailSender emailSender,
                                                AppProperties appProperties) {
        this.recipientResolver = recipientResolver;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void onSubscriptionCancelled(Long subscriberId) {
        Optional<UserContact> contact = recipientResolver.forSubscriber(subscriberId);
        if (contact.isEmpty()) {
            return;
        }
        String plansUrl = appProperties.frontendBaseUrl() + "/plans";
        RenderedEmail rendered = EmailTemplates.subscriptionCancelled(contact.get().firstName(), plansUrl);
        emailSender.send(contact.get().email(), contact.get().firstName(),
                rendered.subject(), rendered.htmlBody());
        log.info("subscription_cancelled_email_dispatched subscriberId={}", subscriberId);
    }
}
