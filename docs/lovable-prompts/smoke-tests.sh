#!/usr/bin/env bash
# Smoke tests for the osrsge sync backend built by Lovable prompt 1.
# Usage: BASE_URL=https://xxx.supabase.co/functions/v1 API_KEY=yyy ./smoke-tests.sh
set -u

: "${BASE_URL:?set BASE_URL to the functions base URL}"
: "${API_KEY:?set API_KEY to the PLUGIN_API_KEY value}"

pass=0
fail=0

check() { # name expected_status actual_status body
  if [ "$2" = "$3" ]; then
    echo "PASS  $1 ($3)"
    pass=$((pass+1))
  else
    echo "FAIL  $1 (expected $2, got $3) body: $4"
    fail=$((fail+1))
  fi
}

trade='{"playerName":"Smoke Test","trades":[{"itemId":444,"itemName":"Gold ore","buyPrice":145,"sellPrice":142,"quantity":1,"profit":-5,"tax":2,"buyTimestamp":1000,"sellTimestamp":2000}]}'
offers='{"playerName":"Smoke Test","offers":[{"slot":0,"itemId":444,"itemName":"Gold ore","price":145,"totalQuantity":10,"quantityFilled":4,"amountSpent":580,"state":"BUYING","isBuy":true,"timestamp":1000}]}'

# 1. bad key -> 401
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/trades-sync" \
  -H "Authorization: Bearer wrong-key" -H "Content-Type: application/json" -d "$trade")
check "trades-sync rejects bad key" 401 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

# 2. valid trade insert -> 200
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/trades-sync" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" -d "$trade")
check "trades-sync accepts trade" 200 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

# 3. duplicate resend -> 200, no duplicate row (idempotency: server-side upsert)
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/trades-sync" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" -d "$trade")
check "trades-sync idempotent resend" 200 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

# 4. malformed body -> 400
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/trades-sync" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" -d '{"nope":true}')
check "trades-sync rejects malformed body" 400 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

# 5. offers replace-all -> 200
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/offers-sync" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" -d "$offers")
check "offers-sync accepts snapshot" 200 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

# 6. offers empty array clears -> 200
body=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/offers-sync" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" \
  -d '{"playerName":"Smoke Test","offers":[]}')
check "offers-sync clears with empty array" 200 "$(tail -1 <<<"$body")" "$(head -1 <<<"$body")"

echo
echo "$pass passed, $fail failed"
echo "NOTE: verify in the dashboard/table that 'Smoke Test' has exactly ONE trade row (idempotency),"
echo "then delete the Smoke Test rows."
exit $((fail > 0))
