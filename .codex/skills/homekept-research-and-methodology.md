# HomeKept research and methodology

> **When to use this:** Open this when proposing a genuinely new direction ("new
> idea", "could we", frontier, data moat), when evaluating whether an idea is
> worth building ("is this worth it"), or when running an experiment and deciding
> if it earned adoption (hypothesis, ops efficiency, Home Health Score, research,
> methodology, advance the product) — also the meta-guide for maintaining this
> skill library.

Two halves: the **frontiers** worth pursuing, and the **method** that decides
whether a pursuit becomes a real change. Nothing here is a promise — candidate
directions are labeled as such. Verified 2026-07-06.

This is the most speculative skill in the library. It never routes around
change control: any idea that becomes code goes through the review gates
(`.codex/skills/homekept-change-control.md` and `AGENTS.md`). "Beyond state of
the art" here does not mean novel computer science; it means a durable
advantage in a home-maintenance business.

## Part A — Frontiers (candidate, not committed)

### Frontier 1 — The Home Health Score as a defensible data asset [primary]
- **Why the obvious approach falls short:** the competition is a one-off handyman
  call, which generates no longitudinal record of a home. There is no incumbent
  with a per-home condition history for GTA-West housing stock.
- **HomeKept's specific asset:** the visit domain already snapshots a Home Health
  Score (migration `V9__health_score_snapshot.sql`) computed from real
  visit/flag data, per home, over time. Every visit compounds it.
- **First three steps in this repo:** (1) verify the rubric is stable and
  explainable (`visit/HealthScoreIntegrationTest`); (2) instrument what actually
  moves the score across real visits once data exists; (3) prototype a
  home-to-home benchmark ("your home vs similar homes") as an *internal* metric
  before any customer-facing claim.
- **Falsifiable milestone:** you have a result when the score, computed only from
  visit N's data, predicts an independently-flagged issue at visit N+1 better
  than a baseline that ignores history. Until then it is a display, not an asset.

### Frontier 2 — Margin per visit (ops efficiency)
- **Why the obvious approach falls short:** the unit economics are fixed by labor
  (`$43/hr`, ~2h/visit) and capacity (~80 visits/mo/tech ≈ 120 Complete subs ≈
  ~$18K MRR). Growth without efficiency just adds trucks.
- **HomeKept's specific asset:** structured visits, known SKU sheets per home,
  and (soon) technician/day scheduling data (`useAdminVisits`, the routes view).
- **First three steps:** (1) measure real per-visit minutes and drive time from
  the visit + technician data; (2) model route density (visits/day by geography)
  against the ~80/mo ceiling; (3) test whether clustering subscribers by FSA
  raises visits/day without lowering quality.
- **Falsifiable milestone:** a routing change that provably raises visits/day for
  a real week without increasing incomplete visits.

### Frontier 3 — Trust/transparency UX for non-technical owners
- **Why the obvious approach falls short:** invisible services lose trust; owners
  cannot see what maintenance happened.
- **HomeKept's specific asset:** the same-day photo report + honest scope rails +
  the de-fabricated, plain-language UI already shipped.
- **First three steps:** (1) once R2/photos are live (#58), measure whether the
  photo report reduces support questions; (2) test comprehension of the Health
  Score with non-technical users; (3) instrument activation→first-visit
  conversion for landlords/PMs specifically.
- **Falsifiable milestone:** a measured lift in retention or referral for cohorts
  that engage with the report vs those that do not.

## Part B — Methodology (how a hunch becomes an accepted result)

1. **Hypothesis predicts numbers before you run.** State what you expect to see
   and the threshold that would count as success, *before* the experiment. A
   result that only makes sense in hindsight is not evidence.
2. **One mechanism must explain ALL observations, including the negatives.** If
   your explanation accounts for the wins but ignores a case where it did not
   work, it is incomplete. Chase the negative.
3. **Survive adversarial refutation.** The project already runs this culture: an
   adversarial security review tries to break a load-bearing change before it
   merges (mandatory on load-bearing paths — see
   `.codex/skills/homekept-change-control.md`). Apply the same discipline to an
   idea — assign someone (or an agent, in a fresh, independent pass) to refute
   it. If it survives a genuine attempt to kill it, it is stronger.
4. **The idea lifecycle.** experiment (behind a flag / off the main path) →
   measured against the pre-stated threshold → **adopted** (goes through the
   change-control gates into the product, see
   `.codex/skills/homekept-change-control.md`) OR **retired** (documented in
   `.codex/skills/homekept-failure-archaeology.md` so it is not re-tried
   blindly). No idea stays in limbo.
5. **Where good ideas have historically come from here:** auditing what was
   assumed (the config-binding audit found #120/#121), refusing to fabricate (the
   de-fabrication wave surfaced the real price bug), and designing the ownership
   choke point before writing the feature (#132). Pattern: look hard at the
   boring assumption; the frontier is often a thing everyone assumed was fine.

## Meta — maintaining this skill library

This library lives as one plain-Markdown file per skill under
`.codex/skills/<name>.md` — there is no YAML frontmatter and no Claude-Code
auto-loader. Each file:

- Opens with a `# <Human Title>` header, then a `> **When to use this:** ...`
  line that carries the trigger words a router would match on (what used to be
  the YAML `description`).
- The router itself is a single entry per skill in `AGENTS.md` at the repo
  root, pointing at the file. There is no separate "Skill tool" — an agent (or
  a person) reads `AGENTS.md` first, follows the pointer, and opens the
  `.codex/skills/<name>.md` file directly.
- Keeps imperative runbook voice, ground-truth-only content, a "When NOT to use
  this / open a sibling instead" section (sibling files are referenced as
  `.codex/skills/homekept-<x>.md`), and a "Provenance and maintenance" section
  with re-verify commands — same shape as before, just without frontmatter.
- **One home per fact**; siblings cross-reference, they do not duplicate.
- **Date-stamp volatile facts** (versions, counts, open issues) and re-verify with
  the provenance commands when they may have drifted.
- **No oversell:** unproven things stay labeled candidate/open. Nothing may
  contradict `CLAUDE.md` or route around the change-control gates
  (`.codex/skills/homekept-change-control.md`, and the workflow + human-only
  boundary in `AGENTS.md`).
- When the repo changes a load-bearing fact, update the owning skill file and
  its provenance block in the same spirit as the api-contract discipline.

## When NOT to use this / open a sibling instead

- Proving a specific invariant holds →
  `.codex/skills/homekept-proof-and-analysis-toolkit.md`.
- What may be *claimed publicly* about a frontier →
  `.codex/skills/homekept-external-positioning.md`.
- The business facts a frontier builds on →
  `.codex/skills/homekept-domain-reference.md`.
- Getting an adopted idea shipped → `.codex/skills/homekept-change-control.md`
  + `AGENTS.md`.

## Provenance and maintenance

Frontiers are candidate directions as of 2026-07-06, not commitments. Re-verify
the assets they rest on:

- Health Score: `visit/HealthScoreIntegrationTest`, `V9__health_score_snapshot.sql`
- Unit economics: `docs/pricing-and-visits.md` "Unit economics"
- Scheduling data: the admin visits/technicians endpoints and the routes view
- Revisit this skill whenever a frontier is adopted or retired; move retired ones
  to `.codex/skills/homekept-failure-archaeology.md`.
