# OSRS GE Tracker

RuneLite plugin that tracks your Grand Exchange flips with accurate
profit math, and can optionally sync your trade history to your
[osrsge.io](https://osrsge.io/) dashboard.

## Features

- **Flip tracking** — buys and sells are matched FIFO per item, with
  partial fills handled correctly.
- **Real profit** — GE tax (2%, 5m cap, exempt items) is included, so
  the numbers match what actually hits your coin pouch.
- **Side panel** — session stats (profit, gp/hr, tax paid), time-range
  filter (hour → month → all), expandable per-item rows with item icons,
  avg buy/sell, ROI, and tax.
- **Persistent history** — trades and unmatched buys survive relogs and
  client restarts, stored per character.
- **Multi-account** — every character you play is tracked separately and
  appears as its own account on the web dashboard.

## Website sync (optional, off by default)

The plugin can upload your completed flips and active offers to your
personal dashboard on [osrsge.io](https://osrsge.io/):

1. Create an account at osrsge.io and generate an API key on the
   dashboard's Settings card.
2. In the plugin settings, paste the Sync URL and your API key, then
   enable sync.

Only your own trade data (character name, item, prices, quantities,
timestamps) is sent, over HTTPS, to the endpoint you configure. Nothing
is sent while sync is disabled or unconfigured.

## Development

```
./gradlew runClient    # launches RuneLite in developer mode with the plugin
./gradlew test         # unit tests
```
