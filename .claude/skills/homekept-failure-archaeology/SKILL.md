---
name: homekept-failure-archaeology
description: >-
  The chronicle of HomeKept's real bugs, dead ends, and fixes as symptom → root
  cause → evidence → status, so no one re-fights a settled battle. Load this
  when a symptom feels familiar, before "fixing" something that may already be
  understood, when reviewing auth/cookie/contrast/config code, or when you want
  the story behind a rule. Triggers: "has this happened before", "why did this
  break", "has login broken before", known login regression, empty body, open
  redirect, WCAG contrast, config not
  binding, DOCTYPE test, "known issue", past bug, regression, history.
---

# HomeKept failure archaeology

The settled battles. Each entry is symptom → root cause → evidence (commit) →
status. If your symptom matches a FIXED entry, the fix is already in — do not
re-derive it; confirm it did not regress. If it matches an OPEN entry, that is a
known gap with a known owner. Verified against git history on 2026-07-06.

**Note on discipline:** `git log --all | grep -i revert` returns **nothing** —
there are no reverts in this project's history. The gates work. The purpose of
this chronicle is to keep that record clean.

## FIXED

### 1. Login silently dead against a real backend
- **Symptom:** correct credentials showed the error banner; login, refresh,
  forgot, and reset all appeared to fail even on a 200.
- **Root cause:** `frontend/src/lib/api.ts` `request<T>()` only skipped
  `res.json()` for HTTP **204**. Those endpoints return empty **200/202** bodies,
  so `res.json()` threw on the empty body and the success path was routed into
  the error banner.
- **Evidence / fix:** commit **255ffb0** ("fix api.ts empty-body 2xx handling
  (#98) (#116)") — read the body as text and parse only if non-empty.
- **Status:** FIXED. Lesson: a 2xx with an empty body is success; never assume a
  JSON body. Related trap in `homekept-debugging-playbook`.

### 2. Open redirect in the sign-in `?next=` parameter
- **Symptom:** a crafted `?next=%2F%09%2Fevil.com` could bounce the user
  off-origin after login.
- **Root cause:** the sanitizer checked `raw.startsWith("/")`, but the WHATWG
  URL parser **strips tab/newline/CR**, so `/\t/evil.com` normalizes to
  `//evil.com` (protocol-relative → off-site). `startsWith("/")` passed it.
- **Evidence / fix:** the #17 sign-in wiring, commit **695db88** (#99) — replaced
  the prefix check with an origin compare:
  `new URL(raw, location.origin).origin === location.origin`.
- **Status:** FIXED. Lesson: validate redirect targets by parsed origin, never by
  string prefix.

### 3. WCAG contrast regressions from the reskin
- **Symptom:** three colour combos failed WCAG AA after the "Considered Modern"
  reskin: sage-on-primary (~4.31:1), the amber accent eyebrow on primary
  (~3.01:1), and the success status pill (~3.96:1).
- **Root cause:** the new token palette placed light text/tints on a primary and
  success that were too light.
- **Evidence / fix:** reskin commit **0de2ef9** (#122); fixed by deepening
  `--primary` to `#0d4132` and `--success` to `#0a6b44` in
  `frontend/src/styles/theme.css`.
- **Status:** FIXED. Lesson: any token change must re-check contrast math; text
  on the amber accent is always the dark ink `#11201a` (non-negotiable #6).

### 4. EmailTemplatesTest false-negative assertion
- **Symptom:** the email template test failed on valid HTML emails.
- **Root cause:** it asserted the HTML body `doesNotContain("!")` to catch
  unrendered `${...}`-style placeholders, but `<!DOCTYPE html>` contains `!`, so
  every real HTML email tripped it.
- **Evidence / fix:** commit **255ffb0** — strip tags
  (`replaceAll("<[^>]*>", " ")`) before the placeholder check.
- **Status:** FIXED. Lesson: assert on rendered/visible text, not raw markup.

### 5. Admin console fabrication (the de-fabrication wave)
- **Symptom:** admin metrics/leads/routes/settings and some customer surfaces
  showed invented data — fake subscriber names, made-up cohort/churn numbers,
  fabricated leads with fake PII, and a factually **wrong** "Resend" email-
  provider claim (the real provider is SendGrid).
- **Root cause:** screens built against mock fixtures (`lib/mock-admin.ts`) were
  never rewired to real endpoints.
- **Evidence / fix:** PRs **#125** (reports), **#133** (admin console) — every
  page now renders real API data or an honest empty state; `mock-admin.ts`
  deleted. Also fixed a real bug: the admin new-booking dropdown showed wrong
  prices ($129/$189/$289 instead of $89/$149/$249).
- **Status:** FIXED. Lesson: an honest empty state beats a fabricated number
  (non-negotiable #7; Competition Act).

## OPEN / LATENT (known, owned, not yet fixed)

### 6. Config-binding no-ops (#120 / #121)
- **Symptom:** setting `SENDGRID_API_KEY` / `R2_BUCKET_NAME` as `.env.example`
  documents leaves email/photos off with no error.
- **Root cause:** no `app.sendgrid:` block in `application.yml` (binder wants
  `APP_SENDGRID_*`); R2 binds `R2_BUCKET`, not `R2_BUCKET_NAME`.
- **Evidence:** `application.yml`, `backend/.env.example`, `docs/go-live-checklist.md`
  §3. **Status:** OPEN — founder hand-write. Detail: `homekept-config-and-flags`.

### 7. `CheckoutService.findByUserId` single-result (portfolio latent)
- **Symptom:** none yet.
- **Root cause:** returns a single `Optional<Subscriber>`; portfolio Phase 1
  (#132) makes "one user, many subscribers" legal, so `createCheckoutSession` /
  `createPortalSession` will throw once a user has 2+.
- **Evidence:** safety review on PR #132. **Status:** OPEN/LATENT — must route
  through `resolveOwnedSubscriber` before Phase 2 ships property creation.

### 8. `api-contract.md` promises visit `photos[]` never built (#58)
- **Root cause:** contract lists a field deferred with the R2 photo work.
- **Status:** OPEN — build it or remove the promise before relying on it.

### 9. Registration path vs contract (#17)
- **Symptom:** the frontend registration flow and the backend contract disagreed
  on how a user is created.
- **Resolution:** the frontend was aligned to the **activation-invite** flow the
  contract specifies (admin issues an activation invite; user sets a password),
  not a direct self-register. Recorded here so it is not "re-fixed" back to
  self-register. **Status:** RESOLVED by convention; check `api-contract.md`
  before touching the register/activation seam.

## When NOT to use this skill / use a sibling instead

- Live triage of a symptom you are seeing now → `homekept-debugging-playbook`
  (this skill is the archive; that one is the flowchart).
- The rule a fix hardened into → `homekept-change-control`.
- Config-binding specifics → `homekept-config-and-flags`.

## Provenance and maintenance

Commits verified 2026-07-06. Re-verify / extend:

- Login/DOCTYPE fix: `git show 255ffb0`
- Reskin/WCAG: `git show 0de2ef9 -- frontend/src/styles/theme.css`
- Sign-in redirect: `git show 695db88`
- Reverts (should stay empty): `git log --oneline --all | grep -i revert`
- Open items: `docs/go-live-checklist.md` §3–4
- When you fix an OPEN item or hit a new hard bug, add it here with commit + status.
