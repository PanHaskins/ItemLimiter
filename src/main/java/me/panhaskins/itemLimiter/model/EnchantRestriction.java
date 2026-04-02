package me.panhaskins.itemLimiter.model;

import java.util.EnumSet;

/**
 * Restriction settings for a specific enchantment.
 */
public record EnchantRestriction(
        String name,
        int maxLevel,
        EnumSet<EnchantSource> sources
) {
    public enum EnchantSource { ENCHANT_TABLE, ANVIL, TREASURE, MOBS }
}
