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
→ `201 { "id": 123, "status": "PENDING" }` · Rate limit: 10/IP/hour. Triggers
booking-confirmation email.

### `GET /api/catalog/plans`
Plan tiers for the pricing page. Three tiers: ESSENTIAL, COMPLETE (the recommended tier)
and PREMIER, returned cheapest-first.

The September 2026 repositioning (V12) removed ESSENTIAL and the founding-member rate
together. **V15 reinstated ESSENTIAL** at its original numbers ($89/mo · $890/yr · 4
visits · 1 included pick · 0 Premium) at the founder's request. The founding-member rate
was NOT reinstated and remains deleted, so no tier carries a founding price.

ESSENTIAL's and COMPLETE's Stripe price ids are both `null` until the founder creates the
live Stripe prices; until then, checkout for either fails closed (see
`POST /api/checkout/session` below). PREMIER's ids are live and unchanged.

→ `200 [ { "code": "ESSENTIAL", "displayName": "Essential", "monthlyPriceCents": 8900, "annualPriceCents": 89000, "visitsPerYear": 4, "includedPicksPerYear": 1, "maxPremiumPicksPerYear": 0, "description": "...", "services": [ … ] }, { "code": "COMPLETE", "displayName": "Complete", "monthlyPriceCents": 16900, "annualPriceCents": 169000, "visitsPerYear": 8, "includedPicksPerYear": 3, "maxPremiumPicksPerYear": 1, "description": "...", "services": [ { "name": "Furnace filter swap", "tierClass": "BASIC", "frequencyPerYear": 4 } ] } ]`

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
ACTIVE — a password has just been set, so the account must be able to authenticate
immediately), `Property` from booking data, and the `Subscriber` row in
`PENDING_ACTIVATION` (so `property.subscriber_id` is never orphaned); consumes the token;
sets auth cookies. The Stripe `checkout.session.completed` webhook later flips the
subscriber (not the user) to `ACTIVE`.
→ `201 { "userId": 9, "next": "CHECKOUT" }`

---

## Public staff invite (token-authed, not session-authed)

Invite-by-email flow for onboarding a technician: admin invites → technician sets their
own password. Token reuses the same table/HMAC scheme as `PasswordResetToken` (no
dedicated staff-invite table), single-use, with a 7-day expiry. The V13 migration added a
`purpose` column (`PASSWORD_RESET` | `STAFF_INVITE`) to that shared table — every lookup
(here and in `/api/auth/reset`) filters by it, and a token of the wrong purpose is always
indistinguishable from one that does not exist at all.

The staff invite email links to `{FRONTEND_BASE_URL}/staff/activate?token=<rawToken>`
(token URL-encoded). The frontend staff-activation page calls
`/api/staff/invite/validate` then `/api/staff/invite/accept` with that token.

Both endpoints are IP rate-limited (10/IP/hour) — invite links leak via forwarded emails,
same as the customer activation link.

### `POST /api/staff/invite/validate`
`{ "token": "..." }` → `200 { "valid": true, "firstName": "Priya" }`
or `200 { "valid": false, "reason": "EXPIRED" | "USED" | "INVALID" }`
(First name only — never the email or role.)

