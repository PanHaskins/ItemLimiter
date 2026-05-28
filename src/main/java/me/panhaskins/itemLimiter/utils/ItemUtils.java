package me.panhaskins.itemLimiter.utils;

import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.EnchantRestriction;
import me.panhaskins.itemLimiter.model.ItemRule;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

/** Helpers for checking and capping item enchantments. */
public final class ItemUtils {
    private ItemUtils() {}

    /**
     * Returns true if any enchant on the item is over its configured max level.
     * Checks both stored enchants (enchanted books) and regular enchants (gear).
     * Read-only — does not change the stack.
     */
    public static boolean hasOverLimitEnchant(ItemStack stack, ConfigItems items) {
        return hasOverLimitEnchant(stack, items, null);
    }

    /**
     * Same as {@link #hasOverLimitEnchant(ItemStack, ConfigItems)} but skips enchants whose
     * {@code _ENCHANT} rule has a matching exception for {@code player} and {@code stack}.
     */
    public static boolean hasOverLimitEnchant(ItemStack stack, ConfigItems items, Player player) {
        if (stack == null || !stack.hasItemMeta()) return false;
        if (stack.getType() == Material.ENCHANTED_BOOK
                && stack.getItemMeta() instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
                if (isEnchantOverLimit(entry.getKey(), entry.getValue(), items, player, stack)) return true;
            }
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            if (isEnchantOverLimit(entry.getKey(), entry.getValue(), items, player, stack)) return true;
        }
        return false;
    }

    private static boolean isEnchantOverLimit(Enchantment enchantment, int level, ConfigItems items, Player player, ItemStack stack) {
        String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
        boolean bypass = items.getItem(key)
                .map(rule -> rule.exception().appliesTo(player, stack))
                .orElse(false);
        if (bypass) return false;
        int max = items.getEnchantRestriction(key).map(EnchantRestriction::maxLevel).orElse(-1);
        if (max < 0) return false;
        return max == 0 || level > max;
    }

    /**
     * Counts items in the player's inventory that match the target rule.
     * Stacks that the rule's exception covers are skipped — exempt items must not use up the cap.
     */
    public static int countItems(Player player, ItemRule target, ConfigItems items, int stopAt) {
        int count = 0;
        for (ItemStack invStack : player.getInventory().getContents()) {
            if (invStack == null || invStack.getType().isAir()) continue;
            ItemRule rule = items.getItem(invStack).orElse(null);
            if (rule == null || !rule.key().equals(target.key())) continue;
            if (target.exception().appliesTo(player, invStack)) continue;
            count += invStack.getAmount();
            if (stopAt > 0 && count >= stopAt) return count;
        }
        return count;
    }

    /**
     * Removes or lowers enchants on the item to fit configured caps.
     *
     * @param stack item to check
     * @param items configuration provider
     */
    public static void capEnchantments(ItemStack stack, ConfigItems items) {
        capEnchantments(stack, items, null);
    }

    /**
     * Same as {@link #capEnchantments(ItemStack, ConfigItems)} but honours each
     * per-enchant exception when {@code player} or {@code stack} matches it.
     */
    public static void capEnchantments(ItemStack stack, ConfigItems items, Player player) {
        if (stack == null || stack.getType().isAir()) return;

        BiPredicate<String, ItemStack> hasBypass = (enchantKey, ref) ->
                items.getItem(enchantKey)
                        .map(rule -> rule.exception().appliesTo(player, ref))
                        .orElse(false);

        Map<Enchantment, Integer> enchants = stack.getEnchantments();
        if (!enchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                String enchantKey = enchantment.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
                if (hasBypass.test(enchantKey, stack)) continue;
                items.getEnchantRestriction(enchantKey).ifPresent(restriction -> {
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
                String enchantKey = enchantment.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
                if (hasBypass.test(enchantKey, stack)) continue;
                int maxLevel = items.getEnchantRestriction(enchantKey).map(EnchantRestriction::maxLevel).orElse(-1);
                if (maxLevel < 0) continue;
                if (maxLevel == 0 || level > maxLevel) {
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
