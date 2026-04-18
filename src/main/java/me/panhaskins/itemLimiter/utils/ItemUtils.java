package me.panhaskins.itemLimiter.utils;

import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.ItemLimiterItem;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Utility methods for enforcing restrictions on ItemStacks. */
public final class ItemUtils {
    private ItemUtils() {}

    /**
     * Returns true if any enchantment on the item exceeds configured max_level.
     * Checks both stored enchants (enchanted books) and regular enchants (gear).
     * Read-only check — does not mutate the stack.
     */
    public static boolean isEnchantmentExceeded(ItemStack stack, ConfigItems items) {
        if (stack == null || !stack.hasItemMeta()) return false;
        if (stack.getType() == Material.ENCHANTED_BOOK
                && stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
                if (enchantExceedsMax(entry.getKey(), entry.getValue(), items)) return true;
            }
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            if (enchantExceedsMax(entry.getKey(), entry.getValue(), items)) return true;
        }
        return false;
    }

    private static boolean enchantExceedsMax(Enchantment enchantment, int level, ConfigItems items) {
        String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
        var restriction = items.getEnchantRestriction(key);
        if (restriction.isEmpty()) return false;
        int max = restriction.get().maxLevel();
        return max <= 0 || level > max;
    }

    /**
     * Counts how many items matching the target config the player has in their inventory.
     */
    public static int countItems(Player player, ItemLimiterItem target, ConfigItems items, int stopAt) {
        int count = 0;
        for (ItemStack invStack : player.getInventory().getContents()) {
            if (invStack == null || invStack.getType().isAir()) continue;
            Optional<ItemLimiterItem> opt = items.getItem(invStack);
            if (opt.isPresent() && opt.get().key().equals(target.key())) {
                count += invStack.getAmount();
                if (stopAt > 0 && count >= stopAt) return count;
            }
        }
        return count;
    }

    /**
     * Removes or downgrades enchantments on the item according to restrictions.
     *
     * @param stack item to check
     * @param items configuration provider
     */
    public static void enforceEnchantmentLimits(ItemStack stack, ConfigItems items) {
        if (stack == null || stack.getType().isAir()) return;

        Map<Enchantment, Integer> enchants = stack.getEnchantments();
        if (!enchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
                items.getEnchantRestriction(key + "_ENCHANT").ifPresent(restriction -> {
                    if (restriction.maxLevel() <= 0 || level > restriction.maxLevel()) {
                        stack.removeEnchantment(enchantment);
                        if (restriction.maxLevel() > 0) {
                            stack.addUnsafeEnchantment(enchantment, restriction.maxLevel());
                        }
                    }
                });
            }
        }

        if (stack.getType() != Material.ENCHANTED_BOOK) return;

        if (stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            boolean changed = false;
            for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(book.getStoredEnchants()).entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
                var restriction = items.getEnchantRestriction(key + "_ENCHANT");
                if (restriction.isEmpty()) continue;
                int maxLevel = restriction.get().maxLevel();
                if (maxLevel <= 0 || level > maxLevel) {
                    book.removeStoredEnchant(enchantment);
                    if (maxLevel > 0) {
                        book.addStoredEnchant(enchantment, maxLevel, true);
                    }
                    changed = true;
                }
            }
            if (changed) stack.setItemMeta(book);
        }
    }
}
