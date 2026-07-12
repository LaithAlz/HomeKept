# HomeKept architecture contract

> **When to use this:** Open this when you need to understand how the HomeKept backend
> is structured, where a piece of logic belongs, what must never break, or whether a
> design you're considering fits — triggers include "where does this go", "which
> domain", "domain boundary", "state machine", "invariant", "graceful degradation",
> "why is it built this way", "known weakness", architecture, entities, and
> integrations.

Subscription home maintenance (GTA West). Spring Boot 4.1.0 / Java 17 backend,
TanStack Start (React 19) frontend on Cloudflare. This skill states the
decisions that are expensive to reverse and the invariants that hold the system
together. It is descriptive of the *why*; the enforceable *rules* live in
`.codex/skills/homekept-change-control.md`.

Verified against the repo on 2026-07-06. Primary sources of record:
`backend/homekept-backend-architecture.md`, `backend/api-contract.md`,
`docs/architecture-and-decisions.md`.

## Domain map

Packages are domain-first under `com.homekept.*`. Each owns its entities, repos,
services, and controllers.

| Package | Owns |
|---|---|
| `identity` | Users, auth, JWT, admin seed, password reset |
| `subscription` | Subscribers, activation, plan selection, pause/resume/cancel, portfolio (multi-property), and **currently** all Stripe checkout/portal/webhook + sync code (`CheckoutController/Service`, `StripeService(Impl)`, `StripeWebhookController/Service`) |
| `property` | Homes, encrypted access notes (lockbox codes) |
| `booking` | Walk-through bookings (the lead → conversion funnel) |
| `catalog` | Plan tiers and the pickable services menu (`/api/catalog/*`) |
| `visit` | Scheduled visits, the visit lifecycle, todos, health score, reschedules |
| `technician` | Technician roster and profiles, the day sheet |
| `billing` | Intended home for an `Invoice`/`PaymentEvent` local cache of Stripe (arch doc §2.9). **Stub today — only `package-info.java`; the live Stripe code sits in `subscription`.** Placement debt, stated plainly, not a home for new Stripe work yet. |
| `notification` | Transactional email (SendGrid) |
| `storage` | Cloudflare R2 (visit photos) |
| `dashboard` | Admin aggregate metrics (`/api/admin/dashboard`) |
| `config` | `AppProperties`, `TimeZoneConfig`, security config |
| `common` / `shared` | Cross-cutting infra (exception handling, base types) |

## The domain-boundary invariant (the most-broken rule)

**A domain may call another domain's service. It may NEVER touch another
domain's repository or entities.** Reaching across couples you to a schema you
do not own and skips that domain's rules.

Worked example (correct): `AppPropertiesService` in `subscription` needs a
home's next-visit date and health score. It calls `VisitQueryService` and
`HealthScoreService` (visit domain *services*) — not the visit repositories. If
you find yourself importing another domain's `*Repository` or `@Entity`, stop
and add or use a service method instead.

## State machines — the only legal way to change status

Status is never written on an **unvalidated** path. The model is
**validate-then-write**: the owning service asks the state machine to validate
the transition (it throws `IllegalVisitTransitionException` /
`IllegalSubscriptionStateException` on an illegal edge), and only then performs
the write on the entity. So `setStatus(...)` calls do exist inside services — but
each must sit behind a state-machine check. A naked status write with no machine
validation is the violation.

