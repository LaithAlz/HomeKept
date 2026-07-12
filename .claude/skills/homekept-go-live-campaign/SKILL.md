---
name: homekept-go-live-campaign
description: >-
  The executable, decision-gated campaign to take HomeKept from code-done to the
  first paying customer: numbered phases, exact commands, the expected
  observation at each gate and where to branch when you see something else, the
  ranked fix menu for the blockers, the wrong paths fenced off, and the
  promotion protocol through change control. Load this when working on launch
  readiness, the config-binding blockers, the end-to-end conversion path, or
  "why can't we go live yet". Triggers: go live, launch readiness, go-live
  blockers, launch checklist, first customer,
  activate, checkout, webhook not firing, email not sending on prod, deploy
  readiness, conversion path, #120, #121, #89.
---

# HomeKept go-live campaign

**Objective:** one real customer reaches `ACTIVE` on the live stack via the
Stripe webhook, and the transactional emails actually arrive. That is the
hardest live problem: the code is done, but the gap between "code-done" and
"live in front of a paying customer" is not.

**This campaign has founder-only gates.** Deploy, secrets, accounts, migrations,
and `application.yml`/`.env.example` edits are the hand-write boundary
(`homekept-change-control`). An engineer/agent can do everything up to those
gates: reproduce, propose the exact hand-write diff, and verify end-to-end. The
human executes the boundary steps. The founder checklist of record is
`docs/go-live-checklist.md`; this skill is the gated procedure. Verified
2026-07-06.

## Definition of done (measurable, not eyeballed)

- [ ] `POST /api/webhooks/stripe` moves a test subscriber to `ACTIVE`.
- [ ] Booking confirmation, activation, welcome, and payment emails arrive.
- [ ] Auth works on `api.homekept.ca` (login, refresh, logout).
- [ ] Flyway `V1..V9` applied on the prod DB.

## Phase 0 — Reproduce the ground state (no deploy)

```bash
cd backend && ./gradlew compileJava compileTestJava      # compiles clean (Docker-less)
# with a local Postgres + ADMIN_SEED_* set, boot the app, then:
EMAIL=<seed-admin> PASSWORD=<seed-pw> \
  .claude/skills/homekept-diagnostics-and-tooling/scripts/api-smoke.sh /api/app/account
```
**Expected:** compiles clean; smoke test logs in and returns a JSON body.
**If login fails with an empty-body error** → the client parse trap, not the
server (`homekept-debugging-playbook`).

## Phase 1 — Fix the config-binding blockers (#120 / #121) [propose; founder writes]

These silently disable email and photos, so nothing downstream can be verified
until they are fixed. They live in `application.yml` + `backend/.env.example`
(hand-write). **Ranked fix menu:**

