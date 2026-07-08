# osrsge.io Sync + Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plugin syncs flips to Lovable Cloud endpoints; user's osrsge.io dashboard shows every flip across all Jagex accounts.

**Architecture:** Plugin gains a configurable sync base URL and posts the existing trade/offer payloads to `<base>/trades-sync` and `<base>/offers-sync`. The server side is built by Lovable from two self-contained prompts stored in this repo. A curl smoke-test script verifies the deployed backend before the plugin flips sync on.

**Tech Stack:** Java 11 / RuneLite / Gson / JUnit 4 (plugin); Lovable Cloud (Supabase Postgres + edge functions, built via prompts); bash + curl (smoke tests).

**Spec:** `docs/superpowers/specs/2026-07-08-osrsge-sync-dashboard-design.md`

---

### Task 1: JSON payload contract test

**Files:**
- Test: `src/test/java/com/osrsge/plugin/api/PayloadContractTest.java`

Locks the wire format. `CompletedTrade`/`TradeOffer` serialize via Gson field names; this test fails if anyone renames a field the server depends on.

- [ ] **Step 1: Write the failing test**

```java
package com.osrsge.plugin.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.TradeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pins the JSON field names the osrsge.io backend contract depends on. */
public class PayloadContractTest
{
    private final Gson gson = new Gson();

    @Test
    public void tradeSerializesContractFields()
    {
        CompletedTrade trade = CompletedTrade.builder()
            .itemId(444)
            .itemName("Gold ore")
            .buyPrice(145)
            .sellPrice(142)
            .quantity(1)
            .profit(-5)
            .tax(2)
            .buyTimestamp(1783265377587L)
            .sellTimestamp(1783265397987L)
            .synced(false)
            .build();

        JsonObject json = gson.toJsonTree(trade).getAsJsonObject();

        assertEquals(444, json.get("itemId").getAsInt());
        assertEquals("Gold ore", json.get("itemName").getAsString());
        assertEquals(145, json.get("buyPrice").getAsInt());
        assertEquals(142, json.get("sellPrice").getAsInt());
        assertEquals(1, json.get("quantity").getAsInt());
        assertEquals(-5, json.get("profit").getAsLong());
        assertEquals(2, json.get("tax").getAsLong());
        assertEquals(1783265377587L, json.get("buyTimestamp").getAsLong());
        assertEquals(1783265397987L, json.get("sellTimestamp").getAsLong());
    }

    @Test
    public void offerSerializesContractFields()
    {
        TradeOffer offer = TradeOffer.builder()
            .slot(2)
            .itemId(444)
            .itemName("Gold ore")
            .price(145)
            .totalQuantity(100)
            .quantityFilled(40)
            .amountSpent(5800)
            .state(GrandExchangeOfferState.BUYING)
            .isBuy(true)
            .timestamp(1783265377587L)
            .build();

        JsonObject json = gson.toJsonTree(offer).getAsJsonObject();

        assertEquals(2, json.get("slot").getAsInt());
        assertEquals(444, json.get("itemId").getAsInt());
        assertEquals("Gold ore", json.get("itemName").getAsString());
        assertEquals(145, json.get("price").getAsInt());
        assertEquals(100, json.get("totalQuantity").getAsInt());
        assertEquals(40, json.get("quantityFilled").getAsInt());
        assertEquals(5800, json.get("amountSpent").getAsLong());
        assertEquals("BUYING", json.get("state").getAsString());
        assertTrue(json.get("isBuy").getAsBoolean());
        assertEquals(1783265377587L, json.get("timestamp").getAsLong());
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew test --tests "com.osrsge.plugin.api.PayloadContractTest" --console=plain`
Expected: PASS immediately (fields already exist) — EXCEPT possibly `isBuy`: Lombok `@Data` on a `boolean isBuy` field generates getter `isBuy()` but Gson serializes the FIELD name `isBuy`, so it should pass. If it fails on `isBuy`, the field serialized as `buy` — then fix the assertion to match reality AND update the Lovable prompt (Task 3) to use the actual name. The point of this test is agreement, not a specific spelling.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/osrsge/plugin/api/PayloadContractTest.java
git commit -m "test: pin sync payload JSON contract"
```

---

### Task 2: Configurable sync base URL

**Files:**
- Modify: `src/main/java/com/osrsge/plugin/GETrackerConfig.java`
- Modify: `src/main/java/com/osrsge/plugin/api/OsrsGeApiClient.java`
- Modify: `src/main/java/com/osrsge/plugin/GETrackerPlugin.java`

- [ ] **Step 1: Config item**

In `GETrackerConfig.java`, add inside the sync section (position 1; bump the existing `syncEnabled` to position 2 and `syncIntervalSeconds` to position 3):

```java
    @ConfigItem(
        keyName = "syncBaseUrl",
        name = "Sync URL",
        description = "Base URL of your osrsge.io sync backend (Lovable Cloud functions URL, e.g. https://abc123.supabase.co/functions/v1). Leave empty to disable sync.",
        section = syncSection,
        position = 1
    )
    default String syncBaseUrl()
    {
        return "";
    }
