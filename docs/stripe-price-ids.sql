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
-- Founding applies to COMPLETE only (ESSENTIAL / PREMIER have no founding rate).
--
--   ESSENTIAL   $89.00/mo   (8900)   ·  $890.00/yr   (89000)    ·  founding: none
--   COMPLETE   $149.00/mo  (14900)  ·  $1,490.00/yr (149000)   ·  founding $129.00/mo (12900)
--   PREMIER    $249.00/mo  (24900)  ·  $2,490.00/yr (249000)   ·  founding: none
--
-- You will create 7 Stripe Prices total (2 + 3 + 2). All are recurring
-- subscription prices; the founding one is a separate monthly price on COMPLETE.

BEGIN;

UPDATE plan_tier SET
    stripe_price_id_monthly = 'price_REPLACE_essential_monthly',   -- $89/mo   (8900)
    stripe_price_id_annual  = 'price_REPLACE_essential_annual'     -- $890/yr  (89000)
WHERE code = 'ESSENTIAL';

UPDATE plan_tier SET
    stripe_price_id_monthly  = 'price_REPLACE_complete_monthly',   -- $149/mo  (14900)
    stripe_price_id_annual   = 'price_REPLACE_complete_annual',    -- $1490/yr (149000)
    stripe_price_id_founding = 'price_REPLACE_complete_founding'   -- $129/mo founding (12900)
WHERE code = 'COMPLETE';

UPDATE plan_tier SET
    stripe_price_id_monthly = 'price_REPLACE_premier_monthly',     -- $249/mo  (24900)
    stripe_price_id_annual  = 'price_REPLACE_premier_annual'       -- $2490/yr (249000)
WHERE code = 'PREMIER';

-- Sanity check — review this before committing:
--   * every non-founding stripe_price_id_* is a real `price_…` id (no REPLACE left),
--   * only COMPLETE has a stripe_price_id_founding (the other two are NULL),
--   * the *_price_cents columns are unchanged.
SELECT code,
       monthly_price_cents, annual_price_cents, founding_monthly_price_cents,
       stripe_price_id_monthly, stripe_price_id_annual, stripe_price_id_founding
FROM plan_tier
ORDER BY code;

-- If the SELECT above is correct, commit. Otherwise `ROLLBACK;` and fix the ids.
-- COMMIT;
