package com.homekept;

import com.homekept.notification.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers a {@code @Primary} recording {@link EmailSender} so notification tests can assert
 * what would have been sent — without the real SendGrid path. Inject the concrete
 * {@link RecordingEmailSender} and call {@link RecordingEmailSender#reset()} in
 * {@code @BeforeEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmailSenderConfig {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    public static class RecordingEmailSender implements EmailSender {

        public record Sent(String toEmail, String toName, String subject, String htmlBody) {}

        public final List<Sent> sent = new ArrayList<>();

        public void reset() {
            sent.clear();
        }

        @Override
        public void send(String toEmail, String toName, String subject, String htmlBody) {
            sent.add(new Sent(toEmail, toName, subject, htmlBody));
        }
    }
}
