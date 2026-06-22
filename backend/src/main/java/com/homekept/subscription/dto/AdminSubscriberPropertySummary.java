package com.homekept.subscription.dto;

/**
 * Minimal property summary embedded in {@link AdminSubscriberDetail}.
 *
 * <p>Never includes decrypted access notes — only the {@code hasAccessNotes} boolean flag.
 */
public record AdminSubscriberPropertySummary(
        String streetAddress,
        String city,
        String postalCode,
        String propertyType,
        boolean hasAccessNotes
) {}
