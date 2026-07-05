# Proposal: the landlord / property-manager portfolio

Status: **draft for founder sign-off.** Nothing here is built yet. Written 2026-07-05,
verified against the code at `main` (post design-system reskin).

## TL;DR

A landlord or property manager should log in once and see every home they look after,
sorted so the ones that need attention are on top, and drill into any one of them.

The good news from reading the code: **the data model already supports this.** One user can
already own many subscribers (one per property), each with its own property, plan, and Stripe
subscription. The "one property per customer" limit lives entirely in the application code, not
the schema. So this is mostly a code feature. **No hand-write SQL migration is strictly
required for the core** (one optional index for performance, and only if you want it).

What it does need from you is a few product/commercial decisions (below), not a database
rewrite.

## What exists today (verified)

- `users` — one row per person who signs in (roles: CUSTOMER / ADMIN / TECHNICIAN).
- `subscriber` — the subscription for one property. Columns include `user_id`
  (`NOT NULL REFERENCES users`, **no UNIQUE**), `property_id`, `plan_tier_id`, `status`,
  `billing_cycle`, `stripe_customer_id`, `stripe_subscription_id`, period dates.
- `property` — the physical home (address, type). 1:1 with a subscriber.
- So the real shape is **User 1:N Subscriber 1:1 Property**, but the code treats it as 1:1:1:
  - `SubscriberQueryService.findByUserId(userId)` returns a single `Optional<Subscriber>` and
    its javadoc says "each user has at most one active subscriber."
  - Every customer endpoint (`/api/app/visits`, `/health-score`, `/todos`,
    `/subscription`, `/account`) resolves that one subscriber from the JWT user id via a
    private `resolveSubscriberId(userId)` / `findByUserId(...).orElseThrow(404)`.
- Activation (`/api/activation/complete`) creates a fresh `User` + `Property` + `Subscriber`
  together, in one transaction.

## Decisions we need from you

1. **Billing model.** Each property is already its own subscriber = its own Stripe
   subscription. Recommended for v1: **keep it that way** (N independent subscriptions,
   N line items), because it reuses everything we have and matches how a landlord thinks about
   per-unit cost. Consolidated/one-invoice billing is a later, larger Stripe change. Do you
   agree with per-property billing for v1?

2. **How a landlord adds a property.** Options:
   a. **Self-serve (recommended):** an "Add a property" action in the portfolio that runs the
      existing walk-through → activation flow, but creates the new `Property` + `Subscriber`
      under the **existing** user (skip user creation). Minimal new work.
   b. Admin-only: the founder adds properties to an account. Simpler now, worse for scale.
   Which do you want for v1?

3. **Account identity.** For v1 the simplest model is: **a landlord is just a CUSTOMER user
   who happens to own more than one subscriber.** No new "organization/team" concept. That
   supports one login managing N properties. A true multi-user org (a PM firm with several
   staff logins over shared properties) would be a later, separate feature. OK to start with
   one-login-owns-many?

4. **Do we want the optional index?** A `CREATE INDEX ON subscriber (user_id)` speeds up the
   "list my properties" query. It's a one-line additive migration (safe, non-breaking). Since
   migrations are your hand-write boundary, this is the only DB change I'd propose, and only if
   you want it. Everything works without it at current scale.

## Proposed build (assuming per-property billing + self-serve add + CUSTOMER-owns-many)

### Backend

1. **Repository / query:** add `SubscriberRepository.findAllByUserId(userId)` and
   `SubscriberQueryService.findAllByUserId(...)`. Keep the existing single-subscriber method for
   backward compatibility (a one-property user is just N=1).

2. **Scope the per-property endpoints.** The existing `/api/app/*` endpoints implicitly use "the
   user's one subscriber." Give them an explicit, ownership-checked property context so the
   frontend can say which home it's viewing:
   - Add an optional `propertyId` (or `subscriberId`) query param to `GET /api/app/visits`,
     `/api/app/health-score`, `/api/app/todos`, `/api/app/subscription`, `/api/app/account`.
   - Resolution rule: if `propertyId` is given, verify it belongs to the authenticated user
     (ownership failure → **404**, per the existing rule) and scope to it; if omitted and the
     user has exactly one property, default to it (fully backward compatible with today's app);
     if omitted and the user has many, return a 400 asking the client to pick, or default to a
     "primary" property (your call, small).

3. **New portfolio endpoint:** `GET /api/app/properties` → a list, one row per property the user
   owns, each with the summary the portfolio grid needs, composed from existing services:
   `{ propertyId, subscriberId, address, status, planCode, healthScore, nextVisitDate,
   openItemsCount, needsAttention }`. This is pure composition over services we already have
   (health-score, visits, subscription), so no new data, no fabrication.

4. **Add-a-property flow (if self-serve):** a variant of activation/booking that attaches a new
   `Property` + `Subscriber` to the current user instead of creating a user. Reuses the
   walk-through → checkout machinery. This is the one genuinely new flow.

5. **Contract + tests:** update `api-contract.md`; Testcontainers tests for multi-subscriber
   ownership scoping (a user sees only their own properties; cross-user `propertyId` → 404).

**Migration:** none required for the core. Optional: the `subscriber(user_id)` index above.

### Frontend

1. **Property switcher** in the top bar (already in the mockups): lists the user's properties
   from `GET /api/app/properties`, sets the selected `propertyId` (in the URL / a small context)
   that the per-property pages pass to their queries.
2. **Portfolio view** (`/app/portfolio` or the default landing when a user has >1 property): the
   grid from the mockup, real data from `GET /api/app/properties`, sorted attention-first, each
   card linking into that property's dashboard.
3. The existing customer pages become property-scoped (they already are, implicitly; they'd read
   the selected `propertyId`).

## Phasing

- **Phase 1 (mostly code, no migration):** `findAllByUserId`, endpoint scoping, `GET
  /api/app/properties`, the property switcher, and the portfolio grid. A landlord whose account
  already has multiple subscribers can now see and navigate them. This is the visible "edge."
- **Phase 2:** self-serve "Add a property" flow.
- **Phase 3 (later, larger):** consolidated billing and/or true multi-user organization accounts.

## Why this is lower-risk than it looked

No schema rewrite, no change to how billing or Stripe works, no change to the auth model. It's
additive: new query, an optional param on existing endpoints (backward compatible), one new
read endpoint, and frontend. The riskiest piece is the add-property flow (Phase 2), which we can
defer.
