#!/usr/bin/env bash
# Heuristic scan for HomeKept non-negotiable violations. These are SMELL checks:
# they surface candidates for a human to review, NOT definitive verdicts. A hit
# is a "look here", not a "this is broken". Run from anywhere in the repo.
#
# Covers (see homekept-change-control for the rules):
#   - direct status writes outside the state machines
#   - hardcoded brand hex in components (should use theme.css tokens)
#   - float money types near money-shaped names (money is integer cents)
#   - em dashes in frontend copy (none in customer-facing copy)
set -uo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

echo "== 1. Direct status writes outside *StateMachine classes (expect none) =="
grep -rnE "setStatus\(|\.status[[:space:]]*=" backend/src/main/java --include=*.java 2>/dev/null \
  | grep -viE "StateMachine" || echo "  (none found)"

echo
echo "== 2. Hardcoded brand hex in frontend (use semantic tokens from theme.css) =="
grep -rniE "#0d4132|#d29a44|#11201a|#0a6b44|#edf1ec" frontend/src --include=*.tsx --include=*.ts 2>/dev/null \
  | grep -v "styles/theme.css" || echo "  (none found)"

echo
echo "== 3. Float money types near money-shaped names (money must be integer cents) =="
grep -rnE "\b(double|float)\b|BigDecimal" backend/src/main/java --include=*.java 2>/dev/null \
  | grep -iE "price|amount|cost|money|cents|mrr|total|revenue" || echo "  (none found)"

echo
echo "== 4. Em dashes in frontend copy (none in customer-facing copy) =="
grep -rn "—" frontend/src --include=*.tsx 2>/dev/null || echo "  (none found)"

echo
echo "Done. Every hit is a candidate, not a verdict. Check it against homekept-change-control."
