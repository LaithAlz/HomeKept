# HomeKept

Subscription home maintenance for the GTA (Oakville · Mississauga · Milton).

| Directory | Contents |
|---|---|
| `backend/` | Spring Boot API (Java 17, Postgres, Flyway). Architecture: [`backend/homekept-backend-architecture.md`](backend/homekept-backend-architecture.md) · API: [`backend/api-contract.md`](backend/api-contract.md) |
| `frontend/` | TanStack Start + React app, deployed to Cloudflare Workers |
| `mockups/` | Static HTML design mockups — `mockups/v2/` is the current design system |
| `ops/` | Deployment and operations notes |

Build plan: the GitHub issues, labeled by week (`week-1` … `week-8`).
Workflow: see `.claude/skills/homekept-feature.md` — branch → PR → CI → review → squash-merge.
Anything labeled `hand-write` (migrations, auth, `application.yml`) is written by hand, not generated.
