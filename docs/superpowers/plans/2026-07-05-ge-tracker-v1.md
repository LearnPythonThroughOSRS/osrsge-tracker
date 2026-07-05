# GE Tracker v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accurate flip tracking with GE tax, FIFO buy/sell matching, duplicate-event protection, and persistent buy ledger — per spec `docs/superpowers/specs/2026-07-05-ge-tracker-v1-design.md`.

**Architecture:** Pure-logic classes (`GeTax`, `FlipTracker`, `OfferEventDeduper`) are unit-tested with JUnit 4 and wired into `GETrackerPlugin.processOfferEvent`. Persistence stays JSON-per-player via `TradeStorage`. Sync code stays but points at osrsge.io and defaults off.

**Tech Stack:** Java 11, RuneLite client API (`latest.release`), Lombok, Gson, JUnit 4. Build: `./gradlew`. Run client: `./gradlew runClient`.

**Test command pattern:** `./gradlew test --tests "com.osrsge.plugin.model.GeTaxTest" --console=plain`

---

### Task 1: GeTax — tax calculation

**Files:**
- Create: `src/main/java/com/osrsge/plugin/model/GeTax.java`
- Test: `src/test/java/com/osrsge/plugin/model/GeTaxTest.java`

GE tax rules (current game rules): 2% of sale price per item, rounded down (integer `price / 50`), capped at 5,000,000 gp per item. Items priced under 50 gp pay 0 (falls out of integer division). Exempt items pay 0 regardless of price.

- [ ] **Step 1: Write the failing test**

```java
package com.osrsge.plugin.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeTaxTest
{
    @Test
    public void twoPercentRoundedDown()
    {
        // User's real trade: gold ore sold at 145 -> 2 gp tax
        assertEquals(2, GeTax.taxFor(444, 145));
        assertEquals(1, GeTax.taxFor(444, 50));
        assertEquals(20_000, GeTax.taxFor(444, 1_000_000));
    }

    @Test
    public void underFiftyGpIsFree()
    {
        assertEquals(0, GeTax.taxFor(444, 49));
        assertEquals(0, GeTax.taxFor(444, 1));
        assertEquals(0, GeTax.taxFor(444, 0));
    }

    @Test
    public void cappedAtFiveMillionPerItem()
    {
        assertEquals(5_000_000, GeTax.taxFor(444, 300_000_000));
    }

    @Test
    public void exemptItemsPayNothing()
    {
        assertEquals(0, GeTax.taxFor(GeTax.OLD_SCHOOL_BOND, 5_000_000));
        assertEquals(0, GeTax.taxFor(2347, 1_000)); // hammer
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.osrsge.plugin.model.GeTaxTest" --console=plain`
Expected: FAIL — `GeTax` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.osrsge.plugin.model;

import java.util.Set;

/**
 * Grand Exchange sell tax: 2% per item rounded down, capped at 5m gp
 * per item. Items under 50 gp and exempt items pay nothing.
 */
public final class GeTax
{
    public static final int OLD_SCHOOL_BOND = 13190;
    public static final long TAX_CAP_PER_ITEM = 5_000_000L;

    // Bond + low-level skilling tools are tax-exempt
    private static final Set<Integer> EXEMPT_ITEMS = Set.of(
        OLD_SCHOOL_BOND,
        1755,  // Chisel
        5325,  // Gardening trowel
        1785,  // Glassblowing pipe
        2347,  // Hammer
        1733,  // Needle
        233,   // Pestle and mortar
        5341,  // Rake
        8794,  // Saw
        5329,  // Secateurs
        5343,  // Seed dibber
        1735,  // Shears
        952,   // Spade
        5331   // Watering can (0)
    );

    private GeTax()
    {
    }

