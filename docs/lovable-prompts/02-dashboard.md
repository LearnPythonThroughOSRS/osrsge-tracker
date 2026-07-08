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
