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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A HomeKept subscriber — the most important domain entity.
 *
 * <p>Status transitions are enforced by {@link SubscriberStateMachine}. No code may
 * write {@code status} directly without first verifying the transition is legal.
 *
 * <p>Cross-domain FK columns ({@code userId}, {@code propertyId}, {@code planTierId})
 * are stored as bare {@code BIGINT} columns with real DB constraints. The service layer
 * crosses domain boundaries via service interfaces, never by joining entities.
 *
 * <p>{@code planTierId} is nullable at creation: it is set by the Stripe
 * {@code checkout.session.completed} webhook when the subscription is activated.
 * {@code billingCycle} defaults to MONTHLY until checkout sets the actual choice.
 *
 * <p>{@code foundingRate} is capped at 15 globally (counted by
 * {@link com.homekept.subscription.FoundingRateAvailabilityImpl}).
 * {@code foundingRateExpiresAt} is set 12 months from activation.
 */
@Entity
@Table(name = "subscriber")
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → users.id (identity domain) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** FK → property.id (property domain) */
    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /**
     * FK → plan_tier.id (catalog domain). Nullable at creation — set by Stripe webhook.
     */
    @Column(name = "plan_tier_id")
    private Long planTierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriberStatus status;

    @Column(name = "founding_rate", nullable = false)
    private boolean foundingRate = false;

    @Column(name = "founding_rate_expires_at")
    private Instant foundingRateExpiresAt;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 10)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_until")
    private Instant pausedUntil;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscriber() {}

    public Subscriber(Long userId, Long propertyId, SubscriberStatus status, BillingCycle billingCycle) {
        this.userId = userId;
        this.propertyId = propertyId;
        this.status = status;
        this.billingCycle = billingCycle;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPropertyId() { return propertyId; }
    public Long getPlanTierId() { return planTierId; }
    public void setPlanTierId(Long planTierId) { this.planTierId = planTierId; }

    public SubscriberStatus getStatus() { return status; }

    /**
     * Sets the subscriber status. Callers MUST verify the transition with
     * {@link SubscriberStateMachine#canTransition} before calling this setter.
     */
    public void setStatus(SubscriberStatus status) { this.status = status; }

    public boolean isFoundingRate() { return foundingRate; }
    public void setFoundingRate(boolean foundingRate) { this.foundingRate = foundingRate; }
    public Instant getFoundingRateExpiresAt() { return foundingRateExpiresAt; }
    public void setFoundingRateExpiresAt(Instant foundingRateExpiresAt) { this.foundingRateExpiresAt = foundingRateExpiresAt; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public BillingCycle getBillingCycle() { return billingCycle; }
    public void setBillingCycle(BillingCycle billingCycle) { this.billingCycle = billingCycle; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getPausedAt() { return pausedAt; }
    public void setPausedAt(Instant pausedAt) { this.pausedAt = pausedAt; }
    public Instant getPausedUntil() { return pausedUntil; }
    public void setPausedUntil(Instant pausedUntil) { this.pausedUntil = pausedUntil; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
