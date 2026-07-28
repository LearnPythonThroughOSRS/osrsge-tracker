package com.osrsge.plugin.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.OfferOutcome;
import com.osrsge.plugin.model.TradeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pins the JSON field names the osrsge.io backend contract depends on. */
public class PayloadContractTest
{
    private final Gson gson = new Gson();

    @Test
    public void tradeSerializesContractFields()
    {
        CompletedTrade trade = CompletedTrade.builder()
            .itemId(444)
            .itemName("Gold ore")
            .buyPrice(145)
            .sellPrice(142)
            .quantity(1)
            .profit(-5)
            .tax(2)
            .buyTimestamp(1783265377587L)
            .sellTimestamp(1783265397987L)
            .synced(false)
            .build();

        JsonObject json = gson.toJsonTree(trade).getAsJsonObject();

        assertEquals(444, json.get("itemId").getAsInt());
        assertEquals("Gold ore", json.get("itemName").getAsString());
        assertEquals(145, json.get("buyPrice").getAsInt());
        assertEquals(142, json.get("sellPrice").getAsInt());
        assertEquals(1, json.get("quantity").getAsInt());
        assertEquals(-5, json.get("profit").getAsLong());
        assertEquals(2, json.get("tax").getAsLong());
        assertEquals(1783265377587L, json.get("buyTimestamp").getAsLong());
        assertEquals(1783265397987L, json.get("sellTimestamp").getAsLong());
    }

    @Test
    public void outcomeSerializesContractFields()
    {
        OfferOutcome outcome = OfferOutcome.builder()
            .itemId(444)
            .itemName("Gold ore")
            .isBuy(true)
            .price(145)
            .totalQuantity(100)
            .quantityFilled(37)
            .placedTimestamp(1783265377587L)
            .endedTimestamp(1783265397987L)
            .cancelled(true)
            .synced(false)
            .build();

        JsonObject json = gson.toJsonTree(outcome).getAsJsonObject();

        assertEquals(444, json.get("itemId").getAsInt());
        assertEquals("Gold ore", json.get("itemName").getAsString());
        assertTrue(json.get("isBuy").getAsBoolean());
        assertEquals(145, json.get("price").getAsInt());
        assertEquals(100, json.get("totalQuantity").getAsInt());
        assertEquals(37, json.get("quantityFilled").getAsInt());
        assertEquals(1783265377587L, json.get("placedTimestamp").getAsLong());
        assertEquals(1783265397987L, json.get("endedTimestamp").getAsLong());
        assertTrue(json.get("cancelled").getAsBoolean());
    }

    @Test
    public void offerSerializesContractFields()
    {
        TradeOffer offer = TradeOffer.builder()
            .slot(2)
            .itemId(444)
            .itemName("Gold ore")
            .price(145)
            .totalQuantity(100)
            .quantityFilled(40)
            .amountSpent(5800)
            .state(GrandExchangeOfferState.BUYING)
            .isBuy(true)
            .timestamp(1783265377587L)
            .build();

        JsonObject json = gson.toJsonTree(offer).getAsJsonObject();

        assertEquals(2, json.get("slot").getAsInt());
        assertEquals(444, json.get("itemId").getAsInt());
        assertEquals("Gold ore", json.get("itemName").getAsString());
        assertEquals(145, json.get("price").getAsInt());
        assertEquals(100, json.get("totalQuantity").getAsInt());
        assertEquals(40, json.get("quantityFilled").getAsInt());
        assertEquals(5800, json.get("amountSpent").getAsLong());
        assertEquals("BUYING", json.get("state").getAsString());
        assertTrue(json.get("isBuy").getAsBoolean());
        assertEquals(1783265377587L, json.get("timestamp").getAsLong());
    }
}