    /** Tax in gp charged on ONE item sold at pricePerItem. */
    public static long taxFor(int itemId, long pricePerItem)
    {
        if (EXEMPT_ITEMS.contains(itemId))
        {
            return 0;
        }
        return Math.min(pricePerItem / 50, TAX_CAP_PER_ITEM);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.osrsge.plugin.model.GeTaxTest" --console=plain`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/osrsge/plugin/model/GeTax.java src/test/java/com/osrsge/plugin/model/GeTaxTest.java
git commit -m "feat: add GE tax calculation (2% floor, 5m cap, exemptions)"
```

---

### Task 2: FIFO FlipTracker + tax-aware CompletedTrade

**Files:**
- Create: `src/main/java/com/osrsge/plugin/model/BuyEntry.java`
- Modify: `src/main/java/com/osrsge/plugin/model/CompletedTrade.java` (add `tax` field)
- Rewrite: `src/main/java/com/osrsge/plugin/model/FlipTracker.java`
- Test: `src/test/java/com/osrsge/plugin/model/FlipTrackerTest.java`

Replaces the one-buy-per-item map with a per-item FIFO queue of buys. A sell consumes the oldest buys first, possibly spanning several entries; produces ONE `CompletedTrade` per sell event with exact cost math (no per-unit rounding loss). Sell quantity beyond what the ledger holds is ignored for flip purposes (item came from outside the GE).

- [ ] **Step 1: Add `tax` field to CompletedTrade**

In `CompletedTrade.java`, add below `private long profit;`:

```java
    private long tax;
```

(Lombok `@Data`/`@Builder` generate accessors/builder entry automatically.)

- [ ] **Step 2: Create BuyEntry model**

```java
package com.osrsge.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One completed buy sitting in the FIFO ledger, partially or fully unmatched. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyEntry
{
    private int itemId;
    private int remaining;
    private int price;      // average gp paid per item
    private long timestamp;
}
```

(No-args constructor needed for Gson deserialization.)

- [ ] **Step 3: Write the failing tests**

```java
package com.osrsge.plugin.model;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FlipTrackerTest
{
    private static final int ITEM = 444; // gold ore, not tax-exempt

    private FlipTracker tracker;

    @Before
    public void setUp()
    {
        tracker = new FlipTracker();
    }

    @Test
    public void simpleFlipComputesTaxedProfit()
    {
        // User's real trade: buy 1 @145, sell 1 @145 -> tax 2, profit -2
        tracker.recordBuy(ITEM, 1, 145, 1000L);
        CompletedTrade trade = tracker.matchSell(ITEM, "Gold ore", 1, 145, 2000L);

        assertEquals(1, trade.getQuantity());
        assertEquals(145, trade.getBuyPrice());
        assertEquals(145, trade.getSellPrice());
        assertEquals(2, trade.getTax());
        assertEquals(-2, trade.getProfit());
        assertEquals(1000L, trade.getBuyTimestamp());
        assertEquals(2000L, trade.getSellTimestamp());
    }

    @Test
    public void sellSpansMultipleBuysFifo()
    {
        tracker.recordBuy(ITEM, 10, 100, 1000L);
        tracker.recordBuy(ITEM, 10, 120, 2000L);

        // sell 15 @150: consumes 10 @100 + 5 @120 = 1600 cost
        CompletedTrade trade = tracker.matchSell(ITEM, "Gold ore", 15, 150, 3000L);

        assertEquals(15, trade.getQuantity());
        // weighted avg buy: 1600/15 = 106 (floor)
        assertEquals(106, trade.getBuyPrice());
        // tax: 150/50=3 per item * 15 = 45
        assertEquals(45, trade.getTax());
        // profit: 150*15 - 45 - 1600 = 2250 - 45 - 1600 = 605
        assertEquals(605, trade.getProfit());
        // oldest buy's timestamp is the flip start
        assertEquals(1000L, trade.getBuyTimestamp());

        // 5 remain of the second buy
        assertEquals(5, tracker.unmatchedQuantity(ITEM));
    }

    @Test
    public void sellBeyondLedgerOnlyMatchesAvailable()
    {
        tracker.recordBuy(ITEM, 3, 100, 1000L);
        CompletedTrade trade = tracker.matchSell(ITEM, "Gold ore", 10, 150, 2000L);

        assertEquals(3, trade.getQuantity());
        assertEquals(0, tracker.unmatchedQuantity(ITEM));
    }

    @Test
    public void sellWithEmptyLedgerReturnsNull()
    {
        assertNull(tracker.matchSell(ITEM, "Gold ore", 5, 150, 2000L));
    }

    @Test
    public void ledgerSnapshotRoundTrip()
    {
        tracker.recordBuy(ITEM, 10, 100, 1000L);
        tracker.matchSell(ITEM, "Gold ore", 4, 150, 2000L);

        List<BuyEntry> snapshot = tracker.snapshot();
        assertEquals(1, snapshot.size());
        assertEquals(6, snapshot.get(0).getRemaining());

        FlipTracker restored = new FlipTracker();
        restored.restore(snapshot);
        assertEquals(6, restored.unmatchedQuantity(ITEM));
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "com.osrsge.plugin.model.FlipTrackerTest" --console=plain`
Expected: FAIL — compile errors (`recordBuy`, `matchSell`, `snapshot` don't exist).

- [ ] **Step 5: Rewrite FlipTracker**

Replace the whole file:

```java
package com.osrsge.plugin.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIFO buy ledger per item. Sells consume the oldest buys first;
 * each sell produces one CompletedTrade with exact cost math and GE tax.
 */
public class FlipTracker
{
    private final Map<Integer, Deque<BuyEntry>> buyLedger = new HashMap<>();

    public void recordBuy(int itemId, int quantity, int avgPrice, long timestamp)
    {
        if (quantity <= 0)
        {
            return;
        }
        buyLedger.computeIfAbsent(itemId, k -> new ArrayDeque<>())
            .addLast(new BuyEntry(itemId, quantity, avgPrice, timestamp));
    }

    /**
     * Match a completed sell against the ledger. Returns null when no
     * buys exist for the item (nothing to flip against).
     */
    public CompletedTrade matchSell(int itemId, String itemName, int quantity, int sellAvgPrice, long timestamp)
    {
        Deque<BuyEntry> buys = buyLedger.get(itemId);
        if (buys == null || buys.isEmpty() || quantity <= 0)
        {
            return null;
        }

        int matched = 0;
        long totalBuyCost = 0;
        long firstBuyTimestamp = buys.peekFirst().getTimestamp();

        while (matched < quantity && !buys.isEmpty())
        {
            BuyEntry oldest = buys.peekFirst();
            int take = Math.min(oldest.getRemaining(), quantity - matched);
            matched += take;
            totalBuyCost += (long) take * oldest.getPrice();
            oldest.setRemaining(oldest.getRemaining() - take);
            if (oldest.getRemaining() == 0)
            {
                buys.pollFirst();
            }
        }

        long taxPerItem = GeTax.taxFor(itemId, sellAvgPrice);
        long totalTax = taxPerItem * matched;
        long profit = (long) sellAvgPrice * matched - totalTax - totalBuyCost;

        return CompletedTrade.builder()
            .itemId(itemId)
            .itemName(itemName)
            .buyPrice((int) (totalBuyCost / matched))
            .sellPrice(sellAvgPrice)
            .quantity(matched)
            .tax(totalTax)
            .profit(profit)
            .buyTimestamp(firstBuyTimestamp)
            .sellTimestamp(timestamp)
            .synced(false)
            .build();
    }

    public int unmatchedQuantity(int itemId)
    {
        Deque<BuyEntry> buys = buyLedger.get(itemId);
        if (buys == null)
        {
            return 0;
        }
        return buys.stream().mapToInt(BuyEntry::getRemaining).sum();
    }

    /** Flat copy of all ledger entries, oldest first per item, for persistence. */
    public List<BuyEntry> snapshot()
    {
        List<BuyEntry> out = new ArrayList<>();
        buyLedger.values().forEach(out::addAll);
        return out;
    }

    public void restore(List<BuyEntry> entries)
    {
        buyLedger.clear();
        if (entries == null)
        {
            return;
        }
        for (BuyEntry e : entries)
        {
            if (e.getRemaining() > 0)
            {
                buyLedger.computeIfAbsent(e.getItemId(), k -> new ArrayDeque<>()).addLast(e);
            }
        }
    }

    public void clear()
    {
        buyLedger.clear();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.osrsge.plugin.model.FlipTrackerTest" --console=plain`
Expected: PASS, 5 tests. Note: `GETrackerPlugin` will now FAIL to compile (it calls removed methods `recordBuyComplete`/`consumeMatchingBuy`). That is expected — run only the model test here; plugin wiring is Task 4. If gradle insists on compiling main first, temporarily comment nothing — instead do Task 4's plugin change in the same commit (see Step 7 note).

- [ ] **Step 7: Compile check the whole project**

Run: `./gradlew compileJava --console=plain`
If it fails on `GETrackerPlugin` (it will — old FlipTracker API gone), apply the Task 4 plugin patch now and treat Task 4 Step 1 as done. Tasks stay separate in the plan for reviewability; the commit may combine them with message noting both.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: FIFO flip matching with GE tax and ledger snapshot"
```

---

### Task 3: Offer event dedupe

**Files:**
- Create: `src/main/java/com/osrsge/plugin/model/OfferEventDeduper.java`
- Test: `src/test/java/com/osrsge/plugin/model/OfferEventDeduperTest.java`

Live session showed CANCELLED_SELL firing twice for the same offer. RuneLite also re-fires last-known offer state on login. Dedupe key: slot -> "state:filled:spent"; identical consecutive value = duplicate. On login the deduper is seeded from the persisted offers snapshot so replayed events are dropped (Task 4).

- [ ] **Step 1: Write the failing test**

```java
package com.osrsge.plugin.model;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferEventDeduperTest
{
    private final OfferEventDeduper deduper = new OfferEventDeduper();

    @Test
    public void repeatedIdenticalEventIsDuplicate()
    {
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.CANCELLED_SELL, 1, 145));
        assertTrue(deduper.isDuplicate(0, GrandExchangeOfferState.CANCELLED_SELL, 1, 145));
    }

    @Test
    public void progressChangeIsNotDuplicate()
    {
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BUYING, 1, 145));
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BUYING, 2, 290));
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BOUGHT, 2, 290));
    }

    @Test
    public void slotsAreIndependent()
    {
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BOUGHT, 1, 145));
        assertFalse(deduper.isDuplicate(1, GrandExchangeOfferState.BOUGHT, 1, 145));
    }

    @Test
    public void seedMarksReplayedStateAsDuplicate()
    {
        deduper.seed(2, GrandExchangeOfferState.BOUGHT, 5, 500);
        assertTrue(deduper.isDuplicate(2, GrandExchangeOfferState.BOUGHT, 5, 500));
        assertFalse(deduper.isDuplicate(2, GrandExchangeOfferState.EMPTY, 0, 0));
    }

    @Test
    public void clearSlotForgetsHistory()
    {
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BOUGHT, 1, 145));
        deduper.clearSlot(0);
        assertFalse(deduper.isDuplicate(0, GrandExchangeOfferState.BOUGHT, 1, 145));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.osrsge.plugin.model.OfferEventDeduperTest" --console=plain`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

