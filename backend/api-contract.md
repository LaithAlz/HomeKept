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

The activation invite email links to `{FRONTEND_BASE_URL}/activate?token=<rawToken>` (token
URL-encoded). The frontend activation page calls `/api/activation/validate` then
`/api/activation/complete` with that token.

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
| `POST /api/auth/reset` | `{ token, password }` | `200`, consumes token, revokes all refresh tokens, updates the password either way; sets fresh cookies only if the user is ACTIVE (no auto-sign-in for a non-ACTIVE user) |

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
| `GET /api/app/subscription` | `{ status, planCode, planDisplayName, billingCycle, priceCents, foundingRate, foundingRateExpiresAt, currentPeriodStart, currentPeriodEnd, nextVisitDate }` — `planCode`/`planDisplayName`/`priceCents` are `null` pre-checkout (`PENDING_ACTIVATION`, no plan tier assigned yet); `priceCents` is the price actually charged for the billing cycle (founding rate takes precedence when active — it is monthly-only, per docs/pricing-and-visits.md); `nextVisitDate` is the subscriber's next SCHEDULED visit, `null` if none. No subscriber row for the authenticated user → `404`. (`picksRemaining`/`premiumPicksRemaining` — picks tracking — are a follow-up issue, not yet built.) |
| `GET /api/app/account` | `{ firstName, lastName, email, streetAddress, unit, city, postalCode }` — settings-page profile; bundles the service property's address with name/email (which also appear on `GET /api/auth/me`) for a single round trip. Never includes decrypted access notes. No subscriber row for the authenticated user → `404` |
| `GET /api/app/visits?status=SCHEDULED&cursor=&limit=` | paginated visits: `{ id, name, scheduledFor, durationMinutes, status, type, technicianFirstName, services: [{ name, source, completed }] }` |
| `GET /api/app/visits/{id}` | full visit incl. checklist, `completionNotes`, notes, `photos: [{ url (signed, 15-min), caption, takenAt }]`, `hasPendingRescheduleRequest: boolean` — true iff the visit has a PENDING `reschedule_request` (lets the UI persist the "reschedule requested" state across reloads instead of relying on optimistic client-side state) |
| `GET /api/app/health-score` | `{ score, delta, computedAt, flagged: [{ id, body, severity, createdAt }] }` — v1 rubric: `score = clamp(100 − open-flag penalty (URGENT 20 / ATTENTION 10 / INFO 3) − checklist deduction (up to 15 × incomplete rate of the last completed visit), 0..100)`, computed on read; `delta` vs the most recent `health_score_snapshot` (written per completed visit); `flagged` = OPEN flags |
| `GET /api/app/activity?cursor=&limit=` | dashboard feed (visit events, billing events, reminders) |
| `GET /api/app/todos` | "your list" — the authenticated customer's todo items, newest first: `[{ id, subscriberId, body, status, visitId, declineNote, createdAt, updatedAt }]` |
| `POST /api/app/todos` | `{ body }` → `201`, creates an `OPEN` item with `visitId: null` |
| `DELETE /api/app/todos/{id}` | Removes an item from the list → `204`. Ownership enforced (404, not 403) |
| `POST /api/app/picks` | `{ serviceId }` — spend an included pick (validates allowance + max-premium); folds into nearest visit |
| `POST /api/app/visits/{id}/reschedule-request` | `{ preferredDates: [Instant, …] }` (1–5 timeslots) → `201 { id, visitId, status, preferredDates, createdAt }`. Stored as a PENDING `reschedule_request` (+ `reschedule_request_slot` rows) for admin confirmation. Visit must be owned (else 404) and SCHEDULED; a duplicate PENDING request for the same visit → 409 |
| `POST /api/checkout/extra` | `{ serviceId }` — one-off Stripe Checkout (`mode=payment`) with `subscriberId`/`serviceId` metadata; on the `checkout.session.completed` webhook (distinguished by mode + metadata from subscription checkouts) an EXTRA visit / `VisitService(source=EXTRA)` is created — never burns the included-picks allowance |
| `POST /api/app/subscription/pause` · `POST /api/app/subscription/resume` | → `200 { status, currentPeriodEnd }` — self-serve via Stripe; the `customer.subscription.paused`/`resumed` webhook applies the status change, so `status` is the current (pre-webhook) value. Pause requires ACTIVE, resume requires PAUSED, else `409 ILLEGAL_STATE_TRANSITION`; no Stripe subscription yet → `409 NO_BILLING_ACCOUNT` |
| `POST /api/app/subscription/cancel` | `{ reason }` (required, churn data) → `200 { status, currentPeriodEnd }` — cancel-at-period-end via Stripe; the reason is stored as a `MANUAL` `subscription_event` (payload `{ "reason": ... }`) and `customer.subscription.deleted` applies CANCELLED when the period ends. Already-cancelled → `409`; blank reason → `400` |

Plan change + payment method = Stripe customer portal (`POST /api/billing/portal-session`).

---

## Technician app (role: TECHNICIAN — at MVP, the two founders)

| Endpoint | Returns / does |
|---|---|
| `GET /api/tech/visits/today` | day sheet: assigned visits w/ address, access notes, `services[]` checklist (template/pick/extra/flagged/todo-sourced `VisitService` rows), `todos[]` — `TodoItem`s folded into this visit (`{ id, subscriberId, body, status, visitId, declineNote, createdAt, updatedAt }`, any status, so already-worked items still show), and `flags[]` — this subscriber's OPEN `Flag`s shown for context (`{ id, subscriberId, originVisitId, body, severity, status, photoStorageKey, createdAt }`); `todos[].id` is the id `PATCH /api/tech/todos/{id}` targets |
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

