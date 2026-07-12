---
name: homekept-config-and-flags
description: >-
  The complete catalog of HomeKept backend configuration: every app.* key, its
  env var, default, whether it is required in production, and how it degrades;
  plus the two known env-var binding bugs and how to add a config axis. Load
  this when setting env vars, wiring an integration (Stripe/SendGrid/R2), a
  feature that should be off, debugging "why is email/photos not working", or
  before touching configuration. Triggers: env var, application.yml,
  .env.example, APP_DEV_MODE, JWT_SIGNING_KEY, SENDGRID, R2_BUCKET, STRIPE_,
  secure cookies, admin seed, config binding, "not sending email", "photos 503".
---

# HomeKept config and flags

All backend config binds from the `app.*` namespace via `AppProperties`
(`@ConfigurationProperties(prefix = "app")`) mapped in `application.yml`.
**`application.yml` and `.env.example` are a founder-only hand-write boundary —
read them, never generate edits to them** (see `homekept-change-control`).
Verified against the repo on 2026-07-06.

## Config catalog

| Env var | `app.*` key | Default (dev) | Prod? | Behaviour |
|---|---|---|---|---|
| `APP_TIMEZONE` | `app.timezone` | `America/Toronto` | keep | Render zone for the `TimeZoneConfig` bean |
| `APP_SECURE_COOKIES` | `app.secure-cookies` | `false` | **`true`** | Forces Secure flag on auth cookies |
| `APP_DEV_MODE` | `app.dev-mode` | `false` | **`false`** | `true` disables the JWT startup guard. **Never `true` in prod.** |
| `JWT_SIGNING_KEY` | `app.jwt.signing-key` | a blocked dev-sentinel value (see `application.yml`; the guard rejects it) | **required** | HS256 key. Guard rejects blank/<32 bytes/the dev sentinel when `dev-mode=false`. Generate: `openssl rand -hex 32` |
| `ACCESS_NOTES_ENC_KEY` | `app.encryption.access-notes-key` | `""` | **required for day sheet** | AES-GCM key (base64 32 bytes) for lockbox codes |
| `ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD` | `app.admin-seed.*` | `""` | one-time | Both set + user absent → seeds one ADMIN on startup (idempotent) |
| `CORS_ALLOWED_ORIGIN_1/2` | `app.cors.allowed-origins` | `localhost:5173`, `4173` | set to `https://homekept.ca` | Allowed browser origins |
| `FRONTEND_BASE_URL` | `app.frontend-base-url` | `http://localhost:8080` | set to prod URL | Base for email links (activation) |
| `STRIPE_SECRET_KEY` | `app.stripe.secret-key` | `""` | required | Blank → warn, API calls fail, no hard-fail |
| `STRIPE_WEBHOOK_SECRET` | `app.stripe.webhook-secret` | `""` | required | Webhook signature verification |
| `STRIPE_SUCCESS_URL` / `_CANCEL_URL` / `_PORTAL_RETURN_URL` | `app.stripe.*` | localhost URLs | required | Redirects |
| `R2_ENDPOINT` | `app.r2.endpoint` | `""` | required for photos | Blank → 503 (graceful) |
| `R2_BUCKET` | `app.r2.bucket` | `""` | required for photos | **See binding bug below** |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | `app.r2.*` | `""` | required for photos | Secret is never logged |
| `R2_REGION` | `app.r2.region` | `auto` | keep | R2 uses `auto` |
| SendGrid | `app.sendgrid.api-key` / `from-email` / `from-name` | blank / blank / `HomeKept` | required for email | **See binding bug below** |
| `POSTHOG_API_KEY` | (analytics) | unset | optional | Analytics at launch (issue #63) |

## Graceful degradation (which integrations fail-fast vs skip)

- **Fail fast in prod:** `JWT_SIGNING_KEY` (the app refuses to start with a
  weak/sentinel key when `APP_DEV_MODE=false`).
- **Skip quietly when blank:** SendGrid (`SendGridEmailSender` logs + skips),
  R2 (`R2StorageService` returns 503), Stripe (logs a warning, calls fail at
  runtime), admin seed (no seed). This is why dev/CI/fresh-prod all boot.

## The two known binding bugs (issues #120 / #121) — founder hand-write

Both are why an integration can look configured but silently stay off. They are
documented in `docs/go-live-checklist.md` §3 and must be fixed by the founder.

1. **SendGrid never binds as `.env.example` documents (#120).** `.env.example`
   lists `SENDGRID_API_KEY`, but **there is no `app.sendgrid:` block in
   `application.yml`**. So Spring's relaxed binding expects `APP_SENDGRID_APIKEY`
   / `APP_SENDGRID_FROMEMAIL`, not `SENDGRID_API_KEY`. Set `SENDGRID_API_KEY` and
   email stays a no-op with no error. Fix (**propose this to the founder; these
   are hand-write files, do NOT edit them yourself**): add an `app.sendgrid.*`
   block to `application.yml` mapping `${SENDGRID_API_KEY:}` etc., so the
   documented env name binds.
2. **R2 var-name mismatch (#121).** `.env.example` says `R2_BUCKET_NAME`, but
   `application.yml` binds `app.r2.bucket: ${R2_BUCKET:}` — i.e. **`R2_BUCKET`**.
   Set `R2_BUCKET_NAME` and the bucket is blank → photos 503. Fix (**propose to
   the founder**): reconcile the two names so `.env.example` matches the
   `${R2_BUCKET}` the binder reads.
3. Related (#121): the privacy policy names PostHog and Sentry as active
   processors, but neither is wired (Sentry not at all). Wire them or remove them
   from the policy before launch.

If you are debugging "email/photos not working", check these FIRST.

## How to add a config axis (checklist)

Adding config touches the hand-write boundary, so you **propose**, the founder
**writes**. The full change is:

1. **[You write this]** Add the field to the right nested record in
   `backend/.../config/AppProperties.java` with a sensible `@DefaultValue` (use
   `@DefaultValue` on the whole sub-record if the integration must degrade
   gracefully rather than NPE — see how `r2`/`sendGrid` are annotated).
2. **[Propose to founder — do NOT edit]** Map it in `application.yml`:
   `app.<group>.<kebab-key>: ${ENV_NAME:default}`.
3. **[Propose to founder — do NOT edit]** Document the env var in
   `backend/.env.example` with the **exact same name** used in the `${ENV_NAME}`
   placeholder (this is the step whose omission caused #120/#121).
4. **[You write this]** If it changes runtime behaviour, add it to
   `config/AppPropertiesBindingTest` so the binding is proven.
5. **[You write this]** Note prod requirements in `docs/go-live-checklist.md`.

## When NOT to use this skill / use a sibling instead

- Recreating the toolchain / running builds → `homekept-build-and-env`.
- Deploying and the go-live secret sequence → `homekept-run-and-operate` and
  `homekept-go-live-campaign`.
- Why an integration degrades the way it does → `homekept-architecture-contract`.
- A specific runtime symptom's triage → `homekept-debugging-playbook`.

## Provenance and maintenance

Verified 2026-07-06 against `application.yml`, `AppProperties.java`,
`backend/.env.example`, `docs/go-live-checklist.md`. Re-verify:

- Binding map: `grep -nE '\$\{[A-Z_]+' backend/src/main/resources/application.yml`
- Typed config: `cat backend/src/main/java/com/homekept/config/AppProperties.java`
- Documented env vars: `cat backend/.env.example`
- Binding bugs status: `docs/go-live-checklist.md` §3 (fixed once the env names match)
