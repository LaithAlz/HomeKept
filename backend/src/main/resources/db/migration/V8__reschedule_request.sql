-- V8__reschedule_request.sql
-- Customer self-serve reschedule requests (#54). A subscriber proposes one or more
-- preferred time slots for an existing visit; an admin confirms via the visit state
-- machine, which creates the replacement visit (confirmed_visit_id) and swaps the
-- schedule.
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums + CHECK, all FKs indexed.
-- No JSONB: preferred slots are normalized into a child table per arch doc §3 (JSONB is
-- allowed only on subscription_event.payload and payment_event.raw_payload).

-- ─────────────────────────────────────────────────────────────────────────────
-- reschedule_request
-- ─────────────────────────────────────────────────────────────────────────────
-- visit_id: the visit the customer wants moved. CASCADE — a request is meaningless
--           without its visit (same parent-child rule as visit_photo / visit_note).
-- subscriber_id: denormalized for ownership scoping (the 404 rule), same as flag /
--                todo_item. RESTRICT — never orphan a request by deleting a subscriber.
-- status: PENDING (awaiting admin), CONFIRMED (admin scheduled the swap),
--         DECLINED (admin could not accommodate).
-- confirmed_visit_id: the replacement visit created on confirm; SET NULL if later deleted.
--                     Null while PENDING / DECLINED.

CREATE TABLE reschedule_request (
    id                  BIGSERIAL    PRIMARY KEY,
    visit_id            BIGINT       NOT NULL REFERENCES visit (id) ON DELETE CASCADE,
    subscriber_id       BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE RESTRICT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                     CHECK (status IN ('PENDING', 'CONFIRMED', 'DECLINED')),
    admin_note          TEXT,
    confirmed_visit_id  BIGINT       REFERENCES visit (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_reschedule_request_subscriber ON reschedule_request (subscriber_id);
CREATE INDEX idx_reschedule_request_visit      ON reschedule_request (visit_id);

-- Integrity guard: at most one PENDING request per visit. Enforced at the DB so the
-- service inserts blind and lets a duplicate fail the constraint (→ 409) instead of
-- spending a pre-check SELECT. Partial (PENDING only) so historical CONFIRMED/DECLINED
-- rows never block a fresh request. (Status index intentionally omitted otherwise:
-- tiny table + low cardinality at MVP.)
CREATE UNIQUE INDEX idx_reschedule_request_pending_visit
    ON reschedule_request (visit_id) WHERE status = 'PENDING';

-- ─────────────────────────────────────────────────────────────────────────────
-- reschedule_request_slot
-- ─────────────────────────────────────────────────────────────────────────────
-- The customer's preferred time slots for the reschedule (1..N rows per request).
-- Normalized into rows rather than a JSONB array per arch doc §3. TIMESTAMPTZ — a
-- specific proposed start time (UTC stored, America/Toronto rendered), same as
-- visit.scheduled_for; the admin picks one when creating the replacement visit.

CREATE TABLE reschedule_request_slot (
    id                     BIGSERIAL    PRIMARY KEY,
    reschedule_request_id  BIGINT       NOT NULL REFERENCES reschedule_request (id) ON DELETE CASCADE,
    preferred_slot         TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_reschedule_request_slot_request ON reschedule_request_slot (reschedule_request_id);
