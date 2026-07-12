---
name: homekept-domain-reference
description: >-
  The home-maintenance business knowledge a mid-level engineer lacks: the tiers,
  the 12-month visit calendar, the picks menu, materials policy, unit economics,
  and the legal scope-and-safety rails that constrain what the product may
  claim or do. Load this when a task involves plans, pricing, visits, picks,
  what a technician is and is not allowed to do, materials/receipts, or any
  copy/feature that could touch licensed-trade or advertising law. Triggers:
  tiers, Essential/Complete/Premier, founding rate, picks, visit calendar,
  seasonal, scope rails, TSSA/ESA, not a certified inspection, materials at
  cost, unit economics, Competition Act, CASL, CPA cooling-off.
---

# HomeKept domain reference

HomeKept sells a **subscription** that keeps a home maintained: scheduled
seasonal visits, a same-day photo report, and a Home Health Score. It is
explicitly **not** a licensed trade. Understanding these rules prevents building
features or writing copy that are illegal, off-strategy, or unbuildable.

**Source of record:** `docs/pricing-and-visits.md` (the commercial spec) is
authoritative and founder-owned. The numbers below are verified against it on
2026-07-06. **Pricing numbers are a hand-write/founder boundary — never change
them here or in code without the founder** (see `homekept-change-control`).

## Positioning in one line

"Essential keeps the house safe; Complete keeps water out of your basement and
leaves out of your gutters; Premier is a house manager." The buyer is often not
technical (homeowners, landlords, property managers), so the product must be
legible at a glance.

## Tiers (verified 2026-07-06 — defer to the spec)

| | Essential | Complete ★ | Premier |
|---|---|---|---|
| Price | $89/mo · $890/yr | $149/mo · $1,490/yr | $249/mo · $2,490/yr |
| Visits/yr | 4 (seasonal anchors) | 8 (anchors + 4 mid-season) | 12 (monthly) |
| Included picks/yr | 1 (Basic/Medium) | 3 (max 1 Premium) | 6 (max 3 Premium) |
| Your-list/visit | ~20 min | ~20 min | up to 1 hr incl. minor repairs |
| Repairs | Quote/refer | Quote/refer | ≤1 hr labor/visit incl., parts at cost |

- **Annual = 2 months free.** Prices are **CAD + HST**.
- **Founding-member rate:** first **15** customers get **Complete at $129/mo
  locked 12 months**, in exchange for a **disclosed testimonial** — *never a
  Google review* (incentivized reviews violate Google policy and Competition Act
  guidance). This is why the admin console tracks "founding-rate slots
  remaining" out of 15.
- **Never discount the public list price.** (The founding rate is the only
  sanctioned discount.)
- The frontend mirrors these in `frontend/src/lib/plans.ts`; the backend serves
  them from `/api/catalog/plans`. Both must match the spec.

## What every visit contains (every tier)

1. **Standing items (~20 min):** filter check/swap; smoke & CO test + batteries;
   mechanicals walkaround (furnace, water heater, panel, under sinks — eyes
   only); humidity reading.
2. **Seasonal focus (~40–50 min):** the month's named work (calendar below).
3. **Your list (~20 min):** customer to-dos queued in the app.
4. **Flagged follow-ups** from prior reports.
5. **Same-day photo report + Home Health Score update**, with receipts for
   at-cost materials.

## The 12-month visit calendar

E = Essential (4 anchor visits) · C adds 4 (=8) · P adds 4 more (=12). Anchors
are **Apr** (Spring readiness), **Jul** (Summer systems), **Oct** (Fall
winterization), and **Jan** (Winter check). The named seasonal focus per month
lives in `docs/pricing-and-visits.md#visit-calendar` — consult it before
building anything that renders or schedules visit content, so the names and
scope match exactly.

## Picks menu

À la carte price bands, also the included-allowance currency: **Basic $49 ·
Medium $89 · Premium $149**. Catalog services carry a `tier_class` of
`BASIC`/`MEDIUM`/`PREMIUM`. Picks fold into scheduled visits where possible;
beyond the allowance they become à la carte (`EXTRA` visit type). **The
included-picks allowance resets on the subscription anniversary, not the
calendar year.** Served from `/api/catalog/picks`.

