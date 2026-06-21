package me.panhaskins.itemLimiter;

import me.panhaskins.itemLimiter.cooldown.CooldownPacketListener;
import me.panhaskins.itemLimiter.listener.PotionEffectListener;
import me.panhaskins.itemLimiter.listener.SourceListener;
import me.panhaskins.itemLimiter.listener.TriggerListener;
import me.panhaskins.itemLimiter.listener.InventoryListener;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.data.UsageTracker;
import me.panhaskins.itemLimiter.utils.ConfigManager;
import me.panhaskins.itemLimiter.utils.database.DatabaseManager;
import me.panhaskins.itemLimiter.utils.database.QueryBuilder;
import me.panhaskins.itemLimiter.utils.item.ItemMaterialRegistry;
import me.panhaskins.itemLimiter.utils.item.materials.HeadDatabaseMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.ItemsAdderMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.MMOItemsMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.NativeHeadMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.NexoMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.OraxenMaterial;
import me.panhaskins.itemLimiter.utils.item.materials.VanillaMaterial;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemLimiter extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ConfigItems items;
    private UsageTracker usageTracker;
    private ItemMaterialRegistry materialRegistry;
    private CooldownPacketListener cooldownPackets;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this, "items.yml", "config.yml", "messages.yml", "examples.yml");
        databaseManager = new DatabaseManager(this, configManager.getConfig("config.yml").getConfigurationSection("database"));

        try {
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
        } catch (QueryBuilder.QueryExecutionException e) {
            getLogger().severe("Failed to create database table: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        materialRegistry = buildMaterialRegistry();
        items = new ConfigItems(this);
        usageTracker = new UsageTracker(this, databaseManager, getLogger());
        usageTracker.loadCache();

        PluginManager pluginManager = getServer().getPluginManager();
        if (pluginManager.isPluginEnabled("packetevents")) {
            cooldownPackets = new CooldownPacketListener(this);
            cooldownPackets.rebuildCache(items.buildMaterialIndex());
        }

        pluginManager.registerEvents(new InventoryListener(this), this);
        pluginManager.registerEvents(new SourceListener(this), this);
        pluginManager.registerEvents(new TriggerListener(this), this);
        pluginManager.registerEvents(new PotionEffectListener(this), this);
    }

    @Override
    public void onDisable() {
        if (cooldownPackets != null) {
            cooldownPackets.close();
            cooldownPackets = null;
        }
        getServer().getAsyncScheduler().cancelTasks(this);
        if (usageTracker != null) usageTracker.saveAll();
        if (databaseManager != null) databaseManager.close();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ConfigItems getItems() {
        return items;
    }

    public UsageTracker getUsageTracker() {
        return usageTracker;
    }

    public ItemMaterialRegistry getMaterialRegistry() {
        return materialRegistry;
    }

    public CooldownPacketListener getCooldownPackets() {
        return cooldownPackets;
    }

    private ItemMaterialRegistry buildMaterialRegistry() {
        ItemMaterialRegistry registry = new ItemMaterialRegistry();
        // Longest prefix wins via Registry sort
        registry.register(new NativeHeadMaterial.Factory());
        registry.register(new HeadDatabaseMaterial.Factory(this));
        registry.register(new ItemsAdderMaterial.Factory(this));
        registry.register(new OraxenMaterial.Factory(this));
        registry.register(new NexoMaterial.Factory(this));
        registry.register(new MMOItemsMaterial.Factory(this));
        // Fallback (must be last, prefix == "")
        registry.register(new VanillaMaterial.Factory(getLogger()));
        return registry;
    }
}
