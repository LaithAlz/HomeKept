# HomeKept — agent guide

Subscription home maintenance (GTA West). Spring Boot backend + TanStack Start frontend
on Cloudflare. Two founders; the CEO writes software and runs visits.

## Read before building anything

In order, as relevant to the task:

1. `docs/pricing-and-visits.md` — the commercial spec: tiers, the 12-month visit
   calendar, picks, materials, legal scope rails. **Business rules live here.**
2. `backend/homekept-backend-architecture.md` — domains, entities, state machines,
   integrations, staged roadmap (Part 10 Stage 1 = current scope).
3. `backend/api-contract.md` — the frontend/backend seam. Renaming or removing an
   endpoint requires updating this file in the same PR.
4. `docs/three-year-plan.md` — phase gates; explains *when* things get built. Don't
   build later-phase items early.
5. The GitHub issue you're implementing. Issues labeled `v1-expansion` extend the
   original #1–#45 plan.

## Workflow (mandatory)

Follow `.claude/skills/homekept-feature.md` for every change: branch from main
(kebab-case, descriptive) → implement → focused commits (no AI attribution, no
Co-Authored-By) → PR with What/Why → wait for CI → subagent review → fix blockers →
squash-merge. Never `git add -A` or `git add .`.

## Non-negotiables

- **Hand-write boundary:** anything labeled `hand-write` (SQL migrations, application
  config (`application.properties`/`.yml`), auth/security core) is written by the
  founder, never generated.
  If your task needs a migration that doesn't exist, stop and say so.
- **Money is integer cents.** Never floats, never BigDecimal-as-float.
- **Timestamps:** `TIMESTAMPTZ` in SQL, `Instant` in Java, UTC stored,
  `America/Toronto` rendered (via the TimeZoneConfig bean, never hardcoded).
- **State changes go through the state machine classes** (subscriber, visit,
  walk-through booking). No direct status writes, anywhere, ever.
- **Domain boundaries:** a domain may call another domain's service, never its
  repository or entities. Domain-first packages (`com.homekept.<domain>`).
- **Ownership failures return 404, not 403** (don't leak existence). 403 = wrong role.
- **Text on honey (#DE8F3F) surfaces is pine (#1E3A2B), never white** (WCAG).
- **No fabricated social proof** in any customer-facing copy (Competition Act).
- Frontend uses the v2 design system tokens in `frontend/src/styles.css`; reference
  mockups in `mockups/v2/`. Match existing conventions; format only files you touch
  (`bunx prettier --write <files>` — repo-wide lint is not clean yet).

## Commands

| What | Where | Command |
|---|---|---|
| Backend build + tests | `backend/` | `./gradlew build` (needs Docker for Testcontainers) |
| Backend compile only | `backend/` | `./gradlew compileJava compileTestJava` |
| Frontend dev | `frontend/` | `bun run dev` (port 8080) |
| Frontend build | `frontend/` | `bun run build` |
| Targeted lint | `frontend/` | `bunx eslint <files>` |

## Testing expectations (arch doc Part 8)

Always test: every state machine transition (legal + illegal), every Stripe webhook
handler (fixture payloads), every mutating service method. Integration tests run against
real Postgres via Testcontainers — never mock the database. Don't test generated
repositories or trivial getters.
