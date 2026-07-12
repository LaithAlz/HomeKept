---
name: homekept-diagnostics-and-tooling
description: >-
  How to MEASURE HomeKept instead of eyeballing it: the smoke-test and
  invariant-scanner scripts shipped here, plus recipes for reading CI logs,
  test reports, Flyway state, and the API over curl. Load this when you need
  evidence rather than a guess: is the API healthy, did a non-negotiable get
  violated, why did CI fail, what did a test actually assert. Triggers:
  "measure", "check the API", "curl", "CI logs", "gh run", "test report", "scan
  for violations", "is it actually working", diagnostics, instrumentation,
  smoke test.
---

# HomeKept diagnostics and tooling

Measure, do not eyeball. This skill ships runnable tools and the recipes to get
ground truth from the running system, CI, and the codebase. Verified 2026-07-06.

## Shipped scripts (in this skill's `scripts/`)

### `api-smoke.sh` — prove auth + an endpoint end to end
```bash
BASE_URL=http://localhost:8080 EMAIL=<admin-email> PASSWORD=<admin-password> \
  .claude/skills/homekept-diagnostics-and-tooling/scripts/api-smoke.sh /api/app/account
```
Logs in via `POST /api/auth/login`, verifies the `hk_access` cookie is set (a 2xx
login has an **empty body** by design — we check the cookie, not JSON), then
calls the endpoint with the cookie. Interpreting the result: `200 + JSON` =
healthy; `401` = cookie not accepted (domain/secure problem); `404` = ownership
scoping; no `Set-Cookie` = server/login problem, not a client parse problem.
This is the fastest way to split a client bug from a server bug
(`homekept-debugging-playbook`).

### `check-invariants.sh` — heuristic non-negotiable scan
```bash
.claude/skills/homekept-diagnostics-and-tooling/scripts/check-invariants.sh
```
Greps for candidate violations: status writes outside state machines, hardcoded
brand hex in components, float money near money-shaped names, em dashes in
frontend copy. **Every hit is a candidate for review, not a verdict.** Worked
example of why that matters: run against `main` on 2026-07-06 it flags many
`setStatus(...)` calls in services — most are legitimate *validated* writes
(the machine checks the transition, the service performs it; see
`homekept-architecture-contract`). Use the scan to find where to look, then read
the code around each hit.

## Measurement recipes

### CI — why did a run fail
```bash
gh pr checks <PR#>                 # per-check pass/fail + links
gh run list --branch <branch>      # recent runs
gh run view <run-id> --log-failed  # only the failed step's log
```
The three checks are Backend (Gradle build + tests), Frontend (build),
GitGuardian. GitGuardian is a separate GitHub App, not a CI job in `ci.yml`.

### Backend test reports (after `./gradlew build`/`test`, needs Docker)
```
backend/build/reports/tests/test/index.html   # human-readable
backend/build/test-results/test/*.xml          # machine-readable JUnit XML
```
Run one class: `./gradlew test --tests 'com.homekept.visit.HealthScoreIntegrationTest'`.

### Flyway / migration state
```bash
ls backend/src/main/resources/db/migration/    # the V1..V9 set applied on boot
```
A boot failure with a Flyway error means a migration failed — do not edit an
applied migration; that is a founder hand-write concern.

### What did a test actually assert
Read the test class, not its name. `grep -rn "assert" backend/src/test/java/com/homekept/<domain>/<Class>.java`.
See `homekept-validation-and-qa` for the always-test rules.

### Inspect the API by hand
```bash
curl -i -c jar.txt -H 'Content-Type: application/json' \
  -d '{"email":"...","password":"..."}' http://localhost:8080/api/auth/login
curl -i -b jar.txt http://localhost:8080/api/app/subscription
```
Watch for `Set-Cookie: hk_access=...` and empty 2xx bodies (both normal).

## When NOT to use this skill / use a sibling instead

- Narrowing a symptom to a cause → `homekept-debugging-playbook` (this skill is
  the instruments; that one is the flowchart).
- Whether a change is *proven* / what to test → `homekept-validation-and-qa`.
- First-principles proofs of an invariant → `homekept-proof-and-analysis-toolkit`.
- Config/env var checks → `homekept-config-and-flags`.

## Provenance and maintenance

Verified 2026-07-06. The scripts are self-contained and versioned with this
skill. Re-verify:

- Scripts run clean: `bash -n scripts/*.sh` then run `check-invariants.sh`
- Endpoint paths: `grep -rnE "@(Get|Post)Mapping" backend/src/main/java/com/homekept/**/*Controller.java`
- CI check names: `.github/workflows/ci.yml`
- If an endpoint or cookie name changes, update `api-smoke.sh` accordingly.
