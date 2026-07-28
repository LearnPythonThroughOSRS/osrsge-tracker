# Data Reference — paste this into Lovable before asking for dashboard changes

This document describes all data my RuneLite plugin syncs into this
project's database, what each field means, and what is or isn't
possible to add. Use it as ground truth when designing dashboard
features. (Maintained in the plugin repo; ask me for the latest.)

## Tables (all rows scoped by user_id; RLS: users see only their own)

### trades — one row per completed flip
| column | meaning |
|---|---|
| player_name | in-game character name (one user has many) |
| item_id / item_name | OSRS item (item_id is the game's canonical id) |
| buy_price | average gp paid per item (weighted across FIFO-matched buys) |
| sell_price | average gp received per item BEFORE tax |
| quantity | items matched in this flip |
| tax | total GE tax paid on this flip (2% per item, 5m/item cap, some items exempt, <50gp exempt) |
| profit | sell_price*qty - tax - buy cost. NET, tax already deducted |
| buy_timestamp | epoch ms when the BUY OFFER WAS PLACED (capital committed) |
| sell_timestamp | epoch ms when the sell completion was observed |

Caveat: offers can fill while the player is logged out. Placement times
are always accurate; completion times are "when the plugin saw it",
which for offline fills = next login. Hold time (sell_timestamp -
buy_timestamp) is therefore accurate for the overnight use case but
completion-side times can be late by the logged-out gap.

### active_offers — live GE slot snapshot, replaced wholesale every ~30s
player_name, slot (0-7), item_id, item_name, price (offered per item),
total_quantity (requested), quantity_filled, amount_spent, state
(BUYING/SELLING/BOUGHT/SOLD/CANCELLED_BUY/CANCELLED_SELL), is_buy,
timestamp. An empty sync clears the player's rows (player has no
active offers or is at login screen).

### offer_outcomes — terminal result of EVERY offer, filled or not
player_name, item_id, item_name, is_buy, price (offered per item),
total_quantity (requested), quantity_filled (0 = never filled),
placed_timestamp, ended_timestamp, cancelled (true = player cancelled
before full fill). Unique per (user_id, player_name, item_id, is_buy,
placed_timestamp).

This is the fill-rate source: fill % = sum(quantity_filled) /
sum(total_quantity); "dead offer" = cancelled with 0 filled.

### api_keys — one per user
key_prefix + dates only are user-visible; key_hash is SHA-256 of the
full key. Sync functions authenticate by hashing the bearer token.

## Sync cadence

Plugin syncs every 30 seconds while logged in, plus immediately on
login and on config change. Dashboard data is at most ~30-90s stale
while the player is online; frozen while offline.

## What the plugin COULD additionally send (ask the plugin developer)

- Cash stack / bank value snapshots (wealth over time charts)
- Session boundaries (login/logout times per character)
- GE slot count in use over time (capital utilization)
- Wiki market prices at trade time (margin vs market analysis)
- Offer price revisions (each cancel-and-relist chain linked together)

## What is NOT possible

- Exact fill times for offers that filled while logged out (the game
  does not report them)
- Historical trades from before the plugin was installed
- Other players' data or market-wide volumes (only the wiki price API,
  which the site already uses)
- Real-time push (the plugin polls; ~30s is the floor)
