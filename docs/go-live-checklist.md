# Go-live checklist (founder-only)

The application is **code-complete**: customer app, technician app, admin console, billing,
visits, photos, emails, and analytics are all built, on real data, no fabrication, reviewed and
merged. **This file is the gap between "code done" and "live in front of paying customers"** —
the items only a human can do: accounts, secrets, deploy, verify.

Last updated 2026-09-05. Order is roughly the order you'd do them in. The code-side blockers that
used to live here (config-binding fixes #120/#121, the reminders migration #89, PostHog wiring,
the visit `photos[]` contract) are all **done** — see "Already handled in code" at the bottom.

## 0. Do these first (security, and they block other steps)

- [ ] **Rotate the Render Postgres password.** The live connection string, credentials included,
      was pasted into a chat session twice on 2026-09-04/05. Rotate it and update
      `DB_URL`/`DB_PASSWORD`.
- [ ] **Lock the Render origin to Cloudflare** (also listed under R2 hardening as #46, but it is
      security-critical, not optional). `ClientIpResolver` trusts the `CF-Connecting-IP` header
      with no Cloudflare-CIDR check. That is safe only while the origin is unreachable except
      through Cloudflare. If `*.onrender.com` answers directly, anyone can send a fresh
      `CF-Connecting-IP` per request and every per-IP rate limiter is defeated at once: login,
      forgot-password, reset, change-password, activation, and staff invite. Use Cloudflare
      authenticated origin pull or a Tunnel, or Render's access control.
- [ ] **Cancel and refund the founder's own live $149 Complete test subscription** before
      archiving the retired prices in step 3.

## 1. Accounts and infrastructure (issue #12)
- [x] **Domain**: `homekept.ca`. Both apps share the registrable domain (auth cookies are
      `SameSite=Lax`): backend on `api.homekept.ca`, frontend on the apex `homekept.ca`.
- [ ] **Render** — the Spring Boot backend is already deployed here. Serve it at
      **`api.homekept.ca`** (CNAME to Render), never the raw `*.onrender.com`.
- [ ] **Cloudflare** — deploy the TanStack Start frontend. From `frontend/`:
      `bun run build && wrangler deploy` (worker config in `wrangler.jsonc`). Build with
      `VITE_API_URL=https://api.homekept.ca` (and `VITE_PUBLIC_POSTHOG_KEY=…` if using analytics).
- [ ] Managed **Postgres** (Render / Neon / Supabase). Flyway runs V1..V15 on boot.

## 2. Secrets (set in the prod environment, never in git)

**The backend refuses to start in production without these three** (unless `APP_DEV_MODE=true`):
- [ ] `JWT_SIGNING_KEY` — HS256 signing key, ≥ 32 bytes, not the dev sentinel.
- [ ] `ACCESS_NOTES_ENC_KEY` — AES-GCM key for property access notes (lockbox codes). Without it
      the technician day sheet can't decrypt access notes.
- [ ] `STRIPE_WEBHOOK_SECRET` — `whsec_…` from step 4 (a blank secret is an auth hole, so it
      fails closed).

**Also set:**
- [x] `FRONTEND_BASE_URL=https://homekept.ca`, `CORS_ALLOWED_ORIGIN_1=https://homekept.ca` (the apex,
      not `app.homekept.ca`, which has no DNS record: every email link is built from this value).
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

Order matters here — do these in sequence, not in parallel:

- [ ] **First, deploy the V12 build** (the one that includes
      `V12__remove_essential_and_founding.sql`). V12 nulls out COMPLETE's Stripe price ids
      on every deploy that runs it for the first time — running the script below before this
      migration has landed means V12 just nulls them right back out again.
- [ ] **Then** sort out the **Products + Prices**, matching `docs/pricing-and-visits.md`:
      - **PREMIER** ($249 / $2,490): live prices already exist and are unchanged. Leave them.
      - **COMPLETE** ($169 / $1,690): needs 2 brand-new recurring prices. The old $149 /
        $1,490 / $129 prices are retired and must not be reused.
      - **ESSENTIAL** ($89 / $890): retired in V12, reinstated in V15 at *exactly* its
        original numbers. So if the old $89 / $890 prices are still active in Stripe (you
        have not archived them yet), **reuse them** rather than making duplicates at the
        same amount. Only create new ones if you have already archived them.
