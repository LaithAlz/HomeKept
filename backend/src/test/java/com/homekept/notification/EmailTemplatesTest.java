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
                EmailTemplates.subscriptionCancelled("A", "https://x/plans"));

        for (RenderedEmail e : all) {
            assertThat(e.subject()).isNotBlank();
            assertThat(e.htmlBody())
                    .startsWith("<!DOCTYPE html>")
                    .contains("HomeKept")
                    .contains("GTA West");   // sender identification in the footer
        }
    }
}
