package com.homekept.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmailTemplates} — no Spring, no Docker. Covers subject lines, link
 * interpolation, the null-name greeting fallback, HTML escaping, and the brand/WCAG rule
 * (honey button surface carries pine text).
 */
class EmailTemplatesTest {

    private static final String HONEY = "#DE8F3F";
    private static final String PINE = "#1E3A2B";

    @Test
    void activationInvite_hasSubjectLinkAndFallbackGreeting() {
        RenderedEmail e = EmailTemplates.activationInvite(null, "https://app.test/activate?token=abc123");

        assertThat(e.subject()).isEqualTo("Activate your HomeKept membership");
        assertThat(e.htmlBody())
                .contains("https://app.test/activate?token=abc123")
                .contains("Hi there,")               // null first name → neutral greeting
                .contains("Activate my membership");
    }

    @Test
    void welcome_greetsByFirstName() {
        RenderedEmail e = EmailTemplates.welcome("Alice", "https://app.test/app");

        assertThat(e.subject()).isEqualTo("Welcome to HomeKept");
        assertThat(e.htmlBody()).contains("Hi Alice,").contains("https://app.test/app");
    }

    @Test
    void ctaButton_putsPineTextOnHoneySurface() {
        RenderedEmail e = EmailTemplates.paymentFailed("Bob", "https://app.test/app/billing");

        // WCAG rule: text on honey is pine, never white.
        assertThat(e.htmlBody())
                .contains("background:" + HONEY)   // honey button cell
                .contains("color:" + PINE);        // pine button text
    }

    @Test
    void escapesHtmlInInterpolatedName() {
        RenderedEmail e = EmailTemplates.welcome("<script>alert(1)</script>", "https://app.test/app");

        assertThat(e.htmlBody()).doesNotContain("<script>");
        assertThat(e.htmlBody()).contains("&lt;script&gt;");
    }

    @Test
    void allTemplates_haveSubjectAndWellFormedBody() {
        List<RenderedEmail> all = List.of(
                EmailTemplates.activationInvite("A", "https://x/activate?token=t"),
                EmailTemplates.welcome("A", "https://x/app"),
                EmailTemplates.visitComplete("A", "https://x/app"),
                EmailTemplates.paymentFailed("A", "https://x/app/billing"),
                EmailTemplates.subscriptionCancelled("A", "https://x/plans"),
                EmailTemplates.bookingConfirmation("A", "July 6, 2026", "afternoon", "Wednesday and Thursday",
                        "14 Maple Ridge Crt, Mississauga"),
                EmailTemplates.passwordReset("A", "https://x/reset-password?token=t"));

        for (RenderedEmail e : all) {
            assertThat(e.subject()).isNotBlank();
            assertThat(e.htmlBody())
                    .startsWith("<!DOCTYPE html>")
                    .contains("HomeKept")
                    .contains("GTA West");   // sender identification in the footer
        }
    }

    @Test
    void bookingConfirmation_containsSubmittedDetailsAndNoCta() {
        RenderedEmail e = EmailTemplates.bookingConfirmation(
                "Priya", "July 6, 2026", "afternoon", "Wednesday and Thursday",
                "14 Maple Ridge Crt, Mississauga");

        assertThat(e.subject()).isEqualTo("Your HomeKept walk-through request");
        assertThat(e.htmlBody())
                .contains("Hi Priya,")
                .contains("within one business day")
                .contains("July 6, 2026")
                .contains("afternoon")
                .contains("Wednesday and Thursday")
                .contains("14 Maple Ridge Crt, Mississauga");

        // No CTA button — nothing yet for the recipient to click.
        assertThat(e.htmlBody()).doesNotContain("<a href=");
    }

    @Test
    void bookingConfirmation_omitsDayPreferencesWhenBlank() {
        RenderedEmail e = EmailTemplates.bookingConfirmation(
                "Priya", "July 6, 2026", "afternoon", null, "14 Maple Ridge Crt, Mississauga");

        assertThat(e.htmlBody()).doesNotContain(", on ");
    }

    @Test
    void bookingConfirmation_nullFirstName_fallsBackToNeutralGreeting() {
        RenderedEmail e = EmailTemplates.bookingConfirmation(
                null, "July 6, 2026", "afternoon", null, "14 Maple Ridge Crt, Mississauga");

        assertThat(e.htmlBody()).contains("Hi there,");
    }

    @Test
    void bookingConfirmation_footerReferencesTheRequestNotMembership() {
        RenderedEmail e = EmailTemplates.bookingConfirmation(
                "Priya", "July 6, 2026", "afternoon", "Wednesday and Thursday",
                "14 Maple Ridge Crt, Mississauga");

        assertThat(e.htmlBody())
                .contains("This is a transactional message about your walk-through request.")
                .doesNotContain("This is a transactional message about your HomeKept membership.");
    }

    @Test
    void welcome_footerStillReferencesMembership() {
        RenderedEmail e = EmailTemplates.welcome("Alice", "https://app.test/app");

        assertThat(e.htmlBody())
                .contains("This is a transactional message about your HomeKept membership.");
    }

    @Test
    void passwordReset_hasSubjectLinkExpiryNoticeAndFallbackGreeting() {
        RenderedEmail e = EmailTemplates.passwordReset(null, "https://app.test/reset-password?token=abc123");

        assertThat(e.subject()).isEqualTo("Reset your HomeKept password");
        assertThat(e.htmlBody())
                .contains("https://app.test/reset-password?token=abc123")
                .contains("Hi there,")               // null first name → neutral greeting
                .contains("Reset my password")
                .contains("expires in 30 minutes");
    }

    @Test
    void passwordReset_greetsByFirstNameAndHasNoEmDashOrExclamationMark() {
        RenderedEmail e = EmailTemplates.passwordReset("Priya", "https://app.test/reset-password?token=abc123");

        assertThat(e.htmlBody()).contains("Hi Priya,");
        // Calm, no-hype copy rule: no em dashes anywhere, no exclamation marks in the copy.
        // (htmlBody always contains "!" via the "<!DOCTYPE html>" preamble, so the exclamation
        // check runs against the subject and the message paragraph, not the whole document.)
        assertThat(e.htmlBody()).doesNotContain("—");
        assertThat(e.subject()).doesNotContain("—").doesNotContain("!");
        // Strip HTML tags (including the "<!DOCTYPE html>" preamble) and assert the visible copy has no "!".
        assertThat(e.htmlBody().replaceAll("<[^>]*>", " ")).doesNotContain("!");
    }

    @Test
    void passwordReset_footerReferencesTheRequestNotMembership() {
        RenderedEmail e = EmailTemplates.passwordReset("Priya", "https://app.test/reset-password?token=abc123");

        assertThat(e.htmlBody())
                .contains("This is a transactional message about your password reset request.")
                .doesNotContain("This is a transactional message about your HomeKept membership.");
    }
}