```java
package com.osrsge.plugin.model;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Drops repeated GrandExchangeOfferChanged events. The GE fires the same
 * terminal state more than once (seen live: double CANCELLED_SELL) and
 * replays last-known state on login.
 */
public class OfferEventDeduper
{
    private final Map<Integer, String> lastSeen = new HashMap<>();

    public boolean isDuplicate(int slot, GrandExchangeOfferState state, int quantityFilled, long spent)
    {
        String key = key(state, quantityFilled, spent);
        return key.equals(lastSeen.put(slot, key));
    }

    /** Prime a slot with persisted state so login replays are dropped. */
    public void seed(int slot, GrandExchangeOfferState state, int quantityFilled, long spent)
    {
        lastSeen.put(slot, key(state, quantityFilled, spent));
    }

    public void clearSlot(int slot)
    {
        lastSeen.remove(slot);
    }

    public void clear()
    {
        lastSeen.clear();
    }

    private static String key(GrandExchangeOfferState state, int quantityFilled, long spent)
    {
        return state + ":" + quantityFilled + ":" + spent;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.osrsge.plugin.model.OfferEventDeduperTest" --console=plain`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/osrsge/plugin/model/OfferEventDeduper.java src/test/java/com/osrsge/plugin/model/OfferEventDeduperTest.java
