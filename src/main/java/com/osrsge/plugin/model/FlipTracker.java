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
