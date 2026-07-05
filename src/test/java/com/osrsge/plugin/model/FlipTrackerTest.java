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
