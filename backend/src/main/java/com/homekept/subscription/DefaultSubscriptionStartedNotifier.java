package com.homekept.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link SubscriptionStartedNotifier}.
 *
 * <p>Logs the would-be welcome notification without sending email.
 * The notification slice (SendGrid) replaces this with the real implementation.
 *
 * <p>No PII in log properties — only the subscriber id (internal ID) and plan code
 * (an enum) per arch doc §5.2.
 */
@Component
public class DefaultSubscriptionStartedNotifier implements SubscriptionStartedNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionStartedNotifier.class);

    @Override
    public void onSubscriptionStarted(Long subscriberId, String planCode) {
        // STUB: notification slice replaces this with a SendGrid welcome email.
        log.info("STUB would send subscription-started notification subscriberId={} planCode={}",
                subscriberId, planCode);
    }
}
