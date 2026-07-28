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
    description = "Tracks Grand Exchange activity and syncs with osrsge.io",
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

    @Getter
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

    private final OfferEventDeduper deduper = new OfferEventDeduper();

    // slot -> last known offer state
    private final Map<Integer, TradeOffer> slotOffers = new ConcurrentHashMap<>();

    // All completed trades for this player
    @Getter
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
        apiClient.setBaseUrl(config.syncBaseUrl());
        apiClient.setApiKey(config.apiKey());
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
                apiClient.setBaseUrl(config.syncBaseUrl());

                // Load saved data
                allTrades = tradeStorage.loadTrades(playerName);
                flipTracker.restore(tradeStorage.loadLedger(playerName));
                for (TradeOffer saved : tradeStorage.loadOffers(playerName))
                {
                    deduper.seed(saved.getSlot(), saved.getState(),
                        saved.getQuantityFilled(), saved.getAmountSpent());
                    // restore slot state so placement timestamps survive relogs
                    slotOffers.put(saved.getSlot(), saved);
                }

                // Process any pending events
                for (GrandExchangeOfferChanged pending : pendingEvents)
                {
                    processOfferEvent(pending);
                }
                pendingEvents.clear();

                // connect immediately instead of waiting for the next timer tick
                syncExecutor.execute(this::performSync);

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
                deduper.clear();
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
            deduper.clearSlot(slot);
            return;
        }

        if (deduper.isDuplicate(slot, state, offer.getQuantitySold(), offer.getSpent()))
        {
            return;
        }

        String itemName = itemManager.getItemComposition(offer.getItemId()).getName();

        // carry the placement time across events for the same offer;
        // a new offer (different item or fresh slot) starts the clock now
        long now = System.currentTimeMillis();
        TradeOffer previous = slotOffers.get(slot);
        long placedTimestamp = (previous != null
            && previous.getItemId() == offer.getItemId()
            && previous.getPlacedTimestamp() > 0)
            ? previous.getPlacedTimestamp()
            : now;

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
            .timestamp(now)
            .placedTimestamp(placedTimestamp)
            .build();

        slotOffers.put(slot, tradeOffer);

        if ((state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY)
            && tradeOffer.getQuantityFilled() > 0)
        {
            // buys start at offer placement, not observed fill — offers that
            // fill while logged out keep their true hold time
            flipTracker.recordBuy(tradeOffer.getItemId(), tradeOffer.getQuantityFilled(),
                tradeOffer.getAveragePrice(), tradeOffer.getPlacedTimestamp());
            sessionStats.recordBuy();
            log.debug("Buy completed: {} x{} @ {}", itemName,
                tradeOffer.getQuantityFilled(), tradeOffer.getAveragePrice());
        }

        if ((state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL)
            && tradeOffer.getQuantityFilled() > 0)
        {
            sessionStats.recordSell();
            CompletedTrade trade = flipTracker.matchSell(tradeOffer.getItemId(), itemName,
                tradeOffer.getQuantityFilled(), tradeOffer.getAveragePrice(), tradeOffer.getTimestamp());

            if (trade != null)
            {
                allTrades.add(trade);
                sessionStats.recordFlip(trade);
                log.info("Flip completed: {} x{} profit={} (tax={})",
                    itemName, trade.getQuantity(), trade.getProfit(), trade.getTax());
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
        if ("syncBaseUrl".equals(event.getKey()))
        {
            apiClient.setBaseUrl(config.syncBaseUrl());
        }
        if ("syncIntervalSeconds".equals(event.getKey()) || "syncEnabled".equals(event.getKey()))
        {
            startSyncTask();
        }
        // immediate feedback when sync settings change
        if ("apiKey".equals(event.getKey()) || "syncBaseUrl".equals(event.getKey())
            || "syncEnabled".equals(event.getKey()))
        {
            syncExecutor.execute(this::performSync);
        }
    }

    private void startSyncTask()
    {
        if (syncTask != null) syncTask.cancel(false);

        if (!config.syncEnabled()) return;

        // short initial delay so the panel shows Synced quickly after login/config
        syncTask = syncExecutor.scheduleAtFixedRate(this::performSync,
            2,
            config.syncIntervalSeconds(),
            TimeUnit.SECONDS);
    }

    private void performSync()
    {
        if (playerName == null || !config.syncEnabled()) return;

        try
        {
            // Sync unsynced trades from the in-memory list (the source of truth);
            // marking the same objects synced keeps the flag across saveState overwrites
            List<CompletedTrade> unsynced = allTrades.stream()
                .filter(t -> !t.isSynced())
                .collect(Collectors.toList());
            if (!unsynced.isEmpty())
            {
                apiClient.syncTrades(unsynced).thenAccept(success -> {
                    if (success)
                    {
                        syncConnected = true;
                        unsynced.forEach(t -> t.setSynced(true));
                        saveState();
                        log.info("Synced {} trades", unsynced.size());
                    }
                    else
                    {
                        syncConnected = false;
                    }
                    updatePanel();
                });
            }

            // Always send the offers snapshot: empty list clears stale server
            // rows and doubles as a connection heartbeat for the Synced label
            apiClient.syncActiveOffers(getActiveOffers()).thenAccept(success -> {
                syncConnected = success;
                updatePanel();
            });
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
        tradeStorage.saveLedger(playerName, flipTracker.snapshot());

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
                panel.updateTradeHistory(allTrades);
            });
        }
    }
}
