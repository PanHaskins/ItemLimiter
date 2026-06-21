package me.panhaskins.itemLimiter.utils.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.panhaskins.itemLimiter.utils.Messager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Static facade over {@link ItemDescriptor}. Parses YAML once into a fully-resolved
 * descriptor and matches a candidate stack against it with subset/contains semantics.
 *
 * <p>All parsing and registry resolution happens in {@link #parse}. The {@link #matches}
 * path does no string parsing, no MiniMessage translation, and no registry lookups.
 *
 * <p>Invalid descriptor fields fail closed: an unrecognised material/enchant/effect
 * is logged at parse time and the descriptor either becomes unbuildable or fails to match.
 */
public final class ItemBuilder {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemBuilder() {}

    // ---------- Parsing ----------

    public static ItemDescriptor parse(ConfigurationSection section, Logger log, ItemMaterialRegistry registry) {
        return parse(section, log, registry, true);
    }

    /**
     * When {@code requireMaterial} is false, a material-less descriptor must declare at least
     * one identifying metadata field, otherwise it is rejected to prevent match-everything bypasses.
     */
    public static ItemDescriptor parse(ConfigurationSection section, Logger log, ItemMaterialRegistry registry, boolean requireMaterial) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(registry, "registry");

        String materialRaw = section.getString("material");
        boolean hasMaterial = materialRaw != null && !materialRaw.isBlank();
        if (!hasMaterial && requireMaterial) {
            if (log != null) log.warning("Item descriptor at '" + section.getCurrentPath() + "' has no material; ignored.");
            return null;
        }
        ItemMaterial material = hasMaterial ? registry.parse(materialRaw) : null;
        if (material != null && !material.isAvailable() && log != null) {
            log.warning("Item descriptor at '" + section.getCurrentPath()
                    + "' uses material '" + materialRaw
                    + "' but the required plugin is not loaded, this descriptor will never match.");
        }

        String displayRaw = firstString(section, "display_name", "name");
        Component displayComp = displayRaw == null ? null : Messager.translate(displayRaw);
        String displayPlain = displayComp == null ? null : PLAIN.serialize(displayComp);

        String itemNameRaw = section.getString("item_name");
        Component itemNameComp = itemNameRaw == null ? null : Messager.translate(itemNameRaw);
        String itemNamePlain = itemNameComp == null ? null : PLAIN.serialize(itemNameComp);

        List<Component> lore = null;
        List<String> lorePlain = null;
        if (section.contains("lore")) {
            List<String> rawLore = section.getStringList("lore");
            if (!rawLore.isEmpty()) {
                lore = new ArrayList<>(rawLore.size());
                lorePlain = new ArrayList<>(rawLore.size());
                for (String line : rawLore) {
                    Component c = Messager.translate(line);
                    lore.add(c);
                    lorePlain.add(PLAIN.serialize(c));
                }
            }
        }

        Integer customModel   = readInt(section, "custom_model_data", "model_data");
        NamespacedKey itemModel = null;
        String itemModelRaw = section.getString("item_model");
        if (itemModelRaw != null) {
            itemModel = NamespacedKey.fromString(itemModelRaw);
            if (itemModel == null && log != null) {
                log.warning("Invalid item_model '" + itemModelRaw + "' at '" + section.getCurrentPath() + "'");
            }
        }

        Boolean unbreakable   = section.contains("unbreakable") ? section.getBoolean("unbreakable") : null;
        Integer durability    = readInt(section, "durability");

        Set<ItemFlag> flags = parseItemFlags(section.getStringList("item_flags"), log);

        Map<Enchantment, Integer> enchants       = parseEnchantList(section.getStringList("enchantments"), log);
        Map<Enchantment, Integer> storedEnchants = parseEnchantList(section.getStringList("stored_enchants"), log);

        Color rgb         = parseColor(section.getString("rgb"), "rgb", log);
        Color potionColor = parseColor(section.getString("potion_color"), "potion_color", log);

        PotionType basePotion = null;
        String basePotionRaw = section.getString("base_potion");
        if (basePotionRaw != null) {
            try { basePotion = PotionType.valueOf(basePotionRaw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) {
                if (log != null) log.warning("Unknown potion type '" + basePotionRaw + "'");
            }
        }

        List<PotionEffect> potionEffects = parsePotionEffects(section.getStringList("potion_effects"), log);
        List<Pattern> bannerPatterns     = parseBannerPatterns(section.getStringList("banner_meta"), log);

        TrimMaterial trimMaterial = resolveTrimMaterial(section.getString("trim_material"), log);
        TrimPattern  trimPattern  = resolveTrimPattern(section.getString("trim_pattern"), log);

        String skullOwner = section.getString("skull_owner");

        ItemDescriptor descriptor = new ItemDescriptor(
                material,
                displayComp, displayPlain,
                itemNameComp, itemNamePlain,
                lore, lorePlain,
                customModel,
                itemModel,
                unbreakable,
                durability,
                flags,
                enchants,
                storedEnchants,
                rgb,
                potionColor,
                basePotion,
                potionEffects,
                bannerPatterns,
                trimMaterial,
                trimPattern,
                skullOwner
        );

        if (material == null && !hasIdentifyingField(descriptor)) {
            if (log != null) log.warning("Item descriptor at '" + section.getCurrentPath()
                    + "' has no material and no identifying metadata; ignored.");
            return null;
        }
        return descriptor;
    }

    /** True when the descriptor has any non-material constraint, used to skip {@code getItemMeta()} for material-only matches. */
    private static boolean hasMetadataConstraint(ItemDescriptor d) {
        return d.displayNamePlain() != null
                || d.itemNamePlain()  != null
                || d.lorePlain()      != null
                || d.customModelData() != null
                || d.itemModel()      != null
                || d.unbreakable()    != null
                || d.durability()     != null
                || d.itemFlags()      != null
                || d.enchantments()   != null
                || d.storedEnchants() != null
                || d.rgb()            != null
                || d.potionColor()    != null
                || d.basePotion()     != null
                || d.potionEffects()  != null
                || d.bannerPatterns() != null
                || d.trimMaterial()   != null
                || d.trimPattern()    != null
                || d.skullOwner()     != null;
    }

    private static boolean loreContains(List<Component> actualLore, String expectedPlain) {
        for (Component line : actualLore) {
            if (expectedPlain.equals(PLAIN.serialize(line))) return true;
        }
        return false;
    }

    private static boolean hasIdentifyingField(ItemDescriptor d) {
        return d.displayNamePlain() != null
                || d.itemNamePlain()  != null
                || d.lorePlain()      != null
                || d.customModelData() != null
                || d.itemModel()      != null
                || d.enchantments()   != null
                || d.storedEnchants() != null
                || d.basePotion()     != null
                || d.potionEffects()  != null
                || d.bannerPatterns() != null
                || (d.trimMaterial() != null && d.trimPattern() != null)
                || d.skullOwner()     != null
                || d.rgb()            != null
                || d.potionColor()    != null
                || Boolean.TRUE.equals(d.unbreakable());
    }

    private static String firstString(ConfigurationSection section, String... keys) {
        for (String k : keys) {
            String v = section.getString(k);
            if (v != null) return v;
        }
        return null;
    }

    private static Integer readInt(ConfigurationSection section, String... keys) {
        for (String k : keys) {
            if (section.isSet(k)) return section.getInt(k);
        }
        return null;
    }

    private static Set<ItemFlag> parseItemFlags(List<String> raw, Logger log) {
        if (raw.isEmpty()) return null;
        EnumSet<ItemFlag> set = EnumSet.noneOf(ItemFlag.class);
        for (String entry : raw) {
            String name = entry.trim();
            if (name.isEmpty()) continue;
            try { set.add(ItemFlag.valueOf(name.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) {
                if (log != null) log.warning("Unknown ItemFlag: " + entry);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static Map<Enchantment, Integer> parseEnchantList(List<String> raw, Logger log) {
        if (raw.isEmpty()) return null;
        Map<Enchantment, Integer> map = new LinkedHashMap<>(raw.size());
        Registry<Enchantment> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (String entry : raw) {
            String[] parts = entry.split(";", 2);
            if (parts[0].isBlank()) continue;
            String name = parts[0].trim().toLowerCase(Locale.ROOT);
            Enchantment ench = reg.get(NamespacedKey.minecraft(name));
            if (ench == null) {
                if (log != null) log.warning("Unknown enchantment '" + parts[0] + "'");
                continue;
            }
            int level = 1;
            if (parts.length == 2) {
                try { level = Integer.parseInt(parts[1].trim()); }
                catch (NumberFormatException ex) {
                    if (log != null) log.warning("Invalid enchantment level in '" + entry + "'");
                }
            }
            map.put(ench, level);
        }
        return map.isEmpty() ? null : map;
    }

    private static Color parseColor(String raw, String fieldName, Logger log) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            if (log != null) log.warning("Invalid " + fieldName + " value '" + raw + "', expected R,G,B");
            return null;
        }
        try {
            int r = clampByte(Integer.parseInt(parts[0].trim()));
            int g = clampByte(Integer.parseInt(parts[1].trim()));
            int b = clampByte(Integer.parseInt(parts[2].trim()));
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException ex) {
            if (log != null) log.warning("Invalid " + fieldName + " value '" + raw + "'");
            return null;
        }
    }

    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }

    private static List<PotionEffect> parsePotionEffects(List<String> raw, Logger log) {
        if (raw.isEmpty()) return null;
        Registry<PotionEffectType> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        List<PotionEffect> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            String[] parts = entry.split(";");
            PotionEffectType type = reg.get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                if (log != null) log.warning("Unknown potion effect '" + parts[0] + "'");
                continue;
            }
            int duration = parts.length > 1 ? parseIntOrDefault(parts[1], 200) : 200;
            int amplifier = parts.length > 2 ? parseIntOrDefault(parts[2], 0) : 0;
            out.add(new PotionEffect(type, duration, amplifier));
        }
        return out.isEmpty() ? null : out;
    }

    private static List<Pattern> parseBannerPatterns(List<String> raw, Logger log) {
        if (raw.isEmpty()) return null;
        Registry<PatternType> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
        List<Pattern> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            String[] parts = entry.split(";", 2);
            if (parts.length != 2) continue;
            DyeColor color;
            try { color = DyeColor.valueOf(parts[0].trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) {
                if (log != null) log.warning("Unknown banner color '" + parts[0] + "'");
                continue;
            }
            PatternType type = reg.get(NamespacedKey.minecraft(parts[1].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                if (log != null) log.warning("Unknown banner pattern '" + parts[1] + "'");
                continue;
            }
            out.add(new Pattern(color, type));
        }
        return out.isEmpty() ? null : out;
    }

    private static TrimMaterial resolveTrimMaterial(String name, Logger log) {
        if (name == null || name.isBlank()) return null;
        TrimMaterial mat = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL)
                .get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (mat == null && log != null) log.warning("Unknown trim material '" + name + "'");
        return mat;
    }

    private static TrimPattern resolveTrimPattern(String name, Logger log) {
        if (name == null || name.isBlank()) return null;
        TrimPattern pat = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN)
                .get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (pat == null && log != null) log.warning("Unknown trim pattern '" + name + "'");
        return pat;
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    // ---------- Building ----------

    /** Materialises the descriptor into a new {@link ItemStack}, or {@code null} when the material is unavailable. */
    public static ItemStack build(ItemDescriptor desc) {
        Objects.requireNonNull(desc, "desc");
        if (desc.material() == null) return null;
        ItemStack stack = desc.material().build();
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (desc.displayName() != null) meta.displayName(desc.displayName());
        if (desc.itemName()    != null) meta.itemName(desc.itemName());
        if (desc.lore()        != null) meta.lore(desc.lore());

        if (desc.customModelData() != null) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) desc.customModelData().intValue()));
            meta.setCustomModelDataComponent(component);
        }
        if (desc.itemModel()       != null) meta.setItemModel(desc.itemModel());
        if (desc.unbreakable()     != null) meta.setUnbreakable(desc.unbreakable());

        if (desc.durability() != null && meta instanceof Damageable damageable) {
            damageable.setDamage(desc.durability());
        }
        if (desc.itemFlags() != null) {
            meta.addItemFlags(desc.itemFlags().toArray(new ItemFlag[0]));
        }
        if (desc.enchantments() != null) {
            for (Map.Entry<Enchantment, Integer> entry : desc.enchantments().entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        if (desc.storedEnchants() != null && meta instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> entry : desc.storedEnchants().entrySet()) {
                book.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        if (desc.rgb() != null && meta instanceof LeatherArmorMeta armor) {
            armor.setColor(desc.rgb());
        }
        if (desc.potionColor() != null && meta instanceof PotionMeta potion) {
            potion.setColor(desc.potionColor());
        }
        if (desc.basePotion() != null && meta instanceof PotionMeta potion) {
            potion.setBasePotionType(desc.basePotion());
        }
        if (desc.potionEffects() != null && meta instanceof PotionMeta potion) {
            for (PotionEffect effect : desc.potionEffects()) {
                potion.addCustomEffect(effect, true);
            }
        }
        if (desc.bannerPatterns() != null && meta instanceof BannerMeta banner) {
            for (Pattern p : desc.bannerPatterns()) {
                banner.addPattern(p);
            }
        }
        if (desc.trimMaterial() != null && desc.trimPattern() != null && meta instanceof ArmorMeta armor) {
            armor.setTrim(new ArmorTrim(desc.trimMaterial(), desc.trimPattern()));
        }
        if (desc.skullOwner() != null && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(desc.skullOwner()));
        }

        stack.setItemMeta(meta);
        return stack;
    }

    // ---------- Matching ----------

    /** Returns {@code true} when every populated field of {@code desc} is satisfied by {@code candidate}. */
    public static boolean matches(ItemStack candidate, ItemDescriptor desc) {
        if (candidate == null || desc == null) return false;
        if (candidate.getType() == Material.AIR) return false;
        if (desc.material() != null && !desc.material().matches(candidate)) return false;
        if (!hasMetadataConstraint(desc)) return true;

        ItemMeta meta = candidate.getItemMeta();

        if (desc.displayNamePlain() != null) {
            if (meta == null) return false;
            String actual = meta.hasDisplayName() ? PLAIN.serialize(meta.displayName()) : "";
            if (!desc.displayNamePlain().equals(actual)) return false;
        }
        if (desc.itemNamePlain() != null) {
            if (meta == null) return false;
            Component nameComp = meta.itemName();
            String actual = nameComp == null ? "" : PLAIN.serialize(nameComp);
            if (!desc.itemNamePlain().equals(actual)) return false;
        }
        if (desc.lorePlain() != null) {
            if (meta == null) return false;
            List<Component> actualLore = meta.lore();
            if (actualLore == null) return false;
            for (String expected : desc.lorePlain()) {
                if (!loreContains(actualLore, expected)) return false;
            }
        }
        if (desc.customModelData() != null) {
            if (meta == null) return false;
            List<Float> floats = meta.getCustomModelDataComponent().getFloats();
            if (floats.isEmpty()
                    || Float.compare(floats.get(0), desc.customModelData().floatValue()) != 0) return false;
        }
        if (desc.itemModel() != null) {
            if (meta == null) return false;
            if (!desc.itemModel().equals(meta.getItemModel())) return false;
        }
        if (desc.unbreakable() != null) {
            if (meta == null || meta.isUnbreakable() != desc.unbreakable()) return false;
        }
        if (desc.durability() != null) {
            if (!(meta instanceof Damageable d) || d.getDamage() != desc.durability()) return false;
        }
        if (desc.itemFlags() != null) {
            if (meta == null) return false;
            Set<ItemFlag> actual = meta.getItemFlags();
            for (ItemFlag flag : desc.itemFlags()) {
                if (!actual.contains(flag)) return false;
            }
        }
        if (desc.enchantments() != null) {
            Map<Enchantment, Integer> actual = candidate.getEnchantments();
            for (Map.Entry<Enchantment, Integer> entry : desc.enchantments().entrySet()) {
                Integer actualLevel = actual.get(entry.getKey());
                if (actualLevel == null || actualLevel < entry.getValue()) return false;
            }
        }
        if (desc.storedEnchants() != null) {
            if (!(meta instanceof EnchantmentStorageMeta book)) return false;
            Map<Enchantment, Integer> stored = book.getStoredEnchants();
            for (Map.Entry<Enchantment, Integer> entry : desc.storedEnchants().entrySet()) {
                Integer actualLevel = stored.get(entry.getKey());
                if (actualLevel == null || actualLevel < entry.getValue()) return false;
            }
        }
        if (desc.rgb() != null) {
            if (!(meta instanceof LeatherArmorMeta armor) || !desc.rgb().equals(armor.getColor())) return false;
        }
        if (desc.potionColor() != null) {
            if (!(meta instanceof PotionMeta potion) || !desc.potionColor().equals(potion.getColor())) return false;
        }
        if (desc.basePotion() != null) {
            if (!(meta instanceof PotionMeta potion) || potion.getBasePotionType() != desc.basePotion()) return false;
        }
        if (desc.potionEffects() != null) {
            if (!(meta instanceof PotionMeta potion)) return false;
            for (PotionEffect expected : desc.potionEffects()) {
                if (!hasMatchingEffect(potion, expected)) return false;
            }
        }
        if (desc.bannerPatterns() != null) {
            if (!(meta instanceof BannerMeta banner)) return false;
            List<Pattern> actual = banner.getPatterns();
            for (Pattern p : desc.bannerPatterns()) {
                if (!actual.contains(p)) return false;
            }
        }
        if (desc.trimMaterial() != null || desc.trimPattern() != null) {
            if (!(meta instanceof ArmorMeta armor) || !armor.hasTrim()) return false;
            ArmorTrim trim = armor.getTrim();
            if (desc.trimMaterial() != null && !desc.trimMaterial().equals(trim.getMaterial())) return false;
            if (desc.trimPattern()  != null && !desc.trimPattern().equals(trim.getPattern())) return false;
        }
        if (desc.skullOwner() != null) {
            if (!(meta instanceof SkullMeta skull)) return false;
            org.bukkit.OfflinePlayer owner = skull.getOwningPlayer();
            return owner != null && desc.skullOwner().equalsIgnoreCase(owner.getName());
        }
        return true;
    }

    private static boolean hasMatchingEffect(PotionMeta potion, PotionEffect expected) {
        for (PotionEffect actual : potion.getCustomEffects()) {
            if (!actual.getType().equals(expected.getType())) continue;
            if (actual.getAmplifier() < expected.getAmplifier()) continue;
            if (actual.getDuration() < expected.getDuration()) continue;
            return true;
        }
        return false;
    }
}
