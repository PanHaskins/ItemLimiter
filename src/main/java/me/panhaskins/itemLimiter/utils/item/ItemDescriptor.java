package me.panhaskins.itemLimiter.utils.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable description of an ItemStack derived from a YAML configuration.
 *
 * <p>{@code null} fields mean "not specified in config, ignore during matching".
 * Lists and maps are wrapped immutable on construction. Values are pre-resolved at
 * parse time, so the matcher does no string parsing or registry lookups on the hot path.
 *
 * <p>{@code material} may be {@code null} for descriptors built in material-optional
 * contexts (currently only {@code exception.items.*} in {@code items.yml}).
 */
public record ItemDescriptor(
        ItemMaterial material,
        Component displayName,         // null if no display_name configured
        String displayNamePlain,       // pre-serialised, for exact comparison
        Component itemName,
        String itemNamePlain,
        List<Component> lore,          // null if no lore configured, pre-translated
        List<String> lorePlain,        // pre-serialised, for subset comparison
        Integer customModelData,
        NamespacedKey itemModel,
        Boolean unbreakable,
        Integer durability,
        Set<ItemFlag> itemFlags,
        Map<Enchantment, Integer> enchantments,
        Map<Enchantment, Integer> storedEnchants,
        Color rgb,
        Color potionColor,
        PotionType basePotion,
        List<PotionEffect> potionEffects,
        List<Pattern> bannerPatterns,
        TrimMaterial trimMaterial,
        TrimPattern trimPattern,
        String skullOwner
) {
    public ItemDescriptor {
        lore             = lore             == null ? null : List.copyOf(lore);
        lorePlain        = lorePlain        == null ? null : List.copyOf(lorePlain);
        itemFlags        = itemFlags        == null || itemFlags.isEmpty()      ? null : Collections.unmodifiableSet(EnumSet.copyOf(itemFlags));
        enchantments     = enchantments     == null || enchantments.isEmpty()   ? null : Map.copyOf(enchantments);
        storedEnchants   = storedEnchants   == null || storedEnchants.isEmpty() ? null : Map.copyOf(storedEnchants);
        potionEffects    = potionEffects    == null || potionEffects.isEmpty()  ? null : List.copyOf(potionEffects);
        bannerPatterns   = bannerPatterns   == null || bannerPatterns.isEmpty() ? null : List.copyOf(bannerPatterns);
    }
}
