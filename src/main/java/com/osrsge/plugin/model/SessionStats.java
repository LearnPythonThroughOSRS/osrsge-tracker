package com.osrsge.plugin.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class SessionStats
{
    private final Instant sessionStart = Instant.now();
    private long totalProfit = 0;
    private int totalFlips = 0;
    private int totalBuys = 0;
    private int totalSells = 0;
    private final List<CompletedTrade> completedTrades = new ArrayList<>();

    public void recordFlip(CompletedTrade trade)
    {
        completedTrades.add(trade);
        totalProfit += trade.getProfit();
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

    public long getProfitPerHour()
    {
        long seconds = getSessionDurationSeconds();
        if (seconds == 0) return 0;
        return (totalProfit * 3600) / seconds;
    }

    public void reset()
    {
        totalProfit = 0;
        totalFlips = 0;
        totalBuys = 0;
        totalSells = 0;
        completedTrades.clear();
    }
}
