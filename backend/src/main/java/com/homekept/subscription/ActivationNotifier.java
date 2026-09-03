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
 * Sends the activation magic-link email via {@link EmailSender} (SendGrid), triggered by the
 * admin invite endpoint after the walk-through is completed.
 *
 * <p>The link points at the frontend activation page ({@code {frontendBaseUrl}/activate?token=…}),
 * which calls {@code /api/activation/validate} then {@code /api/activation/complete}. The raw
 * token is URL-encoded. Neither the email address nor the token is logged.
 */
@Component
public class ActivationNotifier {

    private static final Logger log = LoggerFactory.getLogger(ActivationNotifier.class);

    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public ActivationNotifier(EmailSender emailSender, AppProperties appProperties) {
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    /**
     * Sends the activation magic link to the prospective subscriber.
     *
     * @param email     recipient email address — not logged in properties
     * @param rawToken  the raw HMAC-signed activation token for the magic link
     * @param bookingId the booking id — safe to log (not PII per arch doc §5.2)
     */
    public void sendActivationLink(String email, String rawToken, Long bookingId) {
        String activationUrl = appProperties.frontendBaseUrl()
                + "/activate?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        RenderedEmail rendered = EmailTemplates.activationInvite(null, activationUrl);
        emailSender.send(email, null, rendered.subject(), rendered.htmlBody());
        // No PII in log — booking id only.
        log.info("activation_email_dispatched bookingId={}", bookingId);
    }
}
