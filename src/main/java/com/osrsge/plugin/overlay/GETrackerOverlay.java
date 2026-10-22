package com.osrsge.plugin.overlay;

import com.osrsge.plugin.GETrackerConfig;
import com.osrsge.plugin.GETrackerPlugin;
import com.osrsge.plugin.model.SessionStats;
import com.osrsge.plugin.model.TradeOffer;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class GETrackerOverlay extends OverlayPanel
{
    private final Client client;
    private final GETrackerPlugin plugin;
    private final GETrackerConfig config;

    @Inject
    public GETrackerOverlay(Client client, GETrackerPlugin plugin, GETrackerConfig config)
    {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
        // wider than the 129px default: long labels and values wrap and
        // overlap at the default width
        panelComponent.setPreferredSize(new Dimension(180, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Widget group 465 is the Grand Exchange interface
        if (!config.showOverlay() || client.getWidget(465, 0) == null)
        {
            return null;
        }

        SessionStats stats = plugin.getSessionStats();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("GE Tracker")
            .color(new Color(0, 200, 83))
            .build());

        if (config.showSessionProfit())
        {
            long profit = stats.getTotalProfit();
            Color profitColor = profit >= 0 ? new Color(0, 200, 83) : new Color(255, 80, 80);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(formatGp(profit))
                .rightColor(profitColor)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Flips:")
                .right(String.valueOf(stats.getTotalFlips()))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Time:")
                .right(stats.getSessionDurationFormatted())
                .build());
        }

        if (config.showActiveFlips())
        {
            List<TradeOffer> activeOffers = plugin.getActiveOffers();
            if (!activeOffers.isEmpty())
            {
                panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Active Offers (" + activeOffers.size() + ")")
                    .color(Color.YELLOW)
                    .build());

                for (TradeOffer offer : activeOffers)
                {
                    String action = offer.isBuy() ? "BUY" : "SELL";
                    String progress = formatQuantity(offer.getQuantityFilled())
                        + "/" + formatQuantity(offer.getTotalQuantity());
                    Color actionColor = offer.isBuy() ? new Color(100, 180, 255) : new Color(255, 180, 100);

                    panelComponent.getChildren().add(LineComponent.builder()
                        .left(action + " " + truncate(offer.getItemName(), 12))
                        .leftColor(actionColor)
                        .right(progress)
                        .build());
                }
            }
        }

        // Sync status indicator
        String syncStatus = plugin.isSyncConnected() ? "Connected" : "Offline";
        Color syncColor = plugin.isSyncConnected() ? new Color(0, 200, 83) : Color.GRAY;
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Sync:")
            .right(syncStatus)
            .rightColor(syncColor)
            .build());

        return super.render(graphics);
    }

    /** Keeps long item names from wrapping onto the value beside them. */
    private String truncate(String name, int maxChars)
    {
        if (name == null || name.length() <= maxChars)
        {
            return name;
        }
        return name.substring(0, maxChars - 1) + "..";
    }

    /** Compact quantities so fill progress fits on one line. */
    private String formatQuantity(int quantity)
    {
        if (quantity >= 1_000_000)
        {
            return String.format("%.1fM", quantity / 1_000_000.0);
        }
        if (quantity >= 10_000)
        {
            return String.format("%.0fK", quantity / 1_000.0);
        }
        if (quantity >= 1_000)
        {
            return String.format("%.1fK", quantity / 1_000.0);
        }
        return String.valueOf(quantity);
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000_000)
        {
            return String.format("%.1fB gp", amount / 1_000_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000_000)
        {
            return String.format("%.1fM gp", amount / 1_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000)
        {
            return String.format("%.1fK gp", amount / 1_000.0);
        }
        return amount + " gp";
    }
}
