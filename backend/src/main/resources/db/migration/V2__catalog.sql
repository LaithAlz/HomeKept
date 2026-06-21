-- V2__catalog.sql
-- Catalog domain: service, plan_tier, plan_tier_service
-- Seed data transcribed from docs/pricing-and-visits.md — do not invent numbers.
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums, all FKs indexed.

-- ─────────────────────────────────────────────────────────────────────────────
-- service
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE service (
    id                       BIGSERIAL    PRIMARY KEY,
    name                     VARCHAR(200) NOT NULL,
    category                 VARCHAR(20)  NOT NULL CHECK (category IN ('HVAC', 'PLUMBING', 'EXTERIOR', 'SMART_HOME')),
    tier_class               VARCHAR(10)  NOT NULL CHECK (tier_class IN ('BASIC', 'MEDIUM', 'PREMIUM')),
    default_duration_minutes INTEGER      NOT NULL,
    a_la_carte_price_cents   INTEGER,
    description              TEXT         NOT NULL,
    is_free_with_every_visit BOOLEAN      NOT NULL DEFAULT FALSE,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- plan_tier
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE plan_tier (
    id                            BIGSERIAL    PRIMARY KEY,
    code                          VARCHAR(20)  NOT NULL UNIQUE CHECK (code IN ('ESSENTIAL', 'COMPLETE', 'PREMIER')),
    display_name                  VARCHAR(50)  NOT NULL,
    monthly_price_cents           INTEGER      NOT NULL,
    annual_price_cents            INTEGER      NOT NULL,
    visits_per_year               INTEGER      NOT NULL,
    included_picks_per_year       INTEGER      NOT NULL,
    max_premium_picks_per_year    INTEGER      NOT NULL,
    stripe_price_id_monthly       VARCHAR(255),
    stripe_price_id_annual        VARCHAR(255),
    stripe_price_id_founding      VARCHAR(255),
    founding_monthly_price_cents  INTEGER,
    description                   TEXT         NOT NULL,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at                   TIMESTAMPTZ
);

-- ─────────────────────────────────────────────────────────────────────────────
-- plan_tier_service  (join: which service appears in which tier, how often)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE plan_tier_service (
    plan_tier_id      BIGINT  NOT NULL REFERENCES plan_tier (id) ON DELETE RESTRICT,
    service_id        BIGINT  NOT NULL REFERENCES service (id) ON DELETE RESTRICT,
    frequency_per_year INTEGER NOT NULL,
    PRIMARY KEY (plan_tier_id, service_id)
);

CREATE INDEX idx_plan_tier_service_plan ON plan_tier_service (plan_tier_id);
CREATE INDEX idx_plan_tier_service_svc  ON plan_tier_service (service_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED DATA
-- Source of truth: docs/pricing-and-visits.md
-- Money is integer cents. Never floats.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── plan_tier ────────────────────────────────────────────────────────────────
-- ESSENTIAL: $89/mo · $890/yr · 4 visits · 1 pick (Basic or Medium) · founding N/A
-- COMPLETE:  $149/mo · $1,490/yr · 8 visits · 3 picks (max 1 Premium) · founding $129/mo
-- PREMIER:   $249/mo · $2,490/yr · 12 visits · 6 picks (max 3 Premium) · founding N/A

INSERT INTO plan_tier (
    code, display_name,
    monthly_price_cents, annual_price_cents,
    visits_per_year, included_picks_per_year, max_premium_picks_per_year,
    founding_monthly_price_cents,
    description
) VALUES
(
    'ESSENTIAL', 'Essential',
    8900, 89000,
    4, 1, 0,
    NULL,
    'Four seasonal visits per year. Standing checklist every visit: filter check/swap, smoke and CO test, mechanicals walkaround, humidity reading. One included pick per year (Basic or Medium tier). Consistent technician where possible.'
),
(
    'COMPLETE', 'Complete',
    14900, 149000,
    8, 3, 1,
    12900,
    'Eight visits per year — the four seasonal anchors plus four mid-season visits. Three included picks per year (up to one Premium). Priority scheduling: issue visits within 48 hours, emergency line. Licensed gas tune-up coordination included.'
),
(
    'PREMIER', 'Premier',
    24900, 249000,
    12, 6, 3,
    NULL,
    'Twelve monthly visits, each named. Six included picks per year (up to three Premium). Dedicated technician — guaranteed same person. Same-week scheduling plus 24-hour emergency line. Up to one hour of minor repair labor per visit, parts at cost. Smart-home support and Annual Home Plan included.'
);

-- ── service (standing items — is_free_with_every_visit = TRUE) ───────────────
-- These four items run every visit, every tier. tier_class is BASIC (lowest class,
-- no à la carte price — they are included, not pickable).
-- a_la_carte_price_cents is NULL because these are not picks.

INSERT INTO service (
    name, category, tier_class, default_duration_minutes,
    a_la_carte_price_cents, description, is_free_with_every_visit
) VALUES
(
    'Filter check/swap',
    'HVAC', 'BASIC', 10,
    NULL,
    'Inspect furnace/air handler filter; swap if due. Included materials: 1-inch filters up to MERV 11. Media/HEPA filters logged at cost.',
    TRUE
),
(
    'Smoke and CO test + batteries',
    'HVAC', 'BASIC', 10,
    NULL,
    'Test all smoke and CO detectors; replace batteries. Hardwired replacements are referred. Non-invasive observation only.',
    TRUE
),
(
    'Mechanicals walkaround',
    'HVAC', 'BASIC', 10,
    NULL,
    'Visual inspection of furnace, water heater, electrical panel, and under-sink areas — eyes only, no diagnosis. Observable issues are photographed and flagged for referral.',
    TRUE
),
(
    'Humidity reading',
    'HVAC', 'BASIC', 5,
    NULL,
    'Measure and log indoor humidity; adjust humidifier set-point if accessible.',
    TRUE
);

-- ── service (pickable — Basic tier_class, $49 à la carte) ────────────────────
-- Source: docs/pricing-and-visits.md Picks menu, "Basic — $49" column.

INSERT INTO service (
    name, category, tier_class, default_duration_minutes,
    a_la_carte_price_cents, description, is_free_with_every_visit
) VALUES
(
    'Extra filter visit',
    'HVAC', 'BASIC', 20,
    4900,
    'An additional between-season filter inspection and swap outside the standard visit cadence.',
    FALSE
),
(
    'Weatherstripping touch-up',
    'EXTERIOR', 'BASIC', 25,
    4900,
    'Inspect and replace worn weatherstripping on exterior doors. Included materials: standard foam or V-strip weatherstripping.',
    FALSE
),
(
    'Garage door tune and lube',
    'EXTERIOR', 'BASIC', 20,
    4900,
    'Lubricate hinges, rollers, and springs; check balance and auto-reverse. Visual observation only — mechanical repairs referred.',
    FALSE
),
(
    'Faucet/showerhead descale',
    'PLUMBING', 'BASIC', 20,
    4900,
    'Remove and soak aerators and showerheads to clear mineral buildup. No plumbing repairs — leaks or supply-line issues are referred.',
    FALSE
),
(
    'Detector battery sweep',
    'HVAC', 'BASIC', 15,
    4900,
    'Replace batteries in all smoke and CO detectors outside the standard standing-item sweep. Hardwired replacements referred.',
    FALSE
);

-- ── service (pickable — Medium tier_class, $89 à la carte) ───────────────────
-- Source: docs/pricing-and-visits.md Picks menu, "Medium — $89" column.

INSERT INTO service (
    name, category, tier_class, default_duration_minutes,
    a_la_carte_price_cents, description, is_free_with_every_visit
) VALUES
(
    'Extra water heater flush',
    'PLUMBING', 'MEDIUM', 30,
    8900,
    'Flush sediment from tank water heater and inspect anode rod visually. Skip-rule applies: tank 8+ years or drain valve uncertain — visual and temperature check only, assessment recommended.',
    FALSE
),
(
    'Dryer vent deep clean',
    'HVAC', 'MEDIUM', 35,
    8900,
    'Clear accumulated lint from the full dryer vent run to the exterior cap. Fire-risk reduction. Duct condition noted; professional duct replacement referred if needed.',
    FALSE
),
(
    'Caulking refresh (one area)',
    'EXTERIOR', 'MEDIUM', 30,
    8900,
    'Remove failed caulk and apply fresh bead at one designated area (e.g., tub surround, one window, one door frame). Includes standard silicone or latex caulk material.',
    FALSE
),
(
    'Smart thermostat install',
    'SMART_HOME', 'MEDIUM', 40,
    8900,
    'Install a customer-supplied smart thermostat on a low-voltage 24V system. Line-voltage (240V) systems are referred to a licensed electrician (ESA). Includes wiring and app setup.',
    FALSE
),
(
    'Toilet internals refresh',
    'PLUMBING', 'MEDIUM', 30,
    8900,
    'Replace flapper, fill valve, and flush valve seat as needed. Includes standard replacement parts. Supply-line or tank cracks referred.',
    FALSE
);

-- ── service (pickable — Premium tier_class, $149 à la carte) ─────────────────
-- Source: docs/pricing-and-visits.md Picks menu, "Premium — $149" column.

INSERT INTO service (
    name, category, tier_class, default_duration_minutes,
    a_la_carte_price_cents, description, is_free_with_every_visit
) VALUES
(
    'Extra full gutter clear',
    'EXTERIOR', 'PREMIUM', 45,
    14900,
    'Clear all gutters and flush downspouts outside the fall schedule. Grade-level and eave access only — no roof walking.',
    FALSE
),
(
    'Roof and exterior inspection with report',
    'EXTERIOR', 'PREMIUM', 60,
    14900,
    'Full exterior walkaround: roof-line and fascia from grade, siding, foundation, grading, and caulking. Binary observable criteria — not a certified inspection. Findings photographed and included in a written report.',
    FALSE
),
(
    'Smart-home package install',
    'SMART_HOME', 'PREMIUM', 90,
    14900,
    'Install up to three customer-supplied smart-home devices (smart locks, video doorbells, smart plugs, or similar low-voltage items). Hardwired electrical work referred to a licensed electrician (ESA).',
    FALSE
),
(
    'Pre-winter full-home inspection',
    'EXTERIOR', 'PREMIUM', 75,
    14900,
    'Comprehensive pre-heating-season inspection: exterior envelope, weatherstripping, attic peek for insulation gaps (joist-safe only), mechanicals visual, detector sweep, humidifier service, and written summary. Not a certified home inspection.',
    FALSE
);

-- ── plan_tier_service ─────────────────────────────────────────────────────────
-- Maps services to tiers with frequency_per_year.
--
-- Standing items: frequency = tier's visits_per_year (they run every visit).
-- Pick services: frequency = number of times that pick type appears per tier's calendar.
--   The picks allowance resets annually; picks fold into scheduled visits.
--   For plan_tier_service we record the INCLUDED frequency per year:
--   - Each tier's included picks per year is a budget, not a fixed schedule.
--   - Standing seasonal services (e.g. "Fall winterization focus") are NOT in the
--     catalog as discrete pick services; they are part of the named visit template
--     (deferred to the visit slice per scope).
--   Therefore: pick services appear in plan_tier_service only if the spec
--   unambiguously assigns them as INCLUDED (not just available) at a given frequency.
--
-- The spec does NOT assign specific named picks to specific tiers as pre-scheduled
-- included items — picks are a customer-chosen allowance. The plan_tier_service join
-- for pickable services records frequency_per_year = the tier's included_picks_per_year
-- as a planning figure, but the actual selection is driven by the picks allowance at
-- runtime. We include only the standing items here to avoid inventing frequencies.

-- ESSENTIAL (id resolved by code; using subquery for FK safety)
INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 4
FROM plan_tier pt, service s
WHERE pt.code = 'ESSENTIAL'
  AND s.name = 'Filter check/swap';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 4
FROM plan_tier pt, service s
WHERE pt.code = 'ESSENTIAL'
  AND s.name = 'Smoke and CO test + batteries';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 4
FROM plan_tier pt, service s
WHERE pt.code = 'ESSENTIAL'
  AND s.name = 'Mechanicals walkaround';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 4
FROM plan_tier pt, service s
WHERE pt.code = 'ESSENTIAL'
  AND s.name = 'Humidity reading';

-- COMPLETE (8 visits/yr)
INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 8
FROM plan_tier pt, service s
WHERE pt.code = 'COMPLETE'
  AND s.name = 'Filter check/swap';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 8
FROM plan_tier pt, service s
WHERE pt.code = 'COMPLETE'
  AND s.name = 'Smoke and CO test + batteries';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 8
FROM plan_tier pt, service s
WHERE pt.code = 'COMPLETE'
  AND s.name = 'Mechanicals walkaround';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 8
FROM plan_tier pt, service s
WHERE pt.code = 'COMPLETE'
  AND s.name = 'Humidity reading';

-- PREMIER (12 visits/yr)
INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 12
FROM plan_tier pt, service s
WHERE pt.code = 'PREMIER'
  AND s.name = 'Filter check/swap';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 12
FROM plan_tier pt, service s
WHERE pt.code = 'PREMIER'
  AND s.name = 'Smoke and CO test + batteries';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 12
FROM plan_tier pt, service s
WHERE pt.code = 'PREMIER'
  AND s.name = 'Mechanicals walkaround';

INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 12
FROM plan_tier pt, service s
WHERE pt.code = 'PREMIER'
  AND s.name = 'Humidity reading';
