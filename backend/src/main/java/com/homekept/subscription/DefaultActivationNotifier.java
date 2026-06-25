package com.homekept.subscription;

import com.homekept.config.AppProperties;
import com.homekept.notification.EmailSender;
import com.homekept.notification.EmailTemplates;
import com.homekept.notification.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends the activation magic-link email via {@link EmailSender} (SendGrid).
 *
 * <p>The link points at the frontend activation page ({@code {frontendBaseUrl}/activate?token=…}),
 * which calls {@code /api/activation/validate} then {@code /api/activation/complete}. The raw
 * token is URL-encoded. Neither the email address nor the token is logged.
 */
@Component
public class DefaultActivationNotifier implements ActivationNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultActivationNotifier.class);

    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public DefaultActivationNotifier(EmailSender emailSender, AppProperties appProperties) {
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void sendActivationLink(String email, String rawToken, Long bookingId) {
        String activationUrl = appProperties.frontendBaseUrl()
                + "/activate?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        RenderedEmail rendered = EmailTemplates.activationInvite(null, activationUrl);
        emailSender.send(email, null, rendered.subject(), rendered.htmlBody());
        // No PII in log — booking id only.
        log.info("activation_email_dispatched bookingId={}", bookingId);
    }
}
