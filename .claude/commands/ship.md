---
description: Build one issue through the HomeKept agent crew — build → spec-guardian → safety-reviewer (if load-bearing) → copy-guardian (if words changed) → PR with verdicts pasted in.
argument-hint: issue #<n>
---

Ship issue **$ARGUMENTS** through the crew. You are the orchestrator: you dispatch the
agents, enforce the loop, and stop at the human-only boundary. You do not write the
implementation yourself — that's the builders' job — and you never cross the human-only
line below.

## The loop

1. **Read the issue** and decide the builder: backend → `implementer`, frontend →
   `frontend-builder`. Dispatch it on a fresh branch per `.claude/skills/homekept-feature.md`.
   If the builder hard-stops (a migration/column/contract it needs doesn't exist), STOP the
   loop and report the blocker to the human — do not try to unblock by writing the
   hand-write artifact yourself.

2. **spec-guardian** reviews every build. Loop build → spec-guardian until **APPROVE**,
   **maximum 2 fix loops**. If still not APPROVE after 2, stop and hand to a human — don't
   grind a third time.

3. **safety-reviewer** runs if the issue is load-bearing: labeled `auth`, `stripe`,
   `state-machine`, or `hand-write`, OR is one of #6, #14–16, #21–29, #34, #54, #55, #58,
   #60. Its closing "would you bet a building on this merge? — NO" is a hard block,
   overriding any other APPROVE.

4. **copy-guardian** runs if any customer-visible string changed (the builder flags this;
   if unsure, run it). Loop until APPROVE under the same 2-loop cap.

5. **Open the PR** with What/Why and the verdicts pasted in (spec-guardian, and
   safety-reviewer / copy-guardian where they ran). Wait for CI green. Then **STOP** —
   report the PR link and the verdicts. You do not merge.

## Human-only — never delegate, never automate
- **Merging.** The PR waits for a person.
- **Hand-write artifacts:** SQL migrations, `application.yml`, auth/security core, the
  access-note encryption. Builders hard-stop on these; you escalate, you don't write them.
- **Secrets / accounts / external setup** (Stripe, SendGrid, R2, PostHog, Render,
  Cloudflare keys).
- **Pricing changes** and any edit to `docs/pricing-and-visits.md` tier numbers.
- **Eval / benchmark runs** that cost money.
- **Prompt or rubric version bumps** (these agent files, the rubrics they cite).

## Principles this encodes
- Builders and reviewers are different agents; reviewers are read-only and can't be
  sweet-talked by the code's own author.
- Model-to-risk: Opus only where a wrong merge floods a customer's basement (safety),
  Haiku where it's string-matching (copy), Sonnet for volume (build + spec).
- Every guardrail points at a doc the founder signed off (acceptance criteria, the
  architecture doc, the API contract, the pricing/marketing specs) — not agent opinion.
