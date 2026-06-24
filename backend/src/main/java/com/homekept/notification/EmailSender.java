package com.homekept.notification;

/**
 * Outbound transactional-email seam. The only place that talks to the email provider.
 *
 * <p><strong>Best-effort, never throws.</strong> A send failure must never break or roll
 * back the operation that triggered it (activation, a Stripe webhook, a visit completion).
 * Implementations log failures and return normally.
 *
 * <p>The recipient address is PII — implementations must never log it.
 */
public interface EmailSender {

    /**
     * Sends an HTML email. Returns normally whether or not delivery succeeded.
     *
     * @param toEmail  recipient address (PII — never logged)
     * @param toName   recipient display name (may be null/blank)
     * @param subject  email subject (a static template string — no PII)
     * @param htmlBody the HTML body
     */
    void send(String toEmail, String toName, String subject, String htmlBody);
}
