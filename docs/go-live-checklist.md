# Go-live checklist (founder-only)

The application is **code-complete**: customer app, technician app, admin console, billing,
visits, photos, emails, and analytics are all built, on real data, no fabrication, reviewed and
merged. **This file is the gap between "code done" and "live in front of paying customers"** —
the items only a human can do: accounts, secrets, deploy, verify.

Last updated 2026-08-05. Order is roughly the order you'd do them in. The code-side blockers that
used to live here (config-binding fixes #120/#121, the reminders migration #89, PostHog wiring,
the visit `photos[]` contract) are all **done** — see "Already handled in code" at the bottom.

## 1. Accounts and infrastructure (issue #12)
- [ ] **Domain**: `homekept.ca`. Both apps must share the registrable domain because auth cookies
      are `SameSite=Lax` — put the backend on `api.homekept.ca` and the frontend on
      `app.homekept.ca` (or the apex).
- [ ] **Render** — the Spring Boot backend is already deployed here. Serve it at
      **`api.homekept.ca`** (CNAME to Render), never the raw `*.onrender.com`.
- [ ] **Cloudflare** — deploy the TanStack Start frontend. From `frontend/`:
      `bun run build && wrangler deploy` (worker config in `wrangler.jsonc`). Build with
      `VITE_API_URL=https://api.homekept.ca` (and `VITE_PUBLIC_POSTHOG_KEY=…` if using analytics).
- [ ] Managed **Postgres** (Render / Neon / Supabase). Flyway runs V1..V10 on boot.

## 2. Secrets (set in the prod environment, never in git)

**The backend refuses to start in production without these three** (unless `APP_DEV_MODE=true`):
- [ ] `JWT_SIGNING_KEY` — HS256 signing key, ≥ 32 bytes, not the dev sentinel.
- [ ] `ACCESS_NOTES_ENC_KEY` — AES-GCM key for property access notes (lockbox codes). Without it
      the technician day sheet can't decrypt access notes.
- [ ] `STRIPE_WEBHOOK_SECRET` — `whsec_…` from step 4 (a blank secret is an auth hole, so it
      fails closed).

**Also set:**
- [ ] `FRONTEND_BASE_URL=https://app.homekept.ca`, `CORS_ALLOWED_ORIGIN_0=https://app.homekept.ca`.
- [ ] `APP_SECURE_COOKIES=true`, `APP_DEV_MODE=false`, `APP_TIMEZONE=America/Toronto`.
- [ ] `ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD` — the first admin. **Use a strong password and
      rotate off any weak dev value.** The seeder is idempotent (only creates if absent).
- [ ] `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, `PORT`.

**Graceful-degradation integrations** (blank = feature simply off, no crash) — steps 4–7 below:
- [ ] Stripe: `STRIPE_SECRET_KEY`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_PORTAL_RETURN_URL`.
- [ ] SendGrid: `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, `SENDGRID_FROM_NAME`.
- [ ] R2: `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_REGION`.
- [ ] PostHog: `POSTHOG_API_KEY` (backend) + `VITE_PUBLIC_POSTHOG_KEY` (frontend build).

## 3. Stripe (issue #21) — the biggest external step
- [ ] Create the live **Products + Prices** matching `docs/pricing-and-visits.md` (7 recurring
      prices: ESSENTIAL monthly/annual, COMPLETE monthly/annual/founding, PREMIER monthly/annual).
- [ ] Wire the real Price IDs into the catalog: fill in and run
      **`docs/stripe-price-ids.sql`** against prod (a fill-in-the-blanks `UPDATE` script).
- [ ] Add the **webhook endpoint** → `https://api.homekept.ca/api/webhooks/stripe`; copy its
      signing secret into `STRIPE_WEBHOOK_SECRET`.
- [ ] Set `STRIPE_SECRET_KEY` (`sk_live_…`) and the success / cancel / portal URLs.

## 4. SendGrid (email deliverability)
- [ ] `SENDGRID_API_KEY` + a verified `SENDGRID_FROM_EMAIL`.
- [ ] **Domain authentication (SPF/DKIM)** on `homekept.ca` so mail doesn't land in spam.
- [ ] Optional: run a mail-tester score (>7) on a real send.

## 5. Cloudflare R2 (visit photos)
- [ ] Account + `homekept-photos` bucket + an API token → the `R2_*` vars above.
- [ ] Optional hardening: a lifecycle rule to reap orphaned unconfirmed objects (#47), and lock
      the Render origin to Cloudflare (#46).

## 6. Analytics / error tracking (optional at launch)
- [x] **PostHog** (#63) — keys set on Render + Cloudflare 2026-09-03. Fully wired (server
      events + frontend, PII-scrubbed); verify events in PostHog Activity after a page view.
- [x] **Sentry** (#121) — not used; removed from the privacy policy 2026-09-03.

## 7. Verify end-to-end (against the deployed stack; #13, #20, #28, #33, #37)
- [ ] Auth: register-via-activation, login, refresh, logout, forgot/reset password.
- [ ] Full conversion path: book walk-through → confirmation email → activation invite → set
      password → pick plan → Stripe checkout (test card first) → subscriber ACTIVE via webhook.
- [ ] Visit lifecycle: the auto-scheduled first visit appears; technician day sheet; complete flow.
- [ ] Emails actually arrive: booking confirmation, activation, welcome, payment failed,
      subscription cancelled, visit report, password reset (7), plus the two 24h reminders.

## 8. Pre-launch and launch (#44, #45, #65, #66, #69)
- [ ] Pre-launch checklist (#44).
- [ ] Business formation + legal rails (#66) and field SOPs (#65) — COO deliverables.
- [ ] Google Business Profile + reviews engine (#69) — the highest-leverage local channel.
- [ ] Soft launch and first subscriber (#45).

## Already handled in code (you do NOT need to do these)
- Config-binding fixes (`SENDGRID_*` / `R2_BUCKET` env-var names) — fixed (#150/#151).
- Reminder-email infrastructure (`notification_log` + scheduler) — shipped (#89 / V10).
- PostHog instrumentation — done backend AND frontend, PII-scrubbed; just needs the key (#63).
- Visit `photos[]` in the API contract — built (#58/#124), signed URLs.
- Customer app, technician app, admin console — all on real data, no fabrication, in the
  "Considered Modern" design system. Backend integrations (Stripe, SendGrid, R2, PostHog) are
  coded and no-op gracefully until their secrets above are provided.
