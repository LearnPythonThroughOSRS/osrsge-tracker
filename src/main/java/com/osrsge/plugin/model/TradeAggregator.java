package com.osrsge.plugin.model;

import lombok.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure aggregation of completed trades for panel display: per-item stats
 * and overall totals, filtered by a sell-timestamp cutoff (0 = all time).
 */
public final class TradeAggregator
{
    private TradeAggregator()
    {
    }

    @Value
    public static class ItemStats
    {
        int itemId;
        String itemName;
        long totalProfit;
        long totalTax;
        int flips;
        int quantity;
        long totalBuyCost;
        long totalSellValue;
        long lastSellTimestamp;

        public long getAvgProfitEach()
        {
            return quantity == 0 ? 0 : totalProfit / quantity;
        }

        public int getAvgBuyPrice()
        {
            return quantity == 0 ? 0 : (int) (totalBuyCost / quantity);
        }

        public int getAvgSellPrice()
        {
            return quantity == 0 ? 0 : (int) (totalSellValue / quantity);
        }

        public double getRoi()
        {
            return totalBuyCost == 0 ? 0 : ((double) totalProfit / totalBuyCost) * 100;
        }
    }

    @Value
    public static class Totals
    {
        long profit;
        long tax;
        int flips;
    }

    public static List<ItemStats> aggregate(List<CompletedTrade> trades, long cutoffTimestamp)
    {
        Map<Integer, Builder> byItem = new LinkedHashMap<>();
        for (CompletedTrade t : trades)
        {
            if (t.getSellTimestamp() < cutoffTimestamp)
            {
                continue;
            }
            Builder b = byItem.computeIfAbsent(t.getItemId(), k -> new Builder(t.getItemId(), t.getItemName()));
            b.profit += t.getProfit();
            b.tax += t.getTax();
            b.flips++;
            b.quantity += t.getQuantity();
            b.buyCost += (long) t.getBuyPrice() * t.getQuantity();
            b.sellValue += (long) t.getSellPrice() * t.getQuantity();
            b.lastSell = Math.max(b.lastSell, t.getSellTimestamp());
        }

        List<ItemStats> out = new ArrayList<>();
        for (Builder b : byItem.values())
        {
            out.add(new ItemStats(b.itemId, b.itemName, b.profit, b.tax, b.flips,
                b.quantity, b.buyCost, b.sellValue, b.lastSell));
        }
        out.sort((a, c) -> Long.compare(c.getLastSellTimestamp(), a.getLastSellTimestamp()));
        return out;
    }

    public static Totals totals(List<CompletedTrade> trades, long cutoffTimestamp)
    {
        long profit = 0;
        long tax = 0;
        int flips = 0;
        for (CompletedTrade t : trades)
        {
            if (t.getSellTimestamp() < cutoffTimestamp)
            {
                continue;
            }
            profit += t.getProfit();
            tax += t.getTax();
            flips++;
        }
        return new Totals(profit, tax, flips);
    }

    private static class Builder
    {
        final int itemId;
        final String itemName;
        long profit;
        long tax;
        int flips;
        int quantity;
        long buyCost;
        long sellValue;
        long lastSell;

        Builder(int itemId, String itemName)
        {
            this.itemId = itemId;
            this.itemName = itemName;
        }
    }
}
