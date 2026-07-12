# HomeKept change control

> **When to use this:** Open this before starting any change to HomeKept — to decide
> whether something is safe to touch, whether it crosses the founder-only hand-write
> boundary, which reviews it needs, or when to stop and escalate; triggers include "can
> I change this", "is this safe to merge", "do I need a migration", "who reviews this",
> "hand-write", "ship this", "squash merge", non-negotiables, ownership 404, money in
> cents, state machine, hand-write boundary, and review gates.

This is the constitution. Every other skill defers to it. If a runbook here conflicts
with a clever idea, the runbook wins. When in doubt, STOP and ask the founder — a
blocked change costs an hour; a wrong merge can flood a customer's basement or expose
another tenant's data.

Verified against the repo on 2026-07-06 (Spring Boot 4.1.0, migrations V1–V9, frontend
TanStack Start + bun 1.3.13).

## The one-paragraph model

In this repo's Claude Code setup, builders write code and read-only subagents review
it; the discipline that matters, and that carries over regardless of harness, is that
whoever reviews a diff must be able to reject it and must not be the same pass that
wrote it. A human merges. Some artifacts are never generated at all (the hand-write
boundary). Changes flow: **branch from main → implement → focused commits → PR with
What/Why → CI green → review → fix blockers → squash-merge (human)**. The workflow and
the human-only boundary for a Codex agent working this repo live in `AGENTS.md` at the
repo root — that is the Codex entry point. The equivalent procedure written for the
Claude Code harness is `.claude/skills/homekept-feature.md` (the step-by-step) and
`.claude/commands/ship.md` (the subagent crew orchestration) — useful as ground truth
for what the gates below are standing in for. This skill is the *why* and the *rules*.

## Classify the change first

| If the change touches… | Then it is… | Required gate |
|---|---|---|
| A SQL migration, `application.yml`/`.properties`, auth/security core, the access-note encryption, or pricing numbers in `docs/pricing-and-visits.md` | **Hand-write (founder-only)** | STOP. Do not generate it. Escalate. |
| Auth, Stripe/webhooks, tokens, payments, access-note encryption, or a state machine | **Load-bearing** | Spec review **and** a mandatory adversarial security review |
| Any customer- or admin-visible string | **Copy** | Copy review |
| Any diff at all | **Standard** | Spec review, every time |

Load-bearing also includes issues #6, #14–16, #21–29, #34, #54, #55, #58, #60 (the
load-bearing issue list; for the Claude Code harness it's tracked in
`.claude/commands/ship.md` — carry the same list forward as ground truth regardless of
which agent is doing the review). When unsure whether a string changed, do the copy
review anyway.

## The non-negotiables, and why each exists

Each rule has a rationale and a real incident. Do not "improve" a rule away without the
founder.

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
   the machine enforces. See `.codex/skills/homekept-architecture-contract.md`.

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
   exact regressions and fix values live once in
   `.codex/skills/homekept-failure-archaeology.md`, incident #3.

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

## The review gates

The Claude Code setup runs these as separate read-only subagents (`spec-guardian`,
`safety-reviewer`, `copy-guardian`) that cannot write and cannot be talked into
approving by the code's own author. Codex has no subagent crew and no `/ship` command,
so treat each row below as a **distinct review pass** — a human reviewer's checklist
item, and/or a deliberate self-review step run before asking for human sign-off, done
with fresh eyes rather than as a continuation of the same reasoning that wrote the
diff.

| Gate | Runs on | Checks |
|---|---|---|
| Spec review | Every diff | The diff against the issue's acceptance criteria, `backend/api-contract.md`, `backend/homekept-backend-architecture.md`, and the non-negotiables above |
| Adversarial security review | Load-bearing diffs (see classification table) | Auth, Stripe/webhooks, tokens, payments, access-note encryption, and state-machine paths — attack the diff with concrete exploit sketches, close with "would you bet a building on this merge?" |
| Copy review | Any diff that changes a customer- or admin-visible string | Brand voice, the legal rails (Competition Act, CASL, CPA), and exact prices |

There is no Opus/Haiku/Sonnet model-to-risk split to reason about in Codex — the point
that split encoded still applies: spend the most adversarial scrutiny where a wrong
merge floods a customer's basement or leaks a tenant (the security review), and don't
skip the cheap, mechanical pass (the copy review) just because it feels lower-stakes.

## The review loop

1. Build on a fresh kebab-case branch from `main`.
2. Spec review. Loop build → spec review at most **2** times. Still not approved
   after 2 → stop and hand to a human.
3. Adversarial security review runs if load-bearing. A closing verdict of "would
   you bet a building on this merge? — NO" is a hard block that overrides any
   other approval.
4. Copy review runs if strings changed (same 2-loop cap).
5. Open the PR (What/Why + the review verdicts written up). Wait for CI green.
   **STOP.** A human merges.

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

- Executing the actual build/PR steps → `AGENTS.md` at the repo root (the Codex
  workflow and human-only boundary). The Claude Code harness's equivalent is
  `.claude/skills/homekept-feature.md` (the procedure) and `.claude/commands/ship.md`
  (the crew).
- *Why* an architectural invariant holds → `.codex/skills/homekept-architecture-contract.md`.
- The business rules being gated (tiers, visits, legal scope) →
  `.codex/skills/homekept-domain-reference.md`.
- What counts as evidence a change is correct → `.codex/skills/homekept-validation-and-qa.md`.
- A past incident's full story → `.codex/skills/homekept-failure-archaeology.md`.

## Provenance and maintenance

Volatile facts are date-stamped 2026-07-06. Re-verify when they may have drifted:

- Non-negotiables text: `sed -n '/Non-negotiables/,/Commands/p' CLAUDE.md`
- Crew + human-only boundary: `cat .claude/commands/ship.md` (Claude Code harness
  ground truth; the Codex entry point for the same rules is `AGENTS.md`)
- The procedure: `cat .claude/skills/homekept-feature.md` (Claude Code harness
  version; the Codex-native workflow lives in `AGENTS.md`)
- State machine class names: `ls backend/src/main/java/com/homekept/**/*StateMachine.java`
- Load-bearing issue list: search `safety-reviewer runs` in `.claude/commands/ship.md`
- Agent models: frontmatter of each file in `.claude/agents/` (Claude Code harness
  only — Codex has no subagent crew; see "The review gates" above for the
  harness-neutral version)
