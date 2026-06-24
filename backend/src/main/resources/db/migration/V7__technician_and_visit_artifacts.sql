-- V7__technician_and_visit_artifacts.sql
-- Technician profile + visit artifact tables (visit_photo, visit_note, flag, todo_item).
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums + CHECK, all FKs indexed,
-- money in integer cents (never float).

-- ─────────────────────────────────────────────────────────────────────────────
-- technician_profile
-- ─────────────────────────────────────────────────────────────────────────────
-- MVP: founders ARE the technicians. Do not seed rows — founder onboards via
-- POST /api/admin/technicians (user ids are not known at migration time).
-- fully_loaded_hourly_cost_cents: integer cents. Set a notional value from day 1
-- so per-visit unit economics are real numbers, not vibes.
-- Regions/availability tables are Stage 3 (50+ customers) — deferred per §2.7.

CREATE TABLE technician_profile (
    id                              BIGSERIAL    PRIMARY KEY,
    user_id                         BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE RESTRICT,
    employee_status                 VARCHAR(50),
    hire_date                       DATE,
    fully_loaded_hourly_cost_cents  INTEGER,     -- integer cents; set from day 1 for unit economics
    vehicle_info                    TEXT,
    notes                           TEXT,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_technician_profile_user ON technician_profile (user_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- visit_photo
-- ─────────────────────────────────────────────────────────────────────────────
-- storage_key: R2 object key, server-generated as visits/{visitId}/{uuid}.
-- taken_at: nullable — the technician's device time when photo was captured.
-- ON DELETE CASCADE: deleting a visit removes its photos.

CREATE TABLE visit_photo (
    id           BIGSERIAL    PRIMARY KEY,
    visit_id     BIGINT       NOT NULL REFERENCES visit (id) ON DELETE CASCADE,
    storage_key  TEXT         NOT NULL,
    caption      TEXT,
    taken_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_photo_visit ON visit_photo (visit_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- visit_note
-- ─────────────────────────────────────────────────────────────────────────────
-- author_user_id: bare BIGINT (no FK to users to avoid cross-domain hard FK).
-- ON DELETE CASCADE: deleting a visit removes its notes.

CREATE TABLE visit_note (
    id              BIGSERIAL    PRIMARY KEY,
    visit_id        BIGINT       NOT NULL REFERENCES visit (id) ON DELETE CASCADE,
    author_user_id  BIGINT       NOT NULL,
    body            TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_note_visit ON visit_note (visit_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- flag
-- ─────────────────────────────────────────────────────────────────────────────
-- The persistent "observe → photograph → flag → refer" half of the tech workflow.
-- severity: INFO (informational), ATTENTION (needs attention), URGENT (act soon).
-- status: OPEN (just created), SCHEDULED (folded into a future visit),
--         RESOLVED (addressed), REFERRED (referred to a licensed trade).
-- origin_visit_id: nullable — flags can be created outside a visit context.
-- photo_storage_key: nullable — not all flags have a photo.

CREATE TABLE flag (
    id                 BIGSERIAL    PRIMARY KEY,
    subscriber_id      BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE RESTRICT,
    origin_visit_id    BIGINT       REFERENCES visit (id) ON DELETE SET NULL,
    body               TEXT         NOT NULL,
    severity           VARCHAR(20)  NOT NULL CHECK (severity IN ('INFO', 'ATTENTION', 'URGENT')),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'SCHEDULED', 'RESOLVED', 'REFERRED')),
    photo_storage_key  TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMPTZ
);

CREATE INDEX idx_flag_subscriber ON flag (subscriber_id);
CREATE INDEX idx_flag_status     ON flag (status);

-- ─────────────────────────────────────────────────────────────────────────────
-- todo_item
-- ─────────────────────────────────────────────────────────────────────────────
-- "Your list" — subscriber-submitted maintenance items.
-- status: OPEN (waiting), SCHEDULED (folded into a visit), DONE (completed by tech),
--         DECLINED (tech couldn't/shouldn't do it, with a note).
-- visit_id: nullable — set when the todo is folded into a visit.
-- decline_note: nullable — set when status = DECLINED.

CREATE TABLE todo_item (
    id            BIGSERIAL    PRIMARY KEY,
    subscriber_id BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE RESTRICT,
    body          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'SCHEDULED', 'DONE', 'DECLINED')),
    visit_id      BIGINT       REFERENCES visit (id) ON DELETE SET NULL,
    decline_note  TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_todo_item_subscriber ON todo_item (subscriber_id);
CREATE INDEX idx_todo_item_status     ON todo_item (status);

-- Add the technician_id FK to the visit table now that technician_profile exists.
-- V6 left technician_id as a bare BIGINT with a comment "no FK yet (technician slice)".
ALTER TABLE visit
    ADD CONSTRAINT fk_visit_technician
    FOREIGN KEY (technician_id) REFERENCES users (id) ON DELETE RESTRICT;

CREATE INDEX idx_visit_technician ON visit (technician_id);

-- Add materials_notes to the visit table (filled at completion alongside completion_notes).
ALTER TABLE visit ADD COLUMN materials_notes TEXT;
