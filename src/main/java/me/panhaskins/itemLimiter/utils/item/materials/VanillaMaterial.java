package me.panhaskins.itemLimiter.utils.item.materials;

import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import me.panhaskins.itemLimiter.utils.item.ItemMaterialFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Fallback material — wraps a vanilla {@link Material}.
 *
 * <p>Registered with prefix {@code ""}. Resolves the id via {@link Material#matchMaterial(String)}
 * which accepts both upper- and lower-case names and the {@code minecraft:} namespace prefix.
 */
public final class VanillaMaterial implements ItemMaterial {

    private final String id;
    private final Material material;

    VanillaMaterial(String id, Material material) {
        this.id = id;
        this.material = material;
    }

    @Override public String id() { return id; }

    @Override
    public ItemStack build() {
        return material == null ? null : new ItemStack(material);
    }

    @Override
    public boolean matches(ItemStack item) {
        return item != null && material != null && item.getType() == material;
    }

    public static final class Factory implements ItemMaterialFactory {
        private final Logger logger;

        public Factory(Logger logger) {
            this.logger = logger;
        }

        @Override public String prefix() { return ""; }

        @Override
        public ItemMaterial parse(String idPart) {
            Material m = Material.matchMaterial(idPart);
            if (m == null) {
                logger.warning("Unknown material in item descriptor: " + idPart);
                return new VanillaMaterial(idPart.toUpperCase(Locale.ROOT), null);
            }
            return new VanillaMaterial(m.name(), m);
        }
    }
}