`EXPIRED`/`USED` are only ever returned for a token that really is `STAFF_INVITE`-purpose
and belongs to a still-eligible account — i.e. these two reasons only ever tell the caller
something about a token they already hold the raw bytes of, never about someone else's
account. Every other case reports the same `"INVALID"`, indistinguishable from a token
that never existed:
- a token of any other purpose (e.g. a customer's password-reset token) — the
  purpose-scoped lookup never reaches the consumed/expired checks for it, so it cannot
  report `EXPIRED`/`USED` either;
- a well-formed, unconsumed, unexpired `STAFF_INVITE` token whose resolved account is no
  longer an eligible `PENDING_ACTIVATION` `TECHNICIAN` (e.g. already accepted via a
  different token, or suspended).

### `POST /api/staff/invite/accept`
`{ "token": "...", "password": "..." }` → validates and consumes the token, sets the
password, flips the technician `PENDING_ACTIVATION` → `ACTIVE`, and signs them in
(auth cookies set exactly as `/api/activation/complete` does). The role is always
server-set to TECHNICIAN — never read from the request or the token.
→ `201 { "userId": 9 }`

**Critical guard:** accept rejects any user whose current status is not
`PENDING_ACTIVATION` (or whose role is not TECHNICIAN) with the same generic `400
INVALID_TOKEN` as a malformed/expired/used token — without this, a still-valid invite
token would be a way to reactivate a SUSPENDED account or silently reset an ACTIVE
technician's password outside the normal reset flow. If this guard rejects the request,
the token's consumption is rolled back too (same transaction), so a rejected attempt
never burns the token.

---

## Auth

| Endpoint | Body | Result |
|---|---|---|
| `POST /api/auth/login` | `{ email, password }` | `200` + sets cookies · rate limit 5/email/15min |
| `POST /api/auth/refresh` | — (refresh cookie) | `200` + rotated cookies |
| `POST /api/auth/logout` | — | `204`, revokes all refresh tokens |
| `GET /api/auth/me` | — | `200 { id, firstName, lastName, email, role }` |
| `POST /api/auth/forgot` | `{ email }` | always `202` (same response whether the account exists — no enumeration); emails a single-use HMAC `PASSWORD_RESET`-purpose token, 30-min expiry · rate limit 5/IP/hour |
| `POST /api/auth/reset` | `{ token, password }` | `200 { "signedIn": true }`, consumes the token, revokes all refresh tokens, updates the password, and signs the user in · rate limit 5/IP/hour. The token must be `PASSWORD_RESET`-purpose (a `STAFF_INVITE` token is rejected — see "Public staff invite" above) AND resolve to an `ACTIVE` user; either failure is the same `400 INVALID_TOKEN` as a malformed/expired/used token, and rolls back the token's consumption (so a rejected attempt doesn't burn it). Every genuine customer is `ACTIVE`, so this never fires for a real reset — it exists to close a token-confusion path, not to affect normal use |
| `POST /api/auth/change-password` | `{ currentPassword, newPassword }` | any authenticated role · verifies `currentPassword` with the existing encoder, requires `newPassword` to be at least 8 characters and different from `currentPassword`, sets it, revokes all of the caller's refresh tokens, and sets fresh cookies so the caller stays signed in → `204`. Wrong `currentPassword` → `400` (same generic, nothing-else-revealed wording style as the reset flow); `newPassword` too short or equal to `currentPassword` → `400` · rate limit 10/IP/hour |

There is **no** `POST /api/auth/register` at MVP. Customer accounts are created only via
the activation flow; the first ADMIN (and any TECHNICIAN) users are created by seed
migration. Self-serve registration gets added — behind a deliberate design — only when a
real need appears.

---

## Checkout & billing (role: CUSTOMER)

### `POST /api/checkout/session`
`{ "planCode": "ESSENTIAL" | "COMPLETE" | "PREMIER", "billingCycle": "MONTHLY" | "ANNUAL" }`
→ `200 { "checkoutUrl": "https://checkout.stripe.com/..." }` (Stripe-hosted page)