| Machine | Entity | Verified states (2026-07-06) |
|---|---|---|
| `SubscriberStateMachine` | `Subscriber` | `PENDING_ACTIVATION`, `ACTIVE`, `PAUSED`, `PAYMENT_ISSUE`, `CANCELLED` |
| `VisitStateMachine` | `Visit` | `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `INCOMPLETE`, `CANCELLED`, `RESCHEDULED` |
| `WalkthroughBookingStateMachine` | `WalkthroughBooking` | `PENDING`, `CONFIRMED`, `PERFORMED`, `CONVERTED`, `LOST`, `NO_SHOW` |

Note the split of authority for subscribers: **app-driven** transitions
(pause/resume/cancel requests via `SubscriptionSelfServeService`) validate
through the machine, while the **Stripe-authoritative** status (ACTIVE after
payment, CANCELLED, PAYMENT_ISSUE, PAUSED) is applied by `StripeWebhookService`
when the webhook fires — Stripe is the source of truth for billing state. To see
the legal edges, read the machine class; it is the source of truth, not this
table.

## Integrations and how each degrades

The system is designed to boot and run without any external secret, so dev, CI,
and a fresh prod all start cleanly. Degradation is deliberate and asymmetric:

| Integration | Config (`app.*`) | Behaviour when unconfigured |
|---|---|---|
| JWT signing | `app.jwt.signing-key` | **Fails fast in prod.** When `APP_DEV_MODE=false`, the app refuses to start if the key is blank, <32 bytes, or the dev sentinel. Anyone with a public key forges ADMIN tokens. |
| Access-note encryption | `app.encryption.access-notes-key` | Blank until the property domain needs it; the day sheet cannot decrypt lockbox codes without it. |
| Admin seed | `app.admin-seed.*` | Blank → no seed. Set both → idempotently creates one ADMIN on startup. |
| Stripe | `app.stripe.*` | Blank → logs a warning, API calls fail at runtime, app does NOT hard-fail. |
| SendGrid | `app.sendgrid.*` | Blank → `SendGridEmailSender` logs and skips the send. No hard failure. |
| R2 | `app.r2.*` | Blank → `R2StorageService` returns 503, never NPEs. `@DefaultValue` on the record so a missing block binds to all-blank. |

Detail of every axis, defaults, and the env-var names lives in
`.codex/skills/homekept-config-and-flags.md`.

## Invariants that must hold

- **Money is integer cents** off the wire (see `.codex/skills/homekept-change-control.md`
  rule 2).
- **Time:** `TIMESTAMPTZ` in SQL, `Instant` in Java, stored UTC, rendered
  `America/Toronto` via the `TimeZoneConfig` bean. Never hardcode a zone.
- **Ownership failures return 404, not 403** — resolve ownership at a single
  choke point (e.g. `SubscriberQueryService.resolveOwnedSubscriber`) before
  touching data; `userId` always comes from the JWT principal, never the client.
- **Auth is cookie-based JWT** (`hk_access`/`hk_refresh`, httpOnly) on the API
  origin. Because SSR has no cookie, route guards are **client-side**
  (`useEffect`) — this is a deliberate constraint, not a bug. See
  `.codex/skills/homekept-debugging-playbook.md` for the traps it creates.

## Load-bearing decisions and why

- **Domain-first packages** over layered (all-controllers / all-services): keeps
  a feature's blast radius inside one package and makes the boundary rule
  enforceable by inspection.
- **Staged roadmap.** `backend/homekept-backend-architecture.md` Part 10 Stage 1
  is the *current* scope. `docs/three-year-plan.md` says *when* later things get
  built. Do not build later-phase items early (see
  `.codex/skills/homekept-domain-reference.md`).
- **Real-Postgres tests via Testcontainers, never a mocked DB** — the schema and
  Flyway migrations are part of the contract, so tests run against them. See
  `.codex/skills/homekept-validation-and-qa.md`.
- **Health Score** (visit domain, migration V9) is a rubric computed from real
  visit/flag data, snapshotted — designed to become a defensible data asset (see
  `.codex/skills/homekept-research-and-methodology.md`).

## Known weak points (stated plainly, not hidden)

- **Config-binding mismatches (#120/#121).** `.env.example` documents
  `SENDGRID_API_KEY` and `R2_BUCKET_NAME`, but the binder wants `APP_SENDGRID_*`
  (no `app.sendgrid.*` yml mapping) and `R2_BUCKET`. Email/photos stay silently
  off until reconciled. Founder hand-write. Full detail in
  `.codex/skills/homekept-config-and-flags.md` and
  `.codex/skills/homekept-go-live-campaign.md`.
- **`CheckoutService.findByUserId` is single-result.** Portfolio Phase 1 (#132)
  makes "one user owns several subscribers" legal; the moment a user has 2+,
  `createCheckoutSession`/`createPortalSession` throw. Latent, not live (one
  subscriber per activation today). Must route through `resolveOwnedSubscriber`
  before Phase 2 ships property creation.
- **`api-contract.md` promises a visit-detail `photos[]`** that was never built
  (deferred to #58). Remove the promise or build it.
- **Frontend lint is not repo-enforced** (Lovable-generated code predates the
  prettier config). Enforced per-file in review only.

## When NOT to use this skill / use a sibling instead

- The enforceable rules and review gates → `.codex/skills/homekept-change-control.md`.
- Business rules (tiers, visit calendar, legal scope) →
  `.codex/skills/homekept-domain-reference.md`.
- Config axes and env-var names → `.codex/skills/homekept-config-and-flags.md`.
- How to prove an invariant actually holds → `.codex/skills/homekept-proof-and-analysis-toolkit.md`.
- A weak point's full history → `.codex/skills/homekept-failure-archaeology.md`.

## Provenance and maintenance

Volatile facts date-stamped 2026-07-06. Re-verify:

- Domain packages: `ls backend/src/main/java/com/homekept/`
- State machines: `ls backend/src/main/java/com/homekept/**/*StateMachine.java`
- Status enums: `cat backend/src/main/java/com/homekept/subscription/SubscriberStatus.java` (and `visit/VisitStatus.java`, `booking/BookingStatus.java`)
- Config axes: `cat backend/src/main/java/com/homekept/config/AppProperties.java`
- Integration degradation: read the Javadoc in `AppProperties.java`
- Known weak points: `docs/go-live-checklist.md` sections 3–4
