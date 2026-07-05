# Go-live checklist (founder-only)

The application code is being driven to done: every screen redesigned, on real data, no
fabrication, all reviewed and merged. **This file is the gap between "code done" and "live in
front of paying customers"** — the items only a human can do (accounts, secrets, deploy) plus a
few config fixes that live behind the hand-write boundary.

Last updated 2026-07-05. Order is roughly the order you'd do them in.

## 1. Accounts and infrastructure (issue #12)
- [ ] **Render** account; deploy the Spring Boot backend. Serve it at **`api.homekept.ca`**
      (CNAME to Render), never the raw `*.onrender.com` — same-site cookies depend on it.
- [ ] **Cloudflare** account; deploy the TanStack Start frontend (worker is `homekept-frontend`).
- [ ] Managed **Postgres** (Render or Neon/Supabase). Flyway runs V1..V9 on boot.

## 2. Secrets (set in the prod environment, never in git)
- [ ] `JWT_SIGNING_KEY` — HS256 signing key.
- [ ] `ACCESS_NOTES_ENC_KEY` — AES-GCM key for property access notes (lockbox codes). Without
      it the technician day sheet can't decrypt access notes.
- [ ] `ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD` — the first admin. **Rotate off the weak dev
      value** (`laith@gmail.com` / `Laith123`) before prod, and remove the dev seed admin.
- [ ] Stripe (issue #21): `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, and the seeded
      `stripe_price_id_*` values (incl. the founding price). Create the Products/Prices in Stripe
      and the webhook endpoint pointing at `api.homekept.ca/api/webhooks/stripe`.
- [ ] SendGrid: the API key + verified from-address. **See the config-binding fix below first**,
      or the key won't bind and email silently stays off.
- [ ] R2 (issue #58): account + bucket + credentials for technician photos. **See the var-name
      fix below.**
- [ ] PostHog (issue #63): project key, if you want analytics at launch.

## 3. Config-binding fixes (issues #120, #121) — hand-write, do before wiring the above
These were found during the architecture audit. They are in `application.yml` / `.env.example`
(your hand-write boundary), so they're listed here rather than auto-fixed.
- [ ] **SendGrid won't bind as documented.** `.env.example` uses `SENDGRID_API_KEY`, but there's
      no `app.sendgrid.*` block, so the binder expects `APP_SENDGRID_*`. Reconcile
      `application.yml` + `.env.example` so the env var names match the `@ConfigurationProperties`
      prefix, or email stays no-op even with a valid key.
- [ ] **R2 var name mismatch:** `.env.example` says `R2_BUCKET_NAME`; code binds `R2_BUCKET`.
- [ ] **Privacy policy** names PostHog and Sentry as active processors, but neither is wired
      (Sentry not at all). Either wire them or remove them from the policy before launch —
      naming processors you don't use is a compliance/accuracy problem (#121).
- [ ] **api-contract.md** still promises a visit-detail `photos[]` field that was never built
      (deferred to #58). Remove the promise or build it.

## 4. Reminders infrastructure (issue #89) — founder migration
- [ ] Write the `notification_log` migration (dedupe table) and enable `@Scheduled`, then the
      walk-through + visit 24h reminder emails can ship. The issue reserves this migration to you.

## 5. Verify end-to-end (needs the deployed stack; issues #13, #20, #28, #33, #37)
Run these against the live stack once #1–#3 are done:
- [ ] Auth flow: register-via-activation, login, refresh, logout, forgot/reset password.
- [ ] Full conversion path: book walk-through → confirmation email → activation invite →
      set password → pick plan → Stripe checkout (test card) → subscriber ACTIVE via webhook.
- [ ] Visit lifecycle: auto-scheduled first visit appears; technician day sheet; complete flow.
- [ ] Emails actually arrive (booking confirmation, activation, welcome, payment failed,
      subscription cancelled, visit complete, password reset) once SendGrid is bound.

## 6. Pre-launch and launch (issues #44, #45, #65, #66, #69)
- [ ] Pre-launch checklist (#44).
- [ ] Business formation + legal rails (#66) and field SOPs (#65) — COO deliverables.
- [ ] Google Business Profile + reviews engine (#69) — the highest-leverage local channel.
- [ ] Soft launch and first subscriber (#45).

## What's already done (for reference)
The whole customer app (booking, auth, activation, checkout, dashboard, Home Health, visits,
reports, your-list, billing, settings) and the admin console are on **real data with no
fabrication**, in the "Considered Modern" design system. The technician app and the landlord/PM
portfolio are the remaining redesign pieces in flight. Backend integrations (Stripe, SendGrid,
R2) are all coded and no-op gracefully until their secrets/config above are provided.
