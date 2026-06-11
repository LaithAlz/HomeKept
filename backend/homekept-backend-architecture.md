# HomeKept · Backend Architecture

**Scope:** Complete backend architecture, layered from MVP (10 paying customers) through scale (500+). Decisions are flagged with the stage at which they become relevant. Build only what the current stage demands.

**Tech stack (fixed):** Spring Boot (Java 17+), PostgreSQL, Stripe, SendGrid, deployed on Render or Railway, monitored by Sentry.

---

# Part 1 — Architectural shape and principles

## The shape, in one line

A modular Spring Boot monolith with a single Postgres database, structured by domain. No microservices. No message queue at MVP. No second database. No Redis at MVP.

## Why monolith, not microservices

Microservices solve organizational problems before they solve technical ones. A solo founder building a service business does not have the organizational problem they solve. The cost of microservices at your stage is: 5x deployment complexity, distributed transaction headaches, cross-service auth, observability work, and the ever-present "which service owns this data" question. You get none of those problems from a well-organized monolith until you have multiple engineering teams.

If at year 3 you have a hot subsystem (e.g., a real-time technician routing engine that needs to run separately), you extract it then. Until then, it's all one deployable.

## Domain-driven structure inside the monolith

The codebase is organized **by domain**, not by layer. This is the single most important architectural decision. The alternative ("all controllers in `/controllers`, all services in `/services`") creates a codebase where you can't find anything and everything depends on everything.

```
com.homekept
├── HomeKeptApplication.java
│
├── identity/             — users, auth, sessions, roles
├── property/             — homes, addresses, geo data
├── catalog/              — services, durations, plan tiers
├── subscription/         — subscriber lifecycle, Stripe integration
├── booking/              — walk-through bookings (pre-subscriber)
├── visit/                — visit lifecycle, scheduling, completion
├── technician/           — roster, regions, availability
├── notification/         — email (and later push)
├── billing/              — invoices, payment events (thin wrapper over Stripe)
│
├── common/               — shared utilities, base exceptions, DTOs
├── shared/               — cross-cutting infra (timezone, ID generation)
└── config/               — Spring configuration classes
```

Each domain folder is internally structured as:

```
domain-name/
├── DomainEntity.java          — JPA entity
├── DomainRepository.java      — Spring Data interface
├── DomainController.java      — REST endpoints
├── DomainService.java         — business logic, transactions
├── dto/                       — request and response DTOs
├── exception/                 — domain-specific exceptions
└── (optional) DomainEventHandler.java  — listens to events from other domains
```

**Rule:** a domain may depend on `common` and `shared`. A domain may consume another domain's *interface* (its service class) but should never reach into another domain's entities, repositories, or internals directly. This rule is what makes the eventual extraction (if it ever happens) tractable.

## Layered structure inside each domain

```
Controller       — HTTP boundary, accepts DTOs, returns DTOs
   ↓
Service          — business logic, transactions, orchestration
   ↓
Repository       — data access (Spring Data JPA)
   ↓
Entity           — domain object, persistence mapping
```