1. **SendGrid does not bind (#120) — root cause.** There is no `app.sendgrid:`
   block in `application.yml`, so `SENDGRID_API_KEY` binds to nothing. *Theory:*
   `@ConfigurationProperties(prefix="app")` needs `app.sendgrid.api-key` mapped
   from the env. Fix (propose this exact diff to the founder): add
   ```yaml
   sendgrid:
     api-key: ${SENDGRID_API_KEY:}
     from-email: ${SENDGRID_FROM_EMAIL:}
     from-name: ${SENDGRID_FROM_NAME:HomeKept}
   ```
   under `app:` in `application.yml`. **Gate:** with a real key set, the boot log
   no longer prints the SendGrid "skip" warning.
2. **R2 var-name mismatch (#121).** `.env.example` says `R2_BUCKET_NAME`; the
   binder reads `R2_BUCKET`. Fix (propose this diff to the founder; do NOT edit
   `.env.example` yourself): rename `R2_BUCKET_NAME` to `R2_BUCKET`.
   **Gate:** photo endpoints stop returning 503 once R2 creds are set.
3. **Privacy policy names PostHog/Sentry but neither is wired.** Wire or remove
   from the policy before launch (accuracy/compliance).

**Fenced wrong path:** do NOT "fix" #120 by setting `SENDGRID_API_KEY` harder or
regenerating the key — the key is not the problem, the binding is. Setting
`APP_SENDGRID_APIKEY` would bind, but the intended fix is the yml block so the
documented env name works.

## Phase 2 — Secrets and accounts [founder]

Set in the prod environment (never git): `JWT_SIGNING_KEY` (`openssl rand -hex
32`), `ACCESS_NOTES_ENC_KEY`, `ADMIN_SEED_*` (rotate off the dev value), Stripe
(`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, price ids), SendGrid key + verified
sender, R2 creds, PostHog (optional). **Gate:** app boots with `APP_DEV_MODE=false`
(proves `JWT_SIGNING_KEY` is strong — the guard would abort otherwise).

## Phase 3 — Deploy [founder]

Backend to Render served at **`api.homekept.ca`** (CNAME, never `*.onrender.com`
— the cookie constraint), frontend to Cloudflare (`homekept-frontend`), managed
Postgres. **Gate:** `api.homekept.ca` is reachable; boot log shows Flyway applied
`V1..V9`. **If Flyway fails** → a migration error; do not edit an applied
migration, escalate.

## Phase 4 — Verify the conversion path end to end [engineer can drive]

Walk the real funnel against the live stack and check each gate:

| Step | Expected observation | If not → branch |
|---|---|---|
| Book a walk-through | Confirmation email arrives | Email off → recheck Phase 1 #120, and the verified sender |
| Admin issues activation invite | Activation email with magic link | Link uses `FRONTEND_BASE_URL`; if localhost, that env var is unset |
| Set password via the link | Account becomes usable; login works | 401 after login → cookie/domain (Phase 3) |
| Pick a plan → Stripe checkout (test card) | Redirect to `STRIPE_SUCCESS_URL` | Wrong redirect → `app.stripe.*` URLs still localhost |
| Webhook fires | Subscriber → `ACTIVE` via `StripeWebhookService` | Stays `PENDING_ACTIVATION` → `STRIPE_WEBHOOK_SECRET` wrong or endpoint not registered in Stripe |

**Fenced wrong path:** if the subscriber will not go `ACTIVE`, do NOT set the
status by hand — that violates the state-machine rule and hides a broken webhook.
Fix the webhook (secret + registered endpoint at
`api.homekept.ca/api/webhooks/stripe`).

## Phase 5 — Reminders infrastructure (#89) [founder migration]

1. **[Founder]** Write the `notification_log` (dedupe) migration — reserved to
   the founder (hand-write boundary). 2. **[You, once the migration exists]**
   Enable `@Scheduled`. Then the walk-through + 24h visit reminder emails can
   ship. **Gate:** a scheduled reminder sends exactly once (dedupe holds).

## Promotion protocol (how a fix becomes live)

Every code fix in this campaign routes through the normal crew: build →
`spec-guardian` → `safety-reviewer` if it touches auth/Stripe/webhooks/state →
PR → green CI → human merge (`homekept-change-control`). **Success is measured**
(the gates above), never judged by eye. Hand-write and deploy steps are the
founder's; you propose the exact diff and verify the result.

## When NOT to use this skill / use a sibling instead

- The static human checklist → `docs/go-live-checklist.md`.
- Topology and the cookie constraint in depth → `homekept-run-and-operate`.
- The config keys and binding bugs in depth → `homekept-config-and-flags`.
- A symptom mid-campaign → `homekept-debugging-playbook`.
- Proving a gate really passed → `homekept-proof-and-analysis-toolkit`.

## Provenance and maintenance

Verified 2026-07-06. Re-verify:

- Launch checklist + order: `docs/go-live-checklist.md`
- Binding bugs still open: `grep -n "sendgrid" backend/src/main/resources/application.yml` (absent = #120 open), `grep -n "R2_BUCKET" backend/.env.example`
- Webhook path: `grep -rn "webhooks/stripe" backend/src/main/java`
- When a phase's gate changes, update the table.