- [ ] **Then** wire the new COMPLETE and ESSENTIAL Price IDs into the catalog: fill in and run
      **`docs/stripe-price-ids.sql`** against prod (a fill-in-the-blanks `UPDATE` script,
      COMPLETE and ESSENTIAL only, it does not touch PREMIER).
- [ ] **Then** enable **Stripe Tax** and set all four live prices to
      `tax_behavior: exclusive`. Prices are quoted before tax ("on top"), and the pricing page
      and the customer billing page both already say "plus HST" — but Stripe currently adds
      nothing, so a Complete customer is charged exactly $169.00 while the copy promises
      $169.00 + HST. `tax_behavior` can only be set once, while it is still `unspecified`.
      **Tell the agent once this is done**: the checkout session still needs `automatic_tax`
      enabled in code, and that change cannot ship before this step, because Stripe errors on
      `automatic_tax` when the prices are `unspecified`.
- [ ] **Then** archive the genuinely retired prices: Complete **$149 / $1,490 / $129** only.
      Keep every PREMIER price, and keep the Essential $89 / $890 prices if you are reusing
      them per the step above. Do this only after the new checkout is verified working, and
      after the test subscription in step 0 is cancelled.
- [ ] Add the **webhook endpoint** → `https://api.homekept.ca/api/webhooks/stripe`; copy its
      signing secret into `STRIPE_WEBHOOK_SECRET`.
- [ ] Set `STRIPE_SECRET_KEY` (`sk_live_…`) and the success / cancel / portal URLs.

## 4. SendGrid (email deliverability)
- [ ] `SENDGRID_API_KEY` + a verified `SENDGRID_FROM_EMAIL`.
- [ ] **Domain authentication (SPF/DKIM)** on `homekept.ca` so mail doesn't land in spam.
- [ ] Optional: run a mail-tester score (>7) on a real send.

## 5. Cloudflare R2 (visit photos)
- [ ] Account + `homekept-photos` bucket + an API token → the `R2_*` vars above.
- [ ] Optional hardening: a lifecycle rule to reap orphaned unconfirmed objects (#47). Locking
      the Render origin to Cloudflare (#46) moved up to step 0 — it is a rate-limiter
      prerequisite, not optional hardening.

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

## Open decisions that block code

- [ ] **Legal review of the cancellation window** before Phase 2 of the repositioning is built.
      The brief specifies a 12-month term where cancellation is only allowed within 30 days of
      renewal or the first 30 days of the initial term. Ontario's Consumer Protection Act rules
      on auto-renewing consumer contracts appear to require advance renewal notice and a
      continuing right to cancel, which conflicts with that. This needs a lawyer, not a
      judgement call. Today the product allows cancellation at any time (at period end), and
      the pricing-page FAQ says so — that answer becomes false the moment a term ships.
- [ ] **Reschedule the Nov 15 2026 visit.** It falls on a Sunday. Move it to Mon Nov 16.
- [ ] **Dismiss the six GitGuardian findings.** All are test-fixture passwords in integration
      tests, verified not to be real credentials.

## Already handled in code (you do NOT need to do these)
- Config-binding fixes (`SENDGRID_*` / `R2_BUCKET` env-var names) — fixed (#150/#151).
- Reminder-email infrastructure (`notification_log` + scheduler) — shipped (#89 / V10).
- PostHog instrumentation — done backend AND frontend, PII-scrubbed; just needs the key (#63).
- Visit `photos[]` in the API contract — built (#58/#124), signed URLs.
- Customer app, technician app, admin console — all on real data, no fabrication, in the
  "Considered Modern" design system. Backend integrations (Stripe, SendGrid, R2, PostHog) are
  coded and no-op gracefully until their secrets above are provided.
