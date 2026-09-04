-- HomeKept — wire the live Stripe Price IDs into the catalog.
--
-- WHEN: run this once against the PRODUCTION database, AFTER you have created the
-- live Complete Products/Prices in the Stripe Dashboard (go-live checklist, step 3).
--
-- IMPORTANT — run this AFTER the V11 build is deployed. Running it before means V11's
-- migration nulls the ids right back out (V11__remove_essential_and_founding.sql clears
-- plan_tier.stripe_price_id_monthly / stripe_price_id_annual for COMPLETE on every deploy
-- that includes it, since it hasn't run yet on this database).
--
-- SCOPE: COMPLETE only. Premier's live Stripe price ids are already in production and
-- unchanged by the repositioning — do not touch the PREMIER row.
--
-- HOW: create the two recurring Stripe Prices to match the amounts in the comments below,
-- copy their `price_…` ids into the matching slots, then run this file
-- (e.g. `psql "$DB_URL" -f docs/stripe-price-ids.sql`) and review the SELECT
-- output before you COMMIT.
--
-- The amounts are the seeded COMPLETE prices as of V11__remove_essential_and_founding.sql,
-- which is now the price authority for COMPLETE (not V2__catalog.sql — V11 repriced it as
-- part of the Sep 2026 repositioning). DO NOT change the amounts here — this script only
-- records which Stripe Price maps to which billing cycle.
--
--   COMPLETE   $169.00/mo  (16900)  ·  $1,690.00/yr (169000)
--
-- You will create 2 Stripe Prices total. Both are recurring subscription prices.

BEGIN;

UPDATE plan_tier SET
    stripe_price_id_monthly  = 'price_REPLACE_complete_monthly',   -- $169/mo  (16900)
    stripe_price_id_annual   = 'price_REPLACE_complete_annual'     -- $1690/yr (169000)
WHERE code = 'COMPLETE';

-- Sanity check — review this before committing:
--   * every stripe_price_id_* is a real `price_…` id (no REPLACE left) for COMPLETE,
--   * PREMIER's ids are untouched (whatever was already live in production),
--   * the *_price_cents columns are unchanged.
SELECT code,
       monthly_price_cents, annual_price_cents,
       stripe_price_id_monthly, stripe_price_id_annual
FROM plan_tier
ORDER BY code;

-- If the SELECT above is correct, commit. Otherwise `ROLLBACK;` and fix the ids.
-- COMMIT;
