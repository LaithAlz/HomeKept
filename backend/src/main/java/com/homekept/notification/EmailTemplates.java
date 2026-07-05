package com.homekept.notification;

/**
 * Renders the transactional email bodies (#39). Plain HTML with inline CSS, ≤600px wide —
 * the format that survives Gmail/Outlook.
 *
 * <h2>Brand</h2>
 * <p>Pine ({@code #1E3A2B}) and honey ({@code #DE8F3F}). Per the WCAG rule, text on a honey
 * surface is pine, never white — so the call-to-action button is honey with pine text. The
 * pine header band carries light text (that rule is about honey surfaces specifically).
 *
 * <h2>Copy</h2>
 * <p>Transactional, plain-spoken, no hype, and no fabricated social proof (Competition Act).
 * Each email carries sender identification in the footer.
 *
 * <p>Static helpers only — no state, no secrets, nothing logged here.
 */
public final class EmailTemplates {

    private static final String PINE = "#1E3A2B";
    private static final String HONEY = "#DE8F3F";
    private static final String CREAM = "#FBF7F0";
    private static final String INK = "#2B2B2B";
    private static final String MUTED = "#6B6B6B";

    /** Footer context for the four subscriber-facing templates and the activation invite. */
    private static final String MEMBERSHIP_FOOTER_CONTEXT = "your HomeKept membership";

    private EmailTemplates() {}

    // ── Public templates ────────────────────────────────────────────────────────

    /** Activation magic link (7-day expiry). */
    public static RenderedEmail activationInvite(String firstName, String activationUrl) {
        String body = paragraph("Your HomeKept walk-through is done and your membership is "
                + "ready to activate. Set your password and confirm your plan to get started. "
                + "This link expires in 7 days.");
        return new RenderedEmail(
                "Activate your HomeKept membership",
                layout("You're almost set up", greeting(firstName) + body,
                        "Activate my membership", activationUrl));
    }

    /** Welcome, on PENDING_ACTIVATION → ACTIVE. */
    public static RenderedEmail welcome(String firstName, String dashboardUrl) {
        String body = paragraph("Your membership is active. We'll look after the seasonal "
                + "maintenance your home needs. You'll get a heads-up before each visit and a "
                + "report after. Your schedule and visit history live in your dashboard.");
        return new RenderedEmail(
                "Welcome to HomeKept",
                layout("Welcome to HomeKept", greeting(firstName) + body,
                        "Go to my dashboard", dashboardUrl));
    }

    /** Visit completed, report ready. */
    public static RenderedEmail visitComplete(String firstName, String reportUrl) {
        String body = paragraph("We've completed your recent visit. Your full report is ready in "
                + "your dashboard: what we checked, what we did, and anything worth keeping an eye on.");
        return new RenderedEmail(
                "Your HomeKept visit is complete",
                layout("Your visit is done", greeting(firstName) + body,
                        "View my report", reportUrl));
    }

    /** Invoice payment failed; Stripe will retry. */
    public static RenderedEmail paymentFailed(String firstName, String billingUrl) {
        String body = paragraph("Your latest HomeKept payment didn't go through. This is usually "
                + "an expired or declined card. Please update your payment method to keep your "
                + "membership active, and we'll automatically retry once it's updated.");
        return new RenderedEmail(
                "Action needed: your HomeKept payment didn't go through",
                layout("We couldn't process your payment", greeting(firstName) + body,
                        "Update payment method", billingUrl));
    }

    /** Password reset link (30-minute expiry). */
    public static RenderedEmail passwordReset(String firstName, String resetUrl) {
        String body = paragraph("We received a request to reset your HomeKept password. Use the "
                + "link below to choose a new one. This link expires in 30 minutes. If you did "
                + "not request this, you can ignore this email and nothing will change.");
        return new RenderedEmail(
                "Reset your HomeKept password",
                layout("Reset your password", greeting(firstName) + body,
                        "Reset my password", resetUrl, "your password reset request"));
    }

    /** Subscription cancelled (terminal). */
    public static RenderedEmail subscriptionCancelled(String firstName, String plansUrl) {
        String body = paragraph("Your HomeKept membership has been cancelled. We're sorry to "
                + "see you go. If this was a mistake or you'd like to come back, you can "
                + "resubscribe any time.");
        return new RenderedEmail(
                "Your HomeKept membership has been cancelled",
                layout("Your membership is cancelled", greeting(firstName) + body,
                        "Resubscribe", plansUrl));
    }

