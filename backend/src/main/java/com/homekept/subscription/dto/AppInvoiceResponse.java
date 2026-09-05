package com.homekept.subscription.dto;

import java.time.Instant;

/**
 * One row of {@code GET /api/app/billing/invoices} — a thin projection of a Stripe
 * {@code Invoice}, newest first. {@code amountPaidCents} is integer cents (never a float).
 */
public record AppInvoiceResponse(
        String id,
        String number,
        Instant createdAt,
        int amountPaidCents,
        String currency,
        String status,
        String hostedInvoiceUrl,
        String invoicePdf
) {}
