package com.homekept.notification;

/**
 * A rendered transactional email: subject line + HTML body. Produced by
 * {@link EmailTemplates} and handed to {@link EmailSender}.
 */
public record RenderedEmail(String subject, String htmlBody) {}
