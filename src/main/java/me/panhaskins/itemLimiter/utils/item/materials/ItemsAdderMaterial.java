package me.panhaskins.itemLimiter.utils.item.materials;

import dev.lone.itemsadder.api.CustomStack;
import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** ItemsAdder custom item. Prefix {@code "itemsadder-"}. Expects full namespaced id ({@code namespace:id}). */
public final class ItemsAdderMaterial extends AbstractExternalMaterial {

    private ItemsAdderMaterial(String id, boolean pluginInstalled) {
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

    /** Class loading deferred until first call, only happens when ItemsAdder plugin is enabled. */
    private static final class Bridge {
        static ItemStack build(String id) {
            CustomStack cs = CustomStack.getInstance(id);
            return cs == null ? null : cs.getItemStack();
        }
        static boolean matches(ItemStack stack, String id) {
            CustomStack cs = CustomStack.byItemStack(stack);
            return cs != null && id.equals(cs.getNamespacedID());
        }
    }

    public static final class Factory extends AbstractExternalMaterial.Factory {
        public Factory(Plugin plugin) {
            super(plugin, "itemsadder-", "ItemsAdder");
        }
        @Override public ItemMaterial parse(String idPart) {
            return new ItemsAdderMaterial(idPart, pluginInstalled);
        }
    }
}
