# Lovable Prompt 5 — Overnight flipping stats

Paste everything below the line into Lovable chat.

---

Extend the /dashboard page with deeper flipping statistics. All of this
is computable from the existing `trades` table (buy_timestamp and
sell_timestamp are epoch milliseconds; hold time = sell_timestamp -
buy_timestamp). Everything respects the existing account switcher and
time-range filters, and matches the current dark theme.

## New stat cards (add to the existing row, or a second row)

- **Avg Hold Time** — average of (sell_timestamp - buy_timestamp),
  shown humanized (e.g. "6h 40m").
- **Overnight Flips** — count of flips with hold time >= 8 hours, and
  their combined profit underneath (these are my overnight flips).
- **Best Flip** — single trade with highest profit in the window
  (item name + profit).
- **Worst Flip** — single trade with lowest profit (item + profit).

## New charts (below the existing cumulative profit chart)

1. **Daily profit bars** — one bar per calendar day (local time) of
   summed profit, green for positive days, red for negative. Last 30
   days max within the selected range.
2. **Profit by hour of day** — 24 bars showing summed profit grouped by
   the local hour of sell_timestamp. This shows me which hours my
   flips complete best (overnight flips land in the morning hours).

## Flips table additions

- New column **Held** — humanized hold duration per flip (e.g. "9h 12m",
  "3m"). Sortable.
- New column **ROI %** — profit / (buy_price * quantity) * 100, one
  decimal, green/red.

## Per-item breakdown additions

Where per-item aggregates are shown, add: average hold time and an
"overnight" badge on items whose average hold is >= 8 hours.

Keep everything responsive and keep the 60-second auto-refresh.