```

- [ ] **Step 2: OsrsGeApiClient — instance base URL**

In `OsrsGeApiClient.java`:

Replace:

```java
    private static final String BASE_URL = "https://osrsge.io/api";
```

with:

```java
    private String baseUrl = "";
```

Add setter next to `setApiKey`:

```java
    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }
```

In `syncTrades`, extend the early-out guard:

```java
            if (apiKey == null || apiKey.isEmpty() || baseUrl.isEmpty())
            {
                log.debug("Sync not configured (missing API key or base URL), skipping");
                return false;
            }
```

and change the request URL to:

```java
                    .url(baseUrl + "/trades-sync")
```

In `syncActiveOffers`, same guard change:

```java
            if (apiKey == null || apiKey.isEmpty() || baseUrl.isEmpty()) return false;
```

and URL:

```java
                    .url(baseUrl + "/offers-sync")
```

- [ ] **Step 3: Plugin wiring**

In `GETrackerPlugin.java`:

In the login block (next to `apiClient.setApiKey(config.apiKey());`):

```java
                apiClient.setBaseUrl(config.syncBaseUrl());
```

In `onConfigChanged`, add:

```java
        if ("syncBaseUrl".equals(event.getKey()))
        {
            apiClient.setBaseUrl(config.syncBaseUrl());
        }
```

Also in `startUp()` right after `apiClient = new OsrsGeApiClient(okHttpClient, gson);`:

```java
        apiClient.setBaseUrl(config.syncBaseUrl());
        apiClient.setApiKey(config.apiKey());
```

- [ ] **Step 4: Compile + full tests**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, all suites pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: configurable sync base URL for Lovable Cloud endpoints"
```

---

### Task 3: Lovable Prompt #1 — backend sync API

**Files:**
- Create: `docs/lovable-prompts/01-backend-sync-api.md`

- [ ] **Step 1: Write the prompt file**

Create the file with EXACTLY this content (the fenced block is the whole file):

````markdown
# Lovable Prompt 1 — Backend sync API

Paste everything below the line into Lovable chat. After it finishes,
copy back: (1) the functions base URL, (2) the generated API key.

---

Enable Lovable Cloud for this project. I need a small backend that
receives Grand Exchange trade data from my RuneLite plugin. No UI
changes in this prompt — backend only.

## Database

Create two tables:

**trades**
- id: uuid primary key default gen_random_uuid()
- player_name: text not null
- item_id: integer not null
- item_name: text not null
- buy_price: integer not null
- sell_price: integer not null
- quantity: integer not null
- profit: bigint not null
- tax: bigint not null default 0
- buy_timestamp: bigint not null   (epoch milliseconds)
- sell_timestamp: bigint not null  (epoch milliseconds)
- created_at: timestamptz default now()
- UNIQUE constraint on (player_name, item_id, buy_timestamp, sell_timestamp)

**active_offers**
- id: uuid primary key default gen_random_uuid()
- player_name: text not null
- slot: integer not null
- item_id: integer not null
- item_name: text not null
- price: integer not null
- total_quantity: integer not null
- quantity_filled: integer not null
- amount_spent: bigint not null
- state: text not null
- is_buy: boolean not null
- timestamp: bigint not null
- updated_at: timestamptz default now()

Row Level Security: these tables are written only by edge functions
using the service role; no public/anon access to either table.

## Secret

Generate a strong random API key (32+ chars), store it as an edge
function secret named PLUGIN_API_KEY, and show it to me once so I can
put it in my plugin.

## Edge function: trades-sync

POST only. Auth: require header `Authorization: Bearer <PLUGIN_API_KEY>`;
respond 401 JSON {"ok":false,"error":"unauthorized"} if wrong/missing.
CORS: allow POST from anywhere (the client is a desktop app).
Disable JWT verification for this function (the plugin is not a Supabase
user; auth is the bearer key above).

Request body:

```json
{
  "playerName": "King Salomon",
  "trades": [
    {
      "itemId": 444,
      "itemName": "Gold ore",
      "buyPrice": 145,
      "sellPrice": 142,
      "quantity": 1,
      "profit": -5,
      "tax": 2,
      "buyTimestamp": 1783265377587,
      "sellTimestamp": 1783265397987
    }
  ]
}
```

Behavior: for each trade, UPSERT into trades keyed on
(player_name, item_id, buy_timestamp, sell_timestamp) — re-sending the
same trade must not create a duplicate row. Map camelCase JSON fields to
the snake_case columns. Respond 200 {"ok":true,"inserted":N} where N is
the number of trades received. Malformed body → 400
{"ok":false,"error":"bad request"}.

## Edge function: offers-sync

Same auth, CORS, and JWT settings as trades-sync.

Request body:

```json
{
  "playerName": "King Salomon",
  "offers": [
    {
      "slot": 2,
      "itemId": 444,
      "itemName": "Gold ore",
      "price": 145,
      "totalQuantity": 100,
      "quantityFilled": 40,
      "amountSpent": 5800,
      "state": "BUYING",
      "isBuy": true,
      "timestamp": 1783265377587
    }
  ]
}
```

