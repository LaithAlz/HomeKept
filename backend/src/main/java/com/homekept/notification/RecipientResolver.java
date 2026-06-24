package com.homekept.notification;

import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserQueryService.UserContact;
import com.homekept.subscription.SubscriberQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the email recipient (address + first name) for a subscriber, for transactional
 * emails. Bridges subscription → identity via their services (never their repositories).
 *
 * <p>Returns empty (and logs a skip, no PII) when the subscriber or user can't be found, so
 * callers degrade to "don't send" rather than failing the triggering operation.
 */
@Component
public class RecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolver.class);

    private final SubscriberQueryService subscriberQueryService;
    private final UserQueryService userQueryService;

    public RecipientResolver(SubscriberQueryService subscriberQueryService,
                             UserQueryService userQueryService) {
        this.subscriberQueryService = subscriberQueryService;
        this.userQueryService = userQueryService;
    }

    public Optional<UserContact> forSubscriber(Long subscriberId) {
        var subscriber = subscriberQueryService.findById(subscriberId);
        if (subscriber.isEmpty()) {
            log.warn("email_recipient_subscriber_not_found subscriberId={}", subscriberId);
            return Optional.empty();
        }
        Optional<UserContact> contact = userQueryService.findContactById(subscriber.get().getUserId());
        if (contact.isEmpty()) {
            log.warn("email_recipient_user_not_found subscriberId={}", subscriberId);
        }
        return contact;
    }
}
