# OSRS GE Tracker v1 — Design

Date: 2026-07-05
Status: Approved

## Goal

A RuneLite plugin, modeled on Flipping Utilities, that tracks Grand Exchange
activity and computes accurate flip profits. v1 targets personal use via
RuneLite developer mode. Sync to osrsge.io ships dormant (code present,
disabled by default) until the site exposes API endpoints.

Builds on the existing skeleton in this repo (`com.osrsge.plugin`), not a
rewrite.

## Scope

### In scope (v1)

1. **Build foundation**
   - Git repo (done), baseline commit of skeleton.
   - Compile and run against current RuneLite `latest.release`; fix any API
     drift since April 2025.
   - Add `GETrackerPluginTest` main class (standard `RuneLite.main` pattern)
     under `src/test/java` so the plugin runs in a dev-mode client.

2. **FIFO flip matching (core change)**
   - Replace the current 1:1 buy/sell match in `FlipTracker` with a per-item
     FIFO ledger:
     - Each completed buy enqueues `{itemId, quantityRemaining, avgPrice, timestamp}`.
     - Each completed sell consumes from the oldest buys first, possibly
       spanning multiple buy entries.
     - Each consumption produces a `CompletedTrade` with weighted-average
       buy cost for the consumed quantity.
   - Partial fills are handled by using quantity actually filled
     (`quantitySold` / `spent`), never the offered quantity.
   - Sells with no matching buys (item acquired outside GE) produce no
     flip record; they still count in session sell stats.

3. **GE tax**
   - Sell proceeds subtract GE tax: 2% of sale price per item, rounded down,
     capped at 5,000,000 gp per item, with the exempt-item list (e.g. old
     school bonds, low-value items under 50 gp, listed exempt items).
   - Tax table lives in one class (`GeTax`) with a unit-tested `taxFor(itemId, price)`.
   - `CompletedTrade.profit = (sellAvg - tax) * qty - buyAvg * qty` (computed
     per-flip from actual consumed quantities).

4. **Persistence**
   - Keep JSON file storage (`TradeStorage`) in the RuneLite home dir,
     one file set per player name.
   - Persist: completed trades, open buy ledger (so unmatched buys survive
     relog/restart), active offers snapshot.
   - Unify state: `allTrades` is the single source of truth for history;
     `SessionStats` derives from trades completed after session start.
     Remove the duplicated trade list inside `SessionStats`.

5. **Panel UI** (`GETrackerPanel`)
   - Section 1 — Session stats: total profit, profit/hr, flip count,
     buy/sell counts, session reset button.
   - Section 2 — Active offers: one row per GE slot: item, buy/sell, price,
     fill progress.
   - Section 3 — Flip history: per-item aggregated rows (total profit,
     flip count), profit green/red, most recent first.
   - Overlay: unchanged concept — small session-profit box shown when GE
     interface is open (config-gated).

6. **Sync, dormant**
   - `OsrsGeApiClient` base URL → `https://osrsge.io/api`.
   - `syncEnabled` config defaults to `false`.
   - No server-side work in v1.

### Out of scope (v1)

- In-GE quick-price/quantity widgets (Flipping Utilities' widget injection).
- Slot timers, margin checker, undercut warnings.
- Multi-account aggregation.
- Plugin Hub submission (separate later effort: repo hygiene, review rules).
- osrsge.io server API.

## Architecture

Existing structure kept:

```
com.osrsge.plugin
├── GETrackerPlugin      — event wiring, lifecycle, orchestration
├── GETrackerConfig      — config items
├── api/OsrsGeApiClient  — osrsge.io sync (dormant) + wiki prices (unused v1)
├── db/TradeStorage      — JSON persistence per player
├── model/
│   ├── TradeOffer       — snapshot of a GE slot offer
│   ├── CompletedTrade   — one matched flip (buy cost, sell price, tax, profit)
│   ├── FlipTracker      — FIFO buy ledger + matching logic (reworked)
│   ├── GeTax            — tax calculation (new)
│   └── SessionStats     — derived session aggregates (slimmed)
├── overlay/GETrackerOverlay
└── ui/GETrackerPanel
```

Data flow:

```
GrandExchangeOfferChanged
  → GETrackerPlugin.processOfferEvent
    → buy complete  → FlipTracker.enqueueBuy
    → sell complete → FlipTracker.consumeSell → List<CompletedTrade>
      → allTrades += trades; SessionStats recompute
  → TradeStorage.save (debounced on event)
  → panel update (Swing EDT)
```

## Error handling

- Offer events before login finishes: keep existing pending-event queue.
- Duplicate offer events (RuneLite re-fires state on login): deduplicate by
  slot + state + quantityFilled — only process transitions that change
  filled quantity or state.
- Storage IO errors: log, never crash the client; keep in-memory state.
- Sync failures (when enabled later): flag `syncConnected=false`, retry next
  interval; trades stay marked unsynced.

## Testing

- Unit tests (JUnit 4, per existing gradle setup):
  - `FlipTrackerTest`: single buy/sell, partial-fill sell across multiple
    buys, sell with empty ledger, weighted-average cost, ledger persistence
    round-trip.
  - `GeTaxTest`: 2% rounding, 5m cap, exempt items, sub-50gp exemption.
- Manual: dev-mode client (`GETrackerPluginTest`), real GE buys/sells,
  relog persistence check.

## Risks

- RuneLite API drift since April 2025 — resolved during build-foundation step.
- Login re-fires GE offer events → double-count risk; addressed by dedupe
  rule above (this is a known RuneLite behavior Flipping Utilities also
  handles).
