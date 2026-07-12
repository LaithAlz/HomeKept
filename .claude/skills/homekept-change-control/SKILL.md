---
name: homekept-change-control
description: >-
  How every change to HomeKept is classified, gated, reviewed, and merged, and
  the non-negotiable rules with the reason and incident behind each. Load this
  BEFORE starting any change: deciding whether something is safe to touch,
  whether it crosses the founder-only hand-write boundary, which reviewers must
  see it, or when to STOP and escalate. Triggers: "can I change", "is this safe
  to merge", "do I need a migration", "who reviews this", "hand-write", "ship
  this", "squash merge", non-negotiables, ownership 404, money in cents, state
  machine, hand-write boundary, review gates.
---

# HomeKept change control

This is the constitution. Every other skill defers to it. If a runbook here
conflicts with a clever idea, the runbook wins. When in doubt, STOP and ask the
founder — a blocked change costs an hour; a wrong merge can flood a customer's
basement or expose another tenant's data.

Verified against the repo on 2026-07-06 (Spring Boot 4.1.0, migrations V1–V9,
frontend TanStack Start + bun 1.3.13).

## The one-paragraph model

Builders write code. Read-only reviewers check it and cannot be talked into
approving by the code's own author. A human merges. Some artifacts are never
generated at all (the hand-write boundary). Changes flow: **branch from main →
implement → focused commits → PR with What/Why → CI green → review → fix
blockers → squash-merge (human)**. The full procedure is
`.claude/skills/homekept-feature.md`; the crew orchestration is
`.claude/commands/ship.md`. This skill is the *why* and the *rules*.

## Classify the change first

| If the change touches… | Then it is… | Required gate |
|---|---|---|
| A SQL migration, `application.yml`/`.properties`, auth/security core, the access-note encryption, or pricing numbers in `docs/pricing-and-visits.md` | **Hand-write (founder-only)** | STOP. Do not generate it. Escalate. |
| Auth, Stripe/webhooks, tokens, payments, access-note encryption, or a state machine | **Load-bearing** | `spec-guardian` + `safety-reviewer` (Opus) |
| Any customer- or admin-visible string | **Copy** | `copy-guardian` (Haiku) |
| Any diff at all | **Standard** | `spec-guardian` (Sonnet), every time |

Load-bearing also includes issues #6, #14–16, #21–29, #34, #54, #55, #58, #60
(see `.claude/commands/ship.md`). When unsure whether copy changed, run
copy-guardian anyway.

## The non-negotiables, and why each exists

Each rule has a rationale and a real incident. Do not "improve" a rule away
without the founder.

1. **Hand-write boundary is absolute.** Migrations, `application.yml`, auth/
   security core, and pricing numbers are written by the founder, never
   generated. *Why:* these are the artifacts where a subtle generated error is
   unrecoverable (a bad migration corrupts prod data; a loose security default
   forges ADMIN tokens). *Incident:* the V9 health-score migration and the
   config-binding fixes (#120/#121, `SENDGRID_*` vs `APP_SENDGRID_*`,
   `R2_BUCKET_NAME` vs `R2_BUCKET`) were both deferred to the founder precisely
   because they live behind this boundary. **If your task needs a migration or
   config that does not exist, STOP and say so — do not invent it.**

2. **Money is integer cents.** Never floats, never `BigDecimal`-as-float. *Why:*
   float arithmetic silently loses money and fails reconciliation. *Applies to:*
   everything off the wire (`mrrCents`, catalog price cents). Static local
   product data in `frontend/src/lib/plans.ts` may hold whole-dollar CAD; render
   it with that file's helper, but anything from an API is cents → render with
   `formatCentsCAD`.

3. **State changes go through the state machine classes.** CLAUDE.md states it
   as "no direct status writes, anywhere, ever." In practice that means
   **validate-then-write**: the owning service asks the machine to validate the
   transition (it throws on an illegal edge), then performs the write — a status
   write with no preceding machine check is the violation. The three machines are
   `SubscriberStateMachine`, `VisitStateMachine`, `WalkthroughBookingStateMachine`
   (Stripe-driven subscriber status is applied by `StripeWebhookService`, which
   is the sanctioned billing-state entry point). *Why:* an illegal transition
   (e.g. reactivating a cancelled subscription) skips the invariants and events
   the machine enforces. See `homekept-architecture-contract`.

4. **Domain boundaries: a domain calls another domain's *service*, never its
   repository or entities.** Packages are domain-first (`com.homekept.<domain>`).
   *Why:* reaching into another domain's repo couples you to its schema and
   bypasses its rules. *Example:* `AppPropertiesService` (subscription) calls
   `VisitQueryService`/`HealthScoreService`, never the visit repos.

