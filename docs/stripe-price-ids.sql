-- HomeKept — wire the live Stripe Price IDs into the catalog.
--
-- WHEN: run this once against the PRODUCTION database, AFTER you have created the
-- live Products/Prices in the Stripe Dashboard (go-live checklist, step 4).
--
-- HOW: create each recurring Stripe Price to match the amount in the comment,
-- copy its `price_…` id into the matching slot below, then run this file
-- (e.g. `psql "$DB_URL" -f docs/stripe-price-ids.sql`) and review the SELECT
-- output before you COMMIT.
--
-- The amounts are the seeded plan prices (from V2__catalog.sql, which is the
-- source of truth in docs/pricing-and-visits.md). DO NOT change them here — this
-- script only records which Stripe Price maps to which plan + billing cycle.
--
--   COMPLETE   $169.00/mo  (16900)  ·  $1,690.00/yr (169000)
--   PREMIER    $249.00/mo  (24900)  ·  $2,490.00/yr (249000)
--
-- You will create 4 Stripe Prices total (2 + 2). All are recurring subscription prices.

BEGIN;

UPDATE plan_tier SET
    stripe_price_id_monthly  = 'price_REPLACE_complete_monthly',   -- $169/mo  (16900)
    stripe_price_id_annual   = 'price_REPLACE_complete_annual'     -- $1690/yr (169000)
WHERE code = 'COMPLETE';

UPDATE plan_tier SET
    stripe_price_id_monthly = 'price_REPLACE_premier_monthly',     -- $249/mo  (24900)
    stripe_price_id_annual  = 'price_REPLACE_premier_annual'       -- $2490/yr (249000)
WHERE code = 'PREMIER';

-- Sanity check — review this before committing:
--   * every stripe_price_id_* is a real `price_…` id (no REPLACE left),
--   * the *_price_cents columns are unchanged.
SELECT code,
       monthly_price_cents, annual_price_cents,
       stripe_price_id_monthly, stripe_price_id_annual
FROM plan_tier
ORDER BY code;

-- If the SELECT above is correct, commit. Otherwise `ROLLBACK;` and fix the ids.
-- COMMIT;
