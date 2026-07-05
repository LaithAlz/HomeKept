package com.homekept.identity;

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
 * Sends the password reset link email via {@link EmailSender} (SendGrid).
 *
 * <p>The link points at the frontend reset page ({@code {frontendBaseUrl}/reset-password?token=…}).
 * The raw token is URL-encoded. Neither the email address, the first name, nor the token is
 * logged.
 */
@Component
public class DefaultPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultPasswordResetNotifier.class);

    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public DefaultPasswordResetNotifier(EmailSender emailSender, AppProperties appProperties) {
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void sendResetLink(String email, String firstName, String rawToken, Long userId) {
        String resetUrl = appProperties.frontendBaseUrl()
                + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        RenderedEmail rendered = EmailTemplates.passwordReset(firstName, resetUrl);
        emailSender.send(email, null, rendered.subject(), rendered.htmlBody());
        // No PII in log — user id only.
        log.info("password_reset_email_dispatched userId={}", userId);
    }
}
