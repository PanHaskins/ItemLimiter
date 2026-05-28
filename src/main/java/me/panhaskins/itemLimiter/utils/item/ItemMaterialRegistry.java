package me.panhaskins.itemLimiter.utils.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Registry of {@link ItemMaterialFactory factories} keyed by YAML prefix.
 *
 * <p>Used only at config-parse time. The fallback factory (prefix {@code ""})
 * is required and handles values without a recognised prefix.
 *
 * <p>This class is not thread-safe. All registrations should happen during
 * plugin enable, before any config parsing.
 */
public final class ItemMaterialRegistry {

    private final List<ItemMaterialFactory> prefixed = new ArrayList<>();
    private ItemMaterialFactory fallback;

    /** Registers a factory. Empty prefix sets the fallback (overwrites any previous one). */
    public void register(ItemMaterialFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (factory.prefix().isEmpty()) {
            fallback = factory;
            return;
        }
        prefixed.add(factory);
        prefixed.sort(Comparator.comparingInt((ItemMaterialFactory f) -> f.prefix().length()).reversed());
    }

    /** Parses a raw material value (e.g. {@code "oraxen-magic_apple"}) into a live material object. */
    public ItemMaterial parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        for (ItemMaterialFactory factory : prefixed) {
            String prefix = factory.prefix();
            if (raw.startsWith(prefix)) {
                return factory.parse(raw.substring(prefix.length()));
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("ItemMaterialRegistry has no fallback factory registered");
        }
        return fallback.parse(raw);
    }
}
