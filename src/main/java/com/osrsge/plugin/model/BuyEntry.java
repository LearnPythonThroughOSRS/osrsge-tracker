package com.osrsge.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One completed buy sitting in the FIFO ledger, partially or fully unmatched. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyEntry
{
    private int itemId;
    private int remaining;
    private int price;      // average gp paid per item
    private long timestamp;
}
