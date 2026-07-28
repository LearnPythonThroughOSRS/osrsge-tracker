# Lovable Prompt 6 — Offer outcomes + fill-rate stats

Paste everything below the line into Lovable chat. Prerequisite:
prompts 1-4 deployed (multi-user sync backend + dashboard).

---

My RuneLite plugin now reports the terminal result of EVERY Grand
Exchange offer — including offers that were cancelled after partial or
zero fill. I need a new sync endpoint and fill-rate statistics on the
dashboard.

## New table: offer_outcomes

- id: uuid primary key default gen_random_uuid()
- user_id: uuid not null
- player_name: text not null
- item_id: integer not null
- item_name: text not null
- is_buy: boolean not null
- price: integer not null            (offered price per item)
- total_quantity: integer not null   (requested)
- quantity_filled: integer not null  (0 = never filled)
- placed_timestamp: bigint not null  (epoch ms, when offer was placed)
- ended_timestamp: bigint not null   (epoch ms, completed or cancelled)
- cancelled: boolean not null
- created_at: timestamptz default now()
- UNIQUE (user_id, player_name, item_id, is_buy, placed_timestamp)

RLS: same as trades — users SELECT only their own rows; writes only via
edge functions with the service role.

## New edge function: outcomes-sync

Same auth as trades-sync (SHA-256 the bearer token, look up in
api_keys, 401 unknown, bump last_used_at). POST body:

```json
{
  "playerName": "King Salomon",
  "outcomes": [
    {
      "itemId": 444,
      "itemName": "Gold ore",
      "isBuy": true,
      "price": 145,
      "totalQuantity": 100,
      "quantityFilled": 37,
      "placedTimestamp": 1783265377587,
      "endedTimestamp": 1783265397987,
      "cancelled": true
    }
  ]
}
```

UPSERT on the unique constraint (idempotent retries). Respond
200 {"ok":true}. Malformed body → 400.

## Dashboard: fill-rate section

Add a "Fill Rates" section (respects the account switcher and
time-range filter, filtering by ended_timestamp):

1. Two stat cards: **Buy Fill Rate** and **Sell Fill Rate** — total
   quantity_filled / total total_quantity across outcomes in the
   window, as a percentage.

2. **Per-item fill table**, one row per item, columns for buy side and
   sell side:
   - Offers (count), Fill % (sum filled / sum requested),
     Fully filled (count where quantity_filled = total_quantity),
     Dead offers (count where cancelled and quantity_filled = 0).
   - Color the Fill % green >= 90, yellow 50-89, red < 50.
   - Sortable by Fill %.

3. In the per-item expandable details that already exist, add that
   item's buy fill % and sell fill % when outcome data exists.

This tells me which items reliably buy and sell and which ones sit
unfilled — critical for choosing overnight flips.
