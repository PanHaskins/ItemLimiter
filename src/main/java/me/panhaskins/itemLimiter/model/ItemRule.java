package me.panhaskins.itemLimiter.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.panhaskins.itemLimiter.utils.item.ItemBuilder;
import me.panhaskins.itemLimiter.utils.item.ItemDescriptor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

public record ItemRule(
        String key,
        Material material,
        Enchantment enchantment,
        PotionType potion,
        Limits limit,
        Cooldown cooldown,
        Set<Sources> blacklist,
        Worlds worlds,
        Exception exception
) {
    public ItemRule {
        blacklist = blacklist.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(blacklist));
        if (exception == null) exception = Exception.NONE;
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

        /** Returns {@code true} when restrictions apply in this world. */
        public boolean appliesIn(String worldName) {
            if (list.isEmpty()) return true;
            boolean inList = list.contains(worldName.toLowerCase(Locale.ROOT));
            return blacklist != inList;
        }
    }

    /**
     * Bypass rules for the parent restriction.
     *
     * <p>If the player holds any of the {@code permissions}, the entire rule is skipped
     * (no usage tracking, no cooldown, no inventory cap, no source blacklist, no enchant/potion cap).
     * <p>If the {@link ItemStack} subset-matches any of the {@code items} descriptors, the rule is skipped.
     *
     * <p>Both lists are independently optional. {@link #NONE} marks "no exception" and short-circuits.
     */
    public record Exception(List<String> permissions, Map<String, ItemDescriptor> items) {
        public static final Exception NONE = new Exception(List.of(), Map.of());

        public Exception {
            permissions = permissions.isEmpty() ? List.of() : List.copyOf(permissions);
            // Wrap as unmodifiable LinkedHashMap copy to preserve YAML iteration order,
            // so admins can put cheap descriptors first for faster short-circuit at match time.
            items = items.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new java.util.LinkedHashMap<>(items));
        }

        public boolean isEmpty() { return permissions.isEmpty() && items.isEmpty(); }

        public boolean playerHasBypassPermission(Player player) {
            if (player == null || permissions.isEmpty()) return false;
            for (String perm : permissions) {
                if (player.hasPermission(perm)) return true;
            }
            return false;
        }

        public boolean appliesTo(Player player, ItemStack stack) {
            if (isEmpty()) return false;
            if (playerHasBypassPermission(player)) return true;
            if (!items.isEmpty() && stack != null) {
                for (ItemDescriptor desc : items.values()) {
                    if (ItemBuilder.matches(stack, desc)) return true;
                }
            }
            return false;
        }
    }

    public boolean isSourceBlocked(Sources source) {
        return blacklist.contains(source);
    }

    public boolean shouldTriggerCooldown(Trigger trigger) {
        return cooldown.triggers().contains(trigger) && cooldown.seconds() > 0;
    }
}
