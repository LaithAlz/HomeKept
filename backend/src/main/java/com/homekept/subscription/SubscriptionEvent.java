package com.homekept.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Append-only log of subscription lifecycle events.
 *
 * <p>{@code payload} is JSONB — one of the two allowed JSONB columns in the schema
 * (arch doc §3). It holds raw event data from Stripe webhooks or system events.
 *
 * <p>Idempotency for Stripe webhooks: {@code stripeEventId} stores the Stripe event id.
 * A partial unique index ({@code idx_subscription_event_stripe_id}) on this column
 * (added in V5) prevents the same Stripe event from producing two rows — the database
 * is the final backstop even if the application-layer short-circuit is bypassed by a
 * race condition.
 */
@Entity
@Table(name = "subscription_event")
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSONB payload — raw event data. Null is allowed for system events with no payload. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionEventSource source;

    /**
     * Stripe event id (e.g. {@code evt_1Abc...}). Stored for idempotency: the partial
     * unique index ensures no two rows share the same Stripe event id. Null for
     * non-Stripe events (MANUAL, SYSTEM).
     */
    @Column(name = "stripe_event_id", length = 255)
    private String stripeEventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubscriptionEvent() {}

    public SubscriptionEvent(Long subscriberId, String eventType, String payload,
                             SubscriptionEventSource source) {
        this.subscriberId = subscriberId;
        this.eventType = eventType;
        this.payload = payload;
        this.source = source;
    }

    public SubscriptionEvent(Long subscriberId, String eventType, String payload,
                             SubscriptionEventSource source, String stripeEventId) {
        this.subscriberId = subscriberId;
        this.eventType = eventType;
        this.payload = payload;
        this.source = source;
        this.stripeEventId = stripeEventId;
    }

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public SubscriptionEventSource getSource() { return source; }
    public String getStripeEventId() { return stripeEventId; }
    public void setStripeEventId(String stripeEventId) { this.stripeEventId = stripeEventId; }
    public Instant getCreatedAt() { return createdAt; }
}
