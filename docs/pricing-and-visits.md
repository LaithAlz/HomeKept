# HomeKept · Pricing, Tiers & Service Spec (v1)

The canonical commercial spec. Changes here ripple to: architecture doc (catalog/plans),
api-contract.md (plan payloads), catalog seed data, mockups, landing/plans pages.

## Positioning

Proactive home care, not breakdown insurance. The local anchor is Reliance/Enercare
($10–67/mo per-appliance *repair insurance*): they show up after the furnace dies; we're
why it doesn't — and nobody at Reliance clears a gutter or sends a photo of your roof.
The closest successful comp (Honey Homes, California — $167–396 USD/mo equivalent,
dedicated handyman, annual billing) proves the premium membership model works; we price
below them and above the insurance incumbents.

**The product is trained eyes, a disciplined checklist, and a camera — on a schedule.**
Technicians observe → photograph → flag → refer. Never diagnose, never licensed-trade work.

## Tiers

| | 🌱 Essential | 🏡 Complete ★ Recommended | 🔑 Premier |
|---|---|---|---|
| Price | $89/mo · $890/yr | $149/mo · $1,490/yr | $249/mo · $2,490/yr |
| Visits/year | 4 (seasonal anchors) | 8 (anchors + 4 mid-season) | 12 (monthly, each named) |
| Included picks/yr | 1 (Basic/Medium) | 3 (max 1 Premium) | 6 (max 3 Premium) |
| Your-list time/visit | ~20 min | ~20 min | up to 1 hr incl. minor repairs |
| Technician | Consistent where possible | Consistent where possible | Dedicated — guaranteed same person |
| Scheduling | Standard (~2 wks) | Priority: issue visits in 48h + emergency line | Same-week + 24h emergency |
| Repairs | Quote/refer | Quote/refer | ≤1 hr labor/visit incl., parts at cost |
| Extras | — | Licensed gas tune-up coordination | Smart-home support · Annual Home Plan (5-yr capital forecast) |
| For | Townhomes, condos, newer homes | Most detached/semis | Larger homes, busy households, aging-in-place |

Annual = 2 months free. Prices CAD + HST. **Founding-member rate**: first 15 customers,
Complete at $129/mo locked 12 months, in exchange for a review + testimonial. Never
discount the public list.

## Every visit, every tier

1. **Standing items (~20 min):** filter check/swap · smoke & CO test + batteries ·
   mechanicals walkaround (furnace, water heater, panel, under sinks — eyes only) ·
   humidity reading
2. **Seasonal focus (~40–50 min):** the month's named work (calendar below)
3. **Your list (~20 min):** customer to-dos queued in the app
4. **Flagged follow-ups** from prior reports
5. **Same-day photo report + Home Health Score update**, incl. receipts for at-cost materials

## Visit calendar

E = Essential (4) · C = +Complete (8) · P = +Premier (12)

| Month | Visit | Seasonal focus | Tier |
|---|---|---|---|
| Jan | **Winter check** | Mid-season filter · water heater flush *(skip-rule below)* · attic peek for ice dams (joist-safe, photo) · humidity tune · detector sweep (peak CO season) | E |
| Feb | Deep-winter walkthrough | Condensation/draft check · basement moisture scan · tub/shower caulking · garage door tune | P |
| Mar | Thaw prep | Sump pump test & pit clean · melt drainage/grading check · foundation walkaround (binary criteria, photo) · floor drains | C |
| Apr | **Spring readiness** | Reconnect/test outdoor taps · AC startup observation · spring gutter clear · winter-damage walkaround from grade | E |
| May | Exterior tune | Deck/railing/fence hardware · screens · exterior caulking touch-points · irrigation/hose check | P |
| Jun | Summer prep | Full exterior caulking pass · AC condenser clean (power off, gentle) · bath fan clean · drainage recheck | C |
| Jul | **Summer systems** | AC performance check · under-sink/toilet/appliance leak inspection · dryer vent deep clean · washer hoses | E |
| Aug | Water systems | Water heater visual & temp · water pressure test · toilet internals · sump recheck | P |
| Sep | Pre-heating check | Filter & furnace visual/performance observation · humidifier pad · weatherstripping pass · book licensed gas tune-up if due | C |
| Oct | **Fall winterization** | Shut down & drain outdoor taps · humidifier service · weatherstripping/door sweeps · eaves check · detector sweep | E |
| Nov | Post-leaf gutters | Full gutter & downspout clear · roof-line visual (grade/eaves only) · downspout extensions | C |
| Dec | Holiday & safety | Detectors + extinguisher · dryer vent recheck · cord/space-heater walkaround · your-list catch-up | P |

Upgrade pitch: *Essential keeps the house safe; Complete keeps water out of your basement
and leaves out of your gutters; Premier is a house manager.*

## Picks menu

