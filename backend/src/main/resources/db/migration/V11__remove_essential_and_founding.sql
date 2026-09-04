-- V11__remove_essential_and_founding.sql
-- Repositioning (Sep 2026): the ESSENTIAL tier is discontinued entirely, and the
-- founding-member rate concept is DELETED (not deprecated) — catalog, checkout,
-- webhook, admin, DTOs, analytics props, DB columns, tests, docs. Zero real
-- subscribers exist yet, so no data backfill is needed.
-- Do NOT edit V2__catalog.sql / V4__property_subscriber_activation.sql / V6__visit.sql —
-- their Flyway checksums are live.
-- Conventions: same as prior migrations (TIMESTAMPTZ UTC, integer cents, VARCHAR enums).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Remove ESSENTIAL from the catalog. Children first (plan_tier_service has an
-- ON DELETE RESTRICT FK to plan_tier), then the plan_tier row itself.
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM plan_tier_service
WHERE plan_tier_id = (SELECT id FROM plan_tier WHERE code = 'ESSENTIAL');

DELETE FROM plan_tier
WHERE code = 'ESSENTIAL';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Re-tier the four ESSENTIAL visit templates (months 1, 4, 7, 10 — Winter check,
-- Spring readiness, Summer systems, Fall winterization) up to COMPLETE, the new base
-- tier. The unique index on (month, min_tier) has no collision: COMPLETE currently
-- only owns months 3, 6, 9, 11.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE visit_template
SET min_tier = 'COMPLETE'
WHERE min_tier = 'ESSENTIAL';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Replace the CHECK constraints that enumerated ESSENTIAL with COMPLETE/PREMIER
-- only. Constraint names below are Postgres's default auto-generated names for an
-- unnamed, single-column inline CHECK (<table>_<column>_check) — the only CHECK on
-- each of these columns, so no numeric suffix.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE plan_tier
    DROP CONSTRAINT plan_tier_code_check;
ALTER TABLE plan_tier
    ADD CONSTRAINT plan_tier_code_check CHECK (code IN ('COMPLETE', 'PREMIER'));

ALTER TABLE visit_template
    DROP CONSTRAINT visit_template_min_tier_check;
ALTER TABLE visit_template
    ADD CONSTRAINT visit_template_min_tier_check CHECK (min_tier IN ('COMPLETE', 'PREMIER'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Reprice COMPLETE: $169/mo (16900 cents) / $1,690/yr (169000 cents), per the
-- repositioned docs/pricing-and-visits.md. Clear the old $149 Stripe price ids to
-- NULL — they point at the retired price. Checkout must fail closed (409
-- PLAN_NOT_PURCHASABLE) rather than silently charge the old amount until the founder
-- creates the new Stripe prices and fills these columns back in. Premier is untouched.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE plan_tier
SET monthly_price_cents = 16900,
    annual_price_cents = 169000,
    stripe_price_id_monthly = NULL,
    stripe_price_id_annual = NULL
WHERE code = 'COMPLETE';

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Delete the founding-rate concept. Not deprecated — gone: no more founding price
-- per tier, and no more per-subscriber founding flag/expiry or its index.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE plan_tier DROP COLUMN founding_monthly_price_cents;
ALTER TABLE plan_tier DROP COLUMN stripe_price_id_founding;

DROP INDEX IF EXISTS idx_subscriber_founding;
ALTER TABLE subscriber DROP COLUMN founding_rate;
ALTER TABLE subscriber DROP COLUMN founding_rate_expires_at;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Drop the dead subscriber.paused_until column. Nothing in the application reads
-- or writes it — pause/resume state lives in subscriber.status plus Stripe, not a
-- paused-until timestamp.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE subscriber DROP COLUMN paused_until;
