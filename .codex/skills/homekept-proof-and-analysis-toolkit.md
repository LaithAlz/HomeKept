# HomeKept proof and analysis toolkit

> **When to use this:** Open this when you need to demonstrate a property actually
> holds rather than assert it, when reviewing a load-bearing change, or when
> someone says "it works" without evidence — first-principles "prove it, don't
> assume it" recipes ("is this actually safe", verify invariant, demonstrate,
> measurement, worked example, IDOR test, contrast ratio, binding test) for
> turning a claim like "ownership is safe", "money is cents", "the transition is
> legal", "config binds", "contrast passes", or "the webhook works" into a
> repeatable measurement, each with a worked example from this repo's history.

A claim is not accepted here because it is plausible. It is accepted when there
is a repeatable measurement that would fail if the claim were false. This skill
is the set of such measurements, each with a real example. Verified 2026-07-06.

Use this with `.codex/skills/homekept-validation-and-qa.md` (what to test) and
`.codex/skills/homekept-diagnostics-and-tooling.md` (the instruments). This file
is the *epistemics*: how to design the measurement so a pass actually means
something.

## Recipe 1 — Prove ownership is 404, not a leak

This is an IDOR (insecure direct object reference) check: proving one tenant
cannot reach another tenant's objects by guessing or passing their id.

- **Claim:** "endpoint X cannot expose another tenant's data."
- **Measurement:** an integration test where user A, correctly authenticated,
  passes an id owned by user B. A pass is **404** (indistinguishable from
  not-found), not 403 (leaks existence), not 200 (leaks data), not 500.
- **Worked example:** portfolio Phase 1 (#132) resolves every `propertyId`
  through `resolveOwnedSubscriber`; the test probes a cross-user `propertyId` on
  both a read and a write and asserts 404. The security review traced *every*
  endpoint, not a sample — because one unproven endpoint is the whole breach.

## Recipe 2 — Prove money is integer cents end to end

- **Claim:** "this amount never loses precision."
- **Measurement:** the DTO field is an `int`/`long` of cents; assert the exact
  cents value in the test; render with `formatCentsCAD`. Grep the diff for
  `double|float|BigDecimal` near money names (see
  `.codex/skills/homekept-diagnostics-and-tooling.md` recipe 3).
- **Worked example:** the admin dashboard exposes `mrrCents` (not a float); the
  de-fabrication (#133) rendered it via `formatCentsCAD`, never a dollar float.

## Recipe 3 — Prove a state transition is legal (and the illegal one is refused)

- **Claim:** "this status change is allowed; that one is not."
- **Measurement:** a state-machine test that asserts BOTH directions — the legal
  transition succeeds and the illegal one throws
  (`IllegalVisitTransitionException` / `IllegalSubscriptionStateException`).
- **Worked example:** `SubscriberStateMachineTest`, `VisitStateMachineTest`,
  `WalkthroughBookingStateMachineTest`. A new edge is unproven until both the
  allow and the deny are asserted.

## Recipe 4 — Prove a config key actually binds

- **Claim:** "setting env var E configures behaviour B."
- **Measurement:** a binding test (`config/AppPropertiesBindingTest`) that sets
  the env/property and asserts the bound value, plus a boot check that the log
  no longer prints the "skip/degraded" warning.
- **Worked example / the cautionary tale:** #120/#121 shipped *because this proof
  was skipped*. `SENDGRID_API_KEY` was documented but never bound (no
  `app.sendgrid:` block), and no binding test asserted it, so email silently
  stayed off. The lesson: a documented env var is a claim; prove it binds.

## Recipe 5 — Prove WCAG contrast, do not eyeball it

- **Claim:** "this text is readable on this background."
- **Measurement:** compute the contrast ratio of the two token hexes; AA body
  text needs ≥ 4.5:1 (≥ 3:1 for large text / UI). Do the arithmetic; do not
  judge by eye.
- **Worked example:** the #122 reskin shipped three sub-AA combos, fixed by
  deepening the primary/success tokens until the ratios passed. The exact
  regressions, ratios, and hex values live once in
  `.codex/skills/homekept-failure-archaeology.md` #3. Text on the amber accent
  is always dark ink `#11201a`.

## Recipe 6 — Prove a 2xx is handled, empty body and all

- **Claim:** "the client handles this response."
- **Measurement:** exercise the client path against a 200/202 with an **empty
  body** and assert it routes to success, not the error branch.
- **Worked example:** the login-dead bug (255ffb0) — `res.json()` on an empty
  body threw and success became an error. Proof = a test/curl that returns empty
  2xx and confirms success handling.

## Recipe 7 — Prove a webhook handler with a fixture

- **Claim:** "this Stripe event produces this state change."
- **Measurement:** feed the handler a fixture payload (via
  `FakeStripeServiceConfig`) and assert the resulting subscriber transition, not
  merely a 200.
- **Worked example:** `StripeWebhookIntegrationTest`.

## The general shape

For any invariant: **state the claim → design a measurement that fails if the
claim is false → run it → a pass means something only because the failure mode
was real.** If you cannot describe how the measurement would fail, you have not
proven anything.

## When NOT to use this / open a sibling instead

- The catalog of what must be tested →
  `.codex/skills/homekept-validation-and-qa.md`.
- The runnable instruments (scripts, curl, CI logs) →
  `.codex/skills/homekept-diagnostics-and-tooling.md`.
- Turning a proven result into an accepted project change →
  `.codex/skills/homekept-research-and-methodology.md`.
- The specific past incidents referenced →
  `.codex/skills/homekept-failure-archaeology.md`.

## Provenance and maintenance

Verified 2026-07-06 against the tests and commits cited. Re-verify with the
Provenance commands in `.codex/skills/homekept-validation-and-qa.md` and
`.codex/skills/homekept-failure-archaeology.md`. Add a recipe whenever a new
class of invariant becomes load-bearing.
