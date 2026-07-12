-- V10__notification_log.sql
-- Reminder-send dedupe ledger (#89). The @Scheduled reminder job records every
-- reminder it sends here. A UNIQUE (notification_type, target_type, target_id)
-- lets the job insert BLIND and treat a unique-violation as "already sent, skip"
-- (the same insert-blind integrity pattern as the reschedule PENDING guard in
-- V8), so a given reminder for a given entity is sent AT MOST ONCE even across
-- app restarts, overlapping scheduler ticks, or multiple instances.
-- Conventions: BIGSERIAL PK, TIMESTAMPTZ UTC, VARCHAR enums + CHECK.
--
-- target_id is a plain BIGINT, deliberately NOT a foreign key: the log points at
-- different parent tables depending on target_type (walkthrough_booking vs
-- visit), which a single FK cannot express. A later-deleted or completed target
-- simply leaves a harmless historical row; the job never dereferences a target
-- through this table (it only checks/writes the dedupe key). No cascade needed.
--
-- notification_type / target_type are CHECK-constrained to the current reminder
-- set; adding a new reminder kind is a small ALTER migration (founder), matching
-- how every other enum column in this schema is guarded.

CREATE TABLE notification_log (
    id                 BIGSERIAL    PRIMARY KEY,
    notification_type  VARCHAR(48)  NOT NULL
                                    CHECK (notification_type IN ('WALKTHROUGH_REMINDER_24H', 'VISIT_REMINDER_24H')),
    target_type        VARCHAR(32)  NOT NULL
                                    CHECK (target_type IN ('WALKTHROUGH_BOOKING', 'VISIT')),
    target_id          BIGINT       NOT NULL,
    sent_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_log UNIQUE (notification_type, target_type, target_id)
);

-- The UNIQUE constraint above already creates the (notification_type,
-- target_type, target_id) index the job uses to insert-blind and dedupe; no
-- additional index is needed at MVP volumes.
