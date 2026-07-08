# osrsge.io Sync + Flip Dashboard — Design

Date: 2026-07-08
Status: Approved

## Goal

The plugin syncs every completed flip (and current active offers) to the
user's website osrsge.io, where a private dashboard shows all flips across
all of the user's Jagex accounts.

Division of labor:
- **This repo (Claude):** plugin sync changes, API contract, curl test
  scripts, and the exact prompts the user pastes into Lovable.
- **Lovable (user relays prompts):** backend (Lovable Cloud / Supabase
  tables + edge functions) and the dashboard page in the osrsge.io SPA.

Single-user system for now: one API key, one dashboard login (the user).
Multi-user signup is explicitly out of scope.

## Site facts (probed 2026-07-08)

- osrsge.io is a Lovable-built React SPA behind Cloudflare; no backend
  detected (no /api routes — SPA catch-all answers everything).
- User edits the site only through the Lovable editor (no GitHub sync,
  no local copy).
- Therefore the sync endpoints will live on Lovable Cloud's Supabase
  (edge functions), NOT under `https://osrsge.io/api`. The plugin's sync
  base URL becomes a config item.

## API contract (linchpin — both sides implement exactly this)

Base URL: provided by Lovable Cloud after Prompt #1 runs, shaped like
`https://<project-ref>.supabase.co/functions/v1`.

Auth on both endpoints: header `Authorization: Bearer <API_KEY>`.
The API key is a random secret generated during backend setup; stored as
an edge-function secret on the server and pasted into the plugin config
by the user. Non-matching or missing key → HTTP 401.

### POST /trades-sync

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

Semantics: upsert each trade with uniqueness key
`(playerName, itemId, buyTimestamp, sellTimestamp)`. Re-sending the same
trade is a no-op (idempotent retries). `synced=false` trades are re-sent
by the plugin until a 200 arrives.

Response: `200 {"ok": true, "inserted": <n>}`.

### POST /offers-sync

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

Semantics: replace-all for that player (delete player's rows, insert
current). Represents the live GE slots snapshot.

Response: `200 {"ok": true}`.

## Data model (server, created by Lovable Prompt #1)

- `trades`: id, player_name, item_id, item_name, buy_price, sell_price,
  quantity, profit, tax, buy_timestamp (ms), sell_timestamp (ms),
  created_at. Unique index on (player_name, item_id, buy_timestamp,
  sell_timestamp).
- `active_offers`: id, player_name, slot, item_id, item_name, price,
  total_quantity, quantity_filled, amount_spent, state, is_buy,
  timestamp (ms), updated_at.
- Accounts are derived: `select distinct player_name from trades` —
  no accounts table, no linking flow. Any character syncing with the
  valid key appears automatically.

## Dashboard (Lovable Prompt #2)

Route `/dashboard`, protected by Lovable Cloud auth (single user account —
the site owner). Contents:

- Account switcher: "All accounts" + one entry per distinct player_name.
- Stat cards for current filter: total profit, tax paid, flip count,
  best item by profit.
- Flips table (every flip): time, account, item, quantity, avg buy, avg
  sell, tax, profit (green/red). Sort by time desc; time-range filter:
  Past Hour / 4h / 12h / Day / Week / Month / All (mirrors plugin panel).
- Profit-over-time cumulative line chart for current filter.
- Active offers strip: current GE slots per account from `active_offers`.
- Styling: match existing osrsge.io dark theme.

## Plugin changes (this repo)

1. `GETrackerConfig`: new `syncBaseUrl` config item (default
   `""` = sync disabled regardless of toggle) replacing the hardcoded
   `BASE_URL`; description explains pasting the Lovable Cloud URL.
2. `OsrsGeApiClient`: take base URL as a setter (updated on config
   change); endpoints become `<base>/trades-sync` and `<base>/offers-sync`.
3. Payloads already match the contract (`CompletedTrade` includes `tax`;
   Gson serializes camelCase as specified). Verify field names in a unit
   test that snapshots the JSON.
4. Sync loop already exists (`performSync`, `synced` flag, retries).
   Keep interval config (default 30s).
5. Local verification: tiny mock HTTP server test (or curl-able stub)
   proving the plugin emits contract-correct JSON before Lovable exists.

## Lovable prompts (deliverables in this repo)

Stored under `docs/lovable-prompts/`:
- `01-backend-sync-api.md` — enable Lovable Cloud, create tables +
  unique index, two edge functions with Bearer-key auth and the exact
  semantics above, generate API key secret, output function URLs.
- `02-dashboard.md` — the dashboard page spec above, referencing the
  tables from prompt 1.

Each prompt is self-contained (Lovable has no context from this chat).

## Sequence

1. Plugin changes + JSON snapshot test + prompts written (this repo).
2. User runs Prompt #1 in Lovable → pastes back function base URL + key.
3. Curl smoke tests from here (valid key, bad key 401, duplicate upsert).
4. User pastes URL + key into plugin config, enables sync → live trades
   flow; verify rows server-side via dashboard or curl.
5. User runs Prompt #2 → dashboard live; end-to-end check: flip in game
   → row on osrsge.io/dashboard.

## Error handling

- Plugin: existing behavior — failed sync leaves `synced=false`, retried
  next interval; `syncConnected` flag drives panel "Offline/Synced" label.
- Server: 401 on bad key; 400 on malformed body; upsert prevents
  duplicate rows from retries.
- Dashboard: empty states for no data; derives accounts dynamically.

## Out of scope

- Multi-user signup / public dashboards.
- Deleting or editing trades from the web.
- Real-time push (30s poll interval is fine).
- Historical import of pre-sync trades (local JSON already syncs because
  all trades start `synced=false` — the full backlog uploads on first
  successful sync).

## Testing

- Unit: JSON payload snapshot test against the contract.
- Integration: curl scripts (`docs/lovable-prompts/smoke-tests.sh`)
  for 200/401/idempotency once backend exists.
- E2E: live flip → dashboard row.
