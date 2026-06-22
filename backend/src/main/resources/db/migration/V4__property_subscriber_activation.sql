-- V4__property_subscriber_activation.sql
-- Property, subscriber, subscription_event, and activation_token domains.
-- Resolves the circular FK between property.subscriber_id and subscriber.property_id
-- by making property.subscriber_id DEFERRABLE INITIALLY DEFERRED, allowing the
-- activation transaction to insert both rows before the FK is checked at commit time.
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums + CHECK, all FKs indexed.

-- ─────────────────────────────────────────────────────────────────────────────
-- property
-- access_notes stored as BYTEA (AES-256-GCM IV||ciphertext||tag, app-layer encryption).
-- subscriber_id FK is DEFERRABLE INITIALLY DEFERRED to break the circular FK cycle:
--   property.subscriber_id → subscriber.id
--   subscriber.property_id → property.id
-- Both rows are inserted in one transaction; the FK check fires at COMMIT.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE property (
    id                          BIGSERIAL    PRIMARY KEY,
    -- FK added below as a named deferrable constraint
    subscriber_id               BIGINT,
    street_address              VARCHAR(255) NOT NULL,
    unit                        VARCHAR(50),
    city                        VARCHAR(100) NOT NULL,
    postal_code                 VARCHAR(20)  NOT NULL,
    latitude                    DOUBLE PRECISION,
    longitude                   DOUBLE PRECISION,
    fsa                         VARCHAR(3)   NOT NULL,
    year_built                  INTEGER,
    square_footage_range        VARCHAR(20)  CHECK (square_footage_range IN ('<1500', '1500-2500', '2500-4000', '>4000')),
    property_type               VARCHAR(20)  NOT NULL CHECK (property_type IN ('DETACHED', 'SEMI', 'TOWNHOUSE')),
    -- Encrypted at rest: IV (12 bytes) || ciphertext || GCM auth tag (16 bytes)
    -- Decrypted only server-side; NULL when no notes have been set.
    access_notes                BYTEA,
    -- SKU sheet fields for technician prep
    hvac_filter_sizes           TEXT,
    smoke_co_detector_models    TEXT,
    humidifier_model            TEXT,
    water_heater_age_years      INTEGER,
    water_heater_flush_eligible BOOLEAN,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- subscriber
-- plan_tier_id is nullable here: the Stripe checkout.session.completed webhook
-- sets it when the subscriber is activated. status starts at PENDING_ACTIVATION.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE subscriber (
    id                       BIGSERIAL    PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    property_id              BIGINT       NOT NULL REFERENCES property (id) ON DELETE RESTRICT,
    -- Nullable until Stripe checkout sets it. RESTRICT prevents deleting a plan tier
    -- while subscribers reference it.
    plan_tier_id             BIGINT       REFERENCES plan_tier (id) ON DELETE RESTRICT,
    status                   VARCHAR(30)  NOT NULL
                                          CHECK (status IN (
                                              'PENDING_ACTIVATION', 'ACTIVE', 'PAUSED',
                                              'PAYMENT_ISSUE', 'CANCELLED'
                                          )),
    founding_rate            BOOLEAN      NOT NULL DEFAULT FALSE,
    founding_rate_expires_at TIMESTAMPTZ,
    stripe_customer_id       VARCHAR(255),
    stripe_subscription_id   VARCHAR(255),
    current_period_start     TIMESTAMPTZ,
    current_period_end       TIMESTAMPTZ,
    billing_cycle            VARCHAR(10)  NOT NULL DEFAULT 'MONTHLY'
                                          CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    started_at               TIMESTAMPTZ,
    paused_at                TIMESTAMPTZ,
    paused_until             TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Now that subscriber exists, add the deferrable FK on property.subscriber_id.
-- DEFERRABLE INITIALLY DEFERRED: the FK is checked at COMMIT, not at statement
-- execution. This allows: INSERT property (subscriber_id=NULL) → INSERT subscriber
-- → UPDATE property SET subscriber_id=<new_id> all within one transaction, or
-- alternatively both INSERTs happen and the cycle resolves at commit.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE property
    ADD CONSTRAINT fk_property_subscriber
    FOREIGN KEY (subscriber_id)
    REFERENCES subscriber (id)
    ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;

-- ─────────────────────────────────────────────────────────────────────────────
-- subscription_event
-- One of the two allowed JSONB columns (arch doc §3).
-- source: STRIPE_WEBHOOK (Stripe webhook handler), MANUAL (admin), SYSTEM (scheduler).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE subscription_event (
    id            BIGSERIAL    PRIMARY KEY,
    subscriber_id BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE CASCADE,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB,
    processed_at  TIMESTAMPTZ,
    source        VARCHAR(20)  NOT NULL
                               CHECK (source IN ('STRIPE_WEBHOOK', 'MANUAL', 'SYSTEM')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_event_subscriber ON subscription_event (subscriber_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- activation_token
-- token_hash: SHA-256 hex of the raw HMAC-signed magic-link token.
-- consumed_at: set when /activation/complete runs successfully (single-use).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE activation_token (
    id          BIGSERIAL   PRIMARY KEY,
    booking_id  BIGINT      NOT NULL REFERENCES walkthrough_booking (id) ON DELETE RESTRICT,
    token_hash  TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_activation_token_hash     ON activation_token (token_hash);
CREATE INDEX        idx_activation_token_booking  ON activation_token (booking_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Indexes on subscriber (hot query columns per arch doc §2.4)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_subscriber_user_id      ON subscriber (user_id);
CREATE INDEX idx_subscriber_property_id  ON subscriber (property_id);
CREATE INDEX idx_subscriber_plan_tier_id ON subscriber (plan_tier_id);
CREATE INDEX idx_subscriber_status       ON subscriber (status);
CREATE INDEX idx_subscriber_founding     ON subscriber (founding_rate);

-- Index property.subscriber_id for the reverse lookup.
CREATE INDEX idx_property_subscriber_id ON property (subscriber_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- FK backfill: add the deferred FKs from walkthrough_booking to subscriber and
-- activation_token. Both tables now exist; V3 left these as bare BIGINT columns.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE walkthrough_booking
    ADD CONSTRAINT fk_wb_converted_to_subscriber
    FOREIGN KEY (converted_to_subscriber_id)
    REFERENCES subscriber (id)
    ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE walkthrough_booking
    ADD CONSTRAINT fk_wb_activation_token
    FOREIGN KEY (activation_token_id)
    REFERENCES activation_token (id)
    ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;
