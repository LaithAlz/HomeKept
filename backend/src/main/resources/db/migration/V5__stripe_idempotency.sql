-- V5__stripe_idempotency.sql
-- Adds stripe_event_id to subscription_event for idempotent webhook processing.
-- A partial unique index prevents the same Stripe event from being processed twice.
-- The column is nullable so that non-Stripe events (MANUAL, SYSTEM) are unaffected.

ALTER TABLE subscription_event
    ADD COLUMN stripe_event_id VARCHAR(255);

-- Partial unique index: enforces uniqueness only for rows that carry a Stripe event id.
-- NULL values are excluded, so non-Stripe events never conflict with each other.
CREATE UNIQUE INDEX idx_subscription_event_stripe_id
    ON subscription_event (stripe_event_id)
    WHERE stripe_event_id IS NOT NULL;

-- Index stripe_customer_id and stripe_subscription_id on subscriber for webhook lookups.
CREATE INDEX idx_subscriber_stripe_customer_id
    ON subscriber (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

CREATE INDEX idx_subscriber_stripe_subscription_id
    ON subscriber (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;
