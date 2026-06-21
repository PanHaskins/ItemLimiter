package me.panhaskins.itemLimiter.utils.item.materials;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Nexo custom item (Oraxen fork). Prefix {@code "nexo-"}. */
public final class NexoMaterial extends AbstractExternalMaterial {

    private NexoMaterial(String id, boolean pluginInstalled) {
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

    /** Class loading deferred until first call, only happens when Nexo plugin is enabled. */
    private static final class Bridge {
        static ItemStack build(String id) {
            ItemBuilder b = NexoItems.itemFromId(id);
            return b == null ? null : b.build();
        }
        static boolean matches(ItemStack stack, String id) {
            return id.equals(NexoItems.idFromItem(stack));
        }
    }

    public static final class Factory extends AbstractExternalMaterial.Factory {
        public Factory(Plugin plugin) {
            super(plugin, "nexo-", "Nexo");
        }
        @Override public ItemMaterial parse(String idPart) {
            return new NexoMaterial(idPart, pluginInstalled);
        }
    }
}
