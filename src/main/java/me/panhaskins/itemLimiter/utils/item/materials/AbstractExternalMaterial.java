package me.panhaskins.itemLimiter.utils.item.materials;

import me.panhaskins.itemLimiter.utils.item.ItemMaterial;
import me.panhaskins.itemLimiter.utils.item.ItemMaterialFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Shared base for material types backed by external plugins.
 *
 * <p>Subclasses implement {@link #buildExternal()} / {@link #matchesExternal(ItemStack)}.
 * The public {@link #build()} / {@link #matches(ItemStack)} entry points are sealed in
 * this class and apply the {@code pluginInstalled} guard before any bridge class is touched.
 */
abstract class AbstractExternalMaterial implements ItemMaterial {

    private final String id;
    protected final boolean pluginInstalled;

    AbstractExternalMaterial(String id, boolean pluginInstalled) {
        this.id = id;
        this.pluginInstalled = pluginInstalled;
    }

    @Override public String id() { return id; }

    @Override
    public final ItemStack build() {
        if (!pluginInstalled) return null;
        return buildExternal();
    }

    @Override
    public final boolean matches(ItemStack item) {
        if (item == null || !pluginInstalled) return false;
        return matchesExternal(item);
    }

    @Override
    public boolean isAvailable() { return pluginInstalled; }

    /** Build an ItemStack from the parsed id. Called only when the plugin is installed. */
    protected abstract ItemStack buildExternal();

    /** Test the candidate against the parsed id. Called only when the plugin is installed and the item is non-null. */
    protected abstract boolean matchesExternal(ItemStack item);

    /**
     * Shared base for {@link ItemMaterialFactory factories} of external materials.
     *
     * <p>Captures {@code pluginInstalled} once via Paper's
     * {@link org.bukkit.plugin.PluginManager#isPluginEnabled(String)} and exposes it
     * to subclasses. Subclasses only implement {@link #parse(String)}.
     */
    abstract static class Factory implements ItemMaterialFactory {
        private final String prefix;
        protected final boolean pluginInstalled;

        Factory(Plugin plugin, String prefix, String requiredPlugin) {
            this.prefix = prefix;
            this.pluginInstalled = plugin.getServer().getPluginManager().isPluginEnabled(requiredPlugin);
        }

        @Override public final String prefix() { return prefix; }
    }
}
