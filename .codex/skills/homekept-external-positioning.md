# HomeKept external positioning

> **When to use this:** Open this when writing marketing copy, a landing page, an
> email/SMS campaign, a testimonial, or any outward-facing claim, or when deciding
> whether a number can be stated publicly ("can we say...") — covers the
> advertising-law rails (Competition Act, CASL), what is genuinely novel vs
> commodity (positioning, differentiator), what must be proven before it is
> claimed, and the sanctioned social-proof mechanism, ahead of any launch
> messaging.

Everything outward-facing sits under advertising and anti-spam law, and under
the same anti-fabrication rule as the product. Get this wrong and it is a legal
problem, not a style nit. Verified 2026-07-06. Sources of record:
`docs/marketing-plan.md`, `docs/marketing-videos.md`, and the scope rails in
`docs/pricing-and-visits.md`.

## The claims rails (non-negotiable)

- **Competition Act (misleading advertising): no fabricated social proof,
  anywhere, ever.** No invented customer counts, ratings, testimonials, or
  metrics. This is the same rule as product non-negotiable #7 and is why the
  de-fabrication wave existed. If a number is not real and sourced, it does not
  appear in public copy.
- **Testimonials must be real and disclosed.** The sanctioned social-proof
  mechanism is the **founding-member testimonial** (exact terms, first 15
  customers at the founding rate for a disclosed testimonial, live once in
  `.codex/skills/homekept-domain-reference.md`). **Never a Google review**:
  incentivized or gated reviews violate Google policy and Competition Act
  guidance. Do not build a flow that trades a discount for a review.
- **CASL (email/SMS):** every marketing message carries sender identification +
  an unsubscribe path. Booking consent is captured server-side; outreach to
  realtors and property managers needs its own consent basis.
- **No contracts at the door.** Door-knocking invites people to book online only;
  it never closes a sale on the doorstep (see
  `.codex/skills/homekept-domain-reference.md`).
- **Trades-safe language.** Public copy uses "inspect / observe / refer", never
  language implying licensed-trade work or a certified inspection.

## Novel vs commodity (what is actually differentiated)

Say what is true and specific; avoid category clichés.

- **Genuinely differentiated:** a *subscription* that does preventive seasonal
  maintenance on a schedule; a **same-day photo report** per visit; the **Home
  Health Score** that trends a home's condition over time; a **trades-safe
  referral model** (catch early, refer to a vetted partner at zero markup, track
  the fix). These are real, shipped or specced.
- **Commodity (do not lean on):** "handyman", "we fix things", generic trust
  claims. The competition is a one-off handyman call; the wedge is
  *continuity + transparency*, aimed at non-technical owners (homeowners,
  landlords, PMs).

## Prove before you claim

A public claim about outcomes or scale must trace to real data:

- **Metrics** (customers served, satisfaction, savings) — only if real and
  sourced. Until there is a first cohort, do not state cohort numbers.
- **Capability claims** — only for what is built. The product degrades
  integrations gracefully; "we send you a report" is only true once SendGrid/R2
  are wired and verified (see `.codex/skills/homekept-go-live-campaign.md`).
- **Savings/ROI** — frame as illustrative and label it, never as a measured
  average you cannot back.

The reproducibility standard: if a reviewer asked "where does this number come
from", you must be able to point at the real source. If you cannot, cut it.

## When NOT to use this / open a sibling instead

- Internal docs and PR/commit writing → `.codex/skills/homekept-docs-and-writing.md`.
- The business facts behind a claim (tiers, scope, materials) →
  `.codex/skills/homekept-domain-reference.md`.
- In-product copy rules (em dashes, empty states, contrast) →
  `.codex/skills/homekept-docs-and-writing.md` +
  `.codex/skills/homekept-change-control.md`.
- Where the product could actually advance beyond competitors →
  `.codex/skills/homekept-research-and-methodology.md`.

## Provenance and maintenance

Verified 2026-07-06. Re-verify:

- Marketing strategy + channels: `docs/marketing-plan.md`, `docs/marketing-videos.md`
- Legal rails (Competition Act, CASL, CPA): `docs/pricing-and-visits.md`
  "Scope & safety rails"
- Founding-member testimonial terms: `docs/pricing-and-visits.md` Tiers section
