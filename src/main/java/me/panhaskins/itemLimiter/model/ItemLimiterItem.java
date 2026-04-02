package me.panhaskins.itemLimiter.model;

import java.util.EnumSet;

/**
 * Configuration entry describing how an item should be limited.
 */
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;

public record ItemLimiterItem(
        String key,
        Material material,
        Enchantment enchantment,
        PotionType potion,
        Limits limit,
        Cooldown cooldown,
        EnumSet<Sources> blacklist
) {
    /** Limits applied to this item. */
    public record Limits(int inInventory, int perPlayer, int global) {}

    /** Cooldown settings for this item. */
    public record Cooldown(int seconds, EnumSet<Trigger> triggers) {}

    /** Returns whether the given source is blocked for this item. */
    public boolean isSourceBlocked(Sources source) {
        return blacklist.contains(source);
    }

    /** Returns true if the cooldown is triggered by the given action. */
    public boolean shouldTriggerCooldown(Trigger trigger) {
        return cooldown.triggers().contains(trigger) && cooldown.seconds() > 0;
    }

    /** True if this configuration refers to an enchanted book. */
    public boolean isEnchantedBook() {
        return enchantment != null;
    }

    /** True if this configuration refers to a potion item. */
    public boolean isPotion() {
        return potion != null;
    }
}
