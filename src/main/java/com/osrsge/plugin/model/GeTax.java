package com.osrsge.plugin.model;

import java.util.Set;

/**
 * Grand Exchange sell tax: 2% per item rounded down, capped at 5m gp
 * per item. Items under 50 gp and exempt items pay nothing.
 */
public final class GeTax
{
    public static final int OLD_SCHOOL_BOND = 13190;
    public static final long TAX_CAP_PER_ITEM = 5_000_000L;

    // Bond + low-level skilling tools are tax-exempt
    private static final Set<Integer> EXEMPT_ITEMS = Set.of(
        OLD_SCHOOL_BOND,
        1755,  // Chisel
        5325,  // Gardening trowel
        1785,  // Glassblowing pipe
        2347,  // Hammer
        1733,  // Needle
        233,   // Pestle and mortar
        5341,  // Rake
        8794,  // Saw
        5329,  // Secateurs
        5343,  // Seed dibber
        1735,  // Shears
        952,   // Spade
        5331   // Watering can (0)
    );

    private GeTax()
    {
    }

    /** Tax in gp charged on ONE item sold at pricePerItem. */
    public static long taxFor(int itemId, long pricePerItem)
    {
        if (EXEMPT_ITEMS.contains(itemId))
        {
            return 0;
        }
        return Math.min(pricePerItem / 50, TAX_CAP_PER_ITEM);
    }
}
