package com.homekept.notification;

import com.homekept.config.AppProperties;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SendGrid implementation of {@link EmailSender}.
 *
 * <h2>Graceful degradation</h2>
 * <p>If {@code app.sendgrid.api-key} or {@code app.sendgrid.from-email} is blank the send is
 * skipped with a warning and the app keeps working — dev/test/CI run without a real key
 * (same pattern as Stripe and R2). The API key is NEVER logged.
 *
 * <h2>Never throws</h2>
 * <p>All exceptions (and ≥400 responses) are caught and logged. Callers fire emails from
 * inside transactions (activation, webhooks, visit completion); a delivery failure must
 * never propagate and roll those back.
 *
 * <h2>Stage note</h2>
 * <p>Sends synchronously inline. {@code @Async} email dispatch is a Stage-2 optimization.
 */
@Service
public class SendGridEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);

    private final AppProperties.SendGrid config;

    public SendGridEmailSender(AppProperties appProperties) {
        this.config = appProperties.sendGrid();
    }

    @Override
    public void send(String toEmail, String toName, String subject, String htmlBody) {
        if (config.apiKey().isBlank() || config.fromEmail().isBlank()) {
            log.warn("SendGrid not configured (blank api-key or from-email) — skipping email "
                    + "subject='{}'. Set SENDGRID_API_KEY and SENDGRID_FROM_EMAIL in production.", subject);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Email recipient missing — skipping send subject='{}'", subject);
            return;
        }

        try {
            Email from = new Email(config.fromEmail(), config.fromName());
            Email to = new Email(toEmail, toName);
            Content content = new Content("text/html", htmlBody);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sendGrid = new SendGrid(config.apiKey());
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                // Do NOT log the response body — it can echo the recipient address (PII).
                log.error("email_send_failed subject='{}' status={}", subject, response.getStatusCode());
            } else {
                log.info("email_sent subject='{}' status={}", subject, response.getStatusCode());
            }
        } catch (Exception e) {
            // NEVER rethrow — a delivery failure must not roll back the triggering operation.
            log.error("email_send_error subject='{}'", subject, e);
        }
    }
}
