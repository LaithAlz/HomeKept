# HomeKept · Architecture and Decisions

**What this document is.** A single navigable reference to how the HomeKept system is
actually built: the stack, the domain boundaries, the data model, the live API surface,
the security model, the frontend, the integrations, the tests, and the load-bearing "why"
decisions behind them. Every claim here was verified against the code, not assumed. Where
the code disagrees with the design docs, this file says so (see
[§13 Where code and the design docs disagree](#13-where-code-and-the-design-docs-disagree))
so the founder can reconcile.

This is a companion to, not a replacement for, the documents it references. Business rules
live in `docs/pricing-and-visits.md`. The intended design and staged roadmap live in
`backend/homekept-backend-architecture.md`. The frontend/backend seam is specified in
`backend/api-contract.md`. Project rules and non-negotiables live in `CLAUDE.md`. This
document describes the *as-built* system and the reasoning captured along the way.

**Last verified.** Commit `255ffb0` (branch `architecture-docs`, branched from `main` at
the same commit): "Add forgot/reset password flow; fix api.ts empty-body 2xx handling
(#98) (#116)". Backend and frontend both at that state. Migrations present on disk: V1
through V9.

---

## Table of contents

1. [System overview and stack](#1-system-overview-and-stack)
2. [Domain model and boundaries](#2-domain-model-and-boundaries)
3. [Data model and migrations](#3-data-model-and-migrations)
4. [API surface](#4-api-surface)
5. [Auth and security model](#5-auth-and-security-model)
6. [Frontend architecture](#6-frontend-architecture)
7. [Integrations](#7-integrations)
8. [Testing strategy](#8-testing-strategy)
9. [Key decisions and rationale](#9-key-decisions-and-rationale)
10. [Known gaps and follow-ups](#10-known-gaps-and-follow-ups)
11. [Founder-only boundaries](#11-founder-only-boundaries)
12. [Configuration and secrets reference](#12-configuration-and-secrets-reference)
13. [Where code and the design docs disagree](#13-where-code-and-the-design-docs-disagree)
14. [How to keep this current](#14-how-to-keep-this-current)

---

## 1. System overview and stack

A modular Spring Boot monolith with a single Postgres database, structured by domain, plus
a TanStack Start (React) frontend deployed to Cloudflare. No microservices, no message
queue, no Redis at this stage. See `backend/homekept-backend-architecture.md` Part 1 for
the reasoning; this section records the versions actually pinned.

### Backend (`backend/build.gradle`)

| Component | Version | Notes |
|---|---|---|
| Java | 17 | Gradle toolchain `languageVersion = 17` |
| Spring Boot | 4.1.0 | `org.springframework.boot` plugin |
| Spring Security | 7.1.0 | Managed transitively by Boot 4.1.0 (verified via resolved Gradle cache) |
| Spring Data JPA / Hibernate | Boot-managed | `ddl-auto: validate`, `open-in-view: false` |
| Flyway | Boot-managed | `flyway-database-postgresql`; migrations in `src/main/resources/db/migration` |
| PostgreSQL driver | Boot-managed | `runtimeOnly org.postgresql:postgresql` |
| Stripe SDK | `com.stripe:stripe-java:28.2.0` | Checkout, portal, webhooks, idempotency keys |
| SendGrid SDK | `com.sendgrid:sendgrid-java:4.10.3` | Transactional email |
| AWS SDK v2 (S3) | BOM `software.amazon.awssdk:bom:2.31.0`, `s3` | S3-compatible presigner for Cloudflare R2 |
| Testcontainers | Boot-managed | `testcontainers-postgresql`, JUnit Jupiter integration; container image `postgres:17` |

Actuator is on the classpath; only the `health` endpoint is exposed
(`management.endpoints.web.exposure.include: health`, `show-details: never`).

### Frontend (`frontend/package.json`)

| Component | Version | Notes |
|---|---|---|
| React | 19.2 | |
| TanStack Start | 1.167 | SSR framework |
| TanStack Router | 1.168 | File-based routing under `frontend/src/routes` |
| TanStack Query | 5.83 | Server-state cache (see [§6](#6-frontend-architecture)) |
| Zod | 4.4 | Route search-param validation via `@tanstack/zod-adapter` |
| Vite | 7.3 | Build tool; `@cloudflare/vite-plugin` targets Cloudflare Workers |
| Tailwind CSS | 4.2 | `@tailwindcss/vite`; v2 design tokens in `frontend/src/styles.css` |
| TypeScript | 5.8 | |
| Radix UI | various | shadcn-style primitives under `frontend/src/components/ui` |
| Runtime (CI) | Bun 1.3.13 | `bun install`, `bun run build` |

### Deployment intent (not yet live)

Render (backend + Postgres), Cloudflare Workers (frontend), Sentry (error tracking),
UptimeRobot (uptime against `/api/health`). Production deploy is issue #12, not yet done;
`ops/README.md` holds the notes. No secrets are committed; all runtime configuration is by
environment variable (see [§12](#12-configuration-and-secrets-reference)).

### CI (`.github/workflows/ci.yml`)

Two jobs on every PR and on push to `main`:
- **Backend**: `./gradlew build` on `ubuntu-latest` (Testcontainers uses the runner's
  preinstalled Docker daemon; Docker is CI-only, not required on the founder's Mac for
  compile-only targets).
- **Frontend**: `bun install --frozen-lockfile` then `bun run build`. Repo-wide lint is
  deliberately not enforced yet (the Lovable-generated code predates the prettier config);
  format per-file on files you touch.

---

## 2. Domain model and boundaries

Code is organized by domain under `backend/src/main/java/com/homekept/*`, not by layer.
Within a domain: `Controller` (HTTP boundary, DTOs in / DTOs out) then `Service` (business
logic, transactions) then `Repository` (Spring Data) then `Entity` (JPA). DTOs never cross
below the controller; entities are mapped to DTOs by the service and do not escape through
controllers.

**The boundary rule (verified, no violations found in `src/main`).** A domain may call
another domain's *service*, never its repository or entities. Cross-domain reads go through
narrow query services that return purpose-built records, not JPA entities:

| Query/service | File | Exposes | Called from (examples) |
|---|---|---|---|
| `UserQueryService` | `identity/UserQueryService.java` | `UserContact`, `UserSummary`, `UserProfile` records (never the `User` entity) | `notification/RecipientResolver`, `subscription/SubscriptionAppService`, `technician/TechnicianAdminService` |
| `VisitQueryService` | `visit/VisitQueryService.java` | `findNextScheduledVisitDate(subscriberId)` | `subscription/SubscriptionAppService` |
| `SubscriberQueryService` | `subscription/SubscriberQueryService.java` | `findById` / `findByUserId` (returns the `Subscriber` entity read-only, a documented deliberate exception) | `notification/RecipientResolver`, `visit/TodoAppService`, `visit/HealthScoreService`, `visit/RescheduleService`, `visit/VisitAdminService`, `visit/VisitAppService` |
| `CatalogService` | `catalog/CatalogService.java` | `findPlanTierSummary`, `getMonthlyPriceCents`, `getPlanCode`, `getServiceNamesByIds`, etc. | `subscription/CheckoutService`, `subscription/StripeWebhookService`, `visit/VisitAdminService`, `visit/VisitSchedulingService` |
| `PropertyService`, `StorageService` | `property/`, `storage/` | property read/decrypt; R2 presign | `visit/TechVisitService`, `subscription/*` |

A grep of every `*Repository.java` for cross-domain imports returns zero. Nearly every
cross-domain call site carries an inline comment naming the rule (for example "never the
catalog repository directly"). One historical violation was caught and corrected in review
before merge (PR #85: a builder reached `SubscriberRepository` across a boundary; the fix
routed it through `SubscriberQueryService`).

### Domains as built

| Package | Responsibility | Owns (tables) | State machine |
|---|---|---|---|
| `identity` | Users, auth, JWT sessions, password hashing, password-reset and login rate limiting, admin seeding | `users`, `refresh_tokens`, `password_reset_tokens` | none (UserStatus has no machine class) |
| `property` | The home: address, geo, SKU sheet, encrypted access notes | `property` | none |
| `catalog` | Service and plan-tier definitions; source of truth for Stripe price IDs; founding-rate availability | `service`, `plan_tier`, `plan_tier_service` | none (read-only) |
| `booking` | Pre-subscriber walk-through leads and the admin pipeline | `walkthrough_booking`, `walkthrough_booking_day_preference` | `WalkthroughBookingStateMachine` |
| `subscription` | Subscriber lifecycle **and all Stripe integration** (checkout, portal, webhooks, self-serve pause/resume/cancel), plus activation | `subscriber`, `subscription_event`, `activation_token` | `SubscriberStateMachine` |
| `visit` | Visit lifecycle, templates/scheduling, checklists, photos, notes, todos ("your list"), flags, reschedule requests, Home Health Score | `visit`, `visit_service`, `visit_template`, `visit_template_service`, `visit_photo`, `visit_note`, `todo_item`, `flag`, `reschedule_request`, `reschedule_request_slot`, `health_score_snapshot` | `VisitStateMachine` |
| `technician` | Technician roster (profile + fully-loaded hourly cost); admin onboarding | `technician_profile` | none |
| `notification` | Outbound email (SendGrid seam); template rendering; recipient resolution | none | none |
| `dashboard` | Admin console home aggregate; composes counts from other domains' services | none (composes) | none |
| `storage` | R2 / object-storage abstraction (presigned PUT/GET) | none | none |
| `common` | Shared infra: global exception handler, health controller, client-IP resolver | none | none |
| `config` | Spring config: `SecurityConfig`, `AppProperties`, `StripeConfig`, `TimeZoneConfig` | none | none |
| `billing` | **Empty.** Only `package-info.java`. The invoice/payment-event domain from the design doc is not built; all billing logic lives in `subscription` today. | none | none |
| `shared` | **Empty/vestigial.** Only `package-info.java`; the `TimeZoneConfig` it claims actually lives in `config`. | none | none |

Two note-worthy naming collisions, harmless but worth knowing when navigating:
`catalog/Service.java` and `visit/VisitService.java` are JPA **entities** (a catalog service
definition; a visit-checklist join row), not Spring `@Service` beans. And `booking` carries
its own `PropertyType` enum separate from `property.PropertyType`.

### State machines

Three classes, exactly as `CLAUDE.md` mandates. Each has a `canTransition(from, to)` method
and every status write for that entity is immediately preceded by a `canTransition` guard
(verified line by line across `BookingService`, `TechVisitService`, `VisitAdminService`,
`StripeWebhookService`):

- `subscription/SubscriberStateMachine.java` over `SubscriberStatus`
  (PENDING_ACTIVATION, ACTIVE, PAUSED, PAYMENT_ISSUE, CANCELLED-terminal).
- `visit/VisitStateMachine.java` over `VisitStatus`
  (SCHEDULED, IN_PROGRESS, then terminal COMPLETED / INCOMPLETE / CANCELLED / RESCHEDULED).
- `booking/WalkthroughBookingStateMachine.java` over `BookingStatus`
  (PENDING, CONFIRMED, PERFORMED, then terminal CONVERTED / LOST / NO_SHOW).

`SubscriptionSelfServeService` deliberately does not write `subscriber.status` itself: it
calls `canTransition` only as an eligibility pre-check, then defers the actual status change
to the Stripe webhook (pause/resume/cancel all round-trip through Stripe). Two smaller
status enums, `RescheduleRequestStatus` and `TodoItemStatus`, are mutated with inline guards
rather than a dedicated machine class. This is not a rule violation (the rule names only the
three above) but it means the phrase "every status transition goes through a machine class"
is true only for subscriber/visit/booking.

---

## 3. Data model and migrations

Conventions (verified consistent across all nine migrations): `BIGSERIAL` primary keys;
`TIMESTAMPTZ` for every timestamp (no naked `TIMESTAMP` anywhere); enums stored as
`VARCHAR` + `CHECK` constraint (no native Postgres enum types); money as `INTEGER` cents;
every foreign key indexed. Only two JSONB columns are permitted, and only one exists so far
(`subscription_event.payload`); the second (`payment_event.raw_payload`) is referenced in
comments but its table is not built (the `billing` domain is empty). Java timestamps are
`java.time.Instant` exclusively (grep for `LocalDateTime` / `java.util.Date` across the
domain returns zero).

Migrations are hand-write / founder-owned per `CLAUDE.md`. During the initial backend
bootstrap the agent crew drafted them under explicit, case-by-case founder authorization and
flagged each for founder review before merge (see [§9](#9-key-decisions-and-rationale) item
10).

### Migration list (`backend/src/main/resources/db/migration/`)

| File | Purpose | Key tables / columns |
|---|---|---|
| `V1__identity.sql` | Identity domain | `users` (unique lower(email), role/status CHECK, TIMESTAMPTZ audit cols); `refresh_tokens` (stores `token_hash` only, never the raw token); `password_reset_tokens` |
| `V2__catalog.sql` | Catalog + seed data from the pricing spec | `service` (`a_la_carte_price_cents INTEGER`, `tier_class`/`category` CHECK); `plan_tier` (`monthly_price_cents` / `annual_price_cents` / `founding_monthly_price_cents` INTEGER, `stripe_price_id_*` columns); `plan_tier_service` join. Seeds 3 tiers + service rows transcribed from `docs/pricing-and-visits.md` |
| `V3__booking.sql` | Booking domain | `walkthrough_booking` (status CHECK 6 states, `lead_source` CHECK, `posthog_distinct_id`); `walkthrough_booking_day_preference` (normalized child table, not JSONB) |
| `V4__property_subscriber_activation.sql` | Property, subscriber, subscription events, activation | `property` (**`access_notes BYTEA`** encrypted at rest); `subscriber` (status CHECK 5 states, `founding_rate`, `stripe_customer_id`, `stripe_subscription_id`, nullable `plan_tier_id`); `subscription_event` (`payload JSONB`); `activation_token`. Circular property/subscriber FK resolved with `DEFERRABLE INITIALLY DEFERRED` |
| `V5__stripe_idempotency.sql` | Webhook idempotency | Adds `subscription_event.stripe_event_id` + partial unique index (WHERE NOT NULL); indexes `subscriber.stripe_customer_id` / `stripe_subscription_id` |
| `V6__visit.sql` | Visit domain + seed data | `visit_template` (month + `min_tier` CHECK, unique(month, min_tier)); `visit_template_service`; `visit` (status CHECK 6 states, type CHECK 4 states, `materials_cost_cents INTEGER`); `visit_service` (`source` CHECK: TEMPLATE/PICK/EXTRA/FLAGGED/TODO). Seeds the 12 monthly visit templates from the visit calendar |
| `V7__technician_and_visit_artifacts.sql` | Technician + visit artifacts | `technician_profile` (`fully_loaded_hourly_cost_cents INTEGER`); `visit_photo`; `visit_note`; `flag` (severity/status CHECK); `todo_item` (status CHECK 4 states); adds `visit.technician_id` FK (deferred from V6) and `visit.materials_notes` |
| `V8__reschedule_request.sql` | Self-serve reschedule (#54) | `reschedule_request` (status CHECK 3 states, partial unique index enforcing one PENDING per visit); `reschedule_request_slot` (normalized child, explicitly no JSONB) |
| `V9__health_score_snapshot.sql` | Home Health Score v1 (#53) | `health_score_snapshot` (`score INTEGER` CHECK 0..100, composite index `(subscriber_id, computed_at)`, subscriber FK ON DELETE CASCADE as disposable derived data) |

`access_notes` is created as `BYTEA` in V4 and mapped in `property/Property.java` as
`byte[]` with `columnDefinition = "BYTEA"`. The stored format is
`IV(12 bytes) || ciphertext || GCM tag(16 bytes)` (see [§5.9](#59-access-notes-encryption)).

Money field examples (all `int`/`Integer` with a `_Cents` suffix): `PlanTier.monthlyPriceCents`,
`Service.aLaCartePriceCents`, `Visit.materialsCostCents`,
`TechnicianProfile.fullyLoadedHourlyCostCents`. The only `double` in the domain is a
completion *ratio* in `HealthScoreService`, not money. No `BigDecimal` or `float` anywhere.

Timezone: `config/TimeZoneConfig.java` exposes a single `ZoneId renderZoneId()` bean sourced
from `AppProperties.timezone()` (default `America/Toronto`, overridable via `APP_TIMEZONE`).
Only two consumers inject it (`visit/TechVisitService`, `visit/VisitSchedulingService`).
Grep for a hardcoded `"America/Toronto"` finds it only in the config default and in javadoc
comments, never as `ZoneId.of(...)` in business logic. The one-place rule holds.

---

## 4. API surface

41 endpoints across the controllers under `backend/src/main/java/com/homekept/**`. Roles are
enforced with `@PreAuthorize` on the controller methods; `@EnableMethodSecurity` is on.
Paths marked public are allowlisted in `config/SecurityConfig.java`; everything else requires
a valid access-token cookie.

This table is the as-built surface. Where it differs from `backend/api-contract.md`, the
difference is flagged in [§13](#13-where-code-and-the-design-docs-disagree); renaming or
removing any endpoint requires updating `api-contract.md` in the same PR.

### Public (no auth)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| POST | `/api/bookings/walkthrough` | Submit walk-through booking (rate-limited 3/IP/hr) | `booking/BookingController` |
| GET | `/api/catalog/plans` | Plan tiers and pricing | `catalog/CatalogController` |
| GET | `/api/catalog/picks` | À-la-carte picks menu | `catalog/CatalogController` |
| GET | `/api/health` | Liveness (`{status:UP}`), UptimeRobot target | `common/HealthController` |
| GET | `/actuator/health` | Spring Boot Actuator health (allowlisted; not in api-contract.md) | Actuator auto-config |

### Activation (token-authed, not session)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| POST | `/api/activation/validate` | Validate magic-link token (rate-limited 10/IP/hr) | `subscription/ActivationController` |
| POST | `/api/activation/complete` | Create User/Property/Subscriber, consume token, set cookies | `subscription/ActivationController` |

### Auth

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public, 5/email/15min | Login, set cookies |
| POST | `/api/auth/refresh` | public (refresh cookie) | Rotate refresh token, reissue cookies |
| POST | `/api/auth/logout` | public | Revoke all refresh tokens, clear cookies |
| GET | `/api/auth/me` | authenticated | Caller profile `{id, firstName, lastName, email, role}` |
| POST | `/api/auth/forgot` | public, 5/IP/hr | Always 202; email a 30-min reset token if the account exists |
| POST | `/api/auth/reset` | public | Consume reset token, set new password + cookies |

All auth endpoints live in `identity/AuthController`.

### Checkout and billing (role CUSTOMER)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| POST | `/api/checkout/session` | Create Stripe Checkout session (subscription) | `subscription/CheckoutController` |
| POST | `/api/billing/portal-session` | Create Stripe billing-portal session | `subscription/CheckoutController` |

### Webhooks

| Method | Path | Purpose | Controller |
|---|---|---|---|
| POST | `/api/webhooks/stripe` | Signature-verified, idempotent Stripe event handler | `subscription/StripeWebhookController` |

### Customer app (`/api/app/*`, role CUSTOMER)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| GET | `/api/app/subscription` | Plan/billing summary | `subscription/SubscriptionController` |
| GET | `/api/app/account` | Profile + service-property address | `subscription/SubscriptionController` |
| POST | `/api/app/subscription/pause` | Pause billing (via Stripe) | `subscription/SubscriptionController` |
| POST | `/api/app/subscription/resume` | Resume billing (via Stripe) | `subscription/SubscriptionController` |
| POST | `/api/app/subscription/cancel` | Cancel at period end + store churn reason | `subscription/SubscriptionController` |
| GET | `/api/app/health-score` | Home Health Score v1 | `visit/AppHealthScoreController` |
| GET | `/api/app/todos` | List "your list" items | `visit/AppTodoController` |
| POST | `/api/app/todos` | Create a todo item | `visit/AppTodoController` |
| DELETE | `/api/app/todos/{id}` | Delete an owned todo (404 if not owned) | `visit/AppTodoController` |
| GET | `/api/app/visits` | Paginated visit list | `visit/AppVisitController` |
| GET | `/api/app/visits/{id}` | Visit detail + checklist + photos (404 if not owned) | `visit/AppVisitController` |
| POST | `/api/app/visits/{id}/reschedule-request` | Submit a reschedule request | `visit/AppVisitController` |

### Technician app (`/api/tech/*`, role TECHNICIAN)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| GET | `/api/tech/visits/today` | Day sheet with decrypted access notes, checklist, todos, flags | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/start` | SCHEDULED to IN_PROGRESS | `visit/TechVisitController` |
| PATCH | `/api/tech/visits/{id}/services/{visitServiceId}` | Tick/untick a checklist item | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/photos/upload-url` | Presigned R2 PUT URL | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/photos` | Confirm an uploaded photo | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/flags` | Create a flag (observe/photograph/flag/refer) | `visit/TechVisitController` |
| PATCH | `/api/tech/todos/{id}` | Mark a todo DONE/DECLINED in the field | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/complete` | IN_PROGRESS to COMPLETED, fire report email | `visit/TechVisitController` |
| POST | `/api/tech/visits/{id}/incomplete` | IN_PROGRESS to INCOMPLETE, auto-create follow-up | `visit/TechVisitController` |

### Admin console (`/api/admin/*`, role ADMIN)

| Method | Path | Purpose | Controller |
|---|---|---|---|
| GET | `/api/admin/bookings` | Walk-through pipeline (cursor + optional limit) | `booking/AdminBookingController` |
| PATCH | `/api/admin/bookings/{id}` | Status transition / set scheduledFor | `booking/AdminBookingController` |
| POST | `/api/admin/bookings/{id}/activation-invite` | Mint token + email magic link | `subscription/AdminActivationController` |
| GET | `/api/admin/subscribers` | Subscriber list (cursor + optional limit) | `subscription/AdminSubscriberController` |
| GET | `/api/admin/subscribers/{id}` | Subscriber detail (404 if missing) | `subscription/AdminSubscriberController` |
| GET | `/api/admin/visits` | Visit list (cursor-paginated) | `visit/AdminVisitController` |
| POST | `/api/admin/visits` | Create a visit for a subscriber | `visit/AdminVisitController` |
| PATCH | `/api/admin/visits/{id}` | Reschedule / cancel / assign technician | `visit/AdminVisitController` |
| GET | `/api/admin/reschedule-requests` | PENDING reschedule queue | `visit/AdminRescheduleController` |
| POST | `/api/admin/reschedule-requests/{id}/confirm` | Confirm reschedule via state machine | `visit/AdminRescheduleController` |
| POST | `/api/admin/reschedule-requests/{id}/decline` | Decline with required note | `visit/AdminRescheduleController` |
| GET | `/api/admin/technicians` | Full technician roster | `technician/AdminTechnicianController` |
| POST | `/api/admin/technicians` | Onboard a technician profile (409 if exists) | `technician/AdminTechnicianController` |
| GET | `/api/admin/dashboard` | Aggregate operational metrics | `dashboard/AdminDashboardController` |

**Documented in `api-contract.md` but not built** (all consistent with unfinished picks and
activity-feed work, not silent drift): `GET /api/app/activity`, `POST /api/app/picks`,
`POST /api/checkout/extra`, `POST /api/admin/visits/{id}/complete`. See
[§13](#13-where-code-and-the-design-docs-disagree).

### Standard error envelope and status codes

Errors return `{ "error": { code, message, fields?, request_id } }` from
`common/GlobalExceptionHandler`. Status codes: `200/201/204` success; `400` validation; `401`
missing/expired token (via the security entry point, before any handler); `403` wrong role
(`AccessDeniedException` from `@PreAuthorize`); `404` not found or not yours (ownership
failures return 404, never 403, so existence is not leaked); `409` illegal state transition
or duplicate; `429` rate limited; `5xx` server error.

---

## 5. Auth and security model

Verified in `config/SecurityConfig.java` and the `identity` package. Everything the design
doc describes for auth was found implemented as described; the exceptions are documentation
staleness, noted inline.

### 5.1 Filter chain

`@Configuration @EnableWebSecurity @EnableMethodSecurity`. The chain is stateless
(`SessionCreationPolicy.STATELESS`), CORS enabled via a bean, CSRF explicitly disabled (the
class javadoc documents the rationale: `SameSite=Lax` cookies + short-lived JWT + JSON
bodies on a same-registrable-domain deployment). A custom `JwtAuthFilter` runs before
`UsernamePasswordAuthenticationFilter`. Form login and HTTP Basic are off. A custom
entry point returns 401 for missing/invalid auth; a custom access-denied handler returns 403
for role failures, with an inline comment tying it to the `CLAUDE.md` rule "403 = wrong role,
404 = not found or not yours."

### 5.2 JWT

HS256, hand-rolled with `javax.crypto.Mac` (`HmacSHA256`); no third-party JWT library.
Access token 15 minutes (`access-token-expiry-seconds: 900`), refresh token 7 days
(`604800`). Cookies `hk_access` (path `/api`) and `hk_refresh` (path narrowed to
`/api/auth/refresh`), both `HttpOnly` + `SameSite=Lax`; `Secure` is set when
`APP_SECURE_COOKIES=true` or the request is seen as secure via `X-Forwarded-Proto`
(`forward-headers-strategy: framework`). Signature comparison is constant-time. The signing
key comes from `JWT_SIGNING_KEY`; a `@PostConstruct` guard in `JwtService` refuses to start
(outside dev-mode) if the key is blank, shorter than 32 bytes, or equal to the well-known dev
sentinel in `application.yml`.

### 5.3 Refresh-token rotation and reuse detection

`refresh_tokens` stores only a SHA-256 hex digest; the raw 256-bit `SecureRandom` token is
returned once and never persisted or logged. `RefreshTokenService.rotate()` revokes the
presented token and issues a fresh row on every use. If an already-revoked token is
presented (replay), the entire token family for that user is revoked. Logout
(`AuthService.logout`) bulk-revokes every outstanding refresh token for the user.

### 5.4 Password hashing

`new BCryptPasswordEncoder(12)` is the sole `PasswordEncoder` bean (`SecurityConfig`),
consumed by `AuthService` and `AdminSeeder`. No other encoder exists.

### 5.5 Activation and password-reset tokens

`subscription/ActivationTokenService` and `identity/PasswordResetTokenService` are
structurally identical (the reset service javadoc says it mirrors the activation one). Both
format tokens as `base64url(payload) + "." + base64url(HMAC-SHA256(payload))`, reusing the
JWT signing key as the HMAC key (a documented MVP tradeoff: no extra secret to manage), and
verify in constant time. TTLs: activation 7 days, reset 30 minutes. Single use is enforced
with a `consumed_at` timestamp column and an atomic `consumeIfUnconsumed(hash, now)`
conditional UPDATE (a concurrent second caller updates zero rows and is rejected: race-safe,
not a read-then-write gap). Both services mint a dummy token on the not-found branch to
equalize timing against enumeration.

### 5.6 Ownership 404 vs role 403

Consistent, self-documented pattern. Examples:
`visit/TechVisitService.requireOwnedVisit()` uses
`findByIdAndTechnicianId(...).orElseThrow(VisitNotFoundException::new)` for every mutating
tech endpoint; `visit/VisitAppService.getVisit()` uses `findByIdAndSubscriberId(...)`;
`visit/TodoAppService.deleteTodo()` uses `findByIdAndSubscriberId(...)`. 403 is reserved for
role mismatches, produced automatically by `@PreAuthorize` failures
(`AuthorizationDeniedException` extends `AccessDeniedException`, mapped to 403 in
`GlobalExceptionHandler`). Thirteen controllers carry role gates.

### 5.7 Rate limiting

Four in-memory, per-instance `ConcurrentHashMap` limiters (each documented as MVP, to be
replaced by Bucket4j + Redis at Stage 3). Values match the spec exactly: login 5/email/15min
(`LoginRateLimiter`), walkthrough 3/IP/hr (`BookingRateLimiter`), activation 10/IP/hr shared
across validate+complete (`ActivationRateLimiter`), forgot-password 5/IP/hr
(`ForgotPasswordRateLimiter`). Each is a plain injected `@Component` whose `tryConsume(key)`
is called at the top of the relevant flow, throwing `RateLimitExceededException` (mapped to
429). IPs resolve via `common/ClientIpResolver`, which prefers Cloudflare's
`CF-Connecting-IP` over the spoofable `X-Forwarded-For`. All four are bounded maps with
eviction to prevent memory-DoS from cycled identifiers.

### 5.8 CORS

`SecurityConfig.corsConfigurationSource()` reads allowed origins from
`app.cors.allowed-origins` (defaults to the two localhost dev origins; production origins are
env-injected, never hardcoded). `allowCredentials(true)` (required for cookie auth), methods
GET/POST/PUT/PATCH/DELETE/OPTIONS, headers limited to `Content-Type`/`Accept`, applied only
to `/api/**`. SameSite is set per-cookie in `CookieHelper`, not via a global serializer. The
cross-origin design (frontend `homekept.ca`, API `api.homekept.ca`, same site so `Lax`
cookies flow) is described in `backend/homekept-backend-architecture.md` §5.1.

### 5.9 Access-notes encryption

`property/AccessNotesCipher.java`: AES-256-GCM in application code (not pgcrypto). Format
`IV(12) || ciphertext || GCM tag(16)` stored as `BYTEA`; random IV per encryption; 128-bit
auth tag detects tampering (`AEADBadTagException`). Key from `ACCESS_NOTES_ENC_KEY`
(Base64-encoded 32 bytes); startup fails outside dev-mode if it is blank, non-Base64, or the
wrong length. `Property.hasAccessNotes()` exposes presence without the value and is surfaced
only on admin DTOs. Decryption happens at exactly one call site in the whole codebase,
`TechVisitService.getTodaysVisits()` (that is, `GET /api/tech/visits/today`); grep confirms
no other caller. Customer DTOs never carry decrypted notes.

> Note: the `application.yml` comment calling the encryption block "reserved for future"
> is stale. The feature is fully implemented and wired.

---

## 6. Frontend architecture

TanStack Start (SSR-capable) on Cloudflare, file-based routing under `frontend/src/routes`.
The defining constraint: auth cookies are httpOnly and scoped to the API origin, so an SSR
render on Cloudflare has no session to read. Therefore auth guards are client-side.

### 6.1 The API wrapper (`frontend/src/lib/api.ts`)

Exports `get`, `post`, `patch`, `del`, all thin wrappers over an internal `request<T>`.
`BASE_URL` comes from `VITE_API_URL` (empty means same-origin). Every request is issued with
`credentials: "include"` (hardcoded, so cross-origin cookies always travel). Errors surface
as `ApiError extends Error` carrying `status`, `code`, optional `fields`, and `requestId`,
parsed from the backend's `{ error: {...} }` envelope; a non-JSON error body falls back to
`code: "UNKNOWN_ERROR"`.

Empty-body handling (the load-bearing fix from PR #116): 204 short-circuits to `undefined`;
for any other 2xx the body is read as text and only `JSON.parse`d if non-empty. This exists
because `login`, `refresh`, `forgot`, and `reset` return an empty 200/202 body, and calling
`res.json()` on an empty body throws a `SyntaxError` that previously routed a successful
login into the error path. See [§9](#9-key-decisions-and-rationale) item 7.

### 6.2 Auth helpers (`frontend/src/lib/auth.ts`)

`getSession()` calls `GET /api/auth/me` and treats a 401 as signed-out (`null`); `logout()`
calls `POST /api/auth/logout` best-effort. `useSessionExpiredRedirect(error)` navigates to
`/signin?next=<pathname>` on a 401. The three app shells guard client-side with `useEffect`
+ state, never `beforeLoad`, each rendering only a loading placeholder until the session
resolves (no flash of protected content, no SSR data leak):
- `components/app/AppShell.tsx` (customer): redirect to `/signin` when unauthenticated.
- `components/admin/AdminShell.tsx` (admin): plus a role check, wrong role to `/app`.
- `routes/tech.tsx` `TechGuard` (technician): plus a role check for TECHNICIAN.

### 6.3 Formatting (`frontend/src/lib/format.ts`)

`export const TZ = "America/Toronto"` is passed to every `Intl.DateTimeFormat` call
(`formatFullDate`, `formatTime`, `greetingFor`, relative-time fallback), mirroring the
backend's UTC-stored / Toronto-rendered rule. `tech.tsx` reimplements the same pattern
inline for its day-sheet header instead of importing it (minor duplication, not a bug).

### 6.4 Data layer

`router.tsx` creates a `QueryClient` per router instance and passes it as router context;
`routes/__root.tsx` wraps `<Outlet/>` in `<QueryClientProvider>`. Per-feature hooks live in
`lib/` modules (`account.ts`, `visits.ts`, `todos.ts`, `admin.ts`, `tech.ts`), all built on
the `api.ts` wrapper. Issue #105 (clear the query cache on logout) is an open follow-up.

### 6.5 Route groups and wiring status

| Group | Files | Wiring |
|---|---|---|
| Marketing / public | `index`, `plans`, `milton`, `mississauga`, `oakville`, `learn.$slug`, `privacy`, `terms`, `design-system`, `sitemap[.]xml` | Static content (pricing sourced from `lib/plans.ts`, itself transcribed from the pricing spec). No API/mock. |
| Auth / lead flow | `signin`, `activate`, `forgot-password`, `reset-password`, `book`, `plans` | Wired to real API (`lib/api.ts`). |
| `checkout.tsx` | 1 file | Stub: "Checkout flow coming next." Not wired. |
| Customer app `/app/*` | `app` shell + `index`, `visits`, `visits.$id`, `list`, `health`, `reports`, `billing`, `settings` | **Mixed.** Real: `billing`, `settings`, `list`, `visits`, `visits.$id`. Still mock: `health` (not wired to the shipped `/api/app/health-score`), `reports`; `index` and `AppShell` sidebar pull greeting/name/health from `mock-account.ts`. |
| Admin `/admin/*` | `admin` shell + `index`, `metrics`, `subscribers`, `leads`, `walkthroughs`, `visits`, `routes`, `technicians`, `catalog`, `plans`, `settings` | **Mixed.** Real: `subscribers`, `visits`, `technicians`, `walkthroughs`. Still mock: `plans`, `metrics`, `routes`, `settings`, `catalog`, `leads`; `index` mixes a real dashboard call with mock `attention`/`pendingWalkthroughs`/`subscribers`. |
| Technician `/tech` | `tech.tsx` (self-contained) | Fully wired to `/api/tech` via `lib/tech.ts`. No mock imports. |

Mock data lives in `frontend/src/lib/mock-account.ts` and `mock-admin.ts`; grepping their
imports is the fastest way to see what remains unwired. Open follow-ups: #117 (admin
dashboard panels still mock), #106 finished most admin wiring but left the above.

### 6.6 Open-redirect protection

Only one attacker-influenced redirect exists, in `routes/signin.tsx`. `sanitizeNext()`
resolves the `next` param with `new URL(raw, window.location.origin)` and returns it only if
`url.origin === window.location.origin`, else a safe default. It does **not** use
`startsWith`. The inline comment documents why: the WHATWG URL parser strips tab/newline/CR
before parsing, so a naive `startsWith("/")` check would accept `"/\t/evil.com"` and resolve
off-origin. Origin comparison also rejects absolute URLs and non-http(s) schemes. Called only
from the client submit handler after a user gesture. The three shells build their own `next`
from the already-trusted current pathname, so they need no equivalent check. See
[§9](#9-key-decisions-and-rationale) item 8.

---

## 7. Integrations

All external integrations are coded behind a seam and degrade gracefully when unconfigured,
so the app boots and CI passes without any real secret. Turning each on is founder work
(secrets + accounts).

### 7.1 Stripe (`subscription` domain)

`CheckoutService` / `StripeServiceImpl` create the subscription Checkout session
(`Mode.SUBSCRIPTION`, `clientReferenceId`, metadata `subscriberId`/`planTierId`/`foundingRate`,
reusing an existing `stripeCustomerId`) and the billing-portal session, each with a
deterministic SHA-256 idempotency key via `RequestOptions`. The webhook
(`StripeWebhookController` -> `StripeWebhookService`) verifies the signature with the real
SDK and is idempotent via `subscription_event.stripe_event_id`: the handler checks
`findByStripeEventId` first and short-circuits duplicates, and the event row is persisted in
the same transaction as the state change (a concurrent duplicate throws on the unique-index
flush, rolls back, and the controller returns 200). Handled events:
`checkout.session.completed` (subscription mode -> activate), `customer.subscription.updated`
(sync period/plan/cycle), `customer.subscription.deleted` (CANCELLED),
`invoice.payment_failed` (PAYMENT_ISSUE), `invoice.payment_succeeded` (recover to ACTIVE),
`customer.subscription.paused` / `.resumed`. Ignored with an ack, no row:
`customer.created`, `customer.updated`, `invoice.created`. Every state-changing handler
guards with `stateMachine.canTransition` and no-ops on illegal/out-of-order transitions (so
Stripe retries are not triggered by a losing race). Config: `STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET`, success/cancel/portal URLs. In production a blank webhook secret
fails startup; in dev it warns. **Requires founder Stripe setup (issue #21): real
Products/Prices, a migration wiring real price IDs into `plan_tier`, and the live webhook
endpoint + secret.**

### 7.2 SendGrid (`notification` domain)

Seam: `EmailSender` interface (best-effort, never throws). Implementation:
`SendGridEmailSender` using `sendgrid-java`. There is no separate no-op bean; the same class
degrades gracefully: if the API key or from-email is blank it logs a warning and returns
without sending, and it swallows all send errors. Seven templates in `EmailTemplates.java`,
each a static factory returning a `RenderedEmail` (shared pine/cream/honey HTML layout, honey
CTA uses pine text per the WCAG rule):

| Template | Fired from | Trigger |
|---|---|---|
| `bookingConfirmation` | `DefaultBookingNotifier` <- `BookingService` | Walk-through submitted |
| `activationInvite` | `ActivationNotifier` <- `ActivationService` | Admin sends activation invite |
| `welcome` | `DefaultSubscriptionStartedNotifier` <- `StripeWebhookService` | `checkout.session.completed` |
| `visitComplete` | `DefaultVisitReportNotifier` <- `TechVisitService` | Visit completed |
| `paymentFailed` | `DefaultPaymentFailedNotifier` <- `StripeWebhookService` | `invoice.payment_failed` |
| `subscriptionCancelled` | `DefaultSubscriptionCancelledNotifier` <- `StripeWebhookService` | `customer.subscription.deleted` |
| `passwordReset` | `DefaultPasswordResetNotifier` <- `AuthService` | Forgot-password request |

The two 24h reminder emails (walk-through, visit) are deferred to Stage 2 (issue #89; needs a
founder `notification_log` migration + `@Scheduled`). **Config caveat (verify before
relying on production email):** `application.yml` has `app.stripe.*` and `app.r2.*` blocks
that bridge bare env-var names, but **no `app.sendgrid.*` block**. Because `AppProperties` is
`@ConfigurationProperties(prefix="app")`, Spring's relaxed binding expects
`APP_SENDGRID_API_KEY`, not the bare `SENDGRID_API_KEY` documented in `.env.example` and
`HANDOFF.md`. As written, the bare vars may not bind. The founder should add an
`app.sendgrid.*` block (hand-write) mirroring the Stripe/R2 pattern, or set `APP_SENDGRID_*`
names.

### 7.3 Cloudflare R2 (`storage` domain)

`StorageService` interface; `R2StorageService` uses the AWS SDK v2 S3 presigner (path-style
forced on, required for R2), 15-minute TTL for both PUT and GET. Flow: client requests a
signed PUT URL, uploads directly to R2, then confirms to the backend; downloads use a signed
GET URL. Storage keys are server-generated (`visits/{visitId}/{uuid}`) to block traversal.
**Implemented but unconfigured.** `@PostConstruct` leaves the presigner null if any R2 field
is blank (app still boots); any presign call then throws `StorageUnavailableException` -> 503.
So today every photo upload/download returns 503. Config: `R2_ENDPOINT`, `R2_BUCKET`,
`R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`. Note `.env.example` lists `R2_BUCKET_NAME` while
the code binds `R2_BUCKET`: a name mismatch to reconcile. Founder work: issue #58.

### 7.4 PostHog

**Not integrated in code.** No `posthog-js`, no `posthog-java`, no `AnalyticsService`, in
either backend or frontend (exhaustive grep). What exists is a placeholder for future funnel
stitching: `walkthrough_booking.posthog_distinct_id` column + the optional
`posthogDistinctId` DTO field, passed through by `BookingService`. No frontend snippet
populates it. The canonical event taxonomy in `backend/homekept-backend-architecture.md` §5.7
is a plan (issue #63), not shipped.

> Also worth verifying separately: Sentry is named as an active processor in the privacy
> policy copy (`routes/privacy.tsx`) but no Sentry SDK is present in `build.gradle` or
> `package.json`. Same pattern as PostHog: policy copy ahead of wiring.

---

## 8. Testing strategy

Backend testing follows `backend/homekept-backend-architecture.md` Part 8: real Postgres via
Testcontainers, never a mocked database.

- **Testcontainers.** `TestcontainersConfiguration` is a `@TestConfiguration` exposing a
  `@ServiceConnection PostgreSQLContainer` on `postgres:17`, imported by the `@SpringBootTest`
  integration tests. Grep for `@MockBean` across `backend/src/test/java` returns zero: no
  database mocking. External third parties are faked with `@Primary` stub beans
  (`FakeStripeServiceConfig`, `FakeEmailSenderConfig`, `FakeStorageServiceConfig`), except the
  webhook tests, which use the real `StripeServiceImpl` so signature verification is genuinely
  exercised.
- **State machines.** `SubscriberStateMachineTest`, `VisitStateMachineTest`,
  `WalkthroughBookingStateMachineTest` each cover legal transitions, illegal transitions,
  terminal-state exhaustion, and null-argument checks (roughly 9/10, 5/7, 6/11 legal/illegal
  respectively).
- **Stripe webhooks.** `StripeWebhookIntegrationTest` builds inline JSON payloads (no fixture
  files beyond `application.yml`), computes a real HMAC-SHA256 `Stripe-Signature` against a
  test secret, and covers: bad signature -> 400, activation, idempotent duplicate, payment
  failed, payment recovered, cancelled, paused, resumed, an ignored event type, and an
  illegal transition on an already-cancelled subscriber.
- **Email + cipher + JWT + rate limit unit tests.** `EmailTemplatesTest` (subjects, link
  interpolation, null-name fallback, HTML escaping, the WCAG honey/pine CTA rule),
  `SendGridEmailSenderTest` (graceful degradation on blank key/from/recipient),
  `AccessNotesCipherTest`, `JwtServiceTest`, `LoginRateLimiterTest`,
  `AppPropertiesBindingTest`.
- **Integration coverage** spans auth, activation, booking, catalog, checkout, founding rate,
  password reset, self-serve, admin subscriber/technician/dashboard, and the visit surface
  (app/admin/tech visits, todos, photos, health score, reschedule, scheduling). About 40 test
  files total.
- **Frontend tests: none.** No vitest/jest/testing-library/playwright in
  `frontend/package.json`, no `*.test.*`/`*.spec.*` files, no test config. Zero automated
  frontend coverage today. State this as a known gap.

Docker is required to run the backend suite locally; it is CI-only on the founder's Mac (the
`compileJava`/`compileTestJava` targets do not need it).

---

## 9. Key decisions and rationale

The load-bearing "why" decisions, attributed to where they are enforced and, where a merged
PR captured the reasoning, to that PR. Items 1 through 5 are pre-existing non-negotiables
from `CLAUDE.md` / the architecture doc; the PRs cite compliance rather than re-arguing them,
so the "why" below is the project's stated rationale, and the enforcement point is what the
code actually does.

1. **Money is integer cents.** Floating-point currency arithmetic is the classic
   "charged the wrong amount" bug. Enforced: every `_Cents` column is `INTEGER`, every field
   `int`/`Integer`; no `BigDecimal`/`float` in the domain. Reviewed on every money-touching
   PR (e.g. #81, #84, #114).

2. **Timestamps stored UTC, rendered `America/Toronto`.** Store an unambiguous instant,
   render in the business's operating zone regardless of device. Enforced backend-side via
   `TimeZoneConfig` (one bean, one config default; PR #85 caught and fixed a hardcoded zone).
   Rendering rationale is clearest in **PR #110**, which fixed `format.ts` rendering "in the
   viewer's device time" instead of Toronto (plus an `h23` fix for a midnight-as-24 bug).

3. **Ownership failures return 404, not 403.** Do not leak the existence of resources a
   caller does not own. Enforced with `findByIdAnd<owner>Id(...).orElseThrow(NotFound)` across
   `TechVisitService`, `VisitAppService`, `TodoAppService`, and more; 403 is reserved for role
   mismatches. Stated as an invariant in PRs #85, #86 ("no IDOR"), #88, #112, #114.

4. **A domain calls another domain's service, never its repository/entities.** Keeps the
   eventual extraction tractable and the boundaries real. Enforced via the `*QueryService`
   pattern (see [§2](#2-domain-model-and-boundaries)); zero cross-domain repo imports.
   Declared in PRs #83, #90, #114, #118; PR #85 caught a violation and corrected it in review.

5. **State changes only through state-machine classes.** State machines are the most-bugged
   part of a service backend, so `canTransition` gates every write for subscriber/visit/
   booking, and illegal transitions are logged-and-acked rather than retried (PR #84's
   rationale: illegal/out-of-order webhook events must not trigger a Stripe retry storm; PR
   #85: reschedule preserves history by marking the old visit RESCHEDULED and creating a new
   SCHEDULED row).

6. **Client-side auth guards, because SSR has no cookie.** From **PR #99**: the `/app/*`
   guard is client-side via `useEffect` "since the auth cookies live on the API origin and an
   SSR fetch carries none. SSR always emits the loading placeholder, so no dashboard flash and
   no data leak for signed-out visitors." Reused unchanged for admin (#107) and tech (#109).

7. **The empty-2xx-body `api.ts` fix.** From **PR #116**: `request<T>()` only skipped
   `res.json()` for 204, but `login`/`refresh`/`forgot`/`reset` return an empty 200/202 body,
   so `res.json()` threw a `SyntaxError` and routed success into the error banner ("Sign-in is
   currently broken against a real backend for this reason"). Fix: read as text, parse only if
   non-empty. Enforced in `frontend/src/lib/api.ts`.

8. **The open-redirect fix.** From **PR #99**: the original `?next=` sanitizer used
   `startsWith`, but the WHATWG URL parser strips tab/newline/CR before parsing, so
   `?next=%2F%09%2Fevil.com` reached `//evil.com`. Fix: resolve against
   `window.location.origin` and compare origins; re-verified against userinfo/subdomain/
   scheme-downgrade/port/whitespace variants. Enforced in `routes/signin.tsx` `sanitizeNext()`.

9. **No fabricated data.** Builders removed fakes rather than ship placeholders to a real
   surface, both for honesty and for Competition Act exposure (fabricated social proof).
   **PR #92** removed a "Most chosen" badge (zero customers exist), an invented "Marcus, your
   lead technician" persona, and unbacked "insured and bonded / background-checked" claims.
   **PR #103** omits the "with {name}" clause when `technicianFirstName` is null rather than
   show "with null" or a fake name. **PR #114** dropped a fabricated payment-method card,
   invoice history, phone, neighbourhood, and fake access instructions "rather than show fakes
   on a real page." **PR #118** dropped a fabricated "At-risk subscribers" card and unbacked
   badge counts, and shows `#id` rather than a fabricated name. This is why the admin dashboard
   has no "at-risk" field: there is no backing column for the concept yet.

10. **Migrations/config/auth-core are hand-write, but the crew drafted bootstrap migrations
    under explicit founder direction.** The boundary is never waived. During the V1 through V7
    bootstrap the crew wrote the SQL "per your authorization for this bootstrap" (PRs #81, #82,
    #83, #84). Once past the bootstrap, each new migration (V8 in #88, V9 in #91) shipped with
    an explicit "contains a migration, please review; I drafted it at your direction, vet it as
    your own before merging" callout, and the founder merges those PRs personally. Encoded in
    `.claude/commands/ship.md` (human-only list) and `CLAUDE.md`.

---

## 10. Known gaps and follow-ups

Open issues at the verified commit (from the tracker), grouped. Numbers are GitHub issues.

**Backend follow-ups**
- **#115** auth hardening: (a) `/api/auth/forgot` has a timing side-channel because the
  real branch does a synchronous SendGrid send while the dummy branch does not, undercutting
  the anti-enumeration design; (b) `resetPassword` does not apply the ACTIVE-status gate that
  login/refresh enforce, a latent hole once user suspension ships. (Follow-up from #98.)
- **#89** Stage-2 reminder emails (walk-through + visit, 24h before): needs a founder
  `notification_log` migration for send-once idempotency + a `@Scheduled` job.
- **#60** picks system: à-la-carte EXTRA checkout, allowance tracking (`picksRemaining` /
  `premiumPicksRemaining`), and the `POST /api/app/picks` / `POST /api/checkout/extra`
  endpoints the contract documents but code does not yet have.
- **#63** PostHog instrumentation (server + client taxonomy behind an `AnalyticsService`).
- **#56** property SKU sheet / per-visit cost capture; **#57** visit templates / seasonal
  scheduler polish.

**Frontend / admin still on mock or needing wiring**
- **#117** admin dashboard panels (Recent subscribers / Pending walk-throughs / Needs
  attention) still mock; `app.health` and `app.reports` still mock (health not wired to the
  shipped `/api/app/health-score`); several admin routes (`plans`, `metrics`, `routes`,
  `settings`, `catalog`, `leads`) still mock. `checkout.tsx` is a stub.
- **#105** clear the TanStack Query cache on logout (stale-data leak on a shared device).
- No automated frontend tests at all (see [§8](#8-testing-strategy)).

**Infrastructure / secrets (founder action)**
- **#12** production deploy: Render backend, Cloudflare frontend, `homekept.ca` /
  `api.homekept.ca`, SSL, CORS verification, UptimeRobot. Nothing is deployed to prod yet.
- **#21** Stripe live setup: Products/Prices, a migration wiring real price IDs into
  `plan_tier`, the live webhook endpoint + signing secret.
- **#58** R2 bucket + credentials (photos return 503 until configured).
- **#63** a PostHog project/key must be provisioned before instrumentation can go live.
- **#1** general accounts/services setup (`decisions.md` registry, all rows still unchecked).
- Config to reconcile: the SendGrid `app.sendgrid.*` binding gap and the R2
  `R2_BUCKET_NAME` vs `R2_BUCKET` name mismatch (see [§7](#7-integrations)).

**Business / legal / launch (blocking, not code)**
- **#66** business formation and legal rails; **#65** field SOPs (COO deliverable #1);
  **#44** pre-launch checklist; **#45** soft launch / first subscriber; **#68** technical
  SEO; **#69** Google Business Profile + reviews.

**Tracker hygiene note.** #53 (Home Health Score v1) and #54 (customer self-serve) still show
open in the tracker but were implemented and merged (PRs #91, #87, #88), most likely because
the merging PRs did not use a "Closes #N" keyword. A cluster of week-N "closeout" and
foundational tickets (#3 through #8, #13, #20, #28, #33, #37) also remain open and read as
stale tracking debt, except the closeout tickets, which are deliberate manual end-to-end
verification gates. Worth a founder pass to close or re-scope before treating any of them as
outstanding work.

---

## 11. Founder-only boundaries

Enforced by `.claude/commands/ship.md` (the human-only list) and `CLAUDE.md`. Only a human
does these; the agent crew stops and escalates rather than cross the line:

- **Merging.** Every PR waits for a person. Standing authority (from memory): the crew may
  merge vetted-green PRs that contain no hand-write artifact; any PR containing a
  migration/config/auth-core waits for the founder, who reviews the artifact.
- **Hand-write artifacts.** SQL migrations, `application.yml` / `.properties`, auth/security
  core, and the access-note encryption. Builders hard-stop if they need one that does not
  exist.
- **Secrets, accounts, external setup.** Stripe, SendGrid, R2, PostHog, Sentry, Render,
  Cloudflare keys and accounts. All runtime config is env-injected; nothing is committed
  (`decisions.md` tracks which vault entry holds each).
- **Pricing.** Any change to `docs/pricing-and-visits.md` tier numbers, and anything derived
  from them (catalog seed migration, Stripe Prices, the frontend `lib/plans.ts` mirror).
- **Eval/benchmark runs that cost money**, and **prompt/rubric version bumps** (the agent
  files and rubrics).

---

## 12. Configuration and secrets reference

From `backend/src/main/resources/application.yml` and `.env.example`. No production values
are committed; every entry below is env-injected. The app is designed to boot with none of
these set (integrations degrade to warn/503), which is why CI is green without secrets.

| Env var | Purpose | Behavior if unset |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres connection | Defaults to local `homekept` DB |
| `JWT_SIGNING_KEY` | HS256 signing/HMAC key (also used for activation/reset tokens) | Dev sentinel; startup fails outside dev-mode |
| `APP_SECURE_COOKIES` | Force `Secure` on auth cookies | `false`; also inferred from `X-Forwarded-Proto` |
| `APP_DEV_MODE` | Relax startup guards for local dev | `false` (production posture) |
| `APP_TIMEZONE` | Render zone | `America/Toronto` |
| `CORS_ALLOWED_ORIGIN_1/2` | CORS allowlist | localhost dev origins |
| `ACCESS_NOTES_ENC_KEY` | Base64 32-byte AES-GCM key | Blank; startup fails outside dev-mode |
| `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` | Idempotent first-admin seed on startup | Blank; no seed |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Stripe API + webhook verification | Blank; blank webhook secret fails startup outside dev-mode |
| `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_PORTAL_RETURN_URL` | Redirect targets | localhost defaults |
| `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_REGION` | Cloudflare R2 | Blank; photo endpoints return 503 |
| `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, `SENDGRID_FROM_NAME`, `FRONTEND_BASE_URL` | Email | Emails skip silently. **Binding gap: no `app.sendgrid.*` block yet (see [§7.2](#72-sendgrid-notification-domain)).** |
| `VITE_API_URL` (frontend) | API base origin | Same-origin if empty |

There is one config file, `application.yml`; there are no Spring profiles beyond the
env-var-driven `app.dev-mode` flag.

---

## 13. Where code and the design docs disagree

Collected so the founder can reconcile. None of these are silent: each is either an
unfinished feature the contract already anticipated, or stale doc prose.

**Against `backend/api-contract.md`:**
- Documented, not built: `GET /api/app/activity`, `POST /api/app/picks`,
  `POST /api/checkout/extra`, `POST /api/admin/visits/{id}/complete`. The picks/extra
  endpoints belong to issue #60; the contract already flags picks tracking as "not yet
  built."
- Built, not documented: `GET /actuator/health` (Actuator infrastructure, allowlisted).
- The owner-app section header says "role: CUSTOMER, or ADMIN via ownership check," but every
  `/api/app/*` controller is strictly `@PreAuthorize("hasRole('CUSTOMER')")`; there is no
  `hasAnyRole`/ADMIN path anywhere. The parenthetical is aspirational, not current behavior.
- `GET /api/admin/bookings` and `/subscribers` accept an optional `limit` param (default 20,
  max 100) that the contract signatures omit. Same param, undocumented.

**Against `backend/homekept-backend-architecture.md`:**
- The `billing` domain (§2.9: `invoice`, `payment_event` tables, a thin Stripe cache) **does
  not exist** as code, only an empty `package-info.java`. All billing/Stripe logic lives in
  `subscription`; the `payment_event` table is not built. Reconcile by either building
  `billing` or folding the doc's §2.9 into `subscription`.
- The `shared` package (§1: "timezone config, ID generation") is empty/vestigial;
  `TimeZoneConfig` lives in `config` and there is no separate ID generator.
- `technician`'s own `package-info` says "stubbed until Stage 3," but a working
  `technician_profile` CRUD slice (entity, repo, admin service, controller, DTOs) is built.
  Only regions/availability are genuinely deferred. The one-liner is stale.
- The `application.yml` encryption comment says access-notes encryption is "reserved for
  future"; it is fully implemented.
- "Every status transition goes through a state-machine class" holds for subscriber/visit/
  booking only; `RescheduleRequestStatus` and `TodoItemStatus` use inline-guarded
  `setStatus`. Not a rule violation (the rule names three), but the blanket phrasing overstates.

**Policy copy ahead of wiring:** the privacy policy names PostHog and Sentry as active
processors; neither is wired in code yet (PostHog has only a placeholder column; Sentry has
no SDK). Verify before launch so the policy is accurate.

---

## 14. How to keep this current

Update this file when the shape changes, not on every commit:

- **New domain** -> add a row to [§2](#2-domain-model-and-boundaries) (responsibility, owned
  tables, state machine) and note its cross-domain query service if it exposes one.
- **New endpoint** -> add it to [§4](#4-api-surface) **and** to `backend/api-contract.md` in
  the same PR (the contract rule in `CLAUDE.md`). If the two ever disagree, that is a bug to
  fix, not a difference to document.
- **New migration** -> add a row to the [§3](#3-data-model-and-migrations) table with its
  one-line purpose and key columns; keep the "migrations present" note in the header current.
- **A gap closes or a new one opens** -> update [§10](#10-known-gaps-and-follow-ups); when an
  integration goes live (Stripe keys, R2 bucket, SendGrid, PostHog, deploy), move it out of
  gaps and update [§7](#7-integrations) and [§12](#12-configuration-and-secrets-reference).
- **A design-doc/code disagreement is reconciled** -> remove it from [§13](#13-where-code-and-the-design-docs-disagree).

Refresh the "Last verified" commit in the header whenever you do a substantive pass, so a
reader knows how much to trust the detail. The design docs (`pricing-and-visits.md`,
`homekept-backend-architecture.md`, `api-contract.md`, `three-year-plan.md`) remain the
sources of truth for rules, intent, the seam, and phasing; this file is the map of what is
actually built against them.