| ⚪ Basic — $49 à la carte | 🔵 Medium — $89 | 🟠 Premium — $149 |
|---|---|---|
| Extra filter visit | Extra water heater flush | Extra full gutter clear |
| Weatherstripping touch-up | Dryer vent deep clean | Roof & exterior inspection w/ report |
| Garage door tune & lube | Caulking refresh (one area) | Smart-home package install |
| Faucet/showerhead descale | Smart thermostat install | Pre-winter full-home inspection |
| Detector battery sweep | Toilet internals refresh | |

Picks fold into scheduled visits where possible. Beyond allowance = à la carte
(`EXTRA` visit type). Service catalog gains `tier_class` (BASIC/MEDIUM/PREMIUM).

## Scope & safety rails

- **No licensed-trade work:** gas (TSSA), electrical panels/hardwired fixtures (ESA),
  plumbing repairs, roof walking, renovations, appliance repair, pest, landscaping/snow.
  We inspect, catch it early, refer to a vetted partner, track that it got fixed.
  Partner bills the customer directly; zero markup.
- **Trades-safe wording:** furnace = filter, visual inspection, performance observation
  (gas tune-up is the partner's); detectors = test + battery (hardwired replacement
  referred); water heater = flush/visual/temp only.
- **Water heater skip-rule:** tank ≤ ~8 yrs and drain valve sound → flush; otherwise
  visual + temp only, recommend assessment.
- **Inspection items use binary observable criteria** ("crack wider than a nickel /
  horizontal / stair-step → photograph & flag"), never judgment. Reports state: visual,
  non-invasive observations — not a certified inspection.
- **No contracts at the door** (Ontario CPA door-to-door rules) — door-knocking invites
  people to book online only. Subscription terms carry CPA future-performance
  disclosures + cancellation rights; auto-renewal gets advance notice.
- Before customer #1: CGL insurance, working-at-heights training ×2, terms + privacy
  policy (#41), fixed-fee lawyer review.

## Materials

- **Included** (consumed-by-the-checklist): 1″ filters ≤ MERV 11, batteries, standard
  humidifier pads, touch-up caulk/lube/fasteners/weatherstripping.
- **At cost, zero markup, receipt in report:** media/HEPA filters, specialty bulbs,
  smart devices, Premier repair parts. No parts fees.
- Walk-through captures the home's SKU sheet (filter sizes/counts, detector models,
  humidifier model, water heater age) onto the property record.
- Just-in-time ordering per route — no inventory beyond a small van buffer. Materials
  logged per visit as COGS from visit #1.
- Expected materials cost/customer-year: ~$70 / $120 / $180 by tier.

## Unit economics ($43/hr fully-loaded labor, ~2h/visit all-in)

| | Essential | Complete | Premier |
|---|---|---|---|
| Revenue/yr | $1,068 | $1,788 | $2,988 |
| Direct cost/yr | ~$400 | ~$810 + picks | ~$1,310 + capped repair exposure |
| Gross margin | ~62% | ~55% (worst-case picks ~45%) | ~50% |

Capacity: one full-time tech ≈ 80 visits/mo ≈ ~120 Complete subscribers ≈ $18K MRR.

## Startup costs & cash plan (to 30 customers)

One-time: ~$4.5–6.7K — incorporation $300 · lawyer (shareholders' agreement + terms)
$1.5–2.5K · tools/ladders/PPE $1.5–2K · working-at-heights ×2 ~$350 · van kit + vehicle
magnets $200–400 · brand basics $400–600 · starter materials $300–500.

| Monthly | 0 cust | 10 cust | 30 cust |
|---|---|---|---|
| Infra/software | ~$55 | ~$55 | ~$60 |
| Insurance (CGL + vehicle rider) | ~$200 | ~$200 | ~$225 |
| Bookkeeping | ~$50 | ~$50 | ~$75 |
| Materials (~$15/visit) | $0 | ~$100 | ~$300 |
| Fuel (~$6/visit) | $0 | ~$40 | ~$120 |
| Marketing (flyers; organic $0) | ~$100 | ~$150 | ~$200 |
| Stripe (~3% rev) | $0 | ~$45 | ~$135 |
| **Burn** | **~$400** | **~$640** | **~$1,115** |
| **MRR** (mix 6E/20C/4P) | $0 | ~$1,500 | ~$4,510 |

Operating breakeven ≈ customer 4–5. Cash to launch ~$5–7K; cushion $10K ($5K per
founder). Materials are ~6.6% of revenue at 30 customers and billed-before-consumed —
labor is the only cost that ever scales against us, and it's founder sweat until Stage 3.

## Roles

**CEO (Laith):** product/software, brand, pricing, CX, money/admin — and weekly visits,
permanently at this stage. **COO:** field ops + SOPs + safety, training (CEO is trainee
#1 — first phase runs as a two-person apprenticeship), trade-partner network, suppliers,
scheduling, demand gen. Sales = commission hire at ~30 customers, not a founder.
Vesting 4yr/1yr cliff for both. Customer-facing title for both: "Co-founder."
