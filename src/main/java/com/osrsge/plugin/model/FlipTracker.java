package com.osrsge.plugin.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks buy offers per item to match them with sell offers for profit calculation.
 */
@Data
public class FlipTracker
{
    // itemId -> most recent completed buy offer
    private final Map<Integer, TradeOffer> pendingBuys = new HashMap<>();

    public void recordBuyComplete(TradeOffer offer)
    {
        pendingBuys.put(offer.getItemId(), offer);
    }

    public TradeOffer getMatchingBuy(int itemId)
    {
        return pendingBuys.get(itemId);
    }

    public TradeOffer consumeMatchingBuy(int itemId)
    {
        return pendingBuys.remove(itemId);
    }

    public boolean hasPendingBuy(int itemId)
    {
        return pendingBuys.containsKey(itemId);
    }

    public void clear()
    {
        pendingBuys.clear();
    }
}
