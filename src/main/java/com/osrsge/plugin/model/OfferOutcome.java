package com.osrsge.plugin.model;

import lombok.Builder;
import lombok.Data;

/**
 * Terminal result of one GE offer, recorded whether or not it filled.
 * Feeds fill-rate statistics: which items buy/sell fully, partially,
 * or not at all.
 */
@Data
@Builder
public class OfferOutcome
{
    private int itemId;
    private String itemName;
    private boolean isBuy;
    private int price;             // offered price per item
    private int totalQuantity;     // requested
    private int quantityFilled;    // actually traded (0 = never filled)
    private long placedTimestamp;  // when the offer was placed
    private long endedTimestamp;   // when it completed or was cancelled
    private boolean cancelled;     // true = player cancelled before full fill
    private boolean synced;
}