DTOs never cross above the controller boundary. Entities never cross below the service boundary (i.e., entities don't go out through controllers — services map them to DTOs). This rule keeps the API stable while the data model evolves.

## What this gets you

- A new feature is one domain folder (or modifications to one), not changes across 7 layered directories
- "Where does subscription cancellation logic live?" has one obvious answer: `subscription/SubscriptionService.java`
- Onboarding a future engineer takes hours, not weeks
- The monolith stays organized indefinitely

---

# Part 2 — Domain modules in detail

## 2.1 `identity` — users, auth, roles

**Responsibilities:** user registration, login, password management, JWT issuance and validation, role-based access control.

**Owns:** `users` table, `refresh_tokens` table.

**Key entities:**
- `User` — id, email, password_hash (bcrypt), first_name, last_name, phone, role, status, created_at
- `RefreshToken` — id, user_id, token_hash, expires_at, revoked_at

**Role enum (string-backed):** `CUSTOMER`, `TECHNICIAN`, `ADMIN`. A user has exactly one role at a given time. If a technician later becomes a customer, that's a second user row with a different role — keep it simple.

**Status enum:** `ACTIVE`, `PENDING_ACTIVATION`, `SUSPENDED`. `PENDING_ACTIVATION` is the state after a walk-through has booked but before they've set a password and paid.

**Auth strategy:**
- Access token: JWT in httpOnly cookie, 15-minute expiry, signed with HS256
- Refresh token: opaque random string in httpOnly cookie, 7-day expiry, stored hashed in `refresh_tokens` table
- Refresh token rotation on every use (issue new, revoke old)
- Logout revokes all refresh tokens for that user

**Why JWT in cookies and not localStorage:** XSS protection. A JS-accessible token is a single XSS bug away from full account takeover. HttpOnly cookies are not perfect either (CSRF risk) but CSRF is much easier to mitigate (SameSite=Lax + same-site API) than XSS.

**Scale considerations:**
- *At 10 customers:* hand-rolled Spring Security is fine
- *At 100 customers:* still fine
- *At 1,000+ customers:* consider extracting auth to a managed provider (Auth0, Clerk, Supabase Auth) — but only if you genuinely don't want to maintain it. The migration cost is real.

---

## 2.2 `property` — homes and addresses

**Responsibilities:** the home being maintained. Address, geo data, basic property facts (sq ft, year built, type), notes for technicians ("dog at home", "lockbox code in app").

**Owns:** `property` table.

**Key entity:** `Property` — id, subscriber_id, street_address, unit, city, postal_code, latitude, longitude, fsa (forward sortation area, the first 3 chars of postal code — your region key), year_built, square_footage_range, property_type, access_notes, created_at, updated_at.

**Why a separate domain from subscription:** because a subscriber can eventually own multiple properties (rental units, second homes), and because property facts are owned by the property, not the subscription. Don't combine them.

**Geo handling:**
- *MVP:* Store lat/long if you have it, but don't require it. FSA (e.g., `L5L`) is the practical region key for assignment.
- *Post-MVP (50+ customers):* Geocode addresses via Google Maps API on creation. Store lat/long for routing.
- *Scale (500+):* Consider PostGIS extension for proper geo queries (within radius, within polygon).

**Scale considerations:** at MVP, "region" is just the FSA. Don't draw polygons. The temptation to over-engineer regions has killed more side projects than any other single instinct.

---

## 2.3 `catalog` — services and plan tiers

**Responsibilities:** the definitions of what HomeKept offers. The list of services (HVAC filter swap, gutter clearing, smoke detector test), their default durations, and the three plan tiers with their pricing and inclusions.

**Owns:** `service` table, `plan_tier` table, `plan_tier_service` join table.

**Key entities:**
- `Service` — id, name, category (HVAC / PLUMBING / EXTERIOR / SMART_HOME), default_duration_minutes, description, is_free_with_every_visit, active
- `PlanTier` — id, code (ESSENTIAL / COMPLETE / PREMIER), display_name, monthly_price_cents, annual_price_cents, visits_per_year, stripe_price_id_monthly, stripe_price_id_annual, description
- `PlanTierService` — plan_tier_id, service_id, frequency_per_year

**Why prices in cents:** floating-point currency arithmetic is the single most common source of "we charged you the wrong amount" bugs. Integer cents, always.

**Why Stripe price IDs live here:** this is the source of truth for "Complete plan, monthly billing → Stripe price `price_1Abc...`". Anywhere your code reaches for a Stripe price, it goes through `catalog`.

**Scale considerations:**
- *MVP:* All catalog data seeded via Flyway migrations. You change services by writing a migration. Slow but safe.
- *Post-MVP:* Add a basic admin UI to toggle service `active` flag and add new services. Don't allow plan tier price changes through admin — those are migrations.

---

## 2.4 `subscription` — the subscriber lifecycle

**Responsibilities:** representing an active paying customer. The single most important domain in your codebase, because if it's wrong, you bill people wrong.

**Owns:** `subscriber` table, `subscription_event` table.

**Key entities:**
- `Subscriber` — id, user_id (FK to identity), property_id (FK to property), plan_tier_id, status, stripe_customer_id, stripe_subscription_id, current_period_start, current_period_end, billing_cycle, started_at, paused_at, paused_until, cancelled_at, created_at, updated_at
- `SubscriptionEvent` — id, subscriber_id, event_type, payload (JSONB), processed_at, source (STRIPE_WEBHOOK / MANUAL / SYSTEM)

**Status state machine (this is sacred — see Part 4):**

```
PENDING_ACTIVATION → ACTIVE
ACTIVE → PAUSED → ACTIVE
ACTIVE → PAYMENT_ISSUE → ACTIVE  (when payment retry succeeds)
ACTIVE → PAYMENT_ISSUE → CANCELLED  (when retries are exhausted)
ACTIVE → CANCELLED
PAUSED → CANCELLED
```

**Stripe as source of truth:**
- Stripe owns: payment methods, invoices, payment history, retry logic, dunning
- HomeKept owns: which user owns which subscription, which plan they're on, which property the subscription serves, which visits they're entitled to

Never duplicate what Stripe owns. When you need to know "did this customer pay last month," ask Stripe (or your local cache of webhook events). When you need to know "which property does this subscription serve," only HomeKept knows.

**Webhook handling philosophy (this is your most-debugged code, plan for it):**
- Verify signature on every event (Stripe libraries do this for you, don't roll your own)
- Be idempotent: store every event ID in `subscription_event` and short-circuit duplicates
- Be fast: return 200 within 2 seconds, queue heavy work
- Log every event, processed or not, with full payload — Sentry breadcrumbs
- Handle out-of-order delivery: Stripe does not guarantee event order

**Events you must handle:**

| Event | Action |
|---|---|
| `checkout.session.completed` | Create or activate subscriber row |
| `customer.subscription.updated` | Sync plan tier, period dates |
| `customer.subscription.deleted` | Set status CANCELLED |
| `invoice.payment_failed` | Set status PAYMENT_ISSUE, notify customer |
| `invoice.payment_succeeded` | If PAYMENT_ISSUE, restore to ACTIVE |
| `customer.subscription.paused` | Set status PAUSED |
| `customer.subscription.resumed` | Set status ACTIVE |

**Events to ignore (explicitly):** `customer.created`, `customer.updated`, `invoice.created` — these create noise without changing state.

**Scale considerations:**
- *MVP:* Synchronous webhook processing. Webhook controller calls service directly.
- *Post-MVP (50+):* Move webhook processing to a background queue. Webhook controller writes to `subscription_event` and returns 200 immediately. Background worker processes the event. This handles Stripe burst traffic and gives you retry semantics.
- *Scale (500+):* Same model, but add observability: webhook lag metrics, dead-letter queue for events that fail permanently.

---

## 2.5 `booking` — walk-through bookings

**Responsibilities:** the pre-subscriber world. Handling walk-through booking form submissions from the public site, tracking their status through to conversion (or no-conversion).

**Owns:** `walkthrough_booking` table.

**Key entity:** `WalkthroughBooking` — id, full_name, email, phone, street_address, city, postal_code, year_built, square_footage_range, property_type, preferred_week, time_of_day, day_preferences, notes, status, scheduled_for, performed_at, converted_to_subscriber_id, activation_token_id, lead_source, created_at, updated_at.

**Status enum:**
- `PENDING` — booked, not yet contacted
- `CONFIRMED` — you've reached out, time is locked
- `PERFORMED` — walk-through happened
- `CONVERTED` — became a subscriber (links to subscriber row)
- `LOST` — explicitly declined
- `NO_SHOW` — they didn't answer the door

**Lead source enum:** `NEXTDOOR`, `FACEBOOK_GROUP`, `REFERRAL`, `DOOR_KNOCK`, `WEBSITE_ORGANIC`, `WEBSITE_DIRECT`, `OTHER`. Track this from day 1, even if it's "OTHER" for everyone. By customer #50 you'll desperately wish you knew what channel was working.

**Activation tokens:** the magic-link flow for walk-through → subscriber conversion. Each walk-through gets a single-use HMAC-signed token when you send the activation invite. Token includes booking_id, expiry (7 days), and a nonce stored in a `activation_token` table.

**Why bookings are a separate domain from subscriptions:** because a booking is a *lead*, not a customer. Half of them never convert. Mixing them with the subscriber data model would make your CRM-style admin views painful.

**Scale considerations:**
- *MVP:* Just a form-to-database. Manual follow-up.
- *Post-MVP (50+):* Automated reminder emails for unconverted walk-throughs. SMS reminder 24h before scheduled walk-through.
- *Scale (500+):* Real CRM-style pipeline view, conversion funnel analytics, source attribution by spend.

---

## 2.6 `visit` — the core service domain

**Responsibilities:** scheduled and completed maintenance visits. This is what the business *is*. Every paying subscriber generates 4-24 visits per year; this is the most-touched table in the database.

**Owns:** `visit` table, `visit_service` table, `visit_photo` table (deferred), `visit_note` table.

**Key entities:**
- `Visit` — id, subscriber_id, property_id, technician_id, scheduled_for, duration_minutes, status, type, completion_notes, completed_at, created_at, updated_at
- `VisitService` — id, visit_id, service_id (FK to catalog), completed, completed_at, technician_notes
- `VisitNote` — id, visit_id, author_user_id, body, created_at
- `VisitPhoto` (deferred) — id, visit_id, storage_url, caption, taken_at

**Status state machine (see Part 4 for full diagram):**

```
SCHEDULED → IN_PROGRESS → COMPLETED
SCHEDULED → RESCHEDULED   (creates new SCHEDULED row, marks old one cancelled)
SCHEDULED → CANCELLED
IN_PROGRESS → INCOMPLETE   (technician couldn't finish)
IN_PROGRESS → COMPLETED
```

**Visit types:** `ROUTINE` (part of plan), `EXTRA` (subscriber-requested add-on), `WARRANTY` (re-do of a recent visit), `WALKTHROUGH` (the pre-subscription assessment — yes, it lives here too).

**Scheduling logic:**
- *MVP:* manual. Admin picks a date and a technician from dropdowns.
- *Post-MVP (50+):* a "suggest next visit" function. Looks at the subscriber's plan tier (cadence), last visit date, season, and proposes a date. Admin confirms.
- *Scale (200+):* auto-assignment engine. Takes subscriber's FSA + technician availability + technician region + plan tier + last-technician (Premier dedicated tech), returns a recommended assignment. Admin overrides as needed.

**The auto-assignment engine, when you build it:**
- Phase 1: filter technicians to those serving the property's FSA
- Phase 2: filter to those with availability in the visit's time window
- Phase 3: rank by load balance (least visits assigned this week) and prior relationship (same technician if Premier)
- Returns top 3 candidates; admin picks

**Scale considerations:**
- *MVP:* the visit table is small (<500 rows). Indexes on `subscriber_id`, `scheduled_for`, `status` are plenty.
- *Post-MVP:* add composite index on `(technician_id, scheduled_for)` for daily technician schedule queries.
- *Scale (500+ subscribers, 5,000+ visits/year):* partition `visit` table by year. Move `visit_photo` storage to S3/R2 with signed URL access (never serve photos through your backend).

---

## 2.7 `technician` — workforce management

**Responsibilities:** technician roster, regions they cover, availability, skills.

**Owns:** `technician_profile` table, `technician_region` table, `technician_availability` table.

**Key entities:**
- `TechnicianProfile` — id, user_id (FK to identity), employee_status, hire_date, fully_loaded_hourly_cost_cents, vehicle_info, notes
- `TechnicianRegion` — id, technician_id, fsa, priority (1 = primary region, 2 = secondary, 3 = overflow)
- `TechnicianAvailability` — id, technician_id, day_of_week, start_time, end_time, effective_from, effective_until

**Why hourly cost in cents lives here:** because unit economics analysis needs to know "Marcus costs $43/hr fully-loaded, this visit took him 1.5 hours, the customer pays $189/month for 12 visits — what's the margin?" Don't store the hourly cost in HR/payroll only. The backend needs it for cost reporting.

**Scale considerations:**
- *MVP:* you're the only technician. Skip this entire domain or stub with one row.
- *Post-MVP (5+ technicians):* build it properly. Add the availability check to the assignment engine.
- *Scale (20+ technicians):* add skill tags (HVAC-certified, smart-home-trained), capacity planning views, time-off requests.

---

## 2.8 `notification` — email, push, SMS

**Responsibilities:** all outbound communication. Encapsulates SendGrid / FCM / Twilio so the rest of the codebase only knows "send a notification of type X to user Y."

**Owns:** `notification_template` table, `notification_log` table.

**Key abstractions:**

```java
public interface NotificationService {
    void send(NotificationRequest request);
}

public record NotificationRequest(
    Long userId,                // who
    NotificationType type,      // what (BOOKING_CONFIRMATION, VISIT_REMINDER, etc.)
    Map<String, Object> data,   // template variables
    NotificationChannel channel // EMAIL, SMS, PUSH — derived from type+user prefs
) {}
```

**Why an interface-based abstraction matters here:** because at MVP you have SendGrid only. At post-MVP you add FCM push. At scale you add SMS. Calling code shouldn't change every time.

**Notification log:** every send is logged. Failed sends are retried (with exponential backoff). This is non-negotiable — when a customer says "I never got the email," you need an audit trail.

**Scale considerations:**
- *MVP:* synchronous send. `EmailService.send(...)` blocks the HTTP request until SendGrid returns.
- *Post-MVP (50+ customers):* asynchronous send via Spring's `@Async` annotation. Don't make the API wait for SendGrid.
- *Scale (500+ customers):* durable queue (Jobrunr or similar). Survive backend restarts mid-send.

**Templates strategy:**
- *MVP:* hardcoded HTML strings in a `templates/` resources folder
- *Post-MVP:* templates stored in DB, edit via admin UI (so non-technical co-founders can adjust copy)
- *Scale:* MJML for templating, A/B testing, localization (French for QC expansion)

---

## 2.9 `billing` — invoices and payment events

**Responsibilities:** a thin domain on top of Stripe. Tracks invoices, payment receipts, refunds — only the data HomeKept needs to surface in the admin and customer dashboards.

**Owns:** `invoice` table, `payment_event` table.

**Key entities:**
- `Invoice` — id, subscriber_id, stripe_invoice_id, amount_cents, status, paid_at, period_start, period_end, created_at
- `PaymentEvent` — id, subscriber_id, stripe_event_id, event_type, amount_cents, occurred_at, raw_payload (JSONB)

**Why separate from `subscription`:** because subscriptions are a *state* (currently active) and invoices are *history* (a record of past charges). They're different access patterns. Don't mash them.

**Source of truth:** Stripe. This domain is essentially a local cache of Stripe data for fast queries (don't hit Stripe API every time the customer dashboard loads).

**Reconciliation:**
- *MVP:* trust Stripe webhooks. If a webhook is lost, you'll notice when a customer reports a missing receipt.
- *Post-MVP:* nightly reconciliation job that fetches the last 24h of Stripe events and ensures every one has a matching row locally. Logs discrepancies to Sentry.
- *Scale:* full daily reconciliation report. Catches discrepancies before customers do.

---

# Part 3 — The data model

## ERD overview

```
users (identity)
  ├── 1:1 → technician_profile (technician)
  └── 1:N → subscriber (subscription)

subscriber
  ├── 1:1 → user
  ├── 1:1 → property
  ├── N:1 → plan_tier (catalog)
  ├── 1:N → visit
  ├── 1:N → invoice
  └── 1:N → subscription_event

property
  └── 1:N → visit

visit
  ├── N:1 → subscriber
  ├── N:1 → property
  ├── N:1 → technician (optional)
  ├── 1:N → visit_service
  ├── 1:N → visit_note
  └── 1:N → visit_photo (deferred)

walkthrough_booking (pre-subscription)
  ├── 0:1 → subscriber (after conversion)
  └── 0:1 → activation_token

catalog
  ├── plan_tier 1:N → plan_tier_service N:1 → service
  └── service 1:N → visit_service (back-reference)
```

## Key cross-domain relationships

- `subscriber.user_id` → `users.id` (the customer's login)
- `subscriber.property_id` → `property.id` (the home being maintained)
- `visit.subscriber_id` → `subscriber.id` (who owns this visit)
- `visit.property_id` → `property.id` (denormalized for query speed; in 99% of cases matches the subscriber's property, but allows multi-property subscribers later)
- `visit.technician_id` → `users.id` (technician role)

## Conventions across all tables

**Primary keys:** `BIGSERIAL` everywhere. UUIDs are tempting but cost you in index size and query speed. At your scale, sequential bigints are the right call. If you need external-facing IDs that aren't guessable, add a separate `public_id` column.

**Timestamps:** every table has `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Most tables also have `updated_at` (managed by trigger or by JPA `@LastModifiedDate`). All timestamps stored UTC; rendered in `America/Toronto` at the application layer.

**Soft delete:** use `archived_at TIMESTAMPTZ` (nullable) rather than `deleted_at`. "Deleted" is final-sounding; "archived" admits the reality that you'll need to un-archive things sometimes. Don't hard-delete anything except in explicit GDPR/CASL compliance flows (when a user requests data deletion).

**Money:** always integers in cents. Never doubles, never `BigDecimal` stored as float, never strings. `amount_cents INTEGER` everywhere.

**Enums:** stored as strings, not integers. `status VARCHAR(20) NOT NULL`. Spring maps them to Java enums via `@Enumerated(EnumType.STRING)`. This costs ~20 bytes per row and saves you from "what was status code 3 again?" forever.

**JSONB:** allowed in two places only — `subscription_event.payload` and `payment_event.raw_payload`. These are append-only logs of external system events. Everywhere else, structure your data into columns.

**Foreign keys:** always declared in the database (`REFERENCES`), always with explicit `ON DELETE` behavior (usually `RESTRICT`, occasionally `CASCADE` for child records like `visit_service`).

**Indexes:** every foreign key gets an index (Postgres doesn't auto-create these). Every column that appears in a `WHERE` clause in a hot query gets an index. Don't index everything preemptively — measure first.

## Migrations strategy

Flyway, versioned migrations in `src/main/resources/db/migration/`. Naming: `V{version}__{description}.sql`.

**Rules:**
- Never edit a migration after it's been deployed
- Never use `ddl-auto: update` — always `validate`
- Schema changes that hold long locks (adding NOT NULL columns to large tables, adding indexes) use `CONCURRENTLY` and are split into two migrations: V47 adds the column nullable, V48 backfills, V49 adds the constraint
- Data migrations (renaming enums, etc.) go in the same Flyway pipeline. Don't run them manually.

**Backup before any migration in production.** Render and Railway both do automated daily backups; verify yours is working before week 1 ends.

---

# Part 4 — State machines

State machines are the single most-bugged part of any service business backend. Three of them matter for HomeKept. Each gets a `StateMachine<EnumType>` class with one method: `canTransition(from, to)`. **Every state change in the entire codebase routes through this class.** No exceptions.

## 4.1 Subscriber lifecycle

```
PENDING_ACTIVATION ──→ ACTIVE
                         │
                         ├──→ PAUSED ──→ ACTIVE
                         │       └────→ CANCELLED
                         │
                         ├──→ PAYMENT_ISSUE ──→ ACTIVE
                         │                  └─→ CANCELLED
                         │
                         └──→ CANCELLED  (terminal)
```

**Notes:**
- `PENDING_ACTIVATION` → `CANCELLED` is also legal (token expired, never paid)
- `CANCELLED` is terminal — never transitions out. A returning customer is a new subscriber row.

## 4.2 Visit lifecycle

```
SCHEDULED ──→ IN_PROGRESS ──→ COMPLETED  (terminal)
    │              │
    │              └────→ INCOMPLETE  (terminal — flag for follow-up)
    │
    ├──→ CANCELLED  (terminal)
    │
    └──→ RESCHEDULED  (terminal — but creates a new SCHEDULED visit)
```

**Notes:**
- `RESCHEDULED` is a marker, not a continuation. The old row stays as RESCHEDULED, a new row is created in SCHEDULED. This preserves history.
- `INCOMPLETE` is the "technician couldn't finish, customer needs a follow-up visit" state. A new visit gets auto-created in SCHEDULED.

## 4.3 Walk-through booking lifecycle

```
PENDING ──→ CONFIRMED ──→ PERFORMED ──→ CONVERTED  (terminal)
    │           │             │
    │           │             └────→ LOST  (terminal)
    │           │
    │           └────→ NO_SHOW  (terminal — could re-book later as new PENDING)
    │
    └────→ LOST  (terminal — they declined before walk-through)
```

## Why all state machines live in `shared` or their own domain

Each state machine class lives next to its entity (`subscription/SubscriberStateMachine.java`, etc.). Not in a generic `shared/` location. Because the rules are domain-specific, and `shared` should only hold things genuinely shared across all domains.

---

# Part 5 — Cross-cutting concerns

## 5.1 Security

**Authentication:** JWT in httpOnly cookies (see `identity`). All `/api/*` endpoints require a valid access token except:
- `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`
- `/api/bookings/walkthrough` (public form submission)
- `/api/webhooks/stripe` (signed by Stripe, not session-authed)
- `/api/activation/validate` (token-authed, not session)
- `/api/health` (no auth)

**Authorization:** `@PreAuthorize` annotations on every protected endpoint, explicitly. Don't rely on filter-based auth alone. Pattern:
- `@PreAuthorize("hasRole('CUSTOMER')")` for `/app/*`
- `@PreAuthorize("hasRole('TECHNICIAN')")` for `/tech/*`
- `@PreAuthorize("hasRole('ADMIN')")` for `/admin/*`
- `@PreAuthorize("hasRole('ADMIN') or @subscriberSecurity.isOwner(#id, authentication)")` for resources a customer can only see if they own them

**Rate limiting:**
- *MVP:* none on most endpoints. Add to `/api/auth/login` (5 attempts per email per 15 minutes) and `/api/bookings/walkthrough` (3 submissions per IP per hour).
- *Post-MVP:* Bucket4j with Redis backing.
- *Scale:* Cloudflare or a dedicated WAF in front of the API.

**Secrets:** never in code, never in git. Environment variables only. Use Render / Railway secrets management. Have a documented rotation procedure for the Stripe keys and JWT signing key (you won't follow it, but write it).

**HTTPS:** enforced. Render gives you HTTPS by default; verify HSTS header is set.

**CORS:** restricted to your production frontend domain plus localhost for dev. No wildcards in production.

**Cross-origin auth (frontend on Cloudflare, API on Render):**
- Serve the API at `api.homekept.ca` (CNAME to Render), never at the raw `*.onrender.com` URL. `homekept.ca` and `api.homekept.ca` are different *origins* but the same *site*, so `SameSite=Lax` cookies still flow on `fetch` calls made with `credentials: "include"`.
- Auth cookies are host-only on `api.homekept.ca` (no `Domain` attribute), `HttpOnly`, `Secure`, `SameSite=Lax`.
- CORS config allows exactly `https://homekept.ca` (plus the localhost dev origin) with `Access-Control-Allow-Credentials: true` — credentialed CORS cannot use wildcards.
- Local dev mirrors this: frontend on `localhost:5173`, API on `localhost:8080` — same-site, same cookie rules.
- Escape hatch if cookie friction ever appears: proxy `/api/*` through the Cloudflare Worker to Render so the browser sees a single origin. Don't build this preemptively.

**Input validation:** Bean Validation (`@NotNull`, `@Email`, `@Size`, `@Pattern`) on every DTO. Centralized error handler that returns structured error responses.

**SQL injection:** JPA / Spring Data handles this for you. The risk is if you ever drop to raw SQL — at that point, parameterized queries always, never string concatenation.

**XSS:** the backend doesn't render HTML to users (the React frontend does), so XSS is primarily a frontend concern. Backend role: never store user input as raw HTML, sanitize anything that *could* be rendered (visit notes, etc.) with OWASP Java HTML Sanitizer if needed.

## 5.2 Observability

**Logging:**
- Structured JSON logs (Logback with `logstash-logback-encoder`)
- Log levels: ERROR for things that need human attention, WARN for things to watch, INFO for state changes, DEBUG for everything else
- Never log secrets, passwords, card numbers, full Stripe events (PII), or user PII beyond what's necessary

**Error tracking:** Sentry, every environment. Sentry auto-captures uncaught exceptions; manually capture errors caught in webhook handlers and similar critical paths.

**Metrics:**
- *MVP:* none. Server logs + Sentry are enough.
- *Post-MVP:* Spring Boot Actuator endpoints (health, metrics, info) exposed internally only. Track HTTP latency, webhook processing time, email send success rate.
- *Scale:* Prometheus + Grafana, or a managed APM (Datadog, New Relic).

**Uptime monitoring:** UptimeRobot (free) hitting `/api/health` every 5 minutes from day 1. Pages your phone when the API is down.

**Audit logging:** for admin actions (create subscriber, cancel subscription, mark visit complete), write an audit row. Eventually this saves you in disputes ("did anyone change this customer's plan?").

## 5.3 Background jobs

**MVP approach:** Spring's `@Scheduled` annotation. One method, runs every 15 minutes, does whatever's needed (send reminder emails, retry failed webhooks, etc.).

**Limitations of `@Scheduled`:**
- Jobs are lost on backend restart
- No retry logic
- No visibility into past job runs

**Post-MVP approach:** Jobrunr. Adds:
- Durable storage of pending jobs in Postgres
- Automatic retry with backoff
- Dashboard UI showing job history

**Job types you'll have:**

| Job | Trigger | Stage |
|---|---|---|
| Send walk-through reminder 24h before | scheduled | MVP |
| Send visit reminder 24h before | scheduled | MVP |
| Auto-schedule next visit after completion | event | MVP |
| Retry failed email sends | scheduled | Post-MVP |
| Reconcile Stripe events nightly | scheduled | Post-MVP |
| Auto-pause subscribers with 3+ failed payment attempts | event | Post-MVP |
| Generate monthly MRR report | scheduled | Post-MVP |
| Re-engage unconverted walk-through leads | scheduled | Post-MVP |

## 5.4 Caching

**MVP:** none. Postgres is fast for your scale. Don't add Redis until you actually have a performance problem.

**Post-MVP (when needed):** Redis for:
- Session storage (if you outgrow database-backed refresh tokens)
- Rate limiting counters
- Stripe customer/subscription cache (avoid hitting Stripe API for read-heavy paths)

**Scale:** consider CDN caching for static API responses (catalog data, plan tiers, public assets).

## 5.5 Time zones

Every timestamp in the database is `TIMESTAMPTZ` (timestamp with time zone), stored UTC.

Every timestamp in Java is `Instant` (or `OffsetDateTime` if you really need the offset).

Every timestamp rendered to a user is converted to `America/Toronto` at the application layer.

The string `America/Toronto` is configured in one place (`application.yml`) and referenced everywhere via a `TimeZoneConfig` bean. Never hardcode `"America/Toronto"` in business logic — it will bite you when you expand to Calgary.

## 5.6 API design

**REST conventions:**
- Nouns for resources, verbs for actions (sparingly): `POST /api/visits/{id}/complete` is fine; `POST /api/complete-visit` is not
- HTTP status codes used correctly: 200/201 for success, 4xx for client errors with structured body, 5xx for server errors
- Pagination: cursor-based (`?cursor=abc&limit=50`) over offset-based. Offset pagination breaks under concurrent inserts.
- Consistent error format:

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

**Versioning:**
- *MVP:* none. Single version of the API.
- *Post-MVP:* if you ever break compatibility, version via URL path (`/api/v2/...`). Avoid this if you can — backward-compatible additions are usually possible.

**Documentation:**
- *MVP:* none beyond inline JavaDoc.
- *Post-MVP:* OpenAPI / Swagger auto-generated from controllers via `springdoc-openapi`. Hosted at `/api/docs`, admin-only.

---

# Part 6 — Integration boundaries

## 6.1 Stripe

**Owns externally:** payment methods, invoices, subscription billing logic, dunning, tax calculation.

**HomeKept owns locally:** which user holds which subscription, plan-to-Stripe-price mapping, local cache of subscription state.

**Data flow:**

```
Subscriber creation:
  HomeKept user wants to subscribe
    → POST /api/checkout/session creates Stripe Checkout Session
    → User redirected to Stripe-hosted page
    → User pays
    → Stripe webhook: checkout.session.completed
    → HomeKept creates subscriber row
    → HomeKept fires "subscription started" notification

Plan change / cancellation:
  HomeKept user clicks "manage billing"
    → POST /api/billing/portal-session creates Stripe Portal Session
    → User redirected to Stripe-hosted page
    → User changes plan / cancels
    → Stripe webhook: customer.subscription.updated or deleted
    → HomeKept syncs subscriber state

Payment failure:
  Stripe attempts charge, fails
    → Stripe webhook: invoice.payment_failed
    → HomeKept sets subscriber.status = PAYMENT_ISSUE
    → HomeKept sends notification to user
    → Stripe retries on its schedule (configured in Stripe dashboard)
    → On eventual success: invoice.payment_succeeded → status back to ACTIVE
    → On retry exhaustion: customer.subscription.deleted → status CANCELLED
```

**Idempotency keys:** every write to Stripe (creating a checkout session, etc.) includes an idempotency key. The key is a deterministic hash of the operation. Repeated calls return the same result instead of creating duplicates.

**Test mode strictly separated:** never mix test and live Stripe data. Use separate environment configurations.

## 6.2 SendGrid

**Owns externally:** SMTP delivery, deliverability, bounce/complaint handling.

**HomeKept owns:** what email gets sent, to whom, when, the template content.

**Data flow:**

```
NotificationService.send(request)
  → render template with data
  → SendGridClient.send(rendered, recipient)
  → on success: log to notification_log
  → on failure: log error, enqueue retry (post-MVP)
```

**Bounce handling:**
- *MVP:* manual. You check SendGrid dashboard occasionally.
- *Post-MVP:* webhook from SendGrid → mark user's email as bouncing → admin alert.

## 6.3 Google Places / Maps

**MVP:** not integrated. Address is plain text input.

**Post-MVP:** Google Places Autocomplete on the booking form. Geocode addresses to lat/long. Store API key in environment.

**Cost:** Places Autocomplete + Geocoding is ~$5 per 1,000 requests. At your scale, $20/month max.

## 6.4 File storage (visit photos, deferred)

**Approach when built:**
- Files stored in Cloudflare R2 (S3-compatible, no egress fees)
- Backend never serves files directly — only generates signed URLs
- Upload flow: frontend requests signed upload URL from backend → frontend uploads directly to R2 → frontend tells backend the upload completed
- Download flow: backend generates short-lived (15 min) signed download URL → frontend uses it

**Why never serve files through the backend:** photo downloads at scale will saturate your backend's bandwidth. R2 is built for this; your Spring Boot instance isn't.

---

# Part 7 — Deployment and infrastructure

## 7.1 Environments

**MVP:**
- `local` — your laptop, local Postgres, local Stripe CLI for webhook forwarding
- `staging` — a single small Render service + small Postgres, used for E2E testing before prod deploys
- `production` — a single Render service + Postgres with daily backups

**Post-MVP additions:** none structurally. Same three environments, possibly larger instances.

**Scale:** add a second production region (for failover) only when you have customers across multiple time zones complaining about latency. Likely never relevant for a GTA-only business.

## 7.2 Deployment pipeline

**MVP:** GitHub Actions on push to `main`:
1. Run tests (must pass)
2. Build Docker image
3. Deploy to staging
4. (Manual gate) deploy to production via tag

**Post-MVP:** add:
- Automated smoke tests against staging before allowing production deploy
- Slack notifications on deploy success/failure
- Easy one-click rollback (Render's deploy history makes this trivial)

## 7.3 Database

**MVP:**
- Single Postgres instance on Render (shared resource, cheapest tier)
- Daily automated backups (Render does this automatically; verify)
- No read replicas

**Post-MVP (50+ customers):**
- Dedicated Postgres instance (not shared resource)
- Verify backup restoration works (do a test restore quarterly)
- Add connection pooling via PgBouncer if connection counts grow

**Scale (500+ customers):**
- Read replica for analytics queries
- Partition `visit` and `visit_photo` tables by year
- Consider managed Postgres (RDS, Supabase) for the operational ease

## 7.4 Connection pooling

Spring Boot defaults to HikariCP with sensible settings. At your scale, the defaults are fine. If you start seeing "too many connections" errors at scale, tune the pool size and consider PgBouncer.

---

# Part 8 — Testing strategy

## 8.1 The pyramid

```
                  ▲
                  │  E2E tests (manual at MVP, automated post-MVP)
                  │  ~5 critical paths
                  │
                  │  Integration tests (Spring Boot Test)
                  │  Every service method that touches the database
                  │  ~50-100 tests
                  │
                  │  Unit tests
                  │  Every state machine, every validator, every utility
                  │  ~200+ tests
                  ▼
```

## 8.2 What to test

**Always:**
- Every state machine transition (legal and illegal)
- Every Stripe webhook handler (using fixture event payloads from Stripe docs)
- Every service method that mutates data (idempotency, edge cases)
- Every validator and converter

**Sometimes:**
- Controllers (only if they have non-trivial logic beyond service delegation)
- DTOs (only if they have non-trivial mapping)

**Never:**
- Spring Data repositories (they're generated)
- Trivial getters/setters
- Configuration classes

## 8.3 Test data

Use Testcontainers for integration tests — runs a real Postgres in Docker during tests. Mocking the database leads to tests that pass when the code is broken. Real Postgres in tests is non-negotiable.

For test fixtures, write a `TestDataBuilder` per domain. Hand-write these — they're how you set up complex scenarios cleanly.

## 8.4 E2E tests

**MVP:** manual. You run through the booking → activation → checkout flow before every deploy.

**Post-MVP:** Playwright tests for the 5 critical paths: book walk-through, activate to subscriber, schedule visit, complete visit, cancel subscription.

---

# Part 9 — What you're NOT building, and when you might

The fastest way to fail at building a service business is to architect for Year 5 in Month 1. Here's the explicit list of things this architecture deliberately omits, and the trigger for each:

| Thing | Why not now | Trigger to add it |
|---|---|---|
| Microservices | Solves problems you don't have | Multiple teams (3+ engineers per service) |
| Kafka / RabbitMQ | Overkill for your event volume | >10K events/day, or genuine pub/sub need |
| Redis | Postgres is fast enough | Real measured cache hit rate justification |
| GraphQL | REST is fine | A real second consumer with different needs |
| Event sourcing | Complexity tax | You actually need to time-travel state |
| CQRS | Premature | Read/write workloads are truly asymmetric |
| Polyglot persistence | One DB is one thing to operate | A genuine non-relational workload appears |
| Service mesh | You have one service | You have many |
| Kubernetes | Render handles deployment | You need multi-cloud or strict isolation |
| Multi-region | You serve one metro | You serve multiple metros / international |
| Custom DSL for plan rules | YAGNI | Plan rules become a daily mutation rather than annual |
| ML for technician routing | Postgres + JOINs work fine | >100 visits/day with measurable inefficiency |
| Customer-facing API | Not your business model | A genuine integration partner appears |

For each row above: if someone (including future-you) argues for adding the thing without the trigger condition, push back hard. Architectural debt is real, but premature architecture is also debt — a heavier kind.

---

# Part 10 — The 12-month roadmap

The shape of what gets built when, anchored to customer counts.

## Stage 1 — MVP (0-10 paying customers, weeks 1-8 of build)

Domains built: `identity`, `property`, `catalog`, `subscription` (basic), `booking`, `visit` (basic), `notification` (email only).

State machines built and tested: subscriber, visit (basic), walk-through booking.

Integrations live: Stripe Checkout, Stripe webhooks (5 events), SendGrid.

Deployment: Render + Postgres + Cloudflare Workers (frontend) + Sentry.

Manual everything: assignment, scheduling, follow-ups.

## Stage 2 — Operational scale (10-50 customers, months 3-6)

Additions:
- Async email sending (`@Async`)
- Scheduled jobs (`@Scheduled`): reminders, reconciliation
- Admin UI for visit scheduling and assignment
- Visit photo uploads to R2
- Google Places autocomplete for addresses
- Audit logging for admin actions
- Bounce handling from SendGrid webhooks

State machines refined: visit lifecycle gains `IN_PROGRESS` and `INCOMPLETE` states fully wired through admin and technician views.

## Stage 3 — Workforce scale (50-150 customers, months 6-12)

Additions:
- `technician` domain fully built out: roster, regions, availability
- Auto-assignment engine (three-phase regional matching)
- Technician PWA at `/tech/*`
- Push notifications via FCM
- Home Health Score algorithm + dashboard
- Jobrunr for durable background jobs
- Bucket4j for rate limiting
- Read-replica Postgres for analytics

Major operational shift: you stop being the technician. The architecture must support this.

## Stage 4 — Multi-area scale (150-500 customers, year 2)

Additions:
- Expand region beyond Oakville/Mississauga/Milton
- Multi-property subscribers
- Referral program with attribution tracking
- Customer self-service: pause, plan change, request extra visit
- Webhook processing moved fully async with dead-letter queue
- OpenAPI documentation
- Performance budgets and SLOs

## Stage 5 — Platform considerations (500+ customers, year 2-3)

Only at this stage does the question "do we need to extract any of this into a separate service?" become real. Likely candidates if so:
- Technician routing/dispatch (latency-sensitive, separate scaling needs)
- Reporting and analytics (heavy reads, separate database)

Until you have actual evidence — measurable problems with the monolith — the answer is no.

---

# Final note on architectural discipline

Every line of this document represents a decision. Each decision is defensible *for your stage*. If you find yourself reading something and thinking "but what about [edge case]," ask: is the edge case real now, or am I imagining it for the year-3 version?

Imagined edge cases are how solo founders end up with a 200-table schema, a Kubernetes cluster, and three paying customers.

Build the smallest thing that solves the problem in front of you. Re-architect when the problem in front of you genuinely changes. The architecture above is designed to make those re-architecturings cheap when they come — domain boundaries are clean, state machines are explicit, integrations are abstracted. That's the gift you're giving your future self.

Now write the V1 migration and ship.
