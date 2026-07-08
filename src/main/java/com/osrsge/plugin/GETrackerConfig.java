package com.osrsge.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("osrsge-tracker")
public interface GETrackerConfig extends Config
{
    @ConfigSection(
        name = "Website Sync",
        description = "Settings for syncing with osrsge.io",
        position = 0
    )
    String syncSection = "sync";

    @ConfigSection(
        name = "Overlay",
        description = "In-game overlay settings",
        position = 1
    )
    String overlaySection = "overlay";

    @ConfigItem(
        keyName = "apiKey",
        name = "API Key",
        description = "Your API key from osrsge.io for syncing trade data",
        section = syncSection,
        position = 0,
        secret = true
    )
    default String apiKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "syncBaseUrl",
        name = "Sync URL",
        description = "Base URL of your osrsge.io sync backend (Lovable Cloud functions URL, e.g. https://abc123.supabase.co/functions/v1). Leave empty to disable sync.",
        section = syncSection,
        position = 1
    )
    default String syncBaseUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = "syncEnabled",
        name = "Enable Sync",
        description = "Automatically sync trades to osrsge.io",
        section = syncSection,
        position = 2
    )
    default boolean syncEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "syncIntervalSeconds",
        name = "Sync Interval (seconds)",
        description = "How often to sync data with the website",
        section = syncSection,
        position = 3
    )
    default int syncIntervalSeconds()
    {
        return 30;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Show the in-game GE profit overlay",
        section = overlaySection,
        position = 0
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showSessionProfit",
        name = "Show Session Profit",
        description = "Display session profit in the overlay",
        section = overlaySection,
        position = 1
    )
    default boolean showSessionProfit()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showActiveFlips",
        name = "Show Active Flips",
        description = "Display active flips in the overlay",
        section = overlaySection,
        position = 2
    )
    default boolean showActiveFlips()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showMargins",
        name = "Show Margins",
        description = "Display item margins in the overlay",
        section = overlaySection,
        position = 3
    )
    default boolean showMargins()
    {
        return true;
    }
}
