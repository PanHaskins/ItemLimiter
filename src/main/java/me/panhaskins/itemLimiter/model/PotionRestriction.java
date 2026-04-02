package me.panhaskins.itemLimiter.model;

import java.util.EnumSet;

/**
 * Restriction settings for a specific potion effect.
 */
public record PotionRestriction(
        String name,
        int maxLevel,
        int maxDuration,
        EnumSet<PotionSource> sources
) {
    public enum PotionSource { BREWING, TREASURE, MOBS }
}
