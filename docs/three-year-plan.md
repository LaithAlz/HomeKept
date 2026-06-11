# HomeKept · Three-Year Business & Technology Plan

Companion to `pricing-and-visits.md` (commercial spec) and
`../backend/homekept-backend-architecture.md` (tech architecture). Like the architecture
doc, this plan is anchored to **customer counts and triggers, not dates**. The months are
estimates; the gates are the plan. If growth runs faster or slower, slide the calendar,
never skip a gate.

## Operating principles

1. **Bootstrap.** Breakeven is ~5 customers; the business funds itself. No raise unless
   a deliberate multi-metro land-grab decision is made at Phase 4 — and probably not then.
2. **Density before breadth.** A new customer 10 minutes from three existing ones is
   worth ~2× a customer 40 minutes away. Saturate neighbourhoods; expand contiguously.
3. **The founders stay in the field** until the gates say otherwise. The product is
   built by people who stand in crawlspaces.
4. **Build only what the current stage demands** (architecture doc, Part 9). This plan
   inherits that table wholesale.

## The phases at a glance

| Phase | Customers | ~When | Business theme | Tech theme |
|---|---|---|---|---|
| 0 — Build & launch | 0 → 5 | M0–M3 | Founding members, SOPs, legal rails | MVP (issues #1–45) |
| 1 — Proof | 5 → 30 | M3–M12 | One neighbourhood, referrals, retention proof | Operate & polish |
| 2 — Repeatability | 30 → 100 | M12–M24 | Second/third area, sales hire, founder pay | Automation (Stage 2) |
| 3 — Workforce | 100 → 200 | M24–M30 | Tech hires #1–2, COO off tools | Technician platform (Stage 3) |
| 4 — Scale | 200 → 400+ | M30–M36 | GTA-West brand, partnerships engine | Multi-area & self-serve (Stage 4) |

---

## Phase 0 — Build & launch (0 → 5 customers, ~M0–M3)

**Business**
- Incorporate; shareholders' agreement w/ 4yr/1yr vesting, role split, casting votes,
  reserved matters; $5K capital each
- Legal rails: CGL insurance, working-at-heights ×2, terms + privacy (#41), lawyer review
- COO deliverable #1: field SOP set (yellow-task protocols, skip-rules, photo criteria,
  van kit list) — this is the gate for the equity story being real
- Apprenticeship: every walk-through and visit done as a pair; CEO reaches solo-competent
  on the Essential checklist
- Trade partners signed: TSSA gas, electrician, plumber, roofer (referral, no markup)
- First 5 founding members ($129 Complete, 12-mo lock): friends-of-friends, own street,
  one Nextdoor neighbourhood. Pick ONE FSA and stay in it.

**Tech** — the existing 8-week issue plan (#1–45), unchanged:
- Spring monolith: identity, property, catalog, subscription, booking, visit,
  notification; 3 state machines; Stripe Checkout + webhooks; SendGrid
- Frontend: v2 design system (done — #48/#49), booking wizard port, activation flow,
  customer dashboard on real data, minimal admin
- Deploy: Render + Cloudflare + Sentry + UptimeRobot (#12)
- Walk-through capture includes the SKU sheet (filter sizes, detector models, water
  heater age) → property record

**Exit gate:** 5 paying, insured, SOPs written, both founders solo-capable, deploy live.

---

## Phase 1 — Proof (5 → 30 customers, ~M3–M12)

The only question this phase answers: **do people stay?** Target ≤2%/mo churn after
month 3 of membership. If churn is >4%/mo, stop growing and fix the product/cadence —
nothing else in this plan matters until retention is proven.

**Business**
- Funnel: walk-through volume 6–10/mo, ≥40% close. All founder-led: Nextdoor, FB groups,
  door-hanger invites (book online — never sign at the door), referral ask built into
  month-3 photo report
- Founding rate retired at 15; public pricing from #16
- Realtor seed: 3–5 agents get a "first 60 days on us for your buyers" offer — pipeline
  for Phase 2 partnerships
- Track from day 1: lead source (already in schema), close rate, churn, NPS-ish
  ("would you recommend?" in the app after each report), per-visit material cost
- Year-end: ~30–35 customers, ~$4.5K MRR, ~$30–35K cumulative revenue, cash-positive

**Tech** (operate & polish; arch doc Stage 2 begins)
- Visit photo uploads to R2 (signed URLs) — the report IS the product; this is the
  retention feature
- Async email (@Async), scheduled reminder jobs, Stripe nightly reconciliation
- Admin: visit scheduling + assignment screens replace spreadsheet ops
- Google Places autocomplete on booking; audit logging on admin mutations
- Customer self-serve: pause, plan change via Stripe portal, request EXTRA visit
- "Your list" v1: customer to-do queue on the dashboard, folded into visit checklists
- Home Health Score v1: simple rubric computed from checklist outcomes (not ML — a
  weighted checklist)

**Exit gate:** 30 customers · ≤2%/mo churn (m3+) · ≥40% walk-through close ·
referrals ≥25% of new leads.

---

## Phase 2 — Repeatability (30 → 100 customers, ~M12–M24)

The question: **does the machine work without founder heroics?**

**Business**
- Commission sales hire at ~30–40 customers (the pre-agreed trigger): owns lead gen +
  walk-through booking; founders still run the walk-throughs (the tech IS the close)
- Expand to contiguous FSAs only (Oakville ring → Mississauga-west or Milton, not both)
- Partnerships engine: realtors (closing-gift memberships), property managers (small
  portfolios = multi-property), insurance brokers (referral)
- Founder salaries start when MRR > ~$8K (≈M18): modest and equal
- Pricing review at customer 50 with a year of real cost data; grandfather honestly
- Winter readiness: capacity plan for Oct–Nov gutter/winterization crunch (the seasonal
  calendar concentrates demand — pre-book anchor visits 6 weeks out)
- End state: ~100 customers, ~$15K MRR, two founders ≈ 65–70 visits/mo (manageable but
  near the line — which is the point: Phase 3's hire is forced by arithmetic)

**Tech** (arch doc Stage 2 completed, Stage 3 foundations)
- "Suggest next visit" scheduling assistant (cadence + season + last visit)
- Jobrunr for durable jobs; webhook processing moved async (event table → worker)
- Bucket4j rate limiting; SendGrid bounce webhooks
- Reporting: MRR/churn/cohort dashboard for the admin (read replica NOT yet — Postgres
  is fine)
- Mobile-first pass on technician views (founders are the users; build what the field
  actually needs — this is why founders stay on tools)
- Materials cost per visit becomes a real column; unit-economics report per
  subscriber/tier

**Exit gate:** 100 customers · churn still ≤2%/mo · sales hire produces ≥50% of new
walk-throughs · founders' visit load ≥60/mo (the forcing function).

---

## Phase 3 — Workforce (100 → 200 customers, ~M24–M30)

The question: **does service quality survive employees?** This is the hardest phase —
the arch doc calls it the "major operational shift," and most service businesses die here.

**Business**
- Technician #1 hired at ~$15K MRR: employee (never contractor), WSIB, trained by the
  COO through the apprenticeship program already proven on the CEO. Profile: meticulous
  + great with people + takes good photos > 20 years of habits
- COO comes off daily tools → training, QA (report audits), dispatch
- CEO visit cadence drops to ~1 day/wk (stays in the field on principle)
- Technician #2 at ~160–180 customers; routes split by FSA cluster
- Quality system: post-visit report audit sampling, customer thumbs-up/down per visit,
  technician scorecards — COO-owned
- Premier "dedicated tech" promise now has real assignment constraints — honor it in
  dispatch before it's automated
- ~200 customers ≈ $30K MRR; gross margin watch: blended target ≥50% with paid labor

**Tech** (arch doc Stage 3)
- `technician` domain built fully: roster, regions (FSA priority), availability
- Auto-assignment engine v1 (three-phase filter/rank; admin confirms)
- Technician PWA at /tech (the v2 mockup, now real): day sheet, checklist, photo upload,
  your-list items, completion flow
- FCM push notifications (visit reminders, day-sheet changes)
- Health Score v2 + customer-facing trends
- Payroll/cost integration: fully-loaded hourly cost per tech feeds margin reporting
- Hardening: SLO dashboards, webhook dead-letter queue, quarterly backup-restore drill

**Exit gate:** 200 customers · churn ≤2.5%/mo through the tech transition · report
quality audit ≥95% pass · blended gross margin ≥50%.

---

## Phase 4 — Scale (200 → 400+ customers, ~M30–M36)

The question: **is this a GTA-West institution or a lifestyle business?** (Both are
wins — decide deliberately.)

**Business**
- Geography: fill Oakville/Mississauga/Milton before *any* new metro. 400 customers in
  three towns beats 400 across the GTA
- Team: techs #3–4, part-time admin/dispatcher; org is COO→field, CEO→product+growth
- Referral program formalized (give-a-month/get-a-month) with attribution
- Multi-property subscribers (landlords, the property-manager pipeline)
- Year-3 exit state: ~300–400 customers, $45–60K MRR ($550–700K ARR), team of 6–7,
  founders on full salaries, profitable
- The Phase-4 decision (make it explicitly, in writing): (a) deepen — push to 800–1,000
  in GTA-West, stay private and profitable; (b) replicate — second metro with a hired
  city lead (only if playbook + training program are demonstrably transferable); or
  (c) raise to accelerate. Default: (a). Revisit only with 12 months of Phase-3 data.

**Tech** (arch doc Stage 4)
- Route optimization within auto-assignment (drive-time aware day sheets)
- Customer self-service completeness: reschedule, pick selection, referral tracking
- Webhook/event pipeline fully async w/ DLQ; OpenAPI docs; performance budgets
- Read replica for analytics; partition `visit` by year when >10K rows/yr
- Multi-property data model exercised for real
- Platform extraction question (arch doc Stage 5) gets asked honestly — expected
  answer: still no

---

## Financial trajectory (planning numbers, not promises)

| Milestone | ~Month | Customers | MRR | Team | Notes |
|---|---|---|---|---|---|
| Launch | M2 | 1 | $0.1K | 2 founders | first founding member |
| Breakeven (operating) | M3–4 | 5 | $0.6K | 2 | burn covered |
| Proof | M12 | 30–35 | $4.5–5K | 2 | retention proven |
| Founder salaries | ~M18 | 60 | $9K | 2 + sales (commission) | modest, equal |
| First employee | ~M22 | 100 | $15K | 3.5 | tech #1 |
| Workforce proven | M30 | 200 | $30K | 5 | techs #1–2 + admin PT |
| Year 3 | M36 | 300–400 | $45–60K | 6–7 | profitable, decision point |

Cash: $10K founder float at M0; no external capital in the base plan.

## Standing risks & pre-agreed responses

| Risk | Signal | Response |
|---|---|---|
| Churn (the existential one) | >4%/mo any quarter | Freeze growth spend; cadence/report-quality fix sprint; founder calls every cancellation |
| Boring-month perception | "What am I paying for" in cancels | Your-list emphasis, report richness, visit-content audit |
| Field incident / damage claim | Any | Insurance + SOP review; skip-rules tightened; incident log feeds training |
| Founder unit economics were wrong | Blended margin <45% with paid labor | Reprice at renewal (grandfathered), cadence trim, route density push |
| Key-person (either founder) | — | Cross-training rule (both can run visits; deploy runbook documented); key-person clauses in agreement |
| Winter demand spike | Oct–Nov overload | Pre-booking 6 wks out; seasonal part-time helper before full hire |
| Regulatory (CPA/TSSA) | Complaint or rule change | Lawyer on retainer relationship from Phase 0; scope rails in spec are the defense |

## What this plan deliberately does NOT include

Franchising, raising VC in years 1–3, a consumer marketplace, adjacent verticals
(cleaning, lawn, snow), white-labeling the software, and any second metro before
GTA-West saturation. Each of these is a Phase-4+ conversation that requires the Phase-3
data. The architecture doc's Part 9 table ("what you're NOT building") applies to the
business exactly as it does to the backend.
