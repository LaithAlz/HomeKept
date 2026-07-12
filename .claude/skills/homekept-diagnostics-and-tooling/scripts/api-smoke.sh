#!/usr/bin/env bash
# HomeKept API smoke test: log in, then call an authenticated endpoint using the
# returned httpOnly cookie. Proves auth + one endpoint end to end, and is the
# fastest way to tell a client bug from a server bug.
#
# Usage:
#   BASE_URL=http://localhost:8080 EMAIL=<admin-email> PASSWORD=<admin-password> \
#     ./api-smoke.sh [/api/app/account]
#
# Notes:
#   - The login JSON below assumes the LoginRequest DTO is {email, password}.
#     If the DTO differs, adjust the -d payload.
#   - Auth cookie is hk_access (httpOnly). A 2xx login returns an EMPTY body by
#     design, so we check for the Set-Cookie, not a JSON body.
#   - Requires: curl.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:?set EMAIL=...}"
PASSWORD="${PASSWORD:?set PASSWORD=...}"
ENDPOINT="${1:-/api/app/account}"

JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

echo "==> POST $BASE_URL/api/auth/login"
code=$(curl -s -o /dev/null -w '%{http_code}' -c "$JAR" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/api/auth/login")
echo "    login HTTP $code"
if [ "$code" -ge 400 ]; then echo "    login FAILED"; exit 1; fi
if ! grep -q 'hk_access' "$JAR"; then
  echo "    no hk_access cookie set -> cookie/domain problem, not a parse problem"
  exit 1
fi
echo "    hk_access cookie set OK"

echo "==> GET $BASE_URL$ENDPOINT (with cookie)"
curl -s -i -b "$JAR" "$BASE_URL$ENDPOINT" | head -20
echo
echo "Done. 200 + JSON body => healthy. 401 => cookie not accepted. 404 => ownership scoping."
