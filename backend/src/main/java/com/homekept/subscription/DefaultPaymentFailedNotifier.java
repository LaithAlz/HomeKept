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
 * Sends the "payment didn't go through" email on {@code invoice.payment_failed}.
 * Best-effort; a missing recipient or send failure never breaks webhook processing.
 */
@Component
public class DefaultPaymentFailedNotifier implements PaymentFailedNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentFailedNotifier.class);

    private final RecipientResolver recipientResolver;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public DefaultPaymentFailedNotifier(RecipientResolver recipientResolver,
                                        EmailSender emailSender,
                                        AppProperties appProperties) {
        this.recipientResolver = recipientResolver;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void onPaymentFailed(Long subscriberId) {
        Optional<UserContact> contact = recipientResolver.forSubscriber(subscriberId);
        if (contact.isEmpty()) {
            return;
        }
        String billingUrl = appProperties.frontendBaseUrl() + "/app/billing";
        RenderedEmail rendered = EmailTemplates.paymentFailed(contact.get().firstName(), billingUrl);
        emailSender.send(contact.get().email(), contact.get().firstName(),
                rendered.subject(), rendered.htmlBody());
        log.info("payment_failed_email_dispatched subscriberId={}", subscriberId);
    }
}
