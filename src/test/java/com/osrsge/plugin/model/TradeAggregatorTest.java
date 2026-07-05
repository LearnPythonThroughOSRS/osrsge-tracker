package com.osrsge.plugin.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TradeAggregatorTest
{
    private static CompletedTrade trade(int itemId, String name, int buy, int sell, int qty, long tax, long sellTs)
    {
        return CompletedTrade.builder()
            .itemId(itemId)
            .itemName(name)
            .buyPrice(buy)
            .sellPrice(sell)
            .quantity(qty)
            .tax(tax)
            .profit((long) (sell) * qty - tax - (long) buy * qty)
            .buyTimestamp(sellTs - 1000)
            .sellTimestamp(sellTs)
            .synced(false)
            .build();
    }

    @Test
    public void aggregatesPerItem()
    {
        // User's real session: two gold ore flips, -5 each
        List<CompletedTrade> trades = Arrays.asList(
            trade(444, "Gold ore", 145, 142, 1, 2, 1000L),
            trade(444, "Gold ore", 150, 147, 1, 2, 2000L));

        List<TradeAggregator.ItemStats> items = TradeAggregator.aggregate(trades, 0L);

        assertEquals(1, items.size());
        TradeAggregator.ItemStats gold = items.get(0);
        assertEquals("Gold ore", gold.getItemName());
        assertEquals(-10, gold.getTotalProfit());
        assertEquals(2, gold.getFlips());
        assertEquals(2, gold.getQuantity());
        assertEquals(4, gold.getTotalTax());
        assertEquals(-5, gold.getAvgProfitEach());
        // avg buy: (145+150)/2 = 147 (floor of 147.5 by total/qty: 295/2=147)
        assertEquals(147, gold.getAvgBuyPrice());
        assertEquals(144, gold.getAvgSellPrice()); // 289/2
        // ROI: -10 / 295 = -3.39%
        assertEquals(-3.39, gold.getRoi(), 0.01);
    }

    @Test
    public void cutoffFiltersOldTrades()
    {
        List<CompletedTrade> trades = Arrays.asList(
            trade(444, "Gold ore", 145, 142, 1, 2, 1000L),
            trade(444, "Gold ore", 150, 147, 1, 2, 5000L));

        List<TradeAggregator.ItemStats> items = TradeAggregator.aggregate(trades, 2000L);

        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getFlips());
        assertEquals(-5, items.get(0).getTotalProfit());
    }

    @Test
    public void sortsByMostRecentSell()
    {
        List<CompletedTrade> trades = Arrays.asList(
            trade(444, "Gold ore", 145, 142, 1, 2, 1000L),
            trade(2, "Cannonball", 180, 190, 100, 300, 9000L));

        List<TradeAggregator.ItemStats> items = TradeAggregator.aggregate(trades, 0L);

        assertEquals(2, items.size());
        assertEquals("Cannonball", items.get(0).getItemName());
        assertEquals("Gold ore", items.get(1).getItemName());
    }

    @Test
    public void totalsAcrossItems()
    {
        List<CompletedTrade> trades = Arrays.asList(
            trade(444, "Gold ore", 145, 142, 1, 2, 1000L),
            trade(2, "Cannonball", 180, 190, 100, 300, 9000L));

        TradeAggregator.Totals totals = TradeAggregator.totals(trades, 0L);

        // cannonball profit: 19000 - 300 - 18000 = 700; gold: -5
        assertEquals(695, totals.getProfit());
        assertEquals(302, totals.getTax());
        assertEquals(2, totals.getFlips());
    }
}
