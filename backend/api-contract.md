# HomeKept · MVP API Contract

The endpoint surface for Stage 1 (weeks 1–8, issues #1–#45). This is the seam between
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
  "leadSource": "WEBSITE_ORGANIC"     // optional, defaults WEBSITE_DIRECT
}
```
→ `201 { "id": 123, "status": "PENDING" }` · Rate limit: 3/IP/hour. Triggers
booking-confirmation email.

### `GET /api/catalog/plans`
Plan tiers for the pricing page.
→ `200 [ { "code": "COMPLETE", "displayName": "Complete", "monthlyPriceCents": 18900, "annualPriceCents": 188900, "visitsPerYear": 12, "description": "...", "services": [ { "name": "Furnace filter swap", "frequencyPerYear": 4 } ] } ]`

### `GET /api/health`
→ `200 { "status": "UP" }` (UptimeRobot target)

---

## Activation (token-authed, not session-authed)

Magic-link flow: walk-through → subscriber. Token is single-use, HMAC-signed, 7-day expiry.

### `POST /api/activation/validate`
`{ "token": "..." }` → `200 { "valid": true, "bookingId": 123, "fullName": "Priya Sharma", "email": "priya@example.com" }`
or `200 { "valid": false, "reason": "EXPIRED" | "USED" | "INVALID" }`

### `POST /api/activation/complete`
`{ "token": "...", "password": "..." }` → creates `User` (CUSTOMER, PENDING_ACTIVATION)
+ `Property` from booking data, consumes token, sets auth cookies.
→ `201 { "userId": 9, "next": "CHECKOUT" }`

---

## Auth

| Endpoint | Body | Result |
|---|---|---|
| `POST /api/auth/login` | `{ email, password }` | `200` + sets cookies · rate limit 5/email/15min |
| `POST /api/auth/refresh` | — (refresh cookie) | `200` + rotated cookies |
| `POST /api/auth/logout` | — | `204`, revokes all refresh tokens |
| `GET /api/auth/me` | — | `200 { id, firstName, lastName, email, role }` |

`POST /api/auth/register` exists for admin/technician bootstrap only at MVP — customers
enter via activation. Not exposed in the UI.

---

## Checkout & billing (role: CUSTOMER)

### `POST /api/checkout/session`
`{ "planCode": "COMPLETE", "billingCycle": "MONTHLY" }`
→ `200 { "checkoutUrl": "https://checkout.stripe.com/..." }` (Stripe-hosted page)

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

| Endpoint | Returns |
|---|---|
| `GET /api/app/subscription` | `{ status, planCode, billingCycle, currentPeriodEnd, property: { streetAddress, city, ... } }` |
| `GET /api/app/visits?status=SCHEDULED&cursor=&limit=` | paginated visits: `{ id, scheduledFor, durationMinutes, status, type, technicianFirstName, services: [{ name, completed }] }` |
| `GET /api/app/visits/{id}` | full visit incl. `completionNotes`, notes list |
| `GET /api/app/activity?cursor=&limit=` | dashboard feed (visit events, billing events, reminders) |

Home Health Score is post-MVP (Stage 3) — the dashboard renders without it until then.

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
| `POST /api/admin/visits/{id}/complete` | `{ completionNotes, services: [{ id, completed, technicianNotes }] }` |

All admin mutations write audit rows (Stage 2 formalizes this; log from day 1).

---

## Status codes

`200/201/204` success · `400` validation (envelope above) · `401` missing/expired token ·
`403` wrong role / not owner · `404` not found or not yours (don't leak existence) ·
`409` illegal state transition · `429` rate limited · `5xx` + Sentry.