Behavior: DELETE all rows in active_offers for that player_name, then
INSERT the offers in the request (replace-all snapshot). Empty offers
array is valid — it just clears the player's offers. Respond 200
{"ok":true}.

## When done

Tell me:
1. The base URL for calling these functions
2. The PLUGIN_API_KEY value
````

- [ ] **Step 2: Commit**

```bash
git add docs/lovable-prompts/01-backend-sync-api.md
git commit -m "docs: Lovable prompt for backend sync API"
```

---

### Task 4: Lovable Prompt #2 — dashboard

**Files:**
- Create: `docs/lovable-prompts/02-dashboard.md`

- [ ] **Step 1: Write the prompt file**

Create the file with EXACTLY this content:

````markdown
# Lovable Prompt 2 — Flip dashboard

Run AFTER prompt 1 is deployed and the plugin has synced at least one
trade. Paste everything below the line into Lovable chat.

---

Add a private dashboard page at /dashboard that shows my Grand Exchange
flipping data from the `trades` and `active_offers` tables created
earlier. Match the site's existing dark theme and styling.

## Access

Protect /dashboard with login (Lovable Cloud auth). Only I will have an
account; there is no public signup — hide any signup link, I will create
my single account once. The dashboard reads the tables with my
authenticated session; keep the tables closed to anonymous users.

## Layout, top to bottom

1. **Header row**: page title "Flip Dashboard", an account switcher
   dropdown, and a time-range dropdown.
   - Account switcher options: "All accounts" plus one option per
     distinct player_name in trades, refreshed from the data.
   - Time range options: Past Hour, Past 4 Hours, Past 12 Hours,
     Past Day, Past Week, Past Month, All (default: Past Day). Filter
     trades by sell_timestamp (epoch ms) against the chosen window.

2. **Stat cards** (respect both filters): Total Profit (green if >= 0,
   red if negative), Tax Paid, Flips (row count), Best Item (item_name
   with highest summed profit in the window).

3. **Cumulative profit chart**: line chart of running profit over time
   in the window (x = sell time, y = cumulative profit). Green/red
   depending on final value.

4. **Flips table** — every flip, newest first, paginated (50/page):
   columns Time (local, from sell_timestamp), Account (player_name),
   Item (item_name), Qty, Avg Buy (buy_price), Avg Sell (sell_price),
   Tax, Profit (green/red, signed). Sortable by Time and Profit.

5. **Active offers section**: one card per account that has rows in
   active_offers, listing slot, BUY/SELL (from is_buy), item_name,
   quantity_filled/total_quantity, price, state. Show "No active
   offers" when empty.

## Formatting

- gp amounts: thousands separators; >= 1,000,000 shown as e.g. 2.4M gp.
- Empty state: friendly message telling me to enable sync in my
  RuneLite plugin if there are no trades at all.
- Auto-refresh the data every 60 seconds.
````

- [ ] **Step 2: Commit**

```bash
git add docs/lovable-prompts/02-dashboard.md
git commit -m "docs: Lovable prompt for flip dashboard page"
```

---

### Task 5: Smoke-test script

**Files:**
- Create: `docs/lovable-prompts/smoke-tests.sh` (chmod +x)

- [ ] **Step 1: Write the script**

```bash
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
```

- [ ] **Step 2: Make executable + commit**

```bash
chmod +x docs/lovable-prompts/smoke-tests.sh
git add docs/lovable-prompts/smoke-tests.sh
git commit -m "test: curl smoke tests for sync backend"
```

---

### Task 6: CHECKPOINT — user runs Prompt #1, then verify backend

Not a code task. Sequence:

- [ ] **Step 1:** User pastes `docs/lovable-prompts/01-backend-sync-api.md` (below the line) into Lovable chat; waits for deploy; reports back the functions base URL and PLUGIN_API_KEY.
- [ ] **Step 2:** Run smoke tests from this repo:

```bash
BASE_URL=<reported url> API_KEY=<reported key> ./docs/lovable-prompts/smoke-tests.sh
```

Expected: `6 passed, 0 failed`. If Lovable's implementation deviates (e.g. different status codes), iterate: write a follow-up Lovable prompt fixing the exact failing behavior, re-run until green.

- [ ] **Step 3:** User opens plugin config in RuneLite: pastes Sync URL + API Key, ticks Enable Sync. Existing local trades (all `synced=false`) upload on next 30s tick. Verify: panel shows "Synced"; ask user to confirm or re-run a curl `select` via dashboard once built.

### Task 7: CHECKPOINT — user runs Prompt #2, end-to-end verify

- [ ] **Step 1:** User pastes `docs/lovable-prompts/02-dashboard.md` into Lovable chat, creates their single login account, opens `/dashboard`.
- [ ] **Step 2:** E2E: user does one real flip in game → within ~90s (30s sync + 60s dashboard refresh) the flip appears in the dashboard table with correct tax/profit. Cross-check numbers against the plugin panel.
- [ ] **Step 3:** Update memory file `osrsge-plugin-goals.md` (sync no longer dormant) and commit any final tweaks.
