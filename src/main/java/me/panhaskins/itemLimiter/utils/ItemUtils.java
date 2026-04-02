package me.panhaskins.itemLimiter.utils;

import me.panhaskins.itemLimiter.data.ConfigItems;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Utility methods for sanitizing and validating ItemStacks. */
public final class ItemUtils {
    private ItemUtils() {}

    /**
     * Removes or downgrades enchantments on the item according to restrictions.
     *
     * @param stack item to sanitize
     * @param items configuration provider
     */
    public static void sanitizeEnchantments(ItemStack stack, ConfigItems items) {
        if (stack == null || stack.getType().isAir()) return;
        for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(stack.getEnchantments()).entrySet()) {
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

        if (stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            boolean[] changed = {false};
            for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(book.getStoredEnchants()).entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
                items.getEnchantRestriction(key + "_ENCHANT").ifPresent(restriction -> {
                    if (restriction.maxLevel() <= 0 || level > restriction.maxLevel()) {
                        book.removeStoredEnchant(enchantment);
                        if (restriction.maxLevel() > 0) {
                            book.addStoredEnchant(enchantment, restriction.maxLevel(), true);
                        }
                        changed[0] = true;
                    }
                });
            }
            if (changed[0]) stack.setItemMeta(book);
        }
    }
}
