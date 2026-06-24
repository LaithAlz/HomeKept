# HomeKept · MVP API Contract

The endpoint surface for the expanded v1 (arch doc Stage 1, June 2026 revision —
issues #1–#45 plus the v1-expansion issues). This is the seam between
the Spring backend and the frontend rebuild: both sides build against this document.
Backward-compatible additions are fine; renames and removals require updating this file
in the same PR.

Conventions (from the architecture doc): JSON bodies, money in integer cents, timestamps
as ISO-8601 UTC, errors in the standard envelope:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Email is required",
    "fields": { "email": "must not be blank" },
    "request_id": "req_abc123"
  }
}
```

Auth: JWT access token in an httpOnly cookie (15 min) + opaque refresh token cookie
(7 days, rotated). Roles: `CUSTOMER`, `TECHNICIAN`, `ADMIN`.

---

## Public (no auth)

### `POST /api/bookings/walkthrough`
Walk-through booking form submission (frontend `book` wizard).
```json
{
  "fullName": "Priya Sharma",
  "email": "priya@example.com",
  "phone": "(905) 555-0123",
  "streetAddress": "14 Maple Ridge Crt",
  "city": "Mississauga",
  "postalCode": "L5L 1A1",
  "yearBuilt": 1998,                  // optional
  "squareFootageRange": "1500-2500",  // optional: <1500 | 1500-2500 | 2500-4000 | >4000
  "propertyType": "DETACHED",         // DETACHED | SEMI | TOWNHOUSE
  "preferredWeek": "2026-06-15",      // Monday of chosen week
  "timeOfDay": "AFTERNOON",           // MORNING | AFTERNOON | EVENING
  "dayPreferences": ["WED", "THU"],
  "notes": "Friendly dog in the yard",
  "leadSource": "WEBSITE_ORGANIC",    // optional, defaults WEBSITE_DIRECT
  "contactConsent": true,             // required true (CASL); consent timestamp recorded server-side
  "posthogDistinctId": "0190..."      // optional — anonymous analytics ID for funnel stitching (arch §5.7)
}
```
→ `201 { "id": 123, "status": "PENDING" }` · Rate limit: 3/IP/hour. Triggers
booking-confirmation email.

### `GET /api/catalog/plans`
Plan tiers for the pricing page (prices per docs/pricing-and-visits.md).
→ `200 [ { "code": "COMPLETE", "displayName": "Complete", "monthlyPriceCents": 14900, "annualPriceCents": 149000, "visitsPerYear": 8, "includedPicksPerYear": 3, "maxPremiumPicksPerYear": 1, "foundingRateAvailable": true, "foundingMonthlyPriceCents": 12900, "description": "...", "services": [ { "name": "Furnace filter swap", "tierClass": "BASIC", "frequencyPerYear": 4 } ] } ]`

### `GET /api/catalog/picks`
The pickable services menu, grouped by tier class, with à la carte prices
(`BASIC` 4900 / `MEDIUM` 8900 / `PREMIUM` 14900).

### `GET /api/health`
→ `200 { "status": "UP" }` (UptimeRobot target)

---

## Activation (token-authed, not session-authed)

Magic-link flow: walk-through → subscriber. Token is single-use, HMAC-signed, 7-day expiry.

Both endpoints are IP rate-limited (10/IP/hour) — magic links leak via forwarded emails.

### `POST /api/activation/validate`
`{ "token": "..." }` → `200 { "valid": true, "bookingId": 123, "firstName": "Priya" }`
or `200 { "valid": false, "reason": "EXPIRED" | "USED" | "INVALID" }`
(First name only — a token holder shouldn't learn the full identity.)

### `POST /api/activation/complete`
`{ "token": "...", "password": "..." }` → in one transaction: creates `User` (CUSTOMER,
PENDING_ACTIVATION), `Property` from booking data, and the `Subscriber` row in
`PENDING_ACTIVATION` (so `property.subscriber_id` is never orphaned); consumes the token;
sets auth cookies. The Stripe `checkout.session.completed` webhook later flips the
subscriber to `ACTIVE`.
→ `201 { "userId": 9, "next": "CHECKOUT" }`

---

## Auth

| Endpoint | Body | Result |
|---|---|---|
| `POST /api/auth/login` | `{ email, password }` | `200` + sets cookies · rate limit 5/email/15min |
| `POST /api/auth/refresh` | — (refresh cookie) | `200` + rotated cookies |
| `POST /api/auth/logout` | — | `204`, revokes all refresh tokens |
| `GET /api/auth/me` | — | `200 { id, firstName, lastName, email, role }` |
| `POST /api/auth/forgot` | `{ email }` | always `202` (same response whether the account exists — no enumeration); emails a single-use HMAC reset token, 30-min expiry · rate limit 5/IP/hour |
| `POST /api/auth/reset` | `{ token, password }` | `200`, consumes token, revokes all refresh tokens, sets fresh cookies |

There is **no** `POST /api/auth/register` at MVP. Customer accounts are created only via
the activation flow; the first ADMIN (and any TECHNICIAN) users are created by seed
migration. Self-serve registration gets added — behind a deliberate design — only when a
real need appears.

---

## Checkout & billing (role: CUSTOMER)

### `POST /api/checkout/session`
`{ "planCode": "COMPLETE", "billingCycle": "MONTHLY", "foundingRate": false }`
→ `200 { "checkoutUrl": "https://checkout.stripe.com/..." }` (Stripe-hosted page)

`foundingRate: true` is validated server-side against the cap (15 founding subscribers,
counted from `subscriber.founding_rate = true`) and uses `stripe_price_id_founding`;
`subscriber.founding_rate_expires_at` is set 12 months from activation. The plans
payload's `foundingRateAvailable` is computed from the same count.

### `POST /api/billing/portal-session`
→ `200 { "portalUrl": "https://billing.stripe.com/..." }` (plan change / cancel / cards)

---

## Webhooks

### `POST /api/webhooks/stripe`
Signature-verified (Stripe SDK), idempotent via `subscription_event.stripe_event_id`.
Returns `200` within 2s. Handled events per the architecture doc §2.4; all others
acknowledged and ignored.

---

## Owner app (role: CUSTOMER — or ADMIN via ownership check)

| Endpoint | Returns / does |
|---|---|
| `GET /api/app/subscription` | `{ status, planCode, billingCycle, currentPeriodEnd, picksRemaining, premiumPicksRemaining, property: { streetAddress, city, ... } }` |
| `GET /api/app/visits?status=SCHEDULED&cursor=&limit=` | paginated visits: `{ id, name, scheduledFor, durationMinutes, status, type, technicianFirstName, services: [{ name, source, completed }] }` |
| `GET /api/app/visits/{id}` | full visit incl. checklist, `completionNotes`, notes, `photos: [{ url (signed, 15-min), caption, takenAt }]` |
| `GET /api/app/health-score` | `{ score, delta, computedAt, flagged: [...] }` — v1 rubric (weighted checklist outcomes) |
| `GET /api/app/activity?cursor=&limit=` | dashboard feed (visit events, billing events, reminders) |
| `GET /api/app/todos` · `POST /api/app/todos` · `DELETE /api/app/todos/{id}` | "your list" — `{ body }`; OPEN items fold into the next scheduled visit |
| `POST /api/app/picks` | `{ serviceId }` — spend an included pick (validates allowance + max-premium); folds into nearest visit |
| `POST /api/app/visits/{id}/reschedule-request` | `{ preferredDates: [...] }` — stored as a `reschedule_request` row pending admin confirmation; visit state machine handles the swap |
| `POST /api/checkout/extra` | `{ serviceId }` — one-off Stripe Checkout (`mode=payment`) with `subscriberId`/`serviceId` metadata; on the `checkout.session.completed` webhook (distinguished by mode + metadata from subscription checkouts) an EXTRA visit / `VisitService(source=EXTRA)` is created — never burns the included-picks allowance |
| `POST /api/app/subscription/pause` · `POST /api/app/subscription/resume` | → `200 { status, currentPeriodEnd }` — self-serve via Stripe; the `customer.subscription.paused`/`resumed` webhook applies the status change, so `status` is the current (pre-webhook) value. Pause requires ACTIVE, resume requires PAUSED, else `409 ILLEGAL_STATE_TRANSITION`; no Stripe subscription yet → `409 NO_BILLING_ACCOUNT` |
| `POST /api/app/subscription/cancel` | `{ reason }` (required, churn data) → `200 { status, currentPeriodEnd }` — cancel-at-period-end via Stripe; the reason is stored as a `MANUAL` `subscription_event` (payload `{ "reason": ... }`) and `customer.subscription.deleted` applies CANCELLED when the period ends. Already-cancelled → `409`; blank reason → `400` |

Plan change + payment method = Stripe customer portal (`POST /api/billing/portal-session`).

---

## Technician app (role: TECHNICIAN — at MVP, the two founders)

| Endpoint | Returns / does |
|---|---|
| `GET /api/tech/visits/today` | day sheet: assigned visits w/ address, access notes, checklist (template + picks + todos + flagged) |
| `POST /api/tech/visits/{id}/start` | → IN_PROGRESS |
| `PATCH /api/tech/visits/{id}/services/{visitServiceId}` | `{ completed, technicianNotes }` — checklist tick |
| `POST /api/tech/visits/{id}/photos/upload-url` | `{ contentType }` → `{ uploadUrl, storageKey }` (R2 signed PUT, 15-min) |
| `POST /api/tech/visits/{id}/photos` | `{ storageKey, caption }` — confirm upload, attach to visit |
| `POST /api/tech/visits/{id}/flags` | `{ body, severity, photoStorageKey? }` — creates a `Flag` (the observe→photograph→flag→refer loop); OPEN flags fold into the next visit (`source=FLAGGED`) and feed the health score's `flagged` list |
| `PATCH /api/tech/todos/{id}` | `{ status: "DONE" \| "DECLINED", note? }` — your-list items worked or declined in the field |
| `POST /api/tech/visits/{id}/complete` | `{ completionNotes, actualDurationMinutes, materialsCostCents, materialsNotes }` — → COMPLETED, fires report email |
| `POST /api/tech/visits/{id}/incomplete` | `{ reason }` — → INCOMPLETE, auto-creates follow-up SCHEDULED visit |

Picks accounting: `picksRemaining` counts `VisitService(source=PICK)` rows within the
subscription anniversary year; `source=EXTRA` (paid) rows never count. Health Score v1 is
computed on read from checklist outcomes + OPEN flags; a `health_score_snapshot` row is
written per completed visit so `delta` compares against the previous snapshot.

---

## Admin console (role: ADMIN)

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/bookings?status=&cursor=` | walk-through pipeline list |
| `PATCH /api/admin/bookings/{id}` | status transitions (via `WalkthroughBookingStateMachine`), set `scheduledFor` |
| `POST /api/admin/bookings/{id}/activation-invite` | mint token + send activation email |
| `GET /api/admin/subscribers?cursor=` | subscriber list w/ status, plan, MRR |
| `GET /api/admin/subscribers/{id}` | detail incl. property, visits, Stripe links |
| `POST /api/admin/visits` | `{ subscriberId, scheduledFor, durationMinutes, serviceIds[], technicianUserId? }` |
| `PATCH /api/admin/visits/{id}` | reschedule (creates new row per state machine) / cancel / assign technician |
| `POST /api/admin/visits/{id}/complete` | fallback for tech-app failure only — same payload as the tech complete endpoint (incl. `actualDurationMinutes`, `materialsCostCents`); requires IN_PROGRESS per the state machine |
| `POST /api/admin/technicians` | `{ userId, fullyLoadedHourlyCostCents, employeeStatus?, hireDate? }` — onboard a technician: creates a `technician_profile` for an existing user (the user's TECHNICIAN role is managed separately). 409 if a profile already exists for that user |

All admin mutations write audit rows (Stage 2 formalizes this; log from day 1).

---

## Status codes

`200/201/204` success · `400` validation (envelope above) · `401` missing/expired token ·
`403` wrong role · `404` not found *or not yours* (ownership failures return 404, never 403 —
don't leak existence) · `409` illegal state transition · `429` rate limited · `5xx` + Sentry.
