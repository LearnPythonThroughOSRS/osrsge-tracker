package com.osrsge.plugin.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.TradeOffer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
public class TradeStorage
{
    private static final File PLUGIN_DIR = new File(RUNELITE_DIR, "osrsge-tracker");
    private final Gson gson;

    public TradeStorage(Gson gson)
    {
        this.gson = gson;
        PLUGIN_DIR.mkdirs();
    }

    private File getTradesFile(String playerName)
    {
        return new File(PLUGIN_DIR, playerName.toLowerCase().replace(" ", "_") + "_trades.json");
    }

    private File getOffersFile(String playerName)
    {
        return new File(PLUGIN_DIR, playerName.toLowerCase().replace(" ", "_") + "_offers.json");
    }

    public List<CompletedTrade> loadTrades(String playerName)
    {
        File file = getTradesFile(playerName);
        if (!file.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(file))
        {
            Type type = new TypeToken<List<CompletedTrade>>(){}.getType();
            List<CompletedTrade> trades = gson.fromJson(reader, type);
            return trades != null ? trades : new ArrayList<>();
        }
        catch (IOException e)
        {
            log.error("Failed to load trades for {}", playerName, e);
            return new ArrayList<>();
        }
    }

    public void saveTrades(String playerName, List<CompletedTrade> trades)
    {
        File file = getTradesFile(playerName);
        try (FileWriter writer = new FileWriter(file))
        {
            gson.toJson(trades, writer);
        }
        catch (IOException e)
        {
            log.error("Failed to save trades for {}", playerName, e);
        }
    }

    public List<TradeOffer> loadOffers(String playerName)
    {
        File file = getOffersFile(playerName);
        if (!file.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(file))
        {
            Type type = new TypeToken<List<TradeOffer>>(){}.getType();
            List<TradeOffer> offers = gson.fromJson(reader, type);
            return offers != null ? offers : new ArrayList<>();
        }
        catch (IOException e)
        {
            log.error("Failed to load offers for {}", playerName, e);
            return new ArrayList<>();
        }
    }

    public void saveOffers(String playerName, List<TradeOffer> offers)
    {
        File file = getOffersFile(playerName);
        try (FileWriter writer = new FileWriter(file))
        {
            gson.toJson(offers, writer);
        }
        catch (IOException e)
        {
            log.error("Failed to save offers for {}", playerName, e);
        }
    }

    public List<CompletedTrade> getUnsyncedTrades(String playerName)
    {
        List<CompletedTrade> all = loadTrades(playerName);
        List<CompletedTrade> unsynced = new ArrayList<>();
        for (CompletedTrade trade : all)
        {
            if (!trade.isSynced())
            {
                unsynced.add(trade);
            }
        }
        return unsynced;
    }

    public void markTradesSynced(String playerName, List<CompletedTrade> syncedTrades)
    {
        List<CompletedTrade> all = loadTrades(playerName);
        for (CompletedTrade trade : all)
        {
            for (CompletedTrade synced : syncedTrades)
            {
                if (trade.getBuyTimestamp() == synced.getBuyTimestamp()
                    && trade.getSellTimestamp() == synced.getSellTimestamp()
                    && trade.getItemId() == synced.getItemId())
                {
                    trade.setSynced(true);
                }
            }
        }
        saveTrades(playerName, all);
    }
}
