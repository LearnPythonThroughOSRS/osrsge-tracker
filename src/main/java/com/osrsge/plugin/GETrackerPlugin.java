package com.osrsge.plugin;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.osrsge.plugin.api.OsrsGeApiClient;
import com.osrsge.plugin.db.TradeStorage;
import com.osrsge.plugin.model.*;
import com.osrsge.plugin.overlay.GETrackerOverlay;
import com.osrsge.plugin.ui.GETrackerPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
    name = "OSRS GE Tracker",
    description = "Tracks Grand Exchange activity and syncs with osrsge.lovable.app",
    tags = {"grand exchange", "ge", "flipping", "trading", "profit"}
)
public class GETrackerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private GETrackerConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    private GETrackerOverlay overlay;
    private GETrackerPanel panel;
    private NavigationButton navButton;
    private OsrsGeApiClient apiClient;
    private TradeStorage tradeStorage;

    @Getter
    private SessionStats sessionStats = new SessionStats();

    private FlipTracker flipTracker = new FlipTracker();

    // slot -> last known offer state
    private final Map<Integer, TradeOffer> slotOffers = new ConcurrentHashMap<>();

    // All completed trades for this player
    private List<CompletedTrade> allTrades = new ArrayList<>();

    private ScheduledExecutorService syncExecutor;
    private ScheduledFuture<?> syncTask;

    @Getter
    private boolean syncConnected = false;

    private String playerName;
    private boolean loggedIn = false;

    // Queue for events received before login completes
    private final List<GrandExchangeOfferChanged> pendingEvents = new ArrayList<>();

    @Provides
    GETrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GETrackerConfig.class);
    }

    @Override
    protected void startUp()
    {
        apiClient = new OsrsGeApiClient(okHttpClient, gson);
        tradeStorage = new TradeStorage(gson);

        overlay = new GETrackerOverlay(client, this, config);
        overlayManager.add(overlay);

        panel = new GETrackerPanel(this);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/ge_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("GE Tracker")
            .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        syncExecutor = Executors.newSingleThreadScheduledExecutor();
        startSyncTask();

        log.info("OSRS GE Tracker started");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);

        if (syncTask != null) syncTask.cancel(true);
        if (syncExecutor != null) syncExecutor.shutdownNow();

        saveState();

        log.info("OSRS GE Tracker stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            // Player object appears some frames after LOGGED_IN; retry until it exists
            clientThread.invokeLater(() -> {
                Player localPlayer = client.getLocalPlayer();
                if (localPlayer == null || localPlayer.getName() == null)
                {
                    return false;
                }
                playerName = localPlayer.getName();
                loggedIn = true;

                apiClient.setPlayerName(playerName);
                apiClient.setApiKey(config.apiKey());

                // Load saved data
                allTrades = tradeStorage.loadTrades(playerName);

                // Process any pending events
                for (GrandExchangeOfferChanged pending : pendingEvents)
                {
                    processOfferEvent(pending);
                }
                pendingEvents.clear();

                updatePanel();
                return true;
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (loggedIn)
            {
                saveState();
                loggedIn = false;
                playerName = null;
                slotOffers.clear();
                flipTracker.clear();
            }
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (!loggedIn || playerName == null)
        {
            pendingEvents.add(event);
            return;
        }

        processOfferEvent(event);
    }

    private void processOfferEvent(GrandExchangeOfferChanged event)
    {
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();
        GrandExchangeOfferState state = offer.getState();

        if (state == GrandExchangeOfferState.EMPTY)
        {
            slotOffers.remove(slot);
            return;
        }

        String itemName = itemManager.getItemComposition(offer.getItemId()).getName();

        TradeOffer tradeOffer = TradeOffer.builder()
            .slot(slot)
            .itemId(offer.getItemId())
            .itemName(itemName)
            .price(offer.getPrice())
            .totalQuantity(offer.getTotalQuantity())
            .quantityFilled(offer.getQuantitySold())
            .amountSpent(offer.getSpent())
            .state(state)
            .isBuy(state == GrandExchangeOfferState.BUYING
                || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY)
            .timestamp(System.currentTimeMillis())
            .build();

        TradeOffer previous = slotOffers.get(slot);
        slotOffers.put(slot, tradeOffer);

        // Handle completed buy
        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY)
        {
            if (tradeOffer.getQuantityFilled() > 0)
            {
                flipTracker.recordBuyComplete(tradeOffer);
                sessionStats.recordBuy();
                log.debug("Buy completed: {} x{} @ {}", itemName, tradeOffer.getQuantityFilled(), tradeOffer.getAveragePrice());
            }
        }

        // Handle completed sell - try to match with a buy for flip detection
        if (state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL)
        {
            if (tradeOffer.getQuantityFilled() > 0)
            {
                sessionStats.recordSell();
                TradeOffer matchingBuy = flipTracker.consumeMatchingBuy(tradeOffer.getItemId());

                if (matchingBuy != null)
                {
                    int quantity = Math.min(matchingBuy.getQuantityFilled(), tradeOffer.getQuantityFilled());
                    long profit = ((long) tradeOffer.getAveragePrice() - matchingBuy.getAveragePrice()) * quantity;

                    CompletedTrade trade = CompletedTrade.builder()
                        .itemId(tradeOffer.getItemId())
                        .itemName(itemName)
                        .buyPrice(matchingBuy.getAveragePrice())
                        .sellPrice(tradeOffer.getAveragePrice())
                        .quantity(quantity)
                        .profit(profit)
                        .buyTimestamp(matchingBuy.getTimestamp())
                        .sellTimestamp(tradeOffer.getTimestamp())
                        .synced(false)
                        .build();

                    allTrades.add(trade);
                    sessionStats.recordFlip(trade);

                    log.info("Flip completed: {} x{} profit={}", itemName, quantity, profit);
                }
            }
        }

        updatePanel();
        saveState();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"osrsge-tracker".equals(event.getGroup())) return;

        if ("apiKey".equals(event.getKey()))
        {
            apiClient.setApiKey(config.apiKey());
        }
        if ("syncIntervalSeconds".equals(event.getKey()) || "syncEnabled".equals(event.getKey()))
        {
            startSyncTask();
        }
    }

    private void startSyncTask()
    {
        if (syncTask != null) syncTask.cancel(false);

        if (!config.syncEnabled()) return;

        syncTask = syncExecutor.scheduleAtFixedRate(this::performSync,
            config.syncIntervalSeconds(),
            config.syncIntervalSeconds(),
            TimeUnit.SECONDS);
    }

    private void performSync()
    {
        if (playerName == null || !config.syncEnabled()) return;

        try
        {
            // Sync unsynced trades
            List<CompletedTrade> unsynced = tradeStorage.getUnsyncedTrades(playerName);
            if (!unsynced.isEmpty())
            {
                apiClient.syncTrades(unsynced).thenAccept(success -> {
                    if (success)
                    {
                        syncConnected = true;
                        tradeStorage.markTradesSynced(playerName, unsynced);
                        log.debug("Synced {} trades", unsynced.size());
                    }
                    else
                    {
                        syncConnected = false;
                    }
                    updatePanel();
                });
            }

            // Sync active offers
            List<TradeOffer> active = getActiveOffers();
            if (!active.isEmpty())
            {
                apiClient.syncActiveOffers(active).thenAccept(success -> {
                    syncConnected = success;
                    updatePanel();
                });
            }
        }
        catch (Exception e)
        {
            syncConnected = false;
            log.error("Sync error", e);
        }
    }

    public List<TradeOffer> getActiveOffers()
    {
        return slotOffers.values().stream()
            .filter(TradeOffer::isActive)
            .collect(Collectors.toList());
    }

    public void resetSession()
    {
        sessionStats = new SessionStats();
        updatePanel();
    }

    private void saveState()
    {
        if (playerName == null) return;
        tradeStorage.saveTrades(playerName, allTrades);

        List<TradeOffer> offers = new ArrayList<>(slotOffers.values());
        tradeStorage.saveOffers(playerName, offers);
    }

    private void updatePanel()
    {
        if (panel != null)
        {
            SwingUtilities.invokeLater(() -> {
                panel.updateStats();
                panel.updateActiveOffers(getActiveOffers());
                panel.updateTradeHistory(sessionStats.getCompletedTrades());
            });
        }
    }
}