    /**
     * Walk-through booking received (#11). No CTA — there's nothing yet for the recipient to
     * click, just the expectation the booking form already set on-screen.
     *
     * @param firstName             recipient's first name (nullable — falls back to "there")
     * @param preferredWeekLabel    human-readable preferred week, e.g. "July 6, 2026"
     * @param timeOfDayLabel        human-readable time-of-day preference, e.g. "afternoon"
     * @param dayPreferencesLabel   human-readable day preferences, e.g. "Wednesday and Thursday"
     *                              (nullable/blank when none were given)
     * @param addressLabel          human-readable property address, e.g. "14 Maple Ridge Crt, Mississauga"
     */
    public static RenderedEmail bookingConfirmation(String firstName, String preferredWeekLabel,
            String timeOfDayLabel, String dayPreferencesLabel, String addressLabel) {
        String daySuffix = (dayPreferencesLabel == null || dayPreferencesLabel.isBlank())
                ? ""
                : ", on " + escape(dayPreferencesLabel);
        String details = "Here's what you told us: the week of " + escape(preferredWeekLabel)
                + ", in the " + escape(timeOfDayLabel) + daySuffix
                + ". We'll see you at " + escape(addressLabel) + ".";
        String body = paragraph("Thanks for booking a walk-through with HomeKept. We've received "
                + "your request and will confirm your time within one business day.")
                + paragraph(details);
        return new RenderedEmail(
                "Your HomeKept walk-through request",
                layout("We've got your request", greeting(firstName) + body, null, null,
                        "your walk-through request"));
    }

    // ── Layout ──────────────────────────────────────────────────────────────────

    private static String greeting(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        return paragraph("Hi " + name + ",");
    }

    private static String paragraph(String text) {
        return "<p style=\"margin:0 0 16px 0;font-size:16px;line-height:1.5;color:" + INK + ";\">"
                + text + "</p>";
    }

    /**
     * Wraps content in the shared 600px brand layout with no call-to-action button — used when
     * there's nothing yet for the recipient to click (e.g. a plain confirmation). Uses the
     * default "your HomeKept membership" footer context.
     */
    private static String layout(String heading, String bodyHtml) {
        return layout(heading, bodyHtml, null, null, MEMBERSHIP_FOOTER_CONTEXT);
    }

    /**
     * Wraps content in the shared 600px brand layout: pine header band, cream card with pine
     * heading + body, an optional honey CTA button with PINE text (WCAG), and a
     * sender-identification footer. Pass a null/blank {@code ctaUrl} to omit the button. Uses
     * the default "your HomeKept membership" footer context.
     */
    private static String layout(String heading, String bodyHtml, String ctaLabel, String ctaUrl) {
        return layout(heading, bodyHtml, ctaLabel, ctaUrl, MEMBERSHIP_FOOTER_CONTEXT);
    }

    /**
     * Wraps content in the shared 600px brand layout: pine header band, cream card with pine
     * heading + body, an optional honey CTA button with PINE text (WCAG), and a
     * sender-identification footer. Pass a null/blank {@code ctaUrl} to omit the button.
     *
     * <p>{@code footerContext} is the "this is a transactional message about ___" phrase — most
     * templates are about "your HomeKept membership", but pre-membership emails (e.g. the
     * booking confirmation) need their own wording since the recipient isn't a member yet.
     */
    private static String layout(String heading, String bodyHtml, String ctaLabel, String ctaUrl,
            String footerContext) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;">
                    <tr><td align="center" style="padding:24px 12px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;">
                        <tr><td style="background:%s;border-radius:8px 8px 0 0;padding:20px 28px;">
                          <span style="font-size:20px;font-weight:700;letter-spacing:0.5px;color:%s;">HomeKept</span>
                        </td></tr>
                        <tr><td style="background:#ffffff;padding:28px;border:1px solid #ECE6DC;border-top:none;">
                          <h1 style="margin:0 0 16px 0;font-size:22px;line-height:1.3;color:%s;">%s</h1>
                          %s
                          %s
                        </td></tr>
                        <tr><td style="background:#ffffff;padding:0 28px 24px 28px;border:1px solid #ECE6DC;border-top:none;border-radius:0 0 8px 8px;">
                          <p style="margin:16px 0 0 0;font-size:12px;line-height:1.5;color:%s;">
                            HomeKept. Subscription home maintenance in the GTA West.<br>
                            This is a transactional message about %s.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                CREAM, CREAM,
                PINE, CREAM,
                PINE, escape(heading),
                bodyHtml,
                ctaBlock(ctaLabel, ctaUrl),
                MUTED, escape(footerContext));
    }

    /**
     * Renders the honey CTA button (PINE text, WCAG) or an empty string when the email has no
     * call to action.
     */
    private static String ctaBlock(String ctaLabel, String ctaUrl) {
        if (ctaUrl == null || ctaUrl.isBlank()) {
            return "";
        }
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:8px 0 4px 0;">
                  <tr><td style="background:%s;border-radius:6px;">
                    <a href="%s" style="display:inline-block;padding:13px 22px;font-size:16px;font-weight:600;color:%s;text-decoration:none;">%s</a>
                  </td></tr>
                </table>
                """.formatted(HONEY, escape(ctaUrl), PINE, escape(ctaLabel));
    }

    /** Minimal HTML escaping for interpolated values (names, URLs). */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