Fails closed with `409 { "error": { "code": "PLAN_NOT_PURCHASABLE", "message": "This plan
can't be purchased yet." } }` when the resolved plan tier has no Stripe price id for the
requested `billingCycle` — checkout never reaches Stripe and never charges a stale price.
Both ESSENTIAL and COMPLETE currently have `null` ids and so fail closed: COMPLETE's
because the September 2026 repositioning retired its old $149 prices (V12), ESSENTIAL's
because V15 reinstated the tier without assuming its archived prices still exist. PREMIER
is purchasable.

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
| `GET /api/app/subscription` | `{ status, planCode, planDisplayName, billingCycle, priceCents, currentPeriodStart, currentPeriodEnd, nextVisitDate }` — `planCode`/`planDisplayName`/`priceCents` are `null` pre-checkout (`PENDING_ACTIVATION`, no plan tier assigned yet); `priceCents` is the price actually charged for the billing cycle (monthly or annual); `nextVisitDate` is the subscriber's next SCHEDULED visit, `null` if none. No subscriber row for the authenticated user → `404`. (`picksRemaining`/`premiumPicksRemaining` — picks tracking — are a follow-up issue, not yet built.) |
| `GET /api/app/account` | `{ firstName, lastName, email, phone, streetAddress, unit, city, postalCode }` — settings-page profile; bundles the service property's address with name/email (which also appear on `GET /api/auth/me`) for a single round trip. Never includes decrypted access notes. No subscriber row for the authenticated user → `404` |
| `PATCH /api/app/account` | `{ firstName?, lastName?, phone? }` — every field optional; a field omitted or `null` leaves it unchanged. A provided name must be non-blank and at most 100 characters; a provided phone must be at most 30 characters (phone may be blank — that's how a customer clears a previously-captured number). Returns `200` with the same shape as `GET /api/app/account`. Email is **not** editable here (an email change is an account-takeover-risk operation that needs dual verification — separate, later work), and neither is the service address (it drives routing and the property record — its own admin-only update path). No subscriber row for the authenticated user → `404`; a blank name or an over-length field → `400` |
| `GET /api/app/visits?status=SCHEDULED&cursor=&limit=` | paginated visits: `{ id, name, scheduledFor, durationMinutes, status, type, technicianFirstName, services: [{ name, source, completed }], hasPendingRescheduleRequest: boolean }` — true iff the visit has a PENDING `reschedule_request` (batch-computed for the whole page, one query), same meaning as on the detail endpoint |
| `GET /api/app/visits/{id}` | full visit incl. checklist, `completionNotes`, notes, `photos: [{ url (signed, 15-min), caption, takenAt }]`, `hasPendingRescheduleRequest: boolean` — true iff the visit has a PENDING `reschedule_request` (lets the UI persist the "reschedule requested" state across reloads instead of relying on optimistic client-side state) |
| `GET /api/app/health-score` | `{ score, delta, computedAt, flagged: [{ id, body, severity, createdAt }] }` — v1 rubric: `score = clamp(100 − open-flag penalty (URGENT 20 / ATTENTION 10 / INFO 3) − checklist deduction (up to 15 × incomplete rate of the last completed visit), 0..100)`, computed on read; `delta` vs the most recent `health_score_snapshot` (written per completed visit); `flagged` = OPEN flags |
| `GET /api/app/activity?cursor=&limit=` | dashboard feed (visit events, billing events, reminders) |
| `GET /api/app/todos` | "your list" — the authenticated customer's todo items, newest first: `[{ id, subscriberId, body, status, visitId, declineNote, createdAt, updatedAt }]` |
| `POST /api/app/todos` | `{ body }` → `201`, creates an `OPEN` item with `visitId: null`. `409 SUBSCRIBER_NOT_ACTIVE` if the subscription isn't serviceable (only ACTIVE / PAYMENT_ISSUE may add items) |
| `DELETE /api/app/todos/{id}` | Removes an item from the list → `204`. Ownership enforced (404, not 403) |
| `POST /api/app/picks` | `{ serviceId }` — spend an included pick (validates allowance + max-premium); folds into nearest visit |
| `POST /api/app/visits/{id}/reschedule-request` | `{ preferredDates: [Instant, …] }` (1–5 timeslots) → `201 { id, visitId, status, preferredDates, createdAt }`. Stored as a PENDING `reschedule_request` (+ `reschedule_request_slot` rows) for admin confirmation. Visit must be owned (else 404) and SCHEDULED; a duplicate PENDING request for the same visit → 409. `409 SUBSCRIBER_NOT_ACTIVE` if the subscription isn't serviceable (only ACTIVE / PAYMENT_ISSUE may request) |
| `DELETE /api/app/visits/{id}/reschedule-request` | Withdraws the customer's own PENDING reschedule request → `204` (hard-deletes the request + its slot rows, freeing the partial unique index so a new request can be submitted). 404 if the visit is not owned, or if there is no PENDING request for it (already resolved or never created) |
| `POST /api/checkout/extra` | `{ serviceId }` — one-off Stripe Checkout (`mode=payment`) with `subscriberId`/`serviceId` metadata; on the `checkout.session.completed` webhook (distinguished by mode + metadata from subscription checkouts) an EXTRA visit / `VisitService(source=EXTRA)` is created — never burns the included-picks allowance |
| `POST /api/app/subscription/cancel` | `{ reason }` (required, churn data) → `200 { status, currentPeriodEnd }` — cancel-at-period-end via Stripe; the reason is stored as a `MANUAL` `subscription_event` (payload `{ "reason": ..., "by": "CUSTOMER" }`) and `customer.subscription.deleted` applies CANCELLED when the period ends. Already-cancelled → `409`; blank reason → `400`; a cancellation is already pending for this subscriber → `409 ILLEGAL_STATE_TRANSITION` |
| `GET /api/app/billing/invoices` | → `200 [{ id, number, createdAt, amountPaidCents, currency, status, hostedInvoiceUrl, invoicePdf }]`, newest first, at most 24. Reads the subscriber's Stripe customer via `StripeService.listInvoices`; `amountPaidCents` is integer cents. Returns `200 []` (never an error) when the subscriber has no Stripe customer id yet, or when Stripe isn't configured. No subscriber row for the authenticated user → `404` |
| `GET /api/app/billing/payment-method` | → `200 { brand, last4, expMonth, expYear }` for the default card on file, or `200 null` when there is none or the subscriber has no Stripe customer id yet. Reads via `StripeService.findDefaultPaymentMethod`; never a full PAN, a payment method id, or a fingerprint. No subscriber row for the authenticated user → `404` |

Plan change + payment method = Stripe customer portal (`POST /api/billing/portal-session`).

**There is no pause/resume.** Not for the customer, not for an admin: the only lifecycle
action is cancellation, and access runs through the end of the period already paid for.
`PAUSED` survives as a subscriber status only because the
`customer.subscription.paused`/`.resumed` webhooks still reflect it, for the case where a
subscription is paused directly in the Stripe dashboard. Nothing in this API initiates one.

---

## Technician app (role: TECHNICIAN — at MVP, the two founders)

| Endpoint | Returns / does |
|---|---|
| `GET /api/tech/visits/today` | day sheet: assigned visits w/ address, access notes, `services[]` checklist (template/pick/extra/flagged/todo-sourced `VisitService` rows), `todos[]` — `TodoItem`s folded into this visit (`{ id, subscriberId, body, status, visitId, declineNote, createdAt, updatedAt }`, any status, so already-worked items still show), and `flags[]` — this subscriber's OPEN `Flag`s shown for context (`{ id, subscriberId, originVisitId, body, severity, status, photoStorageKey, createdAt }`); `todos[].id` is the id `PATCH /api/tech/todos/{id}` targets |
| `POST /api/tech/visits/{id}/start` | → IN_PROGRESS. `409 SUBSCRIBER_NOT_ACTIVE` if the subscriber isn't serviceable (only ACTIVE / PAYMENT_ISSUE are; PAUSED / CANCELLED blocked) — guards START only, an already IN_PROGRESS visit still completes |
| `PATCH /api/tech/visits/{id}/services/{visitServiceId}` | `{ completed, technicianNotes }` — checklist tick |
| `POST /api/tech/visits/{id}/photos/upload-url` | `{ contentType, contentLength }` → `{ uploadUrl, storageKey }` (R2 signed PUT, 15-min). `contentLength` is the file's exact byte size: rejected with `400` if missing or not in `(0, 26214400]` (25 MB cap), and signed into the PUT so R2 rejects a body of any other size |
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
| `GET /api/admin/bookings?status=&cursor=` | walk-through pipeline list; each row includes `invitedAt` (`Instant`, `null` until an activation invite has been sent for that booking, else the most recent invite's timestamp — resolved from `activation_token`, never a frontend-only flag) |
| `GET /api/admin/bookings/{id}` | full booking detail — the same shape `PATCH /api/admin/bookings/{id}` returns, now also readable without making an update (backs a standalone walk-through detail page). Unknown `id` → `404` |
| `PATCH /api/admin/bookings/{id}` | status transitions (via `WalkthroughBookingStateMachine`), set `scheduledFor`; response includes `invitedAt` (same semantics as the list) |
| `POST /api/admin/bookings/{id}/activation-invite` | mint token + send activation email |
| `GET /api/admin/subscribers?cursor=` | subscriber list w/ status, plan, MRR, and customer identity — `firstName`, `lastName`, `email`, `phone` (resolved from the identity domain via a single batched query per page, never N+1; customer PII, safe here only because this endpoint is ADMIN-gated — `phone` is frequently `null` since it isn't captured at account creation) |
| `GET /api/admin/subscribers/{id}` | detail incl. property, Stripe links, and customer identity — `firstName`, `lastName`, `email`, `phone` (same identity-domain resolution and PII caveat as the list above). `property` includes `propertyId` (targets the SKU update below) and the SKU sheet fields — `hvacFilterSizes`, `smokeCoDetectorModels`, `humidifierModel`, `waterHeaterAgeYears`, `waterHeaterFlushEligible` (all `null` until captured; technician-prep data per docs/pricing-and-visits.md §Materials) |
| `POST /api/admin/subscribers/{id}/cancel` | `{ reason, immediately? }` (`reason` required, 1–500 chars; `immediately` optional/nullable, default `false` — a boxed boolean on the request DTO, since Jackson 3 rejects a missing primitive) → `200 { status, currentPeriodEnd }`. Records the reason as a `MANUAL` `subscription_event` (payload `{ "reason": ..., "by": "ADMIN", "byUserId": <admin's user id>, "immediate": true|false }`, threaded from the JWT principal) before calling Stripe; `immediately=false` schedules cancellation at period end (same as customer self-serve), `immediately=true` cancels the Stripe subscription right away (Stripe's default cancel behaviour — no proration, no refund). Either way the CANCELLED status itself is applied later by the `customer.subscription.deleted` webhook, not by this endpoint. Unknown subscriber → `404`; no Stripe subscription yet → `409 NO_BILLING_ACCOUNT`; not eligible to cancel → `409 ILLEGAL_STATE_TRANSITION`; a cancellation is already pending for this subscriber (most recent `subscription_event` is `CANCELLATION_REQUESTED` and status hasn't reached CANCELLED yet — blocks double-clicks and immediate-cancel retries) → `409 ILLEGAL_STATE_TRANSITION` ("A cancellation has already been requested for this subscriber.") |
| `GET /api/admin/subscribers/{id}/events` | subscriber activity history, newest first, capped at 100: `[{ id, type, source, occurredAt, note, by, immediate }]` — `source` is the `SubscriptionEventSource` (`STRIPE_WEBHOOK` / `MANUAL` / `SYSTEM`); for `CANCELLATION_REQUESTED` events (extracted from the JSONB payload): `note` is the cancellation reason, `by` is `"CUSTOMER"` or `"ADMIN"`, `immediate` is `true`/`false` for an admin cancel (`null` for a customer self-serve cancel, which is always at period end) — all three `null` for every other event type. Unknown subscriber → `404` |
| `PATCH /api/admin/properties/{propertyId}/sku` | `{ hvacFilterSizes?, smokeCoDetectorModels?, humidifierModel?, waterHeaterAgeYears?, waterHeaterFlushEligible? }` — all fields optional/nullable; a field omitted or `null` leaves that column unchanged (partial/ongoing capture as the SKU sheet is filled in over time). `waterHeaterAgeYears` must be 0–100 when present. 200 with the updated SKU fields; unknown `propertyId` → 404; non-ADMIN → 403; invalid `waterHeaterAgeYears` → 400 `VALIDATION_FAILED` |
| `GET /api/admin/visits?status=&cursor=&limit=` | cursor-paginated visit list (newest first; mirrors the bookings pagination style; also backs the admin Routes day view, which calls this filtered to `status=SCHEDULED` and groups the rows by technician client-side): `[{ id, subscriberId, propertyId, technicianId, scheduledFor, durationMinutes, actualDurationMinutes, materialsCostCents, status, type, completedAt, createdAt, customerFirstName, customerLastName, customerPhone, propertyStreetAddress, propertyCity }]`. The five `customer*`/`property*` fields are resolved via two batched queries per page (identity domain via `UserQueryService.findAdminContactsByIds`, property domain via `PropertyService.findByIds`) — never one query per row — and are `null` only if the referenced subscriber/property is unexpectedly missing; customer PII, safe here only because this endpoint is ADMIN-gated (same caveat as the subscriber list above). Invalid `status` → 400 |
| `POST /api/admin/visits` | `{ subscriberId, scheduledFor, durationMinutes, serviceIds[], technicianUserId? }` |
| `GET /api/admin/visits/{id}` | full single-visit detail: `{ id, subscriberId, technicianId, technicianFirstName, technicianLastName, visitTemplateId, name, scheduledFor, durationMinutes, actualDurationMinutes, materialsCostCents, status, type, completionNotes, materialsNotes, completedAt, createdAt, services[], photos[], property: { propertyId, streetAddress, unit, city, postalCode }, customerFirstName, customerLastName, customerEmail, customerPhone }`. `name` is the resolved display name (template name, or a type-based fallback — same rule `AppVisitDetail` uses). `technician*`/`customer*` fields are `null` (omitted — `NON_NULL`) until a technician is assigned / if the subscriber's identity can't be resolved. `photos[]` are signed R2 download URLs (~15-min TTL, same graceful-degradation as the customer app's visit detail — empty if R2 unconfigured). This is the link a visit row in the admin console opens into (rather than the list growing another row on reschedule — see the events endpoint below for the history). Unknown `id` → `404` |
| `PATCH /api/admin/visits/{id}` | reschedule (updates `scheduledFor`/technician on the SAME visit row IN PLACE — no replacement visit is created — and records a `RESCHEDULED` `visit_event`) / cancel (records a `CANCELLED` `visit_event`) / assign technician (records a `TECHNICIAN_ASSIGNED` `visit_event` only when the technician actually changes). Every `visit_event` this produces has `source=ADMIN` and `byUserId` = the authenticated admin's user id. `scheduledFor`, when present, must be in the future — `400 VALIDATION_FAILED` otherwise. 404 if missing; 409 `ILLEGAL_STATE_TRANSITION` if the visit isn't in a state that permits the requested op (e.g. rescheduling a CANCELLED visit, cancelling a COMPLETED visit — the message names the visit's actual status, not a status transition) |
| `GET /api/admin/visits/{id}/events` | the visit's activity log, newest first, capped at 100 — mirrors `GET /api/admin/subscribers/{id}/events`'s shape and cap: `[{ id, type, source, occurredAt, byUserId, payload }]`. `source` is the `VisitEventSource` (`ADMIN` / `CUSTOMER` / `TECHNICIAN` / `SYSTEM`); `byUserId` is the acting user's id, `null` for `SYSTEM` events; `payload` is the event's raw JSONB detail embedded as a JSON object (e.g. `{ "from": ..., "to": ... }` for `RESCHEDULED`/`TECHNICIAN_ASSIGNED`), `null` for an event with no extra detail (e.g. `CANCELLED`). `type` is not a fixed/exhaustive enum — new event types can be added without a migration. Unknown visit → `404` |
| `GET /api/admin/visits/day-load?from=YYYY-MM-DD&to=YYYY-MM-DD` | Routes month-sidebar aggregate: `from`/`to` are inclusive local dates (in the configured render zone) → `200 [{ "day": "2026-09-08", "total": 3, "unassigned": 1 }]`, one entry per day with at least one SCHEDULED visit (empty days omitted), ascending. Computed as a single grouped aggregate query, never by loading rows. Honest counts only — no capacity/percentage/"slots free" field (technician working hours aren't modelled). `to` before `from`, or a span longer than 62 days, → `400 INVALID_REQUEST`; a missing or unparseable date → the standard validation error |
| `GET /api/admin/reschedule-requests` | PENDING customer reschedule requests (oldest first): `[{ id, visitId, subscriberId, status, preferredDates, adminNote, confirmedVisitId, createdAt }]` |
| `POST /api/admin/reschedule-requests/{id}/confirm` | `{ scheduledFor: Instant, adminNote? }` — reschedules the visit in place (updates `scheduledFor` on the same visit row; no replacement visit is created), marks the request CONFIRMED with `confirmedVisitId` (now simply the same id as `visitId`). Records a `RESCHEDULED` `visit_event` with `source=CUSTOMER` and `byUserId` = the requesting subscriber's own user id (the customer's request drove this, even though an admin executed the confirm) — distinct from a direct admin reschedule via `PATCH /api/admin/visits/{id}`, which is `source=ADMIN`. 404 if missing; 409 if already resolved or the visit is not reschedulable |
| `POST /api/admin/reschedule-requests/{id}/decline` | `{ adminNote }` (required) — marks the request DECLINED. 404 if missing; 409 if already resolved |
| `GET /api/admin/technicians` | full technician roster (small at MVP, no pagination): `[{ id, userId, firstName, lastName, email, role, userStatus, employeeStatus, hireDate, fullyLoadedHourlyCostCents, createdAt, invitedAt }]`. Identity fields resolved from the `users` table via the identity domain's service; internal staff data, not customer PII. `invitedAt` (`Instant`, `null` until an invite has ever been sent) is the most recently minted staff-invite token's timestamp, resolved from the shared invite-token table — same "never a frontend-only flag" semantics as the walk-through pipeline's `invitedAt` |
| `POST /api/admin/technicians` | `{ firstName, lastName, email, phone? }` — invite a new technician by identity only (no `userId`). In one transaction: creates the `User` (TECHNICIAN, `PENDING_ACTIVATION`, an unusable random password — the account cannot authenticate until the invite is accepted), the `technician_profile` (cost/employee status/hire date left `null` — set later on a technician-edit screen, not at invite time), mints a 7-day invite token, and emails the invite link. The role is always server-set to TECHNICIAN. → `201 { id, userId, firstName, lastName, email, userStatus, invitedAt }`. `409` if a user with that email already exists (`"An account already exists for that email address."`) |
| `POST /api/admin/technicians/{id}/invite` | Resends the staff invite for an existing technician profile (`{id}` is the `technician_profile` id, the roster row's `id` — not `userId`). Resolves and checks the target user BEFORE touching any token: only a still-eligible `PENDING_ACTIVATION` `TECHNICIAN` may be re-invited, else `409` (e.g. the roster's "Resend" was clicked against an already-accepted or suspended account — a stale-cache click must not mail that account a fresh, redeemable password-setting link). When eligible: invalidates that user's prior unconsumed `STAFF_INVITE` tokens only (a password reset the same person separately requested is untouched), then mints a fresh one and re-sends the email, in one transaction, so the old link stops working the moment the new one is sent. → `202`. Unknown `id` → `404`; ineligible target → `409` |
| `GET /api/admin/dashboard` | aggregate metrics for the admin home / operational dashboard (#43): `{ activeSubscribers, mrrCents, pendingWalkthroughs, upcomingVisits }`. `mrrCents` sums the current monthly price across ACTIVE subscribers only; `pendingWalkthroughs` counts PENDING (unconfirmed) bookings; `upcomingVisits` counts SCHEDULED visits with `scheduledFor` at or after now. No "at-risk subscribers" field — there is no backing status/column for that concept yet |

All admin mutations write audit rows (Stage 2 formalizes this; log from day 1).

---

## Status codes

`200/201/204` success · `400` validation (envelope above) · `401` missing/expired token ·
`403` wrong role · `404` not found *or not yours* (ownership failures return 404, never 403 —
don't leak existence) · `409` illegal state transition · `429` rate limited · `5xx` + Sentry.
