# HomeKept — agent guide (GPT / Codex)

Subscription home maintenance (GTA West). Spring Boot 4.1 / Java 17 backend +
TanStack Start (React 19) frontend on Cloudflare. Two founders; the CEO writes
software and runs visits.

This is the entry point for a GPT/Codex-based coding agent. It mirrors the
discipline in `CLAUDE.md` (the Claude Code guide) and routes you to a detailed
skill library under `.codex/skills/`. **You have no auto-loader and no subagent
crew** — so when a task matches a skill below, OPEN that file and follow it. Keep
`AGENTS.md` and `CLAUDE.md` consistent if you change either.

Verified against the repo on 2026-07-12.

## Read before building anything (docs of record)

In order, as relevant:
1. `docs/pricing-and-visits.md` — the commercial spec: tiers, the 12-month visit
   calendar, picks, materials, legal scope rails. **Business rules live here.**
2. `backend/homekept-backend-architecture.md` — domains, entities, state
   machines, integrations, staged roadmap (Part 10 Stage 1 = current scope).
3. `backend/api-contract.md` — the frontend/backend seam. Renaming or removing an
   endpoint requires updating this file in the same PR.
4. `docs/three-year-plan.md` — phase gates; explains *when* things get built.
   Don't build later-phase items early.
5. The GitHub issue you're implementing.

## Skill router — open the right file for the task

| When you are… | Open |
|---|---|
| About to change anything: classify it, find the gates, the non-negotiables | `.codex/skills/homekept-change-control.md` |
| Deciding where logic goes; understanding the domains/invariants/weak points | `.codex/skills/homekept-architecture-contract.md` |
| Touching plans, pricing, visits, picks, scope rails, materials | `.codex/skills/homekept-domain-reference.md` |
| Setting env vars / wiring Stripe·SendGrid·R2 / "why is email off" | `.codex/skills/homekept-config-and-flags.md` |
| Setting up the repo / a build or test command fails | `.codex/skills/homekept-build-and-env.md` |
| Running or deploying; topology, cookies, where artifacts land | `.codex/skills/homekept-run-and-operate.md` |
| Measuring instead of guessing (smoke test, invariant scan, CI logs) | `.codex/skills/homekept-diagnostics-and-tooling.md` |
| Writing/reviewing tests; deciding if a change is proven | `.codex/skills/homekept-validation-and-qa.md` |
| Something is broken and you need to triage fast | `.codex/skills/homekept-debugging-playbook.md` |
| A symptom feels familiar; before re-fixing a settled bug | `.codex/skills/homekept-failure-archaeology.md` |
| Writing docs, a PR body, a commit message, or product copy | `.codex/skills/homekept-docs-and-writing.md` |
| Writing marketing/claims; what may be said publicly | `.codex/skills/homekept-external-positioning.md` |
| Driving launch readiness / the config-binding blockers | `.codex/skills/homekept-go-live-campaign.md` |
| Proving an invariant actually holds (don't assume) | `.codex/skills/homekept-proof-and-analysis-toolkit.md` |
| Proposing a new direction; the evidence bar for a result | `.codex/skills/homekept-research-and-methodology.md` |

Each skill ends with a "Provenance and maintenance" block of re-verify commands —
run them if a fact may have drifted.

## Non-negotiables (with the reason)

Full rationale + the historical incident behind each is in
`.codex/skills/homekept-change-control.md`. The short list:

1. **Hand-write boundary is founder-only.** SQL migrations, `application.yml`/
   `.properties`, auth/security core, and pricing numbers are written by the
   founder, never generated. If your task needs one that doesn't exist, **STOP
   and say so** — do not invent it.
2. **Money is integer cents.** Never floats, never `BigDecimal`-as-float.
3. **State changes go through the state machine classes** (`SubscriberStateMachine`,
   `VisitStateMachine`, `WalkthroughBookingStateMachine`): validate the transition,
   then write. No status write without a machine check.
4. **A domain calls another domain's *service*, never its repository or entities.**
   Packages are domain-first (`com.homekept.<domain>`).
5. **Ownership failures return 404, not 403** (don't leak existence). 403 = wrong
   role.
6. **Text on the amber accent (`#d29a44`) is the dark ink (`#11201a`), never
   white** (WCAG). Colour tokens live once in `frontend/src/styles/theme.css`;
   style through the semantic tokens, never a hardcoded brand hex.
7. **No fabricated social proof or data** in customer-facing copy (Competition
   Act). Prefer an honest empty state to a fake number.
8. **No em dashes** in customer-facing copy (emails/UI/marketing). Periods,
   commas, colons. Code comments exempt.
9. **Timestamps:** `TIMESTAMPTZ` in SQL, `Instant` in Java, stored UTC, rendered
   `America/Toronto` via the `TimeZoneConfig` bean — never a hardcoded zone.

## Workflow (mandatory)

Branch from `main` (kebab-case, descriptive) → implement → focused commits → open
a PR with What/Why → wait for CI green → review → fix blockers → squash-merge.

- **Never `git add -A` or `git add .`** — stage explicit paths only.
- **No AI attribution and no `Co-Authored-By` trailers** in commit messages.

**Review gates** (Claude Code runs these as a subagent crew; you have none, so a
human reviewer and/or a deliberate self-review pass must satisfy them, plus CI):
- **Spec review — every diff.** Check against the issue's acceptance criteria,
  `backend/api-contract.md`, `backend/homekept-backend-architecture.md`, and the
  non-negotiables above.
- **Adversarial security review — mandatory on load-bearing paths:** auth,
  Stripe/webhooks, tokens, payments, access-note encryption, and state machines
  (and issues #6, #14–16, #21–29, #34, #54, #55, #58, #60). Attack the diff with
  concrete exploit sketches before merge.
- **Copy review — whenever a customer-visible string changes.** Brand voice,
  legal rails, exact prices, no em dashes, no fabrication.

## Human-only — never automate

Merging (a person merges), the hand-write artifacts (rule 1), secrets/accounts/
external setup (Stripe, SendGrid, R2, PostHog, Render, Cloudflare), pricing
changes and any edit to `docs/pricing-and-visits.md` tier numbers, and eval/
benchmark runs that cost money. For a hand-write fix, **propose the exact diff to
the founder — do not edit the file yourself.**

## Commands

| What | Where | Command |
|---|---|---|
| Backend build + tests | `backend/` | `./gradlew build` (needs Docker for Testcontainers) |
| Backend compile only (local default) | `backend/` | `./gradlew compileJava compileTestJava` |
| Frontend dev | `frontend/` | `bun run dev` (port 8080) |
| Frontend build | `frontend/` | `bun run build` |
| Targeted lint | `frontend/` | `bunx eslint <files>` |
| Format files you touched | `frontend/` | `bunx prettier --write <files>` |

**Docker is CI-only in this project.** Locally, verify a backend change with
`./gradlew compileJava compileTestJava` and let the real Testcontainers suite run
in CI. Frontend lint is not repo-wide-enforced yet — enforce per-file in review.
See `.codex/skills/homekept-build-and-env.md`.

## Provenance and maintenance

Verified 2026-07-12. Re-verify:
- Non-negotiables text: `sed -n '/Non-negotiables/,/Commands/p' CLAUDE.md`
- Skill files present: `ls .codex/skills/*.md`
- Commands + toolchain: the "Commands" section of `CLAUDE.md`, `.github/workflows/ci.yml`
- Keep this file and `CLAUDE.md` consistent; the detailed skills carry their own
  re-verify commands.
