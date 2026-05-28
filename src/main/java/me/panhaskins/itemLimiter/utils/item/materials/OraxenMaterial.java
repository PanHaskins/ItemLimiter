package me.panhaskins.itemLimiter.utils.item.materials;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Oraxen custom item. Prefix {@code "oraxen-"}. */
public final class OraxenMaterial extends AbstractExternalMaterial {

    private OraxenMaterial(String id, boolean pluginInstalled) {
        super(id, pluginInstalled);
    }

    @Override
    protected ItemStack buildExternal() {
        return Bridge.build(id());
    }

    @Override
    protected boolean matchesExternal(ItemStack item) {
        return Bridge.matches(item, id());
    }

    /** Class loading deferred until first call — only happens when Oraxen plugin is enabled. */
    private static final class Bridge {
        static ItemStack build(String id) {
            ItemBuilder b = OraxenItems.getItemById(id);
            return b == null ? null : b.build();
        }
        static boolean matches(ItemStack stack, String id) {
            return id.equals(OraxenItems.getIdByItem(stack));
        }
    }

    public static final class Factory extends AbstractExternalMaterial.Factory {
        public Factory(Plugin plugin) {
            super(plugin, "oraxen-", "Oraxen");
        }
        @Override public ItemMaterial parse(String idPart) {
            return new OraxenMaterial(idPart, pluginInstalled);
        }
    }
}
