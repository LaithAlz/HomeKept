# HomeKept build and environment

> **When to use this:** Open this when setting up the repo, when a build or test
> command fails, when unsure how to verify a change locally, or when confused about the
> Docker/Testcontainers requirement — triggers include "how do I build", "gradlew",
> "bun install", "compile only", "tests won't run", "Docker required",
> "Testcontainers", "route tree", "frozen lockfile", setup, environment, toolchain, and
> versions.

Two independent builds: a Spring Boot backend (`backend/`) and a TanStack Start
frontend (`frontend/`). Verified against the repo on 2026-07-06.

## Toolchain (exact versions)

| Tool | Version | Where pinned |
|---|---|---|
| Java | **17** | `backend/build.gradle` (`JavaLanguageVersion.of(17)`), CI `setup-java` temurin 17 |
| Spring Boot | **4.1.0** | `backend/build.gradle` |
| Gradle | wrapper | `backend/gradlew` (use the wrapper, never a system gradle) |
| bun | **1.3.13** | CI `setup-bun`; match it locally |
| TypeScript | 5.8.x | `frontend/package.json` |
| Vite | 7.x | `frontend/package.json` |

Backend persistence is Postgres; tests spin it up with **Testcontainers**, which
needs a Docker daemon.

## Backend — commands (run from `backend/`)

| Goal | Command | Needs Docker? |
|---|---|---|
| **Compile only (local default)** | `./gradlew compileJava compileTestJava` | No |
| Full build + all tests | `./gradlew build` | **Yes** (Testcontainers) |
| Just tests | `./gradlew test` | **Yes** |
| A single test class | `./gradlew test --tests 'com.homekept.subscription.SubscriberStateMachineTest'` | Yes |

**The Docker trap.** Docker is **CI-only** in this project — the founder's Mac
does not run it locally. So the standard local verification for a backend change
is **compile-only**: `./gradlew compileJava compileTestJava`. The real test suite
runs in CI (GitHub Actions `backend` job runs `./gradlew build --console=plain`
against the runner's Docker daemon). Do not claim tests pass locally unless you
actually have Docker up; say "compiles clean locally, tests run in CI" instead.

## Frontend — commands (run from `frontend/`)

| Goal | Command | Notes |
|---|---|---|
| Dev server | `bun run dev` | Port **8080** (per CLAUDE.md; the vite config sets it) |
| Production build | `bun run build` | This is what CI gates on; runs the TS compile |
| Type-check only | `bunx tsc --noEmit` | Fast; see the known-baseline note below |
| Lint specific files | `bunx eslint <files>` | Lint is **not** repo-wide-enforced (see below) |
| Format files you touched | `bunx prettier --write <files>` | Only format files you changed |
| Install (reproducible) | `bun install --frozen-lockfile` | CI uses `--frozen-lockfile`; match it |

**Known baseline:** as of 2026-07-06 there is one pre-existing, unrelated
`tsc --noEmit` error in `frontend/src/routes/learn.$slug.tsx:199` that exists on
`main`. Do not chase it as if your change caused it; confirm with a clean
checkout if unsure.

## Traps that bite newcomers

- **Route tree is generated.** TanStack Router generates a route tree from the
  files in `frontend/src/routes/`. `bun run dev`/`build` regenerate it; do not
  hand-edit the generated tree. Add a route by adding a route file.
- **Lint is not a repo-wide gate.** The originally Lovable-generated code
  predates the prettier config, so CI does not run lint. Enforce per-file in
  review: `bunx eslint <changed files>` and `bunx prettier --write <changed
  files>`. Do not run a repo-wide format — it would rewrite unrelated files.
- **`--frozen-lockfile`** means a stale `bun.lock` fails CI. If you add a dep,
  commit the lockfile change (frontend deps are not a hand-write boundary; app
  config is).
- **Use the Gradle wrapper** (`./gradlew`), not a globally installed gradle.
- **Backend config is a hand-write boundary.** `application.yml` and
  `.env.example` are founder-owned. If a build needs a new config key that does
  not exist, STOP (see `.codex/skills/homekept-change-control.md`), do not add
  it.

## Repo layout

```
backend/    Spring Boot service (domain-first com.homekept.*), Flyway migrations,
            api-contract.md, homekept-backend-architecture.md, .env.example
frontend/   TanStack Start app (src/routes, src/lib, src/components, src/styles)
docs/       Commercial + planning docs of record (pricing, three-year-plan, ...)
.claude/    Agents, the /ship command, skills (the Claude Code skill library),
            homekept-feature.md
.codex/     Codex-native skills (this library), ported from .claude/skills/;
            AGENTS.md at the repo root is the entry point that routes to these
```

## When NOT to use this skill / use a sibling instead

- Deploying / running against a live stack → `.codex/skills/homekept-run-and-operate.md`.
- The config keys and env vars themselves → `.codex/skills/homekept-config-and-flags.md`.
- What to test and what counts as evidence → `.codex/skills/homekept-validation-and-qa.md`.
- Diagnosing a specific failing build/runtime symptom →
  `.codex/skills/homekept-debugging-playbook.md`.

## Provenance and maintenance

Versions verified 2026-07-06. Re-verify:

- Java/Spring/deps: `grep -nE "springframework.boot|JavaLanguageVersion|testcontainers|flyway" backend/build.gradle`
- CI toolchain + steps: `cat .github/workflows/ci.yml`
- Frontend scripts + bun version: `frontend/package.json`, `.github/workflows/ci.yml`
- Command table: the "Commands" section of `CLAUDE.md`
