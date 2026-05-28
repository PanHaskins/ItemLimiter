package me.panhaskins.itemLimiter.utils.item;

import org.bukkit.inventory.ItemStack;

/**
 * Descriptor-facing material reference.
 *
 * <p>Implementations carry the parsed id and provide direct {@link #build()} and
 * {@link #matches(ItemStack)} methods. No registry lookup happens at match-time.
 *
 * <p>Implementations must be immutable and thread-safe.
 */
public interface ItemMaterial {

    /** The raw id without prefix (e.g. {@code "GOLDEN_APPLE"}, {@code "magic_apple"}). */
    String id();

    /** Builds a new ItemStack representing this material; returns {@code null} when unavailable or invalid. */
    ItemStack build();

    /** Returns {@code true} when the given stack is considered an instance of this material. */
    boolean matches(ItemStack item);

    /** {@code false} when the material needs an external plugin that isn't loaded. */
    default boolean isAvailable() { return true; }
}
