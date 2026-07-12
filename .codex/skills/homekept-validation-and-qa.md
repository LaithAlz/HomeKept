# HomeKept Validation and QA

> **When to use this:** Open this when writing or reviewing tests, deciding
> whether a change is proven, wondering what the test suite covers, or before
> claiming something works. Triggers: "is this tested", "what should I test",
> "how do I add a test", "Testcontainers", "does it pass", "CI green",
> "evidence", "acceptance", state machine test, webhook test, integration test,
> coverage.

Evidence discipline: a change is not "done" because it compiles or looks right.
It is done when the right tests exist and the right review gates are satisfied.
Verified against the repo on 2026-07-06 (34 backend test classes).

## The evidence bar

| Layer | What "passing" means |
|---|---|
| Backend correctness | Integration tests run against **real Postgres via Testcontainers** (never a mocked DB) and pass. CI `backend` job runs `./gradlew build`. |
| Frontend correctness | `bun run build` succeeds (it type-checks); `bunx eslint <changed files>` clean; `bunx prettier --write <changed files>`. |
| Security / spec / copy | The review gates pass (see `.codex/skills/homekept-change-control.md` and `AGENTS.md`): a **spec review** on every diff (against acceptance criteria, `api-contract.md`, the architecture doc, and the non-negotiables), a mandatory **adversarial security review** on load-bearing paths (auth, Stripe/webhooks, tokens, payments, access-note encryption, state machines), and a **copy review** on changed customer-visible strings. |
| CI | Three checks green: **Backend (Gradle build + tests)**, **Frontend (build)**, **GitGuardian**. |

Locally, Docker is CI-only here, so the honest local claim for a backend change
is "compiles clean (`./gradlew compileJava compileTestJava`), tests run in CI" —
not "tests pass". See `.codex/skills/homekept-build-and-env.md`.

## What must ALWAYS be tested (architecture doc Part 8)

1. **Every state machine transition — legal AND illegal.** The three machines
   each have a test:
   - `subscription/SubscriberStateMachineTest`
   - `visit/VisitStateMachineTest`
   - `booking/WalkthroughBookingStateMachineTest`
   A new transition (or a new illegal edge) is not done until both directions are
   asserted.
2. **Every Stripe webhook handler — with fixture payloads.** See
   `StripeWebhookIntegrationTest` and the test-only `FakeStripeServiceConfig`.
   Payment state changes are load-bearing; a webhook handler ships with a fixture
   test.
3. **Every mutating service method — via an integration test.** Examples across
   domains: `SubscriptionSelfServeIntegrationTest` (pause/resume/cancel),
   `visit/RescheduleRequestIntegrationTest`, `visit/AppTodoIntegrationTest`,
   `ActivationIntegrationTest`, `AdminSubscriberIntegrationTest`.
4. **Do NOT test** generated Spring Data repositories or trivial getters. Test
   behaviour, not the framework.

## The certified / golden inventory (backend, on `main` 2026-07-06)

Representative, not exhaustive (34 classes total — run the `find` command in
Provenance for the full list). Also present but not itemized below:
`ActivationIntegrationTest`, `AdminSubscriberIntegrationTest`,
`common/ClientIpResolverTest`, `visit/RescheduleRequestIntegrationTest`.

Auth & identity: `AuthIntegrationTest`, `identity/JwtServiceTest`,
`identity/LoginRateLimiterTest`, `RefreshTokenServiceTest`,
`PasswordResetIntegrationTest`. Config: `config/AppPropertiesBindingTest`.
Booking/catalog: `BookingIntegrationTest`, `CatalogIntegrationTest`,
`FoundingRateIntegrationTest`. Subscription/billing:
`AppSubscriptionQueryIntegrationTest`, `SubscriptionSelfServeIntegrationTest`,
`CheckoutControllerIntegrationTest`, `StripeWebhookIntegrationTest`. Visit:
`HealthScoreIntegrationTest`, `AppVisitIntegrationTest`, `TechVisitIntegrationTest`,
`VisitSchedulingIntegrationTest`, `AdminVisitIntegrationTest`,
`TechPhotoIntegrationTest`, `TechTodoIntegrationTest`, `AppTodoIntegrationTest`. Notification:
`EmailTemplatesTest`, `NotificationEmailIntegrationTest`,
`SendGridEmailSenderTest`. Property: `AccessNotesCipherTest`. Admin/dashboard:
`dashboard/AdminDashboardIntegrationTest`, `technician/AdminTechnicianIntegrationTest`.

(The `subscription/PortfolioMultiPropertyIntegrationTest` referenced in some PRs
lives on the unmerged #132 branch, not on `main` — do not assume it is present
until #132 merges.)

## How to add a test

- **Integration test = real Postgres.** Follow an existing integration test in
  the same domain as your template (they share the Testcontainers bootstrap).
  Do NOT introduce a mocked `DataSource`.
- **Auth in tests is real:** tests log in via `POST /api/auth/login` and carry
  the `hk_access` cookie, rather than faking a principal. Copy the pattern from
  `AuthIntegrationTest` / a domain integration test.
- **Assert ownership as 404, not 403.** For any endpoint that scopes by owner,
  add a cross-user probe that expects 404 (the pattern the portfolio and health-
  score tests use).
- **Webhooks/Stripe:** use `FakeStripeServiceConfig` and fixture payloads; assert
  the resulting state transition, not just a 200.
- **Money:** assert integer cents.

## Acceptance-threshold discipline

- Green CI is necessary, not sufficient — on load-bearing paths, the adversarial
  security review is a hard gate: if it cannot answer yes to "would you bet a
  building on this merge?", that overrides a green build.
- A pre-existing baseline failure is not your regression, but you must confirm
  it exists on `main` before dismissing it (e.g. the known
  `learn.$slug.tsx:199` tsc error). Never launder a new failure as "pre-existing".
- If a rule says test X and no test exists, that is a gap to fill, not a reason
  to skip.

## When NOT to use this skill / use a sibling instead

- Running the build/test commands and the Docker constraint → `.codex/skills/homekept-build-and-env.md`.
- Measuring/inspecting a running system (not unit tests) → `.codex/skills/homekept-diagnostics-and-tooling.md`.
- First-principles "prove the invariant" recipes → `.codex/skills/homekept-proof-and-analysis-toolkit.md`.
- Who reviews what and the gates → `.codex/skills/homekept-change-control.md` (and `AGENTS.md` for the workflow and human-only boundary).

## Provenance and maintenance

Test inventory verified 2026-07-06. Re-verify:

- All test classes: `find backend/src/test/java -name "*Test.java" | sed 's#.*/com/homekept/##' | sort`
- State machine tests: `find backend/src/test/java -iname "*StateMachine*Test.java"`
- Webhook fixtures: `grep -rln "FakeStripeServiceConfig\|Webhook" backend/src/test/java`
- CI checks: `cat .github/workflows/ci.yml`
- Testing rules: architecture doc Part 8, and the "Testing expectations" section of `CLAUDE.md`
