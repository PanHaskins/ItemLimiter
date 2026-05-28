package me.panhaskins.itemLimiter.utils.item.materials;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import me.panhaskins.itemLimiter.utils.item.ItemMaterialFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Native textured player head — built from a base64 {@code textures} property via Paper's
 * {@link PlayerProfile} API. Prefix: {@code "basehead-"} (DeluxeMenu convention).
 *
 * <p>No external plugin required.
 */
public final class NativeHeadMaterial implements ItemMaterial {

    private static final String TEXTURES_PROPERTY = "textures";

    private final String base64;

    NativeHeadMaterial(String base64) {
        this.base64 = base64;
    }

    @Override public String id() { return base64; }

    @Override
    public ItemStack build() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, base64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return false;
        PlayerProfile profile = meta.getPlayerProfile();
        if (profile == null) return false;
        for (ProfileProperty property : profile.getProperties()) {
            if (TEXTURES_PROPERTY.equals(property.getName()) && base64.equals(property.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static final class Factory implements ItemMaterialFactory {
        @Override public String prefix() { return "basehead-"; }
        @Override public ItemMaterial parse(String idPart) { return new NativeHeadMaterial(idPart); }
    }
}
