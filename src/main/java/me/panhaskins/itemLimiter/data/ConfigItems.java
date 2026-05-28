package me.panhaskins.itemLimiter.data;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.cooldown.CooldownPacketListener;
import me.panhaskins.itemLimiter.model.*;
import me.panhaskins.itemLimiter.utils.ConfigManager;
import me.panhaskins.itemLimiter.utils.SchedulerUtil;
import me.panhaskins.itemLimiter.utils.item.ItemBuilder;
import me.panhaskins.itemLimiter.utils.item.ItemDescriptor;
import me.panhaskins.itemLimiter.utils.item.ItemMaterialRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class ConfigItems {
    private final ItemLimiter plugin;
    private final ConfigManager config;
    private final ItemMaterialRegistry materials;
    private Map<String, ItemRule> itemCache = new HashMap<>();
    private Map<String, EnchantRestriction> enchantCache = new HashMap<>();
    private Map<String, PotionRestriction> potionCache = new HashMap<>();
    private Map<PotionEffectType, PotionRestriction> potionByTypeCache;

    public ConfigItems(ItemLimiter plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.materials = plugin.getMaterialRegistry();
        rebuildCache();
    }

    public void rebuildCache() {
        FileConfiguration file = config.getConfig("items.yml");
        Map<String, ItemRule> items = new HashMap<>();
        Map<String, EnchantRestriction> enchants = new HashMap<>();
        Map<String, PotionRestriction> potions = new HashMap<>();

        for (String key : file.getKeys(false)) {
            ConfigurationSection section = file.getConfigurationSection(key);
            if (section == null) continue;

            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.endsWith("_ENCHANT") && section.contains("max_level")) {
                int max = section.getInt("max_level", 0);
                enchants.put(upper, new EnchantRestriction(upper, max));
            }
            if (upper.endsWith("_POTION") && (section.contains("max_level") || section.contains("max_duration"))) {
                int max = section.getInt("max_level", 0);
                int maxDuration = section.getInt("max_duration", 0);
                potions.put(upper, new PotionRestriction(upper, max, maxDuration));
            }

            ItemRule rule = parseItem(key, section);
            if (rule != null) {
                items.put(upper, rule);
            }
        }

        this.itemCache = items;
        this.enchantCache = enchants;
        this.potionCache = potions;
        this.potionByTypeCache = buildPotionByTypeCache(potions);

        CooldownPacketListener cooldownPackets = plugin.getCooldownPackets();
        if (cooldownPackets != null) {
            cooldownPackets.rebuildCache(buildMaterialIndex());
            for (Player p : Bukkit.getOnlinePlayers()) {
                SchedulerUtil.runForEntity(plugin, p, p::updateInventory);
            }
        }
    }

    public Map<Material, List<ItemRule>> buildMaterialIndex() {
        Map<Material, List<ItemRule>> index = new EnumMap<>(Material.class);
        for (ItemRule rule : itemCache.values()) {
            Material material = rule.material();
            if (material == null) continue;
            // One POTION rule covers normal / splash / lingering variants — index under all three.
            if (material == Material.POTION) {
                index.computeIfAbsent(Material.POTION, k -> new ArrayList<>()).add(rule);
                index.computeIfAbsent(Material.SPLASH_POTION, k -> new ArrayList<>()).add(rule);
                index.computeIfAbsent(Material.LINGERING_POTION, k -> new ArrayList<>()).add(rule);
            } else {
                index.computeIfAbsent(material, k -> new ArrayList<>()).add(rule);
            }
        }
        index.replaceAll((k, v) -> List.copyOf(v));
        return Map.copyOf(index);
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
        var effectRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        for (Map.Entry<String, PotionRestriction> entry : potions.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("_POTION")) continue;
            String effectName = key.substring(0, key.length() - "_POTION".length()).toLowerCase(Locale.ROOT);
            PotionEffectType type = effectRegistry.get(NamespacedKey.minecraft(effectName));
            if (type != null) {
                map.putIfAbsent(type, entry.getValue());
            }
        }
        return map;
    }

    public Optional<ItemRule> getItem(String key) {
        return Optional.ofNullable(itemCache.get(key.toUpperCase(Locale.ROOT)));
    }

    public Optional<ItemRule> getItem(ItemStack stack) {
        if (stack == null) return Optional.empty();

        ItemMeta stackMeta = stack.hasItemMeta() ? stack.getItemMeta() : null;

        if (stackMeta instanceof EnchantmentStorageMeta book) {
            for (Enchantment enchant : book.getStoredEnchants().keySet()) {
                String key = enchant.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
                Optional<ItemRule> rule = getItem(key);
                if (rule.isPresent()) return rule;
            }
        }

        if (stackMeta instanceof PotionMeta meta) {
            PotionType base = meta.getBasePotionType();
            if (base != null) {
                List<PotionEffect> effects = base.getPotionEffects();
                if (!effects.isEmpty()) {
                    String effectKey = effects.getFirst().getType().getKey().getKey().toUpperCase(Locale.ROOT) + "_POTION";
                    Optional<ItemRule> rule = getItem(effectKey);
                    if (rule.isPresent()) return rule;
                }
                String typeName = base.name().toUpperCase(Locale.ROOT);
                Optional<ItemRule> rule = getItem(typeName + "_POTION");
                if (rule.isPresent()) return rule;
                String baseName = typeName.replaceFirst("^(LONG|STRONG)_", "");
                if (!baseName.equals(typeName)) {
                    rule = getItem(baseName + "_POTION");
                    if (rule.isPresent()) return rule;
                }
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

    private ItemRule parseItem(String name, ConfigurationSection section) {
        ConfigurationSection limitSection = section.getConfigurationSection("limit");
        int inInv = 0;
        int perPlayer = -1;
        int global = -1;
        if (limitSection != null) {
            if (limitSection.isSet("in_inventory")) {
                inInv = limitSection.getInt("in_inventory");
            }
            ConfigurationSection sourcesSection = limitSection.getConfigurationSection("sources");
            if (sourcesSection != null) {
                if (sourcesSection.isSet("per_player")) {
                    perPlayer = sourcesSection.getInt("per_player");
                }
                if (sourcesSection.isSet("global")) {
                    global = sourcesSection.getInt("global");
                }
            }
        }
        ItemRule.Limits limits = new ItemRule.Limits(inInv, perPlayer, global);

        ConfigurationSection cooldownSection = section.getConfigurationSection("cooldown");
        int cooldownTime = 0;
        EnumSet<Trigger> triggers = EnumSet.noneOf(Trigger.class);
        if (cooldownSection != null) {
            cooldownTime = cooldownSection.getInt("time", 0);
            for (String triggerName : cooldownSection.getStringList("trigger")) {
                try {
                    triggers.add(Trigger.valueOf(triggerName.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ItemRule.Cooldown cooldown = new ItemRule.Cooldown(cooldownTime, triggers);

        EnumSet<Sources> blacklist = EnumSet.noneOf(Sources.class);
        for (String sourceName : section.getStringList("blacklist_sources")) {
            try {
                blacklist.add(Sources.valueOf(sourceName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        ItemRule.Worlds worlds;
        ConfigurationSection worldsSection = section.getConfigurationSection("worlds");
        if (worldsSection != null) {
            boolean isBlacklist = worldsSection.getBoolean("blacklist", true);
            Set<String> worldList = new HashSet<>();
            for (String worldName : worldsSection.getStringList("list")) {
                worldList.add(worldName.toLowerCase(Locale.ROOT));
            }
            worlds = new ItemRule.Worlds(isBlacklist, Set.copyOf(worldList));
        } else {
            worlds = ItemRule.Worlds.NONE;
        }

        ItemRule.Exception exception = parseException(section);

        Material material = Material.matchMaterial(name);
        Enchantment enchant = null;
        PotionType potion = null;
        String upper = name.toUpperCase(Locale.ROOT);

        if (material == null) {
            if (upper.endsWith("_ENCHANT")) {
                String prefix = upper.substring(0, upper.length() - 8).toLowerCase(Locale.ROOT);
                enchant = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(NamespacedKey.minecraft(prefix));
                material = Material.ENCHANTED_BOOK;
                if (enchant == null) {
                    plugin.getLogger().warning("Unknown enchantment: " + prefix + " for item " + name);
                }
            } else if (upper.endsWith("_POTION")) {
                String prefix = upper.substring(0, upper.length() - 7);
                try {
                    potion = PotionType.valueOf(prefix);
                } catch (IllegalArgumentException ex) {
                    boolean knownEffect = RegistryAccess.registryAccess()
                            .getRegistry(RegistryKey.MOB_EFFECT)
                            .get(NamespacedKey.minecraft(prefix.toLowerCase(Locale.ROOT))) != null;
                    if (!knownEffect) {
                        plugin.getLogger().warning("Unknown potion type: " + prefix + " for item " + name);
                    }
                }
                material = Material.POTION;
            } else {
                plugin.getLogger().warning("Unknown material name in config: " + name);
                return null;
            }
        }
        return new ItemRule(upper, material, enchant, potion, limits, cooldown, blacklist, worlds, exception);
    }

    private ItemRule.Exception parseException(ConfigurationSection section) {
        ConfigurationSection exceptionSection = section.getConfigurationSection("exception");
        if (exceptionSection == null) return ItemRule.Exception.NONE;

        List<String> permissions = parseExceptionPermissions(exceptionSection);
        Map<String, ItemDescriptor> exceptionItems = new LinkedHashMap<>();
        ConfigurationSection itemsSection = exceptionSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;
                ItemDescriptor descriptor = ItemBuilder.parse(itemSection, plugin.getLogger(), materials, false);
                if (descriptor != null) exceptionItems.put(itemKey, descriptor);
            }
        }
        if (permissions.isEmpty() && exceptionItems.isEmpty()) return ItemRule.Exception.NONE;
        return new ItemRule.Exception(permissions, exceptionItems);
    }

    private static List<String> parseExceptionPermissions(ConfigurationSection section) {
        return Stream.of("permission", "permissions")
                .flatMap(key -> readStringOrList(section, key).stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> readStringOrList(ConfigurationSection s, String key) {
        return switch (s.get(key)) {
            case String value -> List.of(value);
            case List<?> values -> values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
            case null, default -> List.of();
        };
    }
}
