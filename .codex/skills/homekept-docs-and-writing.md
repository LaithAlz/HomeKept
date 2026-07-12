# HomeKept docs and writing

> **When to use this:** Open this when writing or updating docs, a PR description,
> user-facing copy, or a commit message, or when unsure which doc owns a fact —
> covers which doc is authoritative for what ("update the docs", "which doc"), the
> api-contract discipline, the design-token source of truth, and the house copy
> rules (plain language, no em dashes, no fabrication, amber-on-dark-ink, "copy
> style", "house style", tone, design tokens, README).

Docs are load-bearing here: reviewers check diffs against them. Keep them true
and keep one home per fact. Verified 2026-07-06.

## Documents of record (who owns which fact)

| Doc | Authoritative for |
|---|---|
| `docs/pricing-and-visits.md` | Tiers, prices, the 12-month visit calendar, picks, materials, legal scope rails. **Founder-owned; pricing is hand-write.** |
| `backend/homekept-backend-architecture.md` | Domains, entities, state machines, integrations, the staged roadmap (Part 10 Stage 1 = current) |
| `backend/api-contract.md` | The frontend/backend seam: every endpoint's shape |
| `docs/three-year-plan.md` | Phase gates — *when* things get built |
| `docs/architecture-and-decisions.md` | Cross-cutting engineering decisions and rationale |
| `docs/go-live-checklist.md` | The founder-only gap between code-done and live |
| `docs/portfolio-multi-property-proposal.md` | The landlord/PM portfolio proposal (awaiting sign-off) |
| `docs/marketing-plan.md`, `docs/marketing-videos.md` | Positioning, channels, claims discipline |
| `CLAUDE.md` | The agent guide + non-negotiables (doc of record for the Claude-Code harness; the Codex entry point is `AGENTS.md`) |
| `AGENTS.md` | The mandatory change procedure (workflow + human-only boundary) for a Codex-native agent |

**One home per fact.** If a fact lives in a doc of record, reference it — do not
copy it into another doc where it will drift. (These skill files follow the same
rule: each fact has one owner file; siblings cross-reference.)

## The api-contract discipline (hard rule)

`backend/api-contract.md` is the seam. **Renaming or removing an endpoint
requires updating `api-contract.md` in the same PR.** Adding an endpoint or a
field means documenting its exact shape there. The spec review gate (every
diff — see `.codex/skills/homekept-change-control.md`) checks diffs against it.
A frontend hook and the contract must agree (this is where the #17
register-vs-contract conflict came from — see
`.codex/skills/homekept-failure-archaeology.md`).

## Design tokens are a document of record too

Colour is defined once in `frontend/src/styles/theme.css` (the single source of
truth for every token, both themes). The Tailwind mapping, radii, and type live
in `frontend/src/styles.css`. **Style through the semantic tokens** (`bg-primary`,
`text-muted-foreground`, `bg-accent`, ...), never a hardcoded brand hex in a
component. To restyle the app, change token values in `theme.css`.

## House copy style (customer- and admin-facing)

- **Plain and legible.** The audience includes non-technical homeowners,
  landlords, and property managers. Say what a control does; name things by what
  a person recognizes, not how the system is built.
- **No em dashes** in customer copy: use periods, commas, colons; code comments
  are exempt. (The rule and its rationale live once in
  `.codex/skills/homekept-change-control.md` non-negotiable #8; this is the
  pointer.)
- **No fabricated data or social proof.** Honest empty states over fake numbers.
  See `.codex/skills/homekept-external-positioning.md` for the legal reason.
- **Contrast:** text on the amber accent (`#d29a44`) is the dark ink (`#11201a`),
  never white (WCAG AA).
- **Trades-safe wording** in anything describing the service (see
  `.codex/skills/homekept-domain-reference.md`): "visual inspection /
  performance observation", "not a certified inspection", never implying
  licensed-trade work.

## Templates

**PR description:** What (one paragraph) / Why (one paragraph) / Scope (what is
and is not touched) / Reviews (note the spec review outcome, and the safety/copy
review outcomes where they ran). Wait for CI green, then stop for a human to
merge.

**Commit message:** imperative subject, focused body explaining the *why*. **No
AI attribution, no `Co-Authored-By` trailers.** Stage explicit paths, never
`git add -A`/`.`.

**A new doc:** put it in `docs/`, add a one-line pointer from wherever it is
referenced, and state at the top what it is authoritative for.

## When NOT to use this / open a sibling instead

- Public marketing claims and legal advertising rails →
  `.codex/skills/homekept-external-positioning.md`.
- The business facts the docs describe →
  `.codex/skills/homekept-domain-reference.md`.
- The review gates that enforce doc-accuracy →
  `.codex/skills/homekept-change-control.md`.
- Writing/maintaining these skill files themselves → follow this file's style,
  and the meta-notes in `.codex/skills/homekept-research-and-methodology.md`
  (the "Meta — maintaining this skill library" section).

## Provenance and maintenance

Verified 2026-07-06. Re-verify:

- Docs present: `ls docs/ backend/*.md CLAUDE.md`
- Token source of truth: `frontend/src/styles/theme.css` header comment
- api-contract rule: the "Read before building" section of `CLAUDE.md`
