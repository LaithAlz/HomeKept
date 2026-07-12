---
name: homekept-debugging-playbook
description: >-
  Symptom-to-cause triage for HomeKept's real failure modes, the traps that cost
  time, and the experiment that tells two causes apart. Load this FIRST when
  something is broken and you need to narrow it fast: login/auth failing, email
  or photos not working, the backend won't start, a 403/404 you did not expect,
  a build failing, wrong money or time on screen. Triggers: "not working",
  "login fails", "no email", "photos 503", "won't start", "unexpected 403",
  "unexpected 404", "build fails", debug, triage, broken, stuck.
---

# HomeKept debugging playbook

Start here when something is broken. Find the symptom, run the first check, and
branch. Most HomeKept failures are one of a small set of causes. The *stories*
behind these live in `homekept-failure-archaeology`; this is the flowchart.
Verified 2026-07-06.

## Symptom → first check → likely cause

| Symptom | First check | Likely cause / fix |
|---|---|---|
| **Login/auth fails on valid creds** | Is the response a 2xx with an empty body? Does the client try `JSON.parse` on it? | The empty-body-2xx trap. `api.ts request<T>()` must read text and parse only if non-empty. (Fixed in 255ffb0 — confirm no regression.) |
| **Login works locally, fails deployed** | Is the API on `api.homekept.ca` (not `*.onrender.com`)? Is `APP_SECURE_COOKIES=true`? | Cookie is not same-site with the frontend, or not Secure behind TLS. See `homekept-run-and-operate`. |
| **Protected page flashes then redirects** | Is the guard running in `useEffect`? | Expected: SSR has no cookie, so guards are client-side. Not a bug; do not "fix" by moving auth to SSR. |
| **Email never arrives** | Is the env var `APP_SENDGRID_*` (not `SENDGRID_API_KEY`)? | Config-binding bug #120: no `app.sendgrid:` yml block, so `SENDGRID_API_KEY` binds to nothing and the sender logs-and-skips. `homekept-config-and-flags`. |
| **Photo upload/serve returns 503** | Is `R2_BUCKET` set (not `R2_BUCKET_NAME`)? | Config-binding bug #121, or R2 simply unconfigured (graceful 503). |
| **Backend refuses to start** | Look at the boot log: JWT guard? Flyway? | Weak/sentinel `JWT_SIGNING_KEY` with `APP_DEV_MODE=false`, or a failed migration `V1..V9`. Both are intended hard-stops. |
| **Unexpected 403** | Is it a role check or an ownership check? | 403 = wrong role (`@PreAuthorize`). Ownership failures are **404**, never 403. If you got 403 on your own resource, the role is wrong, not ownership. |
| **404 on a resource you own** | Are you passing the right `propertyId`, and does the caller own it? | Ownership resolution (`resolveOwnedSubscriber`) returns 404 for unowned/unknown ids by design. Check the id and the owning user. |
| **Frontend build/tsc fails** | Is the only error `learn.$slug.tsx:199`? | That is a known pre-existing baseline on `main`. Anything else is yours. |
| **Contrast/looks-wrong after a style change** | Did you change a token in `theme.css`? | Re-check WCAG AA; text on amber accent is dark ink `#11201a`. (See the #122 regression.) |
| **Redirect goes off-site** | Is the target validated by parsed origin? | Use `new URL(raw, location.origin).origin === location.origin`, never `startsWith("/")`. |
| **Money is 100x off** | cents vs dollars? | API money is integer **cents** → `formatCentsCAD`. Local `plans.ts` is whole dollars → `formatCad`. Do not mix. |
| **Wrong time displayed** | Is it rendered via the `TimeZoneConfig` bean? | Store UTC (`Instant`/`TIMESTAMPTZ`), render `America/Toronto`; never hardcode a zone. |
| **Backend tests "won't run" locally** | Is Docker up? | Testcontainers needs Docker (CI-only here). Locally use `./gradlew compileJava compileTestJava`. |

## The traps that cost real time

1. **Empty-body 2xx is success.** The single most expensive frontend bug in this
   repo's history. Any new fetch wrapper or endpoint that returns 200/202 with no
   body must not `JSON.parse`.
2. **SSR has no cookie, so guards are client-side.** Do not try to authenticate
   during SSR; you will see "logged out" flashes and be tempted to move auth to
   the server. That is the architecture, not a bug (`homekept-architecture-contract`).
3. **Integrations fail silently by design.** SendGrid/R2/Stripe degrade
   gracefully. "It didn't error" is not "it worked." Check the boot log for the
   warning and verify the env-var *name* against `homekept-config-and-flags`.
4. **404-not-403 is deliberate.** Do not "fix" an ownership 404 into a 403 to be
   "more informative" — it leaks existence and violates non-negotiable #5.
5. **Docker-less locally.** Never claim the test suite passed locally without
   Docker; say "compiles clean, tests in CI."

## Discriminating experiments

- **Is login broken by the cookie or by the body parse?** `curl -i` the login
  endpoint. If you see `Set-Cookie: hk_access=...` and a 200 with an empty body,
  the server is fine — it is the client parse (trap #1). If no `Set-Cookie` or a
  cross-domain host, it is the cookie/domain (trap #2).
- **Is email off due to binding or the key?** Grep the boot log for the SendGrid
  "skip" warning. If present with a key set, the env-var *name* is wrong (#120),
  not the key.
- **Is it role or ownership?** Hit the endpoint as the resource owner with the
  correct role. 404 → ownership scoping; 403 → role/`@PreAuthorize`.
- **Is a frontend error mine?** `git stash && bunx tsc --noEmit` on a clean tree;
  if the error is still there, it is baseline, not yours.

## When NOT to use this skill / use a sibling instead

- The full story/commit behind a fixed bug → `homekept-failure-archaeology`.
- Measuring rather than guessing (tools, logs, curl recipes) →
  `homekept-diagnostics-and-tooling`.
- Config/env specifics → `homekept-config-and-flags`.
- Whether your fix needs a reviewer/gate → `homekept-change-control`.

## Provenance and maintenance

Verified 2026-07-06. Re-verify the referenced fixes with the commands in
`homekept-failure-archaeology` (Provenance). Add a new row here whenever a
symptom costs more than an hour to diagnose.
