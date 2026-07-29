# Lovable Prompt 7 — Market replay + refined stats

Paste everything below the line into Lovable chat. Prerequisites:
prompts 1-6 deployed. Include the data reference doc
(00-data-reference.md) in the same message if this is a new chat.

---

Dashboard refinements for an overnight flipper. Three parts: remove a
stat, add better aggregate stats, and add a market-replay view that
overlays my flips on real price history.

## 1. Remove

If the dashboard shows any GP/hour or hourly-profit stat, remove it —
I flip overnight, hourly rates are meaningless for me.

## 2. New aggregate stats (respect account + time-range filters)

Cards / small widgets:
- **Win Rate** — % of flips with profit > 0. Also show per item in the
  item table as a column.
- **Top 3 Concentration** — % of total profit coming from the 3 most
  profitable items in the window.
- **Max Drawdown** — largest peak-to-trough drop in the cumulative
  profit series, in gp.
- **Streaks** — current and longest run of consecutive profitable
  flips; biggest single win and loss (item + amount).
- **Tax Burden** — total tax as a percentage of gross profit
  (profit before tax = profit + tax).
- **Day-of-week bars** — summed profit per weekday.
- **Dead Offer Leaderboard** — from offer_outcomes: items ranked by
  count of cancelled offers with quantity_filled = 0 (my "won't fill
  overnight" avoid-list). Show buy and sell side separately.

## 3. Market replay (the big feature)

The OSRS wiki price API provides historical prices per item, free, no
auth, CORS-enabled. Endpoint:

```
GET https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=<step>&id=<item_id>
```

Returns JSON: {"data":[{"timestamp":<unix seconds>,"avgHighPrice":...,
"avgLowPrice":...,"highPriceVolume":...,"lowPriceVolume":...}, ...]}
Steps: "5m" (covers roughly the last day), "1h" (couple of weeks),
"6h" (months), "24h" (years). Pick the smallest step whose window
covers the flip being viewed. Timestamps in my tables are epoch
MILLIseconds; the wiki uses SECONDS.

Build an **item detail / flip replay view**, opened by clicking a flip
row or an item in the table:

- Line chart of the item's market price (avgHighPrice and avgLowPrice
  as two lines or a band) covering from ~12h before my buy placement to
  ~12h after my sell (or now, if sooner).
- Markers on the chart: buy offer placed (buy_timestamp) at my
  buy_price, and sell completion (sell_timestamp) at my sell_price.
- Computed stats for the flip, shown next to the chart:
  - **Market move during hold**: market mid price at sell time minus at
    buy time.
  - **Capture ratio**: (my sell_price - my buy_price) / market move,
    as %, only when market move > 0.
  - **Entry quality**: market mid 2h after my buy vs at my buy —
    "price rose after entry" good / "fell" bad.
  - **Exit quality**: market mid 6h after my sell vs my sell_price —
    if it kept rising well above my sell, label "sold early"; if it
    dropped, "sold well".
- When multiple flips exist for the item in the window, show all their
  markers on one chart.
- Cache wiki responses in memory for the session; do not hammer the
  API (one request per item+step per 5 minutes max).

Handle items where the wiki has no data (rare/untradeable edge cases)
with a friendly empty state.