git commit -m "feat: dedupe repeated GE offer events"
```

---

### Task 4: Wire into plugin + ledger persistence + SessionStats slim

**Files:**
- Modify: `src/main/java/com/osrsge/plugin/GETrackerPlugin.java`
- Modify: `src/main/java/com/osrsge/plugin/db/TradeStorage.java`
- Modify: `src/main/java/com/osrsge/plugin/model/SessionStats.java`
- Modify: `src/main/java/com/osrsge/plugin/ui/GETrackerPanel.java` (one call-site line)

No new unit tests (plugin class needs a live client); verified by Task 6 playtest. Model classes carrying the logic are already tested.

- [ ] **Step 1: TradeStorage — add ledger persistence**

Add to `TradeStorage.java` (below `getOffersFile`):

```java
    private File getLedgerFile(String playerName)
    {
        return new File(PLUGIN_DIR, playerName.toLowerCase().replace(" ", "_") + "_ledger.json");
    }

    public List<BuyEntry> loadLedger(String playerName)
    {
        File file = getLedgerFile(playerName);
        if (!file.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(file))
        {
            Type type = new TypeToken<List<BuyEntry>>(){}.getType();
            List<BuyEntry> entries = gson.fromJson(reader, type);
            return entries != null ? entries : new ArrayList<>();
        }
        catch (IOException e)
        {
            log.error("Failed to load ledger for {}", playerName, e);
            return new ArrayList<>();
        }
    }

    public void saveLedger(String playerName, List<BuyEntry> entries)
    {
        File file = getLedgerFile(playerName);
        try (FileWriter writer = new FileWriter(file))
        {
            gson.toJson(entries, writer);
        }
        catch (IOException e)
        {
            log.error("Failed to save ledger for {}", playerName, e);
        }
    }