Your-list folding: a customer-created todo starts `OPEN` with `visitId: null`. `PATCH
/api/tech/todos/{id}` already lets a technician resolve (`DONE`/`DECLINED`) any `OPEN` todo
belonging to a subscriber for whom they have an active (`SCHEDULED`/`IN_PROGRESS`) visit,
independent of `visitId` — so a customer's item is actionable on the subscriber's next visit
without a separate fold step. Automatically flipping a todo to `SCHEDULED` + setting `visitId`
when a visit is confirmed, and surfacing OPEN todos in the `GET /api/tech/visits/today`
checklist response, are not yet built.

---

## Admin console (role: ADMIN)

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/bookings?status=&cursor=` | walk-through pipeline list |
| `PATCH /api/admin/bookings/{id}` | status transitions (via `WalkthroughBookingStateMachine`), set `scheduledFor` |
| `POST /api/admin/bookings/{id}/activation-invite` | mint token + send activation email |
| `GET /api/admin/subscribers?cursor=` | subscriber list w/ status, plan, MRR |
| `GET /api/admin/subscribers/{id}` | detail incl. property, visits, Stripe links. `property` includes `propertyId` (targets the SKU update below) and the SKU sheet fields — `hvacFilterSizes`, `smokeCoDetectorModels`, `humidifierModel`, `waterHeaterAgeYears`, `waterHeaterFlushEligible` (all `null` until captured; technician-prep data per docs/pricing-and-visits.md §Materials) |
| `PATCH /api/admin/properties/{propertyId}/sku` | `{ hvacFilterSizes?, smokeCoDetectorModels?, humidifierModel?, waterHeaterAgeYears?, waterHeaterFlushEligible? }` — all fields optional/nullable; a field omitted or `null` leaves that column unchanged (partial/ongoing capture as the SKU sheet is filled in over time). `waterHeaterAgeYears` must be 0–100 when present. 200 with the updated SKU fields; unknown `propertyId` → 404; non-ADMIN → 403; invalid `waterHeaterAgeYears` → 400 `VALIDATION_FAILED` |
| `GET /api/admin/visits?status=&cursor=&limit=` | cursor-paginated visit list (newest first; mirrors the bookings pagination style): `[{ id, subscriberId, propertyId, technicianId, scheduledFor, durationMinutes, actualDurationMinutes, materialsCostCents, status, type, completedAt, createdAt }]`. Invalid `status` → 400 |
| `POST /api/admin/visits` | `{ subscriberId, scheduledFor, durationMinutes, serviceIds[], technicianUserId? }` |
| `PATCH /api/admin/visits/{id}` | reschedule (creates new row per state machine) / cancel / assign technician |
| `GET /api/admin/reschedule-requests` | PENDING customer reschedule requests (oldest first): `[{ id, visitId, subscriberId, status, preferredDates, adminNote, confirmedVisitId, createdAt }]` |
| `POST /api/admin/reschedule-requests/{id}/confirm` | `{ scheduledFor: Instant, adminNote? }` — reschedules the visit (RESCHEDULED old + new SCHEDULED via the state machine), marks the request CONFIRMED with `confirmedVisitId`. 404 if missing; 409 if already resolved or the visit is not reschedulable |
| `POST /api/admin/reschedule-requests/{id}/decline` | `{ adminNote }` (required) — marks the request DECLINED. 404 if missing; 409 if already resolved |
| `POST /api/admin/visits/{id}/complete` | fallback for tech-app failure only — same payload as the tech complete endpoint (incl. `actualDurationMinutes`, `materialsCostCents`); requires IN_PROGRESS per the state machine |
| `GET /api/admin/technicians` | full technician roster (small at MVP, no pagination): `[{ id, userId, firstName, lastName, email, role, userStatus, employeeStatus, hireDate, fullyLoadedHourlyCostCents, createdAt }]`. Identity fields resolved from the `users` table via the identity domain's service; internal staff data, not customer PII |
| `POST /api/admin/technicians` | `{ userId, fullyLoadedHourlyCostCents, employeeStatus?, hireDate? }` — onboard a technician: creates a `technician_profile` for an existing user (the user's TECHNICIAN role is managed separately). 409 if a profile already exists for that user |
| `GET /api/admin/dashboard` | aggregate metrics for the admin home / operational dashboard (#43): `{ activeSubscribers, mrrCents, pendingWalkthroughs, upcomingVisits, foundingRateSlotsRemaining }`. `mrrCents` sums the current monthly price across ACTIVE subscribers only; `pendingWalkthroughs` counts PENDING (unconfirmed) bookings; `upcomingVisits` counts SCHEDULED visits with `scheduledFor` at or after now; `foundingRateSlotsRemaining` is the 15-subscriber founding cap minus current founding subscribers. No "at-risk subscribers" field — there is no backing status/column for that concept yet |

All admin mutations write audit rows (Stage 2 formalizes this; log from day 1).

---

## Status codes

`200/201/204` success · `400` validation (envelope above) · `401` missing/expired token ·
`403` wrong role · `404` not found *or not yours* (ownership failures return 404, never 403 —
don't leak existence) · `409` illegal state transition · `429` rate limited · `5xx` + Sentry.
