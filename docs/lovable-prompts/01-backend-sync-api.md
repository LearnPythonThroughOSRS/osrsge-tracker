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
