package me.panhaskins.itemLimiter.utils.item.materials;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** HeadDatabase head lookup. Prefix {@code "hdb-"}. */
public final class HeadDatabaseMaterial extends AbstractExternalMaterial {

    private HeadDatabaseMaterial(String id, boolean pluginInstalled) {
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

    /** Class loading deferred until first call — only happens when HeadDatabase plugin is enabled. */
    private static final class Bridge {
        private static final HeadDatabaseAPI API = new HeadDatabaseAPI();

        static ItemStack build(String id) {
            return API.getItemHead(id);
        }
        static boolean matches(ItemStack stack, String id) {
            return id.equals(API.getItemID(stack));
        }
    }

    public static final class Factory extends AbstractExternalMaterial.Factory {
        public Factory(Plugin plugin) {
            super(plugin, "hdb-", "HeadDatabase");
        }
        @Override public ItemMaterial parse(String idPart) {
            return new HeadDatabaseMaterial(idPart, pluginInstalled);
        }
    }
}
