---
name: homekept-feature
description: |
  Full feature development workflow for the HomeKept project. Use this skill whenever
  you are working on a feature, fix, or any code change in this repo — whether from
  a GitHub issue, a user request, or a ticket. This skill covers everything from
  branching through implementation, CI, code review, and merge. Do not skip any stage.
---

# HomeKept Feature Workflow

This is the standard development workflow for all code changes in this repo. Follow
every stage in order. Do not squash stages together.

---

## Stage 1 — Branch

Create a branch from `main`. The branch name should be lowercase, kebab-case, and
descriptive enough that someone reading `git branch` immediately understands the work.

```bash
git checkout main && git pull origin main
git checkout -b <branch-name>
```

Good branch names: `v1-users-migration`, `walkthrough-booking-controller`,
`stripe-webhook-idempotency`, `fix-jwt-cookie-domain`.

Bad branch names: `feature`, `fix`, `update`, `my-branch`.

If you're working from a GitHub issue, the branch name should reflect the issue's
subject, not its number.

---

## Stage 2 — Implement

Build the feature. Follow the existing code conventions in the repo. Read adjacent
files before writing new ones so your code fits in naturally.

A few non-negotiable rules for this project:
- Backend package structure is domain-first (e.g., `com.homekept.booking`, `com.homekept.auth`)
- Money is always integers (cents), never decimals
- All timestamps are `TIMESTAMPTZ` in SQL; `Instant` in Java
- Hand-write anything marked `hand-write` in the issue — do not AI-generate SQL migrations or security-critical code

---

## Stage 3 — Commit

Commit regularly as you work. Keep commits focused — one logical change per commit.

**Commit message rules (strict):**
- Short imperative subject line, max 72 chars
- No "Co-Authored-By" lines
- No "Generated with Claude Code" or any AI attribution
- No references to the AI, the session, or the tool
- Write as a human engineer would: `Add V1 users migration`, `Wire booking controller to SendGrid`, `Fix JWT cookie SameSite attribute`

```bash
git add <specific files>
git commit -m "Your short message here"
```

Never use `git add -A` or `git add .` — add files explicitly to avoid committing
secrets or build artifacts.

---

## Stage 4 — Push and open PR

Push the branch and open a PR against `main`.

```bash
git push -u origin <branch-name>
gh pr create --base main --title "<title>" --body "<body>"
```

**PR title:** same style as a commit message — short, imperative, specific.

**PR body template:**
```
## What
- <bullet: what changed>
- <bullet: what changed>

## Why
- <bullet: motivation or requirement>
```

Keep it factual. No fluff, no "This PR introduces...", no AI-flavored phrasing.

---

## Stage 5 — CI

Check whether any GitHub Actions workflows exist in this repo:

```bash
gh workflow list
```

If workflows exist, wait for all of them to pass on your PR. Poll every 30 seconds:

```bash
gh run list --branch <branch-name> --limit 10
```

If a run fails, read the logs, fix the issue, push a new commit, and wait again.
Do not proceed to review or merge until all runs show `completed / success`.

If no workflows exist, skip this stage.

---

## Stage 6 — Review

Dispatch a subagent to review the PR. Give it this prompt:

```
Review the PR at <pr-url> in the LaithAlz/HomeKept repo.

Fetch the diff with: gh pr diff <pr-number> --repo LaithAlz/HomeKept

Give a structured review covering:
1. Correctness — does the implementation match the spec/issue? any logic bugs?
2. Security — any auth bypasses, injection risks, secrets in code, insecure defaults?
3. Style — does it follow the conventions in the surrounding code?

For each section, list findings as: [BLOCKER], [SUGGESTION], or [PRAISE].
A BLOCKER means the PR must not merge until it is fixed.
End with a one-line verdict: APPROVE or REQUEST CHANGES.
```

If the subagent returns REQUEST CHANGES with blockers:
1. Fix each blocker
2. Commit the fixes (same commit style rules apply)
3. Push and return to Stage 5 (re-run CI)
4. Re-run this review stage

If the verdict is APPROVE (or only suggestions/praise remain), proceed.

---

## Stage 7 — Merge

```bash
gh pr merge <pr-number> --repo LaithAlz/HomeKept --squash --delete-branch
```

Confirm the PR is closed and the branch is deleted. Done.