## Scope & safety rails — the legal boundary (read before any feature or copy)

These are not guidelines; crossing them is a legal problem. The whole product is
built to stay inside them.

- **No licensed-trade work.** No gas (TSSA), electrical panels/hardwired
  fixtures (ESA), plumbing repairs, roof walking, renovations, appliance repair,
  pest, or landscaping/snow. HomeKept **inspects, catches early, refers to a
  vetted partner, and tracks that it got fixed.** The partner bills the customer
  directly, zero markup.
- **Trades-safe wording** is mandatory in copy and reports: furnace = filter +
  visual inspection + performance observation (the gas tune-up is the partner's);
  detectors = test + battery (hardwired replacement is referred); water heater =
  flush/visual/temp only.
- **Water-heater skip-rule:** tank ≤ ~8 yrs and drain valve sound → flush;
  otherwise visual + temp only and recommend assessment.
- **Inspection items use binary observable criteria** ("crack wider than a
  nickel / horizontal / stair-step → photograph & flag"), never subjective
  judgment. Every report states: *visual, non-invasive observations — not a
  certified inspection.* Do not let copy or a feature imply a certified
  inspection.
- **No fabricated social proof (Competition Act).** No invented customer counts,
  ratings, or testimonials — anywhere, ever. Real disclosed testimonials only.
  This is the legal spine behind non-negotiable #7 in `homekept-change-control`
  and the whole de-fabrication effort.
- **CASL** (email/SMS): sender identification + unsubscribe on every marketing
  message; realtor/PM outreach needs its own consent basis.
- **CPA** (consumer protection): subscription terms carry future-performance
  disclosures + cancellation rights incl. the 10-day cooling-off where
  applicable; auto-renewal gets advance notice. No contracts at the door —
  door-knocking invites people to book online only.
- **Before customer #1** (founder items): CGL insurance, working-at-heights
  training ×2, terms + privacy policy (#41), fixed-fee lawyer review.

## Materials policy

- **Included** (checklist consumables): 1″ filters ≤ MERV 11, batteries,
  standard humidifier pads, touch-up caulk/lube/fasteners/weatherstripping.
- **At cost, zero markup, receipt in the report:** media/HEPA filters, specialty
  bulbs, smart devices, Premier repair parts. **No parts fees.**
- The walk-through captures the home's SKU sheet (filter sizes/counts, detector
  models, humidifier model, water-heater age) onto the property record.
- Expected materials cost/customer-year ≈ **$70 / $120 / $180** by tier.

## Unit economics (why margins are what they are)

Fully-loaded labor **$43/hr**, ~2h/visit all-in. Revenue/yr ≈ $1,068 / $1,788 /
$2,988; gross margin ≈ **58% / 55% / 50%** (worse when picks/repairs are used
heavily). Capacity: one full-time technician ≈ **80 visits/mo ≈ ~120 Complete
subscribers ≈ ~$18K MRR**. This is why ops efficiency (routing, utilization) is
a real frontier — see `homekept-research-and-methodology`.

## When NOT to use this skill / use a sibling instead

- The enforceable non-negotiables and review gates → `homekept-change-control`.
- Where domain logic lives in code (packages, entities, state machines) →
  `homekept-architecture-contract`.
- Marketing claims and what may be said publicly → `homekept-external-positioning`.
- Phase timing (when a capability is allowed to be built) → read
  `docs/three-year-plan.md`.

## Provenance and maintenance

Numbers verified 2026-07-06 against `docs/pricing-and-visits.md`. That doc is the
authority; this skill is a fast index. Re-verify:

- Tiers/picks/calendar/rails: `docs/pricing-and-visits.md`
- Frontend mirror of plans: `frontend/src/lib/plans.ts`
- Backend catalog: `GET /api/catalog/plans`, `GET /api/catalog/picks` (see
  `backend/src/main/java/com/homekept/catalog/`)
- Phase timing: `docs/three-year-plan.md`
