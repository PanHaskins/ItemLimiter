package me.panhaskins.itemLimiter.data;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.model.*;
import me.panhaskins.itemLimiter.utils.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;

import java.util.*;

/**
 * Provides direct access to item configuration data from items.yml without caching.
 */
public class ConfigItems {
    private final ItemLimiter plugin;
    private final ConfigManager config;
    private final FileConfiguration itemsFile;

    public ConfigItems(ItemLimiter plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.itemsFile = config.getConfig("items.yml");
    }

    private FileConfiguration file() {
        return itemsFile;
    }

    /**
     * Returns configuration for an item key if present.
     */
    public Optional<ItemLimiterItem> getItem(String key) {
        ConfigurationSection cs = file().getConfigurationSection(key);
        if (cs == null) return Optional.empty();
        return Optional.ofNullable(parseItem(key, cs));
    }

    /**
     * Returns configuration for a Bukkit ItemStack.
     */
    public Optional<ItemLimiterItem> getItem(ItemStack stack) {
        if (stack == null) return Optional.empty();
        // enchanted books
        if (stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            for (Enchantment ench : book.getStoredEnchants().keySet()) {
                String key = ench.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
                Optional<ItemLimiterItem> optionalItem = getItem(key);
                if (optionalItem.isPresent()) return optionalItem;
            }
        }
        // potions
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            PotionType base = meta.getBasePotionType();
            String keyName;
            List<PotionEffect> potionEffectList = base.getPotionEffects();
            if (!potionEffectList.isEmpty()) {
                keyName = potionEffectList.getFirst().getType().getName();
            } else {
                keyName = base.name();
            }
            String potionKey = keyName.toUpperCase(Locale.ROOT) + "_POTION";
            Optional<ItemLimiterItem> optionalPotionItem = getItem(potionKey);
            if (optionalPotionItem.isPresent()) return optionalPotionItem;
        }
        return getItem(stack.getType().name());
    }

    /** Parses ItemLimiterItem from configuration section. */
    private ItemLimiterItem parseItem(String name, ConfigurationSection cs) {
        ConfigurationSection limitSec = cs.getConfigurationSection("limit");
        int inInv = 0;
        int perPlayer = -1;
        int global = -1;
        if (limitSec != null) {
            if (limitSec.isSet("in_inventory")) {
                inInv = limitSec.getInt("in_inventory");
            }
            ConfigurationSection srcSec = limitSec.getConfigurationSection("sources");
            if (srcSec != null) {
                if (srcSec.isSet("per_player")) {
                    perPlayer = srcSec.getInt("per_player");
                }
                if (srcSec.isSet("global")) {
                    global = srcSec.getInt("global");
                }
            }
        }
        ItemLimiterItem.Limits limits = new ItemLimiterItem.Limits(inInv, perPlayer, global);

        ConfigurationSection cdSec = cs.getConfigurationSection("cooldown");
        int cdTime = 0;
        EnumSet<Trigger> triggers = EnumSet.noneOf(Trigger.class);
        if (cdSec != null) {
            cdTime = cdSec.getInt("time", 0);
            for (String t : cdSec.getStringList("trigger")) {
                try {
                    triggers.add(Trigger.valueOf(t.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ItemLimiterItem.Cooldown cooldown = new ItemLimiterItem.Cooldown(cdTime, triggers);

        EnumSet<Sources> blacklist = EnumSet.noneOf(Sources.class);
        for (String s : cs.getStringList("blacklist_sources")) {
            try {
                blacklist.add(Sources.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Material material = Material.matchMaterial(name);
        Enchantment enchant = null;
        PotionType potion = null;

        if (material == null) {
            if (name.toUpperCase(Locale.ROOT).endsWith("_ENCHANT")) {
                String prefix = name.substring(0, name.length() - 8).toLowerCase(Locale.ROOT);
                enchant = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(prefix));
                material = Material.ENCHANTED_BOOK;
                if (enchant == null) {
                    plugin.getLogger().warning("Unknown enchantment: " + prefix + " for item " + name);
                }
            } else if (name.toUpperCase(Locale.ROOT).endsWith("_POTION")) {
                String prefix = name.substring(0, name.length() - 7).toUpperCase(Locale.ROOT);
                try {
                    potion = PotionType.valueOf(prefix);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown potion type: " + prefix + " for item " + name);
                }
                material = Material.POTION;
            } else {
                plugin.getLogger().warning("Unknown material name in config: " + name);
                return null;
            }
        }
        return new ItemLimiterItem(name.toUpperCase(Locale.ROOT), material, enchant, potion, limits, cooldown, blacklist);
    }

    /** Returns potion restriction data by potion key. */
    public Optional<PotionRestriction> getPotionRestriction(String potionKey) {
        ConfigurationSection cs = file().getConfigurationSection(potionKey);
        if (cs == null) return Optional.empty();
        int max = cs.contains("max_level") ? cs.getInt("max_level") : 0;
        int maxDuration = cs.contains("max_duration") ? cs.getInt("max_duration") : 0;
        EnumSet<PotionRestriction.PotionSource> sources = EnumSet.noneOf(PotionRestriction.PotionSource.class);
        for (String s : cs.getStringList("blacklist_sources")) {
            try {
                sources.add(PotionRestriction.PotionSource.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.of(new PotionRestriction(potionKey, max, maxDuration, sources));
    }

    /** Returns potion restriction data by effect type. */
    public Optional<PotionRestriction> getPotionRestriction(PotionEffectType type) {
        String key = type.getName().toUpperCase(Locale.ROOT) + "_POTION";
        Optional<PotionRestriction> res = getPotionRestriction(key);
        if (res.isPresent()) return res;
        for (PotionType potion : PotionType.values()) {
            for (PotionEffect effect : potion.getPotionEffects()) {
                if (effect.getType().equals(type)) {
                    res = getPotionRestriction(potion.name() + "_POTION");
                    if (res.isPresent()) {
                        return res;
                    }
                    break;
                }
            }
        }
        return res;
    }

    /** Returns enchantment restriction data by enchant key. */
    public Optional<EnchantRestriction> getEnchantRestriction(String enchantKey) {
        ConfigurationSection cs = file().getConfigurationSection(enchantKey);
        if (cs == null) return Optional.empty();
        int max = cs.contains("max_level") ? cs.getInt("max_level") : 0;
        EnumSet<EnchantRestriction.EnchantSource> sources = EnumSet.noneOf(EnchantRestriction.EnchantSource.class);
        for (String s : cs.getStringList("blacklist_sources")) {
            try {
                sources.add(EnchantRestriction.EnchantSource.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.of(new EnchantRestriction(enchantKey, max, sources));
    }
}
