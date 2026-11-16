# HomeKept

Subscription home maintenance for the GTA (Oakville, Mississauga, Milton). Homeowners subscribe, technicians carry out the visits, and the business runs on this codebase.

**Live at [homekept.ca](https://homekept.ca).** Two founders. I (Laith) write all the software.

## What is in here

Three apps on one API:

- **Owner app:** homeowners manage their property, subscription, and visits.
- **Technician app:** technicians see their visits and how to get into each home.
- **Admin console:** running the business day to day.

| Directory | Contents |
|---|---|
| `backend/` | Spring Boot API (Java 17, PostgreSQL, Flyway). Architecture: [`backend/homekept-backend-architecture.md`](backend/homekept-backend-architecture.md). API: [`backend/api-contract.md`](backend/api-contract.md) |
| `frontend/` | TanStack Start and React app with server-side rendering, deployed to Cloudflare Workers |
| `mockups/` | Static HTML design mockups. `mockups/v2/` is the current design system |
| `ops/` | Deployment and operations notes |
| `docs/` | Architecture decisions and the go-live checklist |

## Stack

Java 17, Spring Boot, PostgreSQL, Flyway, TypeScript, React 19 (TanStack Start), Stripe, SendGrid, Cloudflare Workers and R2, Docker, Testcontainers.

## Engineering notes

- **State machines own every status change.** Subscriptions and visits move through explicit state machine classes. Nothing writes a status column directly.
- **Billing handles duplicate webhooks.** Stripe can deliver the same event twice, so an idempotency ledger makes a repeat a no-op.
- **Sensitive data is encrypted.** Property access notes (how a technician gets into a home) are encrypted per column with AES-256-GCM.
- **It fails closed.** The service refuses to start without its JWT signing key, Stripe webhook secret, or encryption key.
- **Ownership failures return 404, not 403,** so the API does not reveal which resources exist.
- **Money is integer cents.** Timestamps are stored in UTC and shown in Toronto time.
- **Security review.** I ran an adversarial review against my own code and fixed what it found in billing integrity, authentication, visit lifecycle guards, and input validation.

## Running it

Backend tests use Testcontainers, so `./gradlew test` and `./gradlew build` need a running Docker daemon. Compile targets do not.

## How it is built

Work goes branch, pull request, CI, review, squash-merge. I use Claude Code as a pair programmer and review every pull request. Anything labeled `hand-write` (migrations, auth, `application.yml`) is written by hand, not generated.
