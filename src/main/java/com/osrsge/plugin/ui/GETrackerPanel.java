package com.osrsge.plugin.ui;

import com.osrsge.plugin.GETrackerPlugin;
import com.osrsge.plugin.model.CompletedTrade;
import com.osrsge.plugin.model.SessionStats;
import com.osrsge.plugin.model.TradeAggregator;
import com.osrsge.plugin.model.TradeOffer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class GETrackerPanel extends PluginPanel
{
    private static final Color GREEN = new Color(0, 200, 83);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color ORANGE = new Color(255, 180, 100);
    private static final Color BLUE = new Color(100, 180, 255);
    private static final Color ROW_BG = new Color(40, 40, 40);
    private static final Color ROW_HOVER_BG = new Color(50, 50, 50);

    private enum Range
    {
        SESSION("Session", -1),
        HOUR_1("Past Hour", 3_600_000L),
        HOUR_4("Past 4 Hours", 4 * 3_600_000L),
        HOUR_12("Past 12 Hours", 12 * 3_600_000L),
        DAY_1("Past Day", 24 * 3_600_000L),
        WEEK_1("Past Week", 7 * 24 * 3_600_000L),
        MONTH_1("Past Month", 30L * 24 * 3_600_000L),
        ALL("All", 0);

        final String label;
        final long windowMillis; // -1 = session start, 0 = everything

        Range(String label, long windowMillis)
        {
            this.label = label;
            this.windowMillis = windowMillis;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private final GETrackerPlugin plugin;

    private final JLabel profitLabel = new JLabel("0 gp");
    private final JLabel flipsLabel = new JLabel("0");
    private final JLabel taxLabel = new JLabel("0 gp");
    private final JLabel sessionTimeLabel = new JLabel("00:00:00");
    private final JLabel syncStatusLabel = new JLabel("Offline");

    private final JComboBox<Range> rangeCombo = new JComboBox<>(Range.values());

    private final JPanel activeOffersPanel = new JPanel();
    private final JPanel itemsPanel = new JPanel();

    private List<CompletedTrade> latestTrades = new ArrayList<>();

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
        container.add(buildRangePanel());
        container.add(Box.createVerticalStrut(8));
        container.add(buildStatsPanel());
        container.add(Box.createVerticalStrut(8));
        container.add(buildActiveOffersSection());
        container.add(Box.createVerticalStrut(8));
        container.add(buildItemsSection());
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
        title.setForeground(GREEN);
        header.add(title, BorderLayout.WEST);

        syncStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        syncStatusLabel.setForeground(Color.GRAY);
        header.add(syncStatusLabel, BorderLayout.EAST);

        return header;
    }

    private JPanel buildRangePanel()
    {
        JPanel rangePanel = new JPanel(new BorderLayout());
        rangePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rangePanel.setBorder(new EmptyBorder(6, 10, 6, 10));

        rangeCombo.setFont(FontManager.getRunescapeSmallFont());
        rangeCombo.setFocusable(false);
        rangeCombo.addActionListener(e -> render());
        rangePanel.add(rangeCombo, BorderLayout.CENTER);

        return rangePanel;
    }

    private JPanel buildStatsPanel()
    {
        JPanel stats = new JPanel(new GridLayout(4, 2, 5, 5));
        stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stats.setBorder(new EmptyBorder(10, 10, 10, 10));

        stats.add(createLabel("Profit:", Color.WHITE));
        profitLabel.setForeground(GREEN);
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(profitLabel);

        stats.add(createLabel("Flips:", Color.WHITE));
        flipsLabel.setForeground(Color.WHITE);
        flipsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(flipsLabel);

        stats.add(createLabel("Tax Paid:", Color.WHITE));
        taxLabel.setForeground(ORANGE);
        taxLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stats.add(taxLabel);

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

    private JPanel buildItemsSection()
    {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel sectionTitle = new JLabel("Items");
        sectionTitle.setForeground(BLUE);
        sectionTitle.setFont(FontManager.getRunescapeSmallFont());
        section.add(sectionTitle, BorderLayout.NORTH);

        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(itemsPanel);
        scrollPane.setPreferredSize(new Dimension(0, 260));
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
        openWebButton.addActionListener(e -> LinkBrowser.browse("https://osrsge.io/"));
        actions.add(openWebButton);

        JButton resetButton = new JButton("Reset Session");
        resetButton.setFocusPainted(false);
        resetButton.addActionListener(e -> {
            plugin.resetSession();
            render();
        });
        actions.add(resetButton);

        return actions;
    }

    // --- update entry points (called from plugin) ---

    public void updateStats()
    {
        SwingUtilities.invokeLater(this::render);
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
            latestTrades = new ArrayList<>(trades);
            render();
        });
    }

    // --- rendering ---

    private long cutoff()
    {
        Range range = (Range) rangeCombo.getSelectedItem();
        if (range == null || range == Range.ALL)
        {
            return 0;
        }
        if (range == Range.SESSION)
        {
            return plugin.getSessionStats().getSessionStart().toEpochMilli();
        }
        return System.currentTimeMillis() - range.windowMillis;
    }

    private void render()
    {
        SessionStats session = plugin.getSessionStats();
        Range range = (Range) rangeCombo.getSelectedItem();
        long cutoff = cutoff();

        TradeAggregator.Totals totals = TradeAggregator.totals(latestTrades, cutoff);
        Color profitColor = totals.getProfit() >= 0 ? GREEN : RED;

        profitLabel.setText(formatGp(totals.getProfit()));
        profitLabel.setForeground(profitColor);

        flipsLabel.setText(String.valueOf(totals.getFlips()));
        taxLabel.setText(formatGp(totals.getTax()));
        sessionTimeLabel.setText(session.getSessionDurationFormatted());

        boolean connected = plugin.isSyncConnected();
        syncStatusLabel.setText(connected ? "Synced" : "Offline");
        syncStatusLabel.setForeground(connected ? GREEN : Color.GRAY);

        renderItems(cutoff);
    }

    private void renderItems(long cutoff)
    {
        itemsPanel.removeAll();

        List<TradeAggregator.ItemStats> items = TradeAggregator.aggregate(latestTrades, cutoff);
        if (items.isEmpty())
        {
            JLabel empty = new JLabel("No completed trades yet");
            empty.setForeground(Color.GRAY);
            empty.setFont(FontManager.getRunescapeSmallFont());
            itemsPanel.add(empty);
        }
        else
        {
            int shown = 0;
            for (TradeAggregator.ItemStats item : items)
            {
                if (shown++ >= 25)
                {
                    break;
                }
                itemsPanel.add(new ItemRow(item));
                itemsPanel.add(Box.createVerticalStrut(2));
            }
        }

        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    /** Expandable per-item row: header line + click-to-toggle details. */
    private class ItemRow extends JPanel
    {
        private final JPanel details;
        private final JLabel arrow;

        ItemRow(TradeAggregator.ItemStats item)
        {
            setLayout(new BorderLayout());
            setBackground(ROW_BG);

            JPanel headerRow = new JPanel(new BorderLayout());
            headerRow.setBackground(ROW_BG);
            headerRow.setBorder(new EmptyBorder(5, 6, 5, 6));

            arrow = new JLabel("▶");
            arrow.setForeground(Color.GRAY);
            // default Swing font: the RuneScape font lacks the triangle glyphs
            arrow.setFont(arrow.getFont().deriveFont(9f));
            arrow.setBorder(new EmptyBorder(0, 0, 0, 6));

            JLabel icon = new JLabel();
            icon.setBorder(new EmptyBorder(0, 0, 0, 6));
            AsyncBufferedImage itemImage = plugin.getItemManager()
                .getImage(item.getItemId(), item.getQuantity(), item.getQuantity() > 1);
            itemImage.addTo(icon);

            JLabel name = new JLabel(item.getItemName());
            name.setForeground(Color.WHITE);
            name.setFont(FontManager.getRunescapeSmallFont());

            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(arrow, BorderLayout.WEST);
            left.add(icon, BorderLayout.CENTER);
            left.add(name, BorderLayout.EAST);
            headerRow.add(left, BorderLayout.WEST);

            Color profitColor = item.getTotalProfit() >= 0 ? GREEN : RED;
            String sign = item.getTotalProfit() >= 0 ? "+" : "";
            JLabel profit = new JLabel(sign + formatGp(item.getTotalProfit()));
            profit.setForeground(profitColor);
            profit.setFont(FontManager.getRunescapeSmallFont());
            headerRow.add(profit, BorderLayout.EAST);

            details = buildDetails(item);
            details.setVisible(false);

            add(headerRow, BorderLayout.NORTH);
            add(details, BorderLayout.CENTER);

            MouseAdapter toggle = new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    boolean open = !details.isVisible();
                    details.setVisible(open);
                    arrow.setText(open ? "▼" : "▶");
                    itemsPanel.revalidate();
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    headerRow.setBackground(ROW_HOVER_BG);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    headerRow.setBackground(ROW_BG);
                }
            };
            headerRow.addMouseListener(toggle);
            headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 400));
        }

        private JPanel buildDetails(TradeAggregator.ItemStats item)
        {
            JPanel grid = new JPanel(new GridLayout(7, 2, 4, 3));
            grid.setBackground(ROW_BG);
            grid.setBorder(new EmptyBorder(2, 18, 6, 6));

            Color profitColor = item.getTotalProfit() >= 0 ? GREEN : RED;

            addDetail(grid, "Total Profit:", formatGp(item.getTotalProfit()), profitColor);
            addDetail(grid, "Avg Profit ea:", formatGp(item.getAvgProfitEach()) + "/ea", profitColor);
            addDetail(grid, "Avg ROI:", String.format("%.2f%%", item.getRoi()), profitColor);
            addDetail(grid, "Flips:", String.valueOf(item.getFlips()), Color.WHITE);
            addDetail(grid, "Avg Buy Price:", formatGp(item.getAvgBuyPrice()), Color.WHITE);
            addDetail(grid, "Avg Sell Price:", formatGp(item.getAvgSellPrice()), Color.WHITE);
            addDetail(grid, "Tax Paid:", formatGp(item.getTotalTax()), ORANGE);

            return grid;
        }

        private void addDetail(JPanel grid, String key, String value, Color valueColor)
        {
            JLabel k = new JLabel(key);
            k.setForeground(Color.LIGHT_GRAY);
            k.setFont(FontManager.getRunescapeSmallFont());
            grid.add(k);

            JLabel v = new JLabel(value);
            v.setForeground(valueColor);
            v.setFont(FontManager.getRunescapeSmallFont());
            v.setHorizontalAlignment(SwingConstants.RIGHT);
            grid.add(v);
        }
    }

    private JPanel createOfferRow(TradeOffer offer)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));

        String action = offer.isBuy() ? "BUY" : "SELL";
        Color actionColor = offer.isBuy() ? BLUE : ORANGE;

        JLabel iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
        AsyncBufferedImage itemImage = plugin.getItemManager()
            .getImage(offer.getItemId(), offer.getTotalQuantity(), offer.getTotalQuantity() > 1);
        itemImage.addTo(iconLabel);

        JLabel nameLabel = new JLabel(action + " " + offer.getItemName());
        nameLabel.setForeground(actionColor);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(iconLabel, BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);
        row.add(left, BorderLayout.WEST);

        String progress = offer.getQuantityFilled() + "/" + offer.getTotalQuantity();
        JLabel progressLabel = new JLabel(progress + " @ " + formatGp(offer.getPrice()));
        progressLabel.setForeground(Color.LIGHT_GRAY);
        progressLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(progressLabel, BorderLayout.EAST);

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
