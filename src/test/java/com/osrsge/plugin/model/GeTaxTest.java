package com.osrsge.plugin.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeTaxTest
{
    @Test
    public void twoPercentRoundedDown()
    {
        // User's real trade: gold ore sold at 145 -> 2 gp tax
        assertEquals(2, GeTax.taxFor(444, 145));
        assertEquals(1, GeTax.taxFor(444, 50));
        assertEquals(20_000, GeTax.taxFor(444, 1_000_000));
    }

    @Test
    public void underFiftyGpIsFree()
    {
        assertEquals(0, GeTax.taxFor(444, 49));
        assertEquals(0, GeTax.taxFor(444, 1));
        assertEquals(0, GeTax.taxFor(444, 0));
    }

    @Test
    public void cappedAtFiveMillionPerItem()
    {
        assertEquals(5_000_000, GeTax.taxFor(444, 300_000_000));
    }

    @Test
    public void exemptItemsPayNothing()
    {
        assertEquals(0, GeTax.taxFor(GeTax.OLD_SCHOOL_BOND, 5_000_000));
        assertEquals(0, GeTax.taxFor(2347, 1_000)); // hammer
    }
}
