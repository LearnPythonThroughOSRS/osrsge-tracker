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
