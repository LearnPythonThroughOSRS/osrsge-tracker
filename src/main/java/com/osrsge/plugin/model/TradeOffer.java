package com.osrsge.plugin.model;

import lombok.Builder;
import lombok.Data;
import net.runelite.api.GrandExchangeOfferState;

@Data
@Builder
public class TradeOffer
{
    private int slot;
    private int itemId;
    private String itemName;
    private int price;
    private int totalQuantity;
    private int quantityFilled;
    private long amountSpent;
    private GrandExchangeOfferState state;
    private boolean isBuy;
    private long timestamp;

    public boolean isComplete()
    {
        return state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.SOLD
            || state == GrandExchangeOfferState.CANCELLED_BUY
            || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    public boolean isActive()
    {
        return state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.SELLING;
    }

    public int getAveragePrice()
    {
        if (quantityFilled == 0) return price;
        return (int) (amountSpent / quantityFilled);
    }
}
