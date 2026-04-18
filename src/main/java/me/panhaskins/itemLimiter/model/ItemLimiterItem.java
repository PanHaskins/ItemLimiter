package me.panhaskins.itemLimiter.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

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
        Set<Sources> blacklist,
        Worlds worlds
) {
    public ItemLimiterItem {
        blacklist = blacklist.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(blacklist));
    }

    public record Limits(int inInventory, int perPlayer, int global) {}

    public record Cooldown(int seconds, Set<Trigger> triggers) {
        public Cooldown {
            triggers = triggers.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(triggers));
        }
    }

    public record Worlds(boolean blacklist, Set<String> list) {
        public static final Worlds NONE = new Worlds(true, Set.of());

        public boolean isRestricted(String worldName) {
            if (list.isEmpty()) return true;
            boolean inList = list.contains(worldName.toLowerCase(Locale.ROOT));
            return blacklist != inList;
        }
    }

    public boolean isSourceBlocked(Sources source) {
        return blacklist.contains(source);
    }

    public boolean shouldTriggerCooldown(Trigger trigger) {
        return cooldown.triggers().contains(trigger) && cooldown.seconds() > 0;
    }
}
