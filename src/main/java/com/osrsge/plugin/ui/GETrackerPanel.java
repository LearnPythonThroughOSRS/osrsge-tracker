package com.osrsge.plugin.ui;

import com.osrsge.plugin.GETrackerPlugin;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.SessionStats;
import com.osrsge.plugin.model.TradeOffer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GETrackerPanel extends PluginPanel
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final GETrackerPlugin plugin;

    // Stats labels
    private final JLabel profitLabel = new JLabel("0 gp");
    private final JLabel profitPerHourLabel = new JLabel("0 gp/hr");
    private final JLabel flipsLabel = new JLabel("0");
    private final JLabel sessionTimeLabel = new JLabel("00:00:00");
    private final JLabel syncStatusLabel = new JLabel("Offline");

    // Panels
    private final JPanel activeOffersPanel = new JPanel();
    private final JPanel tradeHistoryPanel = new JPanel();

    public GETrackerPanel(GETrackerPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildMainPanel(), BorderLayout.NORTH);
    }

    private JPanel buildMainPanel()
    {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);

        container.add(buildHeaderPanel());
        container.add(Box.createVerticalStrut(8));
        container.add(buildStatsPanel());
        container.add(Box.createVerticalStrut(8));
        container.add(buildActiveOffersSection());
        container.add(Box.createVerticalStrut(8));
        container.add(buildTradeHistorySection());
        container.add(Box.createVerticalStrut(8));
        container.add(buildActionsPanel());

        return container;
    }

    private JPanel buildHeaderPanel()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("OSRS GE Tracker");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 200, 83));
        header.add(title, BorderLayout.WEST);

        syncStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        syncStatusLabel.setForeground(Color.GRAY);
        header.add(syncStatusLabel, BorderLayout.EAST);

        return header;
    }

    private JPanel buildStatsPanel()
    {
        JPanel stats = new JPanel(new GridLayout(4, 2, 5, 5));
        stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stats.setBorder(new EmptyBorder(10, 10, 10, 10));

        stats.add(createLabel("Session Profit:", Color.WHITE));
        profitLabel.setForeground(new Color(0, 200, 83));
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(profitLabel);

        stats.add(createLabel("GP/Hour:", Color.WHITE));
        profitPerHourLabel.setForeground(new Color(0, 200, 83));
        profitPerHourLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(profitPerHourLabel);

        stats.add(createLabel("Flips:", Color.WHITE));
        flipsLabel.setForeground(Color.WHITE);
        flipsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(flipsLabel);

        stats.add(createLabel("Session:", Color.WHITE));
        sessionTimeLabel.setForeground(Color.WHITE);
        sessionTimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(sessionTimeLabel);

        return stats;
    }

    private JPanel buildActiveOffersSection()
    {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel sectionTitle = new JLabel("Active Offers");
        sectionTitle.setForeground(Color.YELLOW);
        sectionTitle.setFont(FontManager.getRunescapeSmallFont());
        section.add(sectionTitle, BorderLayout.NORTH);

        activeOffersPanel.setLayout(new BoxLayout(activeOffersPanel, BoxLayout.Y_AXIS));
        activeOffersPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.add(activeOffersPanel, BorderLayout.CENTER);

        return section;
    }

    private JPanel buildTradeHistorySection()
    {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel sectionTitle = new JLabel("Recent Trades");
        sectionTitle.setForeground(new Color(100, 180, 255));
        sectionTitle.setFont(FontManager.getRunescapeSmallFont());
        section.add(sectionTitle, BorderLayout.NORTH);

        tradeHistoryPanel.setLayout(new BoxLayout(tradeHistoryPanel, BoxLayout.Y_AXIS));
        tradeHistoryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(tradeHistoryPanel);
        scrollPane.setPreferredSize(new Dimension(0, 200));
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setBorder(null);
        section.add(scrollPane, BorderLayout.CENTER);

        return section;
    }

    private JPanel buildActionsPanel()
    {
        JPanel actions = new JPanel(new GridLayout(2, 1, 5, 5));
        actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
        actions.setBorder(new EmptyBorder(5, 10, 10, 10));

        JButton openWebButton = new JButton("Open Dashboard");
        openWebButton.setFocusPainted(false);
        openWebButton.addActionListener(e -> LinkBrowser.browse("https://osrsge.lovable.app/"));
        actions.add(openWebButton);

        JButton resetButton = new JButton("Reset Session");
        resetButton.setFocusPainted(false);
        resetButton.addActionListener(e -> {
            plugin.resetSession();
            updateStats();
        });
        actions.add(resetButton);

        return actions;
    }

    public void updateStats()
    {
        SessionStats stats = plugin.getSessionStats();

        long profit = stats.getTotalProfit();
        Color profitColor = profit >= 0 ? new Color(0, 200, 83) : new Color(255, 80, 80);

        profitLabel.setText(formatGp(profit));
        profitLabel.setForeground(profitColor);

        profitPerHourLabel.setText(formatGp(stats.getProfitPerHour()) + "/hr");
        profitPerHourLabel.setForeground(profitColor);

        flipsLabel.setText(String.valueOf(stats.getTotalFlips()));
        sessionTimeLabel.setText(stats.getSessionDurationFormatted());

        boolean connected = plugin.isSyncConnected();
        syncStatusLabel.setText(connected ? "Synced" : "Offline");
        syncStatusLabel.setForeground(connected ? new Color(0, 200, 83) : Color.GRAY);
    }

    public void updateActiveOffers(List<TradeOffer> offers)
    {
        SwingUtilities.invokeLater(() -> {
            activeOffersPanel.removeAll();

            if (offers.isEmpty())
            {
                JLabel empty = new JLabel("No active offers");
                empty.setForeground(Color.GRAY);
                empty.setFont(FontManager.getRunescapeSmallFont());
                activeOffersPanel.add(empty);
            }
            else
            {
                for (TradeOffer offer : offers)
                {
                    activeOffersPanel.add(createOfferRow(offer));
                }
            }

            activeOffersPanel.revalidate();
            activeOffersPanel.repaint();
        });
    }

    public void updateTradeHistory(List<CompletedTrade> trades)
    {
        SwingUtilities.invokeLater(() -> {
            tradeHistoryPanel.removeAll();

            if (trades.isEmpty())
            {
                JLabel empty = new JLabel("No completed trades yet");
                empty.setForeground(Color.GRAY);
                empty.setFont(FontManager.getRunescapeSmallFont());
                tradeHistoryPanel.add(empty);
            }
            else
            {
                // Show most recent 20 trades
                int start = Math.max(0, trades.size() - 20);
                for (int i = trades.size() - 1; i >= start; i--)
                {
                    tradeHistoryPanel.add(createTradeRow(trades.get(i)));
                    tradeHistoryPanel.add(Box.createVerticalStrut(2));
                }
            }

            tradeHistoryPanel.revalidate();
            tradeHistoryPanel.repaint();
        });
    }

    private JPanel createOfferRow(TradeOffer offer)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));

        String action = offer.isBuy() ? "BUY" : "SELL";
        Color actionColor = offer.isBuy() ? new Color(100, 180, 255) : new Color(255, 180, 100);

        JLabel nameLabel = new JLabel(action + " " + offer.getItemName());
        nameLabel.setForeground(actionColor);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(nameLabel, BorderLayout.WEST);

        String progress = offer.getQuantityFilled() + "/" + offer.getTotalQuantity();
        JLabel progressLabel = new JLabel(progress + " @ " + formatGp(offer.getPrice()));
        progressLabel.setForeground(Color.LIGHT_GRAY);
        progressLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(progressLabel, BorderLayout.EAST);

        return row;
    }

    private JPanel createTradeRow(CompletedTrade trade)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(new Color(40, 40, 40));
        row.setBorder(new EmptyBorder(4, 5, 4, 5));

        JLabel nameLabel = new JLabel(trade.getItemName() + " x" + trade.getQuantity());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(nameLabel, BorderLayout.WEST);

        long profit = trade.getProfit();
        Color profitColor = profit >= 0 ? new Color(0, 200, 83) : new Color(255, 80, 80);
        String sign = profit >= 0 ? "+" : "";
        JLabel profitLabel = new JLabel(sign + formatGp(profit));
        profitLabel.setForeground(profitColor);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(profitLabel, BorderLayout.EAST);

        return row;
    }

    private JLabel createLabel(String text, Color color)
    {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000_000)
        {
            return String.format("%.1fB", amount / 1_000_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000_000)
        {
            return String.format("%.1fM", amount / 1_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000)
        {
            return String.format("%.1fK", amount / 1_000.0);
        }
        return amount + " gp";
    }
}
