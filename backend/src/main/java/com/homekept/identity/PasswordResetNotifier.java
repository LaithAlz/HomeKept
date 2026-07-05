package com.homekept.identity;

/**
 * Seam for sending password reset link emails.
 *
 * <p>The real implementation ({@link DefaultPasswordResetNotifier}) sends via the
 * notification domain's {@code EmailSender} (SendGrid), which no-ops gracefully when
 * SendGrid is unconfigured.
 */
public interface PasswordResetNotifier {

    /**
     * Sends the password reset link to the account owner.
     *
     * @param email     recipient email address — not logged
     * @param firstName recipient's first name — not logged (used only for the email greeting)
     * @param rawToken  the raw HMAC-signed reset token for the link — not logged
     * @param userId    the user id — safe to log (not PII per arch doc §5.2)
     */
    void sendResetLink(String email, String firstName, String rawToken, Long userId);
}
