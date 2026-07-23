package com.homekept.notification;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link SendGridEmailSender}'s graceful-degradation contract — no Spring, no
 * Docker, no network. When SendGrid is unconfigured (or the recipient is missing) the send is
 * skipped and never throws, so a missing key can't break the triggering operation.
 */
class SendGridEmailSenderTest {

    @Test
    void blankApiKey_skipsWithoutThrowing() {
        SendGridEmailSender sender = new SendGridEmailSender(appProps("", "from@homekept.test"));
        assertThatCode(() -> sender.send("a@b.test", "A", "Subject", "<p>hi</p>"))
                .doesNotThrowAnyException();
    }

    @Test
    void blankFromEmail_skipsWithoutThrowing() {
        SendGridEmailSender sender = new SendGridEmailSender(appProps("SG.test-key", ""));
        assertThatCode(() -> sender.send("a@b.test", "A", "Subject", "<p>hi</p>"))
                .doesNotThrowAnyException();
    }

    @Test
    void blankRecipient_skipsWithoutThrowing() {
        SendGridEmailSender sender = new SendGridEmailSender(appProps("SG.test-key", "from@homekept.test"));
        assertThatCode(() -> sender.send("  ", "A", "Subject", "<p>hi</p>"))
                .doesNotThrowAnyException();
    }

    private AppProperties appProps(String apiKey, String fromEmail) {
        return new AppProperties(
                "America/Toronto",
                false,
                true,
                new AppProperties.Cors(List.of()),
                new AppProperties.Jwt("test-signing-key-placeholder-xx", 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""),
                "http://localhost:8080",
                new AppProperties.SendGrid(apiKey, fromEmail, "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com"));
    }
}
