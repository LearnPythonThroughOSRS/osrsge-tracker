package com.osrsge.plugin.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.OfferOutcome;
import com.osrsge.plugin.model.TradeOffer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class OsrsGeApiClient
{
    private static final String WIKI_API_URL = "https://prices.runescape.wiki/api/v1/osrs";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private String baseUrl = "";
    private String apiKey;
    private String playerName;

    public OsrsGeApiClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public void setPlayerName(String playerName)
    {
        this.playerName = playerName;
    }

    /**
     * Sync completed trades to osrsge.io
     */
    public CompletableFuture<Boolean> syncTrades(List<CompletedTrade> trades)
    {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isEmpty() || baseUrl.isEmpty())
            {
                log.debug("Sync not configured (missing API key or base URL), skipping");
                return false;
            }

            try
            {
                SyncPayload payload = new SyncPayload();
                payload.playerName = playerName;
                payload.trades = trades;

                String json = gson.toJson(payload);
                RequestBody body = RequestBody.create(JSON, json);

                Request request = new Request.Builder()
                    .url(baseUrl + "/trades-sync")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "osrsge-runelite-plugin")
                    .post(body)
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Successfully synced {} trades", trades.size());
                        return true;
                    }
                    else
                    {
                        log.warn("Failed to sync trades: {} {}", response.code(), response.message());
                        return false;
                    }
                }
            }
            catch (IOException e)
            {
                log.error("Error syncing trades", e);
                return false;
            }
        });
    }

    /**
     * Sync active offers to osrsge.io
     */
    public CompletableFuture<Boolean> syncActiveOffers(List<TradeOffer> offers)
    {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isEmpty() || baseUrl.isEmpty()) return false;

            try
            {
                OffersPayload payload = new OffersPayload();
                payload.playerName = playerName;
                payload.offers = offers;

                String json = gson.toJson(payload);
                RequestBody body = RequestBody.create(JSON, json);

                Request request = new Request.Builder()
                    .url(baseUrl + "/offers-sync")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "osrsge-runelite-plugin")
                    .post(body)
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    return response.isSuccessful();
                }
            }
            catch (IOException e)
            {
                log.error("Error syncing offers", e);
                return false;
            }
        });
    }

    /**
     * Sync offer outcomes (fill-rate data) to osrsge.io
     */
    public CompletableFuture<Boolean> syncOutcomes(List<OfferOutcome> outcomes)
    {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isEmpty() || baseUrl.isEmpty()) return false;

            try
            {
                OutcomesPayload payload = new OutcomesPayload();
                payload.playerName = playerName;
                payload.outcomes = outcomes;

                String json = gson.toJson(payload);
                RequestBody body = RequestBody.create(JSON, json);

                Request request = new Request.Builder()
                    .url(baseUrl + "/outcomes-sync")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "osrsge-runelite-plugin")
                    .post(body)
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (!response.isSuccessful())
                    {
                        log.warn("Failed to sync outcomes: {}", response.code());
                    }
                    return response.isSuccessful();
                }
            }
            catch (IOException e)
            {
                log.error("Error syncing outcomes", e);
                return false;
            }
        });
    }

    /**
     * Fetch latest prices from OSRS Wiki API for margin checking.
     */
    public CompletableFuture<Map<String, WikiPrice>> fetchLatestPrices()
    {
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                Request request = new Request.Builder()
                    .url(WIKI_API_URL + "/latest")
                    .header("User-Agent", "osrsge-runelite-plugin - GE Tracker")
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        String responseBody = response.body().string();
                        Type type = new TypeToken<WikiPriceResponse>(){}.getType();
                        WikiPriceResponse priceResponse = gson.fromJson(responseBody, type);
                        return priceResponse.data;
                    }
                }
            }
            catch (IOException e)
            {
                log.error("Error fetching wiki prices", e);
            }
            return null;
        });
    }

    // Payload classes for JSON serialization
    private static class SyncPayload
    {
        String playerName;
        List<CompletedTrade> trades;
    }

    private static class OffersPayload
    {
        String playerName;
        List<TradeOffer> offers;
    }

    private static class OutcomesPayload
    {
        String playerName;
        List<OfferOutcome> outcomes;
    }

    public static class WikiPriceResponse
    {
        public Map<String, WikiPrice> data;
    }

    public static class WikiPrice
    {
        public int high;
        public long highTime;
        public int low;
        public long lowTime;

        public int getMargin()
        {
            return high - low;
        }
    }
}
