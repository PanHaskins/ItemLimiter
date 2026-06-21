package me.panhaskins.itemLimiter.utils.item.materials;

import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** MMOItems custom item. Prefix {@code "mmoitems-"}. Id format: {@code TYPE:ID} (e.g. {@code SWORD:CUTLASS}). */
public final class MMOItemsMaterial extends AbstractExternalMaterial {

    private final String typeId;
    private final String itemId;

    private MMOItemsMaterial(String id, boolean pluginInstalled, String typeId, String itemId) {
        super(id, pluginInstalled);
        this.typeId = typeId;
        this.itemId = itemId;
    }

    @Override
    protected ItemStack buildExternal() {
        return Bridge.build(typeId, itemId);
    }

    @Override
    protected boolean matchesExternal(ItemStack item) {
        return Bridge.matches(item, typeId, itemId);
    }

    /** Class loading deferred until first call, only happens when MMOItems plugin is enabled. */
    private static final class Bridge {
        static ItemStack build(String typeId, String itemId) {
            MMOItems mmo = MMOItems.plugin;
            Type type = mmo.getTypes().get(typeId);
            return type == null ? null : mmo.getItem(type, itemId);
        }
        static boolean matches(ItemStack stack, String typeId, String itemId) {
            if (stack.getType().isAir()) return false;
            String id = MMOItems.getID(stack);
            if (!itemId.equals(id)) return false;
            Type type = MMOItems.getType(stack);
            return type != null && typeId.equalsIgnoreCase(type.getId());
        }
    }

    public static final class Factory extends AbstractExternalMaterial.Factory {
        public Factory(Plugin plugin) {
            super(plugin, "mmoitems-", "MMOItems");
        }
        @Override public ItemMaterial parse(String idPart) {
            int colon = idPart.indexOf(':');
            boolean valid = colon > 0 && colon < idPart.length() - 1;
            String typeId = valid ? idPart.substring(0, colon) : "";
            String itemId = valid ? idPart.substring(colon + 1) : "";
            return new MMOItemsMaterial(idPart, pluginInstalled && valid, typeId, itemId);
        }
    }
}
