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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigItems {
    private final ItemLimiter plugin;
    private final ConfigManager config;
    private Map<String, ItemLimiterItem> itemCache = new HashMap<>();
    private Map<String, EnchantRestriction> enchantCache = new HashMap<>();
    private Map<String, PotionRestriction> potionCache = new HashMap<>();
    private Map<PotionEffectType, PotionRestriction> potionByTypeCache;

    public ConfigItems(ItemLimiter plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        rebuildCache();
    }

    public void rebuildCache() {
        FileConfiguration file = config.getConfig("items.yml");
        Map<String, ItemLimiterItem> items = new HashMap<>();
        Map<String, EnchantRestriction> enchants = new HashMap<>();
        Map<String, PotionRestriction> potions = new HashMap<>();

        for (String key : file.getKeys(false)) {
            ConfigurationSection cs = file.getConfigurationSection(key);
            if (cs == null) continue;

            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.endsWith("_ENCHANT") && cs.contains("max_level")) {
                int max = cs.getInt("max_level", 0);
                enchants.put(upper, new EnchantRestriction(upper, max));
            }
            if (upper.endsWith("_POTION") && (cs.contains("max_level") || cs.contains("max_duration"))) {
                int max = cs.contains("max_level") ? cs.getInt("max_level") : 0;
                int maxDuration = cs.contains("max_duration") ? cs.getInt("max_duration") : 0;
                potions.put(upper, new PotionRestriction(upper, max, maxDuration));
            }

            ItemLimiterItem item = parseItem(key, cs);
            if (item != null) {
                items.put(upper, item);
            }
        }

        this.itemCache = items;
        this.enchantCache = enchants;
        this.potionCache = potions;
        this.potionByTypeCache = buildPotionByTypeCache(potions);
    }

    private Map<PotionEffectType, PotionRestriction> buildPotionByTypeCache(Map<String, PotionRestriction> potions) {
        Map<PotionEffectType, PotionRestriction> map = new HashMap<>();
        for (PotionType type : PotionType.values()) {
            for (PotionEffect effect : type.getPotionEffects()) {
                String key = effect.getType().getKey().getKey().toUpperCase(Locale.ROOT) + "_POTION";
                PotionRestriction res = potions.get(key);
                if (res == null) {
                    res = potions.get(type.name() + "_POTION");
                }
                if (res != null) {
                    map.putIfAbsent(effect.getType(), res);
                }
            }
        }
        return map;
    }

    public Optional<ItemLimiterItem> getItem(String key) {
        return Optional.ofNullable(itemCache.get(key.toUpperCase(Locale.ROOT)));
    }

    public Optional<ItemLimiterItem> getItem(ItemStack stack) {
        if (stack == null) return Optional.empty();

        if (stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            for (Enchantment ench : book.getStoredEnchants().keySet()) {
                String key = ench.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
                Optional<ItemLimiterItem> opt = getItem(key);
                if (opt.isPresent()) return opt;
            }
        }

        if (stack.getItemMeta() instanceof PotionMeta meta) {
            PotionType base = meta.getBasePotionType();
            List<PotionEffect> effects = base.getPotionEffects();
            if (!effects.isEmpty()) {
                String effectKey = effects.getFirst().getType().getKey().getKey().toUpperCase(Locale.ROOT) + "_POTION";
                Optional<ItemLimiterItem> opt = getItem(effectKey);
                if (opt.isPresent()) return opt;
            }
            String typeName = base.name().toUpperCase(Locale.ROOT);
            Optional<ItemLimiterItem> opt = getItem(typeName + "_POTION");
            if (opt.isPresent()) return opt;
            String baseName = typeName.replaceFirst("^(LONG|STRONG)_", "");
            if (!baseName.equals(typeName)) {
                opt = getItem(baseName + "_POTION");
                if (opt.isPresent()) return opt;
            }
        }

        return getItem(stack.getType().name());
    }

    public Optional<EnchantRestriction> getEnchantRestriction(String enchantKey) {
        return Optional.ofNullable(enchantCache.get(enchantKey.toUpperCase(Locale.ROOT)));
    }

    public Optional<PotionRestriction> getPotionRestriction(String potionKey) {
        return Optional.ofNullable(potionCache.get(potionKey.toUpperCase(Locale.ROOT)));
    }

    public Optional<PotionRestriction> getPotionRestriction(PotionEffectType type) {
        return Optional.ofNullable(potionByTypeCache.get(type));
    }

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

        ItemLimiterItem.Worlds worlds;
        ConfigurationSection worldsSec = cs.getConfigurationSection("worlds");
        if (worldsSec != null) {
            boolean isBlacklist = worldsSec.getBoolean("blacklist", true);
            Set<String> worldList = new HashSet<>();
            for (String w : worldsSec.getStringList("list")) {
                worldList.add(w.toLowerCase(Locale.ROOT));
            }
            worlds = new ItemLimiterItem.Worlds(isBlacklist, Set.copyOf(worldList));
        } else {
            worlds = ItemLimiterItem.Worlds.NONE;
        }

        Material material = Material.matchMaterial(name);
        Enchantment enchant = null;
        PotionType potion = null;

        if (material == null) {
            if (name.toUpperCase(Locale.ROOT).endsWith("_ENCHANT")) {
                String prefix = name.substring(0, name.length() - 8).toLowerCase(Locale.ROOT);
                enchant = io.papermc.paper.registry.RegistryAccess.registryAccess()
                        .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                        .get(org.bukkit.NamespacedKey.minecraft(prefix));
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
        return new ItemLimiterItem(name.toUpperCase(Locale.ROOT), material, enchant, potion, limits, cooldown, blacklist, worlds);
    }
}
