-- V6__visit.sql
-- Visit domain: visit_template, visit_template_service, visit, visit_service
-- Seed data: 12 visit templates from docs/pricing-and-visits.md visit calendar.
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums + CHECK, all FKs indexed.

-- ─────────────────────────────────────────────────────────────────────────────
-- visit_template
-- ─────────────────────────────────────────────────────────────────────────────
-- min_tier: the tier at which this visit first appears (cumulative calendar).
--   ESSENTIAL  → Essential, Complete, and Premier all get this visit (4 E-tier visits/yr)
--   COMPLETE   → Complete and Premier only (4 additional C-tier visits/yr)
--   PREMIER    → Premier only (4 additional P-tier visits/yr)
-- This matches the E/C/P column in the calendar table in docs/pricing-and-visits.md.

CREATE TABLE visit_template (
    id          BIGSERIAL    PRIMARY KEY,
    month       INTEGER      NOT NULL CHECK (month BETWEEN 1 AND 12),
    name        VARCHAR(100) NOT NULL,
    min_tier    VARCHAR(20)  NOT NULL CHECK (min_tier IN ('ESSENTIAL', 'COMPLETE', 'PREMIER')),
    description TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_visit_template_month_tier ON visit_template (month, min_tier);

-- ─────────────────────────────────────────────────────────────────────────────
-- visit_template_service
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE visit_template_service (
    visit_template_id BIGINT  NOT NULL REFERENCES visit_template (id) ON DELETE RESTRICT,
    service_id        BIGINT  NOT NULL REFERENCES service (id) ON DELETE RESTRICT,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (visit_template_id, service_id)
);

CREATE INDEX idx_visit_template_service_tmpl ON visit_template_service (visit_template_id);
CREATE INDEX idx_visit_template_service_svc  ON visit_template_service (service_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- visit
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE visit (
    id                       BIGSERIAL    PRIMARY KEY,
    subscriber_id            BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE RESTRICT,
    property_id              BIGINT       NOT NULL REFERENCES property (id) ON DELETE RESTRICT,
    technician_id            BIGINT,                    -- nullable; no FK yet (technician slice)
    visit_template_id        BIGINT       REFERENCES visit_template (id) ON DELETE RESTRICT,
    scheduled_for            TIMESTAMPTZ  NOT NULL,
    duration_minutes         INTEGER      NOT NULL,
    actual_duration_minutes  INTEGER,                   -- filled at completion
    materials_cost_cents     INTEGER,                   -- integer cents; filled at completion
    status                   VARCHAR(20)  NOT NULL CHECK (status IN (
                                 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED',
                                 'INCOMPLETE', 'CANCELLED', 'RESCHEDULED'
                             )),
    type                     VARCHAR(20)  NOT NULL CHECK (type IN (
                                 'ROUTINE', 'EXTRA', 'WARRANTY', 'WALKTHROUGH'
                             )),
    completion_notes         TEXT,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_subscriber   ON visit (subscriber_id);
CREATE INDEX idx_visit_scheduled    ON visit (scheduled_for);
CREATE INDEX idx_visit_status       ON visit (status);

-- ─────────────────────────────────────────────────────────────────────────────
-- visit_service
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE visit_service (
    id                BIGSERIAL    PRIMARY KEY,
    visit_id          BIGINT       NOT NULL REFERENCES visit (id) ON DELETE CASCADE,
    service_id        BIGINT       NOT NULL REFERENCES service (id) ON DELETE RESTRICT,
    source            VARCHAR(20)  NOT NULL CHECK (source IN (
                          'TEMPLATE', 'PICK', 'EXTRA', 'FLAGGED', 'TODO'
                      )),
    completed         BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at      TIMESTAMPTZ,
    technician_notes  TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_service_visit ON visit_service (visit_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED DATA: 12 visit templates from docs/pricing-and-visits.md
-- Source of truth: the visit calendar table.
-- Description = "Seasonal focus" column text; trades-safe wording throughout.
-- ─────────────────────────────────────────────────────────────────────────────

-- Jan  — Winter check (E = ESSENTIAL; Essential, Complete, Premier all get it)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(1, 'Winter check', 'ESSENTIAL',
 'Mid-season filter inspection and swap. Water heater flush visual check (skip-rule: tank 8+ years or drain valve uncertain — visual and temperature check only, assessment recommended). Attic peek for ice dams (joist-safe, photo observation). Humidity tune. Smoke and CO detector sweep (peak CO season).');

-- Feb  — Deep-winter walkthrough (P = PREMIER only)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(2, 'Deep-winter walkthrough', 'PREMIER',
 'Condensation and draft check at windows and doors. Basement moisture scan (visual, non-invasive). Tub and shower caulking inspection and touch-up. Garage door tune and lubrication.');

-- Mar  — Thaw prep (C = COMPLETE and above)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(3, 'Thaw prep', 'COMPLETE',
 'Sump pump test and pit clean. Snowmelt drainage and grading check (visual observation at grade). Foundation walkaround using binary observable criteria — photographed and flagged if issues found. Floor drain visual check.');

-- Apr  — Spring readiness (E = ESSENTIAL)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(4, 'Spring readiness', 'ESSENTIAL',
 'Reconnect and test outdoor taps after winter shutoff. AC startup observation — visual and audible check, not a diagnostic. Spring gutter clear (grade and eave access only, no roof walking). Winter-damage walkaround from grade.');

-- May  — Exterior tune (P = PREMIER only)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(5, 'Exterior tune', 'PREMIER',
 'Deck, railing, and fence hardware inspection and tightening. Screen inspection and minor repair. Exterior caulking touch-points at doors and windows. Irrigation system and hose bib check.');

-- Jun  — Summer prep (C = COMPLETE and above)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(6, 'Summer prep', 'COMPLETE',
 'Full exterior caulking pass at all accessible touch-points. AC condenser clean — power off, gentle rinse (visual observation; refrigerant work is referred). Bathroom fan clean. Drainage recheck after spring melt season.');

-- Jul  — Summer systems (E = ESSENTIAL)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(7, 'Summer systems', 'ESSENTIAL',
 'AC performance check — visual and audible observation, temperature differential noted. Under-sink, toilet, and appliance leak inspection (visual only; plumbing repairs referred). Dryer vent deep clean — full run to exterior cap. Washer hose visual inspection.');

-- Aug  — Water systems (P = PREMIER only)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(8, 'Water systems', 'PREMIER',
 'Water heater visual inspection and temperature check (flush if tank is under 8 years and drain valve sound; otherwise visual and temperature only, assessment recommended). Water pressure test at a tap. Toilet internals visual check. Sump pump recheck.');

-- Sep  — Pre-heating check (C = COMPLETE and above)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(9, 'Pre-heating check', 'COMPLETE',
 'Filter inspection and furnace visual and performance observation (gas tune-up is referred to a licensed TSSA technician if due). Humidifier pad inspection and replacement if due. Weatherstripping pass at all exterior doors. Book licensed gas tune-up if technician observes it is due.');

-- Oct  — Fall winterization (E = ESSENTIAL)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(10, 'Fall winterization', 'ESSENTIAL',
 'Shut down and drain outdoor taps for winter. Humidifier service and pad inspection. Weatherstripping and door sweep inspection and touch-up. Eaves visual check from grade. Smoke and CO detector sweep.');

-- Nov  — Post-leaf gutters (C = COMPLETE and above)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(11, 'Post-leaf gutters', 'COMPLETE',
 'Full gutter and downspout clear after leaf fall. Roof-line visual observation from grade and eave level only — no roof walking. Downspout extension inspection and repositioning as needed.');

-- Dec  — Holiday and safety (P = PREMIER only)
INSERT INTO visit_template (month, name, min_tier, description) VALUES
(12, 'Holiday and safety', 'PREMIER',
 'Smoke and CO detector and fire extinguisher inspection and battery check. Dryer vent visual recheck. Extension cord and space-heater walkthrough (fire-safety observation). Your-list catch-up: carry over open customer items into this visit.');

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: link the 4 standing-item services to EVERY template
-- "Every visit, every tier" items per docs/pricing-and-visits.md §"Every visit, every tier"
-- These are the services flagged is_free_with_every_visit = TRUE in the service table.
-- sort_order: 1–4 so they appear first on every checklist.
-- ─────────────────────────────────────────────────────────────────────────────

-- Filter check/swap → all 12 templates, sort_order 1
INSERT INTO visit_template_service (visit_template_id, service_id, sort_order)
SELECT vt.id, s.id, 1
FROM visit_template vt, service s
WHERE s.name = 'Filter check/swap';

-- Smoke and CO test + batteries → all 12 templates, sort_order 2
INSERT INTO visit_template_service (visit_template_id, service_id, sort_order)
SELECT vt.id, s.id, 2
FROM visit_template vt, service s
WHERE s.name = 'Smoke and CO test + batteries';

-- Mechanicals walkaround → all 12 templates, sort_order 3
INSERT INTO visit_template_service (visit_template_id, service_id, sort_order)
SELECT vt.id, s.id, 3
FROM visit_template vt, service s
WHERE s.name = 'Mechanicals walkaround';

-- Humidity reading → all 12 templates, sort_order 4
INSERT INTO visit_template_service (visit_template_id, service_id, sort_order)
SELECT vt.id, s.id, 4
FROM visit_template vt, service s
WHERE s.name = 'Humidity reading';
