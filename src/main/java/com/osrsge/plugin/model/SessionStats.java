package com.osrsge.plugin.model;

import lombok.Data;

import java.time.Instant;

@Data
public class SessionStats
{
    private final Instant sessionStart = Instant.now();
    private long totalProfit = 0;
    private long totalTax = 0;
    private int totalFlips = 0;
    private int totalBuys = 0;
    private int totalSells = 0;

    public void recordFlip(CompletedTrade trade)
    {
        totalProfit += trade.getProfit();
        totalTax += trade.getTax();
        totalFlips++;
    }

    public void recordBuy()
    {
        totalBuys++;
    }

    public void recordSell()
    {
        totalSells++;
    }

    public long getSessionDurationSeconds()
    {
        return Instant.now().getEpochSecond() - sessionStart.getEpochSecond();
    }

    public String getSessionDurationFormatted()
    {
        long seconds = getSessionDurationSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

}
