---
name: homekept-run-and-operate
description: >-
  How HomeKept runs and is deployed: what runs where, the api.homekept.ca cookie
  requirement, Flyway-on-boot, where artifacts (photos, emails, logs) land, and
  the founder-only launch sequence. Load this when running the app against a
  real backend, deploying, reasoning about production topology, or wiring
  domains/cookies. Triggers: deploy, Render, Cloudflare, worker, api.homekept.ca,
  same-site cookie, Flyway on boot, production, hosting, "where do photos go",
  go live, launch readiness, deploy sequence.
---

# HomeKept run and operate

Two deployables. The **backend** (Spring Boot) runs on **Render**, served at
**`api.homekept.ca`**. The **frontend** (TanStack Start) runs on **Cloudflare**
as the worker **`homekept-frontend`**. Postgres is managed (Render or
Neon/Supabase). Verified against `docs/go-live-checklist.md` on 2026-07-06.

> Deployment itself — accounts, secrets, DNS, the actual `go live` — is a
> **founder-only** boundary. This skill explains the topology and how to operate
> it; the executable, gated launch runbook is `homekept-go-live-campaign`, and
> the human checklist is `docs/go-live-checklist.md`.

## Topology and the one hard constraint

```
Browser ──> homekept.ca            (Cloudflare worker: homekept-frontend, SSR)
        └─> api.homekept.ca        (Render: Spring Boot API)  ──> managed Postgres
                                    ├─> SendGrid (email)
                                    └─> Cloudflare R2 (visit photos)
```

**The cookie constraint (do not violate):** the API must be served at
`api.homekept.ca` (a CNAME to Render), **never the raw `*.onrender.com` host**.
Auth uses httpOnly JWT cookies (`hk_access`/`hk_refresh`) that must be same-site
with the frontend; a different registrable domain breaks login silently. In prod
also set `APP_SECURE_COOKIES=true` and rely on `server.forward-headers-strategy`
so `isSecure()` is true behind Render's TLS proxy.

## What happens on boot

- **Flyway runs migrations `V1..V9`** automatically on startup against the
  configured Postgres. A failed migration fails the boot — that is intended.
  Migrations are a founder hand-write artifact; never edit an applied one.
- The **JWT startup guard** aborts boot if `JWT_SIGNING_KEY` is weak/sentinel
  and `APP_DEV_MODE=false` (see `homekept-config-and-flags`).
- The **admin seeder** creates one ADMIN if `ADMIN_SEED_*` are set and absent.
- SendGrid/R2/Stripe bind but stay dormant until their secrets are set (graceful
  degradation).

## Where artifacts land

| Artifact | Destination |
|---|---|
| Visit photos | Cloudflare **R2** bucket (`R2_BUCKET`); `R2StorageService` returns 503 until configured |
| Transactional email | **SendGrid**; `SendGridEmailSender` logs-and-skips until configured |
| App logs | Render service logs (backend), Cloudflare logs (frontend worker) |
| Payments state | Stripe → webhook → subscriber state via the state machine |

## Running against a real backend (locally or staging)

- The frontend expects the API on the origin its cookies are scoped to. Guards
  are **client-side** because SSR has no cookie — see `homekept-debugging-playbook`
  for the login/redirect traps this creates.
- Empty-body 2xx responses (login/refresh/forgot/reset) are normal; the client
  must not try to `JSON.parse` an empty body (this exact bug once killed login —
  see `homekept-failure-archaeology`).

## The launch sequence (summary — founder executes)

Full detail and checkboxes in `docs/go-live-checklist.md`. Order:
1. Accounts + infra (Render backend at `api.homekept.ca`, Cloudflare frontend,
   managed Postgres).
2. Secrets (`JWT_SIGNING_KEY`, `ACCESS_NOTES_ENC_KEY`, `ADMIN_SEED_*`, Stripe,
   SendGrid, R2, PostHog).
3. **Fix the config-binding bugs first** (#120/#121) or email/photos stay off.
4. Reminders infra (#89: the `notification_log` migration + enable `@Scheduled`)
   — founder migration.
5. Verify end-to-end on the live stack (auth, conversion path, visit lifecycle,
   emails).
6. Pre-launch + soft launch.

## When NOT to use this skill / use a sibling instead

- Building/testing locally (not deploying) → `homekept-build-and-env`.
- The env vars and the binding bugs in detail → `homekept-config-and-flags`.
- The step-by-step, decision-gated launch campaign → `homekept-go-live-campaign`.
- Inspecting a running system's state → `homekept-diagnostics-and-tooling`.

## Provenance and maintenance

Verified 2026-07-06 against `docs/go-live-checklist.md`. Re-verify:

- Topology, domains, worker name, secrets: `docs/go-live-checklist.md` §1–2
- Migration set applied on boot: `ls backend/src/main/resources/db/migration/`
- Cookie/security config: `application.yml` (`server.forward-headers-strategy`,
  `app.secure-cookies`) and the identity domain
