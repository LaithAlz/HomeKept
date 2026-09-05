-- V15__restore_essential_tier.sql
-- Reinstates the ESSENTIAL tier that V12 removed. Founder decision, 2026-09-05:
-- restore it exactly as it was, $89/mo · $890/yr · 4 visits · 1 included pick ·
-- 0 Premium picks. This is a deliberate reversal of V12 section 1-3, not a bug fix.
--
-- NOT restored: the founding-member rate. V12 deleted that concept entirely
-- (founding_monthly_price_cents, stripe_price_id_founding, subscriber.founding_rate)
-- and it stays deleted, so the INSERT below omits those columns.
--
-- Do NOT edit V2/V6/V12 — their Flyway checksums are live.
-- Conventions: same as prior migrations (TIMESTAMPTZ UTC, integer cents, VARCHAR enums).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Widen the CHECK constraints back out to admit ESSENTIAL. Must happen before
-- the INSERT and the UPDATE below, or both are rejected. Constraint names are the
-- ones V12 created explicitly.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE plan_tier
    DROP CONSTRAINT plan_tier_code_check;
ALTER TABLE plan_tier
    ADD CONSTRAINT plan_tier_code_check CHECK (code IN ('ESSENTIAL', 'COMPLETE', 'PREMIER'));

ALTER TABLE visit_template
    DROP CONSTRAINT visit_template_min_tier_check;
ALTER TABLE visit_template
    ADD CONSTRAINT visit_template_min_tier_check
        CHECK (min_tier IN ('ESSENTIAL', 'COMPLETE', 'PREMIER'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Re-seed the ESSENTIAL plan_tier row, with the V2 values and description
-- verbatim. Both Stripe price ids are left NULL on purpose: the retired $89 prices
-- must not be reused, and until the founder creates new live Stripe Prices and fills
-- these in, ESSENTIAL checkout fails closed with 409 PLAN_NOT_PURCHASABLE rather
-- than charging nothing or charging the wrong amount. Same fail-closed posture V12
-- applied to COMPLETE.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO plan_tier (
    code, display_name,
    monthly_price_cents, annual_price_cents,
    visits_per_year, included_picks_per_year, max_premium_picks_per_year,
    stripe_price_id_monthly, stripe_price_id_annual,
    description
) VALUES (
    'ESSENTIAL', 'Essential',
    8900, 89000,
    4, 1, 0,
    NULL, NULL,
    'Four seasonal visits per year. Standing checklist every visit: filter check/swap, smoke and CO test, mechanicals walkaround, humidity reading. One included pick per year (Basic or Medium tier). Consistent technician where possible.'
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Re-link the four standing services at 4x/year (once per Essential visit).
-- Resolved by name via subquery, exactly as V2 seeded them, so the FK is safe
-- regardless of the service ids on this database.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO plan_tier_service (plan_tier_id, service_id, frequency_per_year)
SELECT pt.id, s.id, 4
FROM plan_tier pt, service s
WHERE pt.code = 'ESSENTIAL'
  AND s.name IN (
      'Filter check/swap',
      'Smoke and CO test + batteries',
      'Mechanicals walkaround',
      'Humidity reading'
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Return the four seasonal anchor templates (months 1, 4, 7, 10 — Winter check,
-- Spring readiness, Summer systems, Fall winterization) to min_tier ESSENTIAL,
-- undoing V12 section 2.
--
-- min_tier is a FLOOR, not an exclusive assignment: a template at ESSENTIAL applies
-- to Essential and every tier above it. So this does not take the four anchors away
-- from Complete or Premier. Complete still gets 8 visits a year (these 4 anchors plus
-- the 4 it owns at months 3, 6, 9, 11).
--
-- No collision with idx_visit_template_month_tier UNIQUE (month, min_tier): after
-- V12 those months sat at COMPLETE alongside COMPLETE's own months 3/6/9/11, and
-- moving 1/4/7/10 down to ESSENTIAL restores V6's original layout exactly.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE visit_template
SET min_tier = 'ESSENTIAL'
WHERE min_tier = 'COMPLETE'
  AND month IN (1, 4, 7, 10);
