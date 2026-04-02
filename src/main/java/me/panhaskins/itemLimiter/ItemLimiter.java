package me.panhaskins.itemLimiter;

import me.panhaskins.itemLimiter.listener.SourceListener;
import me.panhaskins.itemLimiter.listener.TriggerListener;
import me.panhaskins.itemLimiter.listener.InventoryListener;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.data.UsageTracker;
import me.panhaskins.itemLimiter.utils.ConfigManager;
import me.panhaskins.itemLimiter.utils.database.DatabaseManager;
import me.panhaskins.itemLimiter.utils.database.QueryBuilder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemLimiter extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ConfigItems items;
    private UsageTracker usageTracker;
    private boolean packetEvents;

    @Override
    public void onEnable() {
        // Plugin startup logic

        configManager = new ConfigManager(this, "items.yml", "config.yml", "messages.yml", "examples.yml");
        databaseManager = new DatabaseManager(this, configManager.getConfig("config.yml").getConfigurationSection("database"));

        items = new ConfigItems(this);
        usageTracker = new UsageTracker(this, databaseManager, getLogger());
        packetEvents = getServer().getPluginManager().isPluginEnabled("packetevents");

        // unified statistics table used for both global and per-player counters
        QueryBuilder.create(databaseManager).createTable(
                "usage_stats",
                "uuid VARCHAR(36) NOT NULL",
                "category VARCHAR(32) NOT NULL",
                "target VARCHAR(64) NOT NULL",
                "action VARCHAR(32) NOT NULL",
                "count INT DEFAULT 0",
                "last_used TIMESTAMP",
                "PRIMARY KEY (uuid, category, target, action)"
        );

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new InventoryListener(this), this);
        pluginManager.registerEvents(new SourceListener(this), this);
        pluginManager.registerEvents(new TriggerListener(this), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigItems getItems() {
        return items;
    }

    public UsageTracker getUsageTracker() {
        return usageTracker;
    }

    public boolean hasPacketEvents() {
        return packetEvents;
    }
}
