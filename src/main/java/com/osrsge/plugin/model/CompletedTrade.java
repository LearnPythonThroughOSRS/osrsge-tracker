package com.osrsge.plugin.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompletedTrade
{
    private int itemId;
    private String itemName;
    private int buyPrice;
    private int sellPrice;
    private int quantity;
    private long profit;
    private long tax;
    private long buyTimestamp;
    private long sellTimestamp;
    private boolean synced;

    public int getMargin()
    {
        return sellPrice - buyPrice;
    }

    public double getRoi()
    {
        if (buyPrice == 0) return 0;
        return ((double) getMargin() / buyPrice) * 100;
    }
}