```

Add import: `import com.osrsge.plugin.model.BuyEntry;`

- [ ] **Step 2: SessionStats — counters only, plus tax total**

Replace `SessionStats.java` body changes: remove `completedTrades` list and its uses; add `totalTax`. Full class after edit:

```java
package com.osrsge.plugin.model;

import lombok.Data;

import java.time.Instant;

@Data
public class SessionStats
{
    private final Instant sessionStart = Instant.now();
    private long totalProfit = 0;
    private long totalTax = 0;
    private int totalFlips = 0;
    private int totalBuys = 0;
    private int totalSells = 0;

    public void recordFlip(CompletedTrade trade)
    {
        totalProfit += trade.getProfit();
        totalTax += trade.getTax();
        totalFlips++;
    }

    public void recordBuy()
    {
        totalBuys++;
    }

    public void recordSell()
    {
        totalSells++;
    }

    public long getSessionDurationSeconds()
    {
        return Instant.now().getEpochSecond() - sessionStart.getEpochSecond();
    }

    public String getSessionDurationFormatted()
    {
        long seconds = getSessionDurationSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public long getProfitPerHour()
    {
        long seconds = getSessionDurationSeconds();
        if (seconds == 0) return 0;
        return (totalProfit * 3600) / seconds;
    }
}
```

(`reset()` removed — plugin resets by replacing the object, which it already does in `resetSession()`.)

- [ ] **Step 3: GETrackerPlugin — new wiring**

Changes to `GETrackerPlugin.java`:

3a. Add field next to `flipTracker`:

```java
    private final OfferEventDeduper deduper = new OfferEventDeduper();
```

Add getter for trades (panel needs it):

```java
    @Getter
    private List<CompletedTrade> allTrades = new ArrayList<>();
```

(`allTrades` already exists — just add `@Getter` above it.)

3b. In the login block (inside the `invokeLater` after `allTrades = tradeStorage.loadTrades(playerName);`), add ledger restore and deduper seeding:

```java
                    flipTracker.restore(tradeStorage.loadLedger(playerName));
                    for (TradeOffer saved : tradeStorage.loadOffers(playerName))
                    {
                        deduper.seed(saved.getSlot(), saved.getState(),
                            saved.getQuantityFilled(), saved.getAmountSpent());
                    }
```

3c. In the LOGIN_SCREEN block, add `deduper.clear();` next to `flipTracker.clear();`.
Also note: `flipTracker.clear()` after `saveState()` is fine — ledger was saved first.

3d. Replace `processOfferEvent` body:

```java
    private void processOfferEvent(GrandExchangeOfferChanged event)
    {
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();
        GrandExchangeOfferState state = offer.getState();

        if (state == GrandExchangeOfferState.EMPTY)
        {
            slotOffers.remove(slot);
            deduper.clearSlot(slot);
            return;
        }

        if (deduper.isDuplicate(slot, state, offer.getQuantitySold(), offer.getSpent()))
        {
            return;
        }

        String itemName = itemManager.getItemComposition(offer.getItemId()).getName();

        TradeOffer tradeOffer = TradeOffer.builder()
            .slot(slot)
            .itemId(offer.getItemId())
            .itemName(itemName)
            .price(offer.getPrice())
            .totalQuantity(offer.getTotalQuantity())
            .quantityFilled(offer.getQuantitySold())
            .amountSpent(offer.getSpent())
            .state(state)
            .isBuy(state == GrandExchangeOfferState.BUYING
                || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY)
            .timestamp(System.currentTimeMillis())
            .build();

        slotOffers.put(slot, tradeOffer);

        if ((state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY)
            && tradeOffer.getQuantityFilled() > 0)
        {
            flipTracker.recordBuy(tradeOffer.getItemId(), tradeOffer.getQuantityFilled(),
                tradeOffer.getAveragePrice(), tradeOffer.getTimestamp());
            sessionStats.recordBuy();
            log.debug("Buy completed: {} x{} @ {}", itemName,
                tradeOffer.getQuantityFilled(), tradeOffer.getAveragePrice());
        }

        if ((state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL)
            && tradeOffer.getQuantityFilled() > 0)
        {
            sessionStats.recordSell();
            CompletedTrade trade = flipTracker.matchSell(tradeOffer.getItemId(), itemName,
                tradeOffer.getQuantityFilled(), tradeOffer.getAveragePrice(), tradeOffer.getTimestamp());

            if (trade != null)
            {
                allTrades.add(trade);
                sessionStats.recordFlip(trade);
                log.info("Flip completed: {} x{} profit={} (tax={})",
                    itemName, trade.getQuantity(), trade.getProfit(), trade.getTax());
            }
        }

        updatePanel();
        saveState();
    }
```

(The unused local `previous` from the old version is gone.)

3e. In `saveState()`, add ledger save:

```java
        tradeStorage.saveLedger(playerName, flipTracker.snapshot());
```

3f. In `updatePanel()`, change the history line to use all trades:

```java
                panel.updateTradeHistory(allTrades);
```

3g. Remove now-unused import if flagged (`java.util.stream.Collectors` stays — still used by `getActiveOffers`).

- [ ] **Step 4: Compile + full test run**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass (GeTaxTest 4, FlipTrackerTest 5, OfferEventDeduperTest 5).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: wire FIFO matching, tax, dedupe, and ledger persistence into plugin"
```

---

### Task 5: Panel tax line + osrsge.io URLs + sync defaults

**Files:**
- Modify: `src/main/java/com/osrsge/plugin/ui/GETrackerPanel.java`
- Modify: `src/main/java/com/osrsge/plugin/GETrackerConfig.java`
- Modify: `src/main/java/com/osrsge/plugin/api/OsrsGeApiClient.java`
- Modify: `runelite-plugin.properties`

- [ ] **Step 1: Panel — add "Tax Paid" stat row**

In `GETrackerPanel.java`:

Add field next to the other labels:

```java
    private final JLabel taxLabel = new JLabel("0 gp");
```

In `buildStatsPanel()`, change grid to 5 rows: `new GridLayout(5, 2, 5, 5)`, and add after the Flips rows:

```java
        stats.add(createLabel("Tax Paid:", Color.WHITE));
        taxLabel.setForeground(new Color(255, 180, 100));
        taxLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(taxLabel);
```

In `updateStats()`, add:

```java
        taxLabel.setText(formatGp(stats.getTotalTax()));
```

- [ ] **Step 1b: Panel — per-item aggregated history (spec 5.3)**

Replace the body of `updateTradeHistory` in `GETrackerPanel.java` so rows aggregate by item (total profit, flip count), most recently traded first:

```java
    public void updateTradeHistory(List<CompletedTrade> trades)
    {
        SwingUtilities.invokeLater(() -> {
            tradeHistoryPanel.removeAll();

            if (trades.isEmpty())
            {
                JLabel empty = new JLabel("No completed trades yet");
                empty.setForeground(Color.GRAY);
                empty.setFont(FontManager.getRunescapeSmallFont());
                tradeHistoryPanel.add(empty);
            }
            else
            {
                // Aggregate per item: total profit, flip count, latest sell time
                Map<Integer, ItemAggregate> byItem = new LinkedHashMap<>();
                for (CompletedTrade trade : trades)
                {
                    ItemAggregate agg = byItem.computeIfAbsent(trade.getItemId(),
                        k -> new ItemAggregate(trade.getItemName()));
                    agg.profit += trade.getProfit();
                    agg.flips++;
                    agg.lastSellTimestamp = Math.max(agg.lastSellTimestamp, trade.getSellTimestamp());
                }

                byItem.values().stream()
                    .sorted((a, b) -> Long.compare(b.lastSellTimestamp, a.lastSellTimestamp))
                    .limit(20)
                    .forEach(agg -> {
                        tradeHistoryPanel.add(createAggregateRow(agg));
                        tradeHistoryPanel.add(Box.createVerticalStrut(2));
                    });
            }

            tradeHistoryPanel.revalidate();
            tradeHistoryPanel.repaint();
        });
    }

    private static class ItemAggregate
    {
        final String itemName;
        long profit;
        int flips;
        long lastSellTimestamp;

        ItemAggregate(String itemName)
        {
            this.itemName = itemName;
        }
    }

    private JPanel createAggregateRow(ItemAggregate agg)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(new Color(40, 40, 40));
        row.setBorder(new EmptyBorder(4, 5, 4, 5));

        JLabel nameLabel = new JLabel(agg.itemName + " (" + agg.flips + ")");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(nameLabel, BorderLayout.WEST);

        Color profitColor = agg.profit >= 0 ? new Color(0, 200, 83) : new Color(255, 80, 80);
        String sign = agg.profit >= 0 ? "+" : "";
        JLabel profitLabel = new JLabel(sign + formatGp(agg.profit));
        profitLabel.setForeground(profitColor);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(profitLabel, BorderLayout.EAST);

        return row;
    }
```

Delete the old `createTradeRow` method (no longer called). Add imports:

```java
import java.util.LinkedHashMap;
import java.util.Map;
```

- [ ] **Step 2: Panel — dashboard URL**

In `buildActionsPanel()`:

```java
        openWebButton.addActionListener(e -> LinkBrowser.browse("https://osrsge.io/"));
```

- [ ] **Step 3: Config — osrsge.io text, sync off by default**

In `GETrackerConfig.java`: replace every `osrsge.lovable.app` string with `osrsge.io` (section description + two config item descriptions), and change `syncEnabled()` default:

```java
    default boolean syncEnabled()
    {
        return false;
    }
```

- [ ] **Step 4: API client — base URL**

In `OsrsGeApiClient.java`:

```java
    private static final String BASE_URL = "https://osrsge.io/api";
```

Update the two javadoc comments mentioning lovable.app to say osrsge.io.

- [ ] **Step 4b: Overlay — show only while GE interface is open (spec 5)**

In `GETrackerOverlay.java`, at the top of `render(...)` extend the guard. The GE interface is widget group 465:

```java
        if (!config.showOverlay() || client.getWidget(465, 0) == null)
        {
            return null;
        }
```

Add import if missing: none needed (`client` already injected).

- [ ] **Step 5: Plugin metadata**

In `runelite-plugin.properties` and the `@PluginDescriptor` in `GETrackerPlugin.java`, replace `osrsge.lovable.app` with `osrsge.io`.

- [ ] **Step 6: Compile + tests**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: tax display in panel, point sync at osrsge.io, sync off by default"
```

---

### Task 6: Live verification (playtest)

**Files:** none (manual test)

- [ ] **Step 1: Clear stale data from naive-matching era**

```bash
rm -f ~/.runelite/osrsge-tracker/*.json
```

(Old trades have wrong profit math; clean slate for verification.)

- [ ] **Step 2: Launch client**

Run: `./gradlew runClient` (background). User logs in (Jagex credentials auto-load).

- [ ] **Step 3: Repro the user's original trade**

User instant-buys 1 gold ore (~145 gp) and instant-sells it. Expected in log:
`Flip completed: Gold ore x1 profit=-2 (tax=2)` (exact numbers depend on prices; profit must equal sell − tax − buy).

- [ ] **Step 4: Verify panel + persistence**

- Panel: Session Profit negative red value, Tax Paid shows tax, Flips = 1.
- `cat ~/.runelite/osrsge-tracker/*_trades.json` shows `"tax":2` and matching profit.
- `cat ~/.runelite/osrsge-tracker/*_ledger.json` exists (empty array after full match).

- [ ] **Step 5: Relog dedupe check**

User logs out and back in at the GE. Expected: no new `Flip completed` line, flip count unchanged (seeded deduper drops replayed BOUGHT/SOLD events).

- [ ] **Step 6: Commit any fixes found, then final commit**

```bash
git add -A
git commit -m "chore: v1 verified live - tax, FIFO matching, dedupe"
```
