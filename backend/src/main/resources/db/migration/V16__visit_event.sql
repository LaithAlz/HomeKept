-- ─────────────────────────────────────────────────────────────────────────────
-- visit_event
--
-- Per-visit activity log. Mirrors subscription_event (V4) deliberately: same
-- id / entity_id / event_type / payload / source / created_at shape, so the two
-- history surfaces in the admin console read and query the same way.
--
-- WHY THIS EXISTS: reschedule used to preserve history by marking the old visit
-- RESCHEDULED and inserting a brand-new SCHEDULED visit (arch doc §4.2). That
-- put every reschedule into the admin visit list as an extra row and broke the
-- "Visit #N" identity a human uses to refer to a visit. A visit is now
-- rescheduled in place and the before/after is recorded here instead, so the
-- list stays one row per real visit and the history moves to the visit's own
-- detail view.
--
-- event_type is intentionally NOT constrained by a CHECK, matching
-- subscription_event.event_type: adding a new kind of event should not require
-- a migration. source IS constrained, also matching subscription_event, because
-- the set of actors is closed.
--
-- by_user_id is the acting user (the admin who clicked Reschedule, the
-- technician who completed the visit). NULL for SYSTEM events, and ON DELETE
-- SET NULL so removing a staff account never deletes the operational record of
-- what happened to a customer's visit.
--
-- payload is JSONB for the event's specifics, e.g. for RESCHEDULED:
--   { "from": "2026-10-15T16:00:00Z", "to": "2026-11-16T16:00:00Z", "reason": "..." }
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE visit_event (
    id         BIGSERIAL    PRIMARY KEY,
    visit_id   BIGINT       NOT NULL REFERENCES visit (id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    payload    JSONB,
    by_user_id BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    source     VARCHAR(20)  NOT NULL
                            CHECK (source IN ('ADMIN', 'CUSTOMER', 'TECHNICIAN', 'SYSTEM')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The only read pattern is "this visit's history, newest first".
CREATE INDEX idx_visit_event_visit ON visit_event (visit_id, created_at DESC);
