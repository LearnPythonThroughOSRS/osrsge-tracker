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
