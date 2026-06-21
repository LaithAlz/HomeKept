-- V3__booking.sql
-- Booking domain: walkthrough_booking, walkthrough_booking_day_preference
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums + CHECKs, all FKs indexed.

-- ─────────────────────────────────────────────────────────────────────────────
-- walkthrough_booking
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE walkthrough_booking (
    id                          BIGSERIAL    PRIMARY KEY,
    full_name                   VARCHAR(200) NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    phone                       VARCHAR(30)  NOT NULL,
    street_address              VARCHAR(255) NOT NULL,
    city                        VARCHAR(100) NOT NULL,
    postal_code                 VARCHAR(20)  NOT NULL,
    year_built                  INTEGER,
    square_footage_range        VARCHAR(20)  CHECK (square_footage_range IN ('<1500', '1500-2500', '2500-4000', '>4000')),
    property_type               VARCHAR(20)  NOT NULL CHECK (property_type IN ('DETACHED', 'SEMI', 'TOWNHOUSE')),
    preferred_week              DATE         NOT NULL,
    time_of_day                 VARCHAR(20)  NOT NULL CHECK (time_of_day IN ('MORNING', 'AFTERNOON', 'EVENING')),
    notes                       TEXT,
    status                      VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'PERFORMED', 'CONVERTED', 'LOST', 'NO_SHOW')),
    scheduled_for               TIMESTAMPTZ,
    performed_at                TIMESTAMPTZ,
    lead_source                 VARCHAR(30)  NOT NULL CHECK (lead_source IN ('NEXTDOOR', 'FACEBOOK_GROUP', 'REFERRAL', 'DOOR_KNOCK', 'WEBSITE_ORGANIC', 'WEBSITE_DIRECT', 'OTHER')),
    posthog_distinct_id         VARCHAR(255),
    contact_consent_at          TIMESTAMPTZ  NOT NULL,
    -- No FK constraint yet — subscriber table does not exist; a later migration adds the FK.
    converted_to_subscriber_id  BIGINT,
    -- No FK constraint yet — activation_token table does not exist; a later migration adds the FK.
    activation_token_id         BIGINT,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Pipeline queries filter and order by these columns.
CREATE INDEX idx_walkthrough_booking_status     ON walkthrough_booking (status);
CREATE INDEX idx_walkthrough_booking_created_at ON walkthrough_booking (created_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- walkthrough_booking_day_preference
-- Normalized child table for day-of-week preferences (avoids JSONB per spec).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE walkthrough_booking_day_preference (
    booking_id  BIGINT       NOT NULL REFERENCES walkthrough_booking (id) ON DELETE CASCADE,
    day_of_week VARCHAR(3)   NOT NULL CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    PRIMARY KEY (booking_id, day_of_week)
);

CREATE INDEX idx_wbdp_booking_id ON walkthrough_booking_day_preference (booking_id);
