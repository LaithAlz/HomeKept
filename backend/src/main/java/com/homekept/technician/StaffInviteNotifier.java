package com.homekept.technician;

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
 * Sends the staff (technician) invite email via {@link EmailSender} (SendGrid), triggered by
 * {@code TechnicianAdminService} on invite and on resend.
 *
 * <p>The link points at the frontend staff-activation page
 * ({@code {frontendBaseUrl}/staff/activate?token=…}), which calls
 * {@code /api/staff/invite/validate} then {@code /api/staff/invite/accept}. The raw token is
 * URL-encoded. Neither the email address nor the token is logged. The email itself carries
 * no password, no role, and no ids.
 */
@Component
public class StaffInviteNotifier {

    private static final Logger log = LoggerFactory.getLogger(StaffInviteNotifier.class);

    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public StaffInviteNotifier(EmailSender emailSender, AppProperties appProperties) {
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    /**
     * Sends the staff invite link to the newly-invited (or re-invited) technician.
     *
     * @param email     recipient email address — not logged
     * @param firstName recipient first name, for the greeting — not logged
     * @param rawToken  the raw HMAC-signed invite token for the link — not logged
     */
    public void sendInvite(String email, String firstName, String rawToken) {
        String inviteUrl = appProperties.frontendBaseUrl()
                + "/staff/activate?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        RenderedEmail rendered = EmailTemplates.staffInvite(firstName, inviteUrl);
        emailSender.send(email, firstName, rendered.subject(), rendered.htmlBody());
        log.info("staff_invite_email_dispatched");
    }
}