5. **Ownership failures return 404, not 403.** 403 is only for wrong role. *Why:*
   a 403 leaks that the resource exists. *Incident:* the portfolio Phase 1 work
   (#132) routes every `propertyId` through `resolveOwnedSubscriber`, which
   throws `SubscriberNotFoundException` → 404 so a cross-tenant probe is
   indistinguishable from "not found".

6. **Text on the amber accent (`#d29a44`) is the dark ink (`#11201a`), never
   white.** *Why:* white on amber fails WCAG AA contrast. Colour tokens are the
   single source of truth in `frontend/src/styles/theme.css`. *Incident:* the
   "Considered Modern" reskin (#122) shipped three contrast regressions; the
   exact regressions and fix values live once in `homekept-failure-archaeology`
   #3.

7. **No fabricated social proof or data in customer-facing copy.** *Why:*
   Competition Act (Canada) exposure, and it corrodes trust. *Incident:* the
   de-fabrication wave (#125, #133) tore out fake reports, fabricated admin
   metrics/leads/routes, and a factually wrong "Resend" email-provider claim
   (the real provider is SendGrid). Prefer an honest empty state to a fake
   number, always.

8. **No em dashes in customer-facing copy** (emails, UI, marketing). Use periods,
   commas, colons. Code comments are exempt. Founder style rule.

9. **Timestamps:** `TIMESTAMPTZ` in SQL, `Instant` in Java, stored UTC, rendered
   `America/Toronto` via the `TimeZoneConfig` bean — never a hardcoded zone.

## The crew and why the models differ

| Agent | Model | Role | Can write? |
|---|---|---|---|
| `implementer` | Sonnet | Backend builder | Yes |
| `frontend-builder` | Sonnet | UI builder | Yes |
| `spec-guardian` | Sonnet | Checks every diff vs acceptance criteria, arch doc, api-contract, never-break rules | No |
| `safety-reviewer` | Opus | Adversarial security review of load-bearing diffs | No |
| `copy-guardian` | Haiku | Brand voice, legal rails, exact prices on changed strings | No |

Model-to-risk: **Opus only where a wrong merge floods a basement or leaks a
tenant** (safety); **Haiku where it is string-matching** (copy); **Sonnet for
volume** (build + spec). Builders and reviewers are different agents on purpose:
a reviewer cannot be sweet-talked by the code's own author.

## The loop (what `/ship` enforces)

1. Build on a fresh kebab-case branch from `main`.
2. `spec-guardian` reviews. Loop build→spec at most **2** times. Still not
   APPROVE after 2 → hand to a human.
3. `safety-reviewer` runs if load-bearing. Its closing **"would you bet a
   building on this merge? — NO"** is a hard block that overrides any APPROVE.
4. `copy-guardian` runs if strings changed (same 2-loop cap).
5. Open the PR (What/Why + pasted verdicts). Wait for CI green. **STOP.** A
   human merges.

## Human-only — never delegate, never automate

- **Merging.** (In this project the founder has, at times, authorized the
  orchestrator to merge *vetted, green* PRs in order; that authority is granted
  per-session by the founder and is not assumed. Absent explicit authorization,
  stop at the green PR.)
- **Hand-write artifacts** (rule 1 above).
- **Secrets / accounts / external setup** (Stripe, SendGrid, R2, PostHog,
  Render, Cloudflare).
- **Pricing changes** and any edit to `docs/pricing-and-visits.md` tier numbers.
- **Eval/benchmark runs that cost money.**
- **Prompt/rubric version bumps** (the agent files in `.claude/`, the rubrics
  they cite).

## Git hygiene (hard rules)

- **Never `git add -A` or `git add .`.** Stage explicit paths only. *Why:* it is
  how stray files, secrets, and unrelated changes leak into a commit.
- **No AI attribution and no `Co-Authored-By` trailers** in commit messages.
- Branch names are kebab-case and descriptive. Commits are focused. PRs squash-
  merge.

## When NOT to use this skill / use a sibling instead

- Executing the actual build/PR steps → `homekept-feature.md` (the procedure)
  and `.claude/commands/ship.md` (the crew).
- *Why* an architectural invariant holds → `homekept-architecture-contract`.
- The business rules being gated (tiers, visits, legal scope) →
  `homekept-domain-reference`.
- What counts as evidence a change is correct → `homekept-validation-and-qa`.
- A past incident's full story → `homekept-failure-archaeology`.

## Provenance and maintenance

Volatile facts are date-stamped 2026-07-06. Re-verify when they may have drifted:

- Non-negotiables text: `sed -n '/Non-negotiables/,/Commands/p' CLAUDE.md`
- Crew + human-only boundary: `cat .claude/commands/ship.md`
- The procedure: `cat .claude/skills/homekept-feature.md`
- State machine class names: `ls backend/src/main/java/com/homekept/**/*StateMachine.java`
- Load-bearing issue list: search `safety-reviewer runs` in `.claude/commands/ship.md`
- Agent models: frontmatter of each file in `.claude/agents/`
