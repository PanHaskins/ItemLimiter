package me.panhaskins.itemLimiter.cooldown;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCooldown;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.model.ItemRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Hides the visual cooldown overlay on exception items by replacing them in
 * outgoing packets with a clone that carries a different {@code use_cooldown}
 * group. Vanilla material cooldown then skips them because the client groups
 * items by the {@code cooldown_group} string instead of by material.
 *
 * <p>Tagging path mirrors Spice-of-Life: convert the packet item to Bukkit,
 * clone it, apply the component through Paper's data-component API, then go
 * back through {@link SpigotConversionUtil#fromBukkitItemStack} so the wire
 * patch is identical to what the server would emit natively. That avoids the
 * hash desync that broke drag/click when components were edited in-place.
 */
public final class CooldownPacketListener {

    /**
     * Paper rejects {@code useCooldown(0f)}. The value is irrelevant — the
     * component only carries our {@code cooldown_group}; we never trigger
     * that group via {@code SET_COOLDOWN}.
     */
    private static final float EXCEPTION_TAG_SECONDS = Float.MIN_VALUE;
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final ItemLimiter plugin;
    private final PacketListenerCommon outgoingHandle;
    private volatile Map<Material, List<CachedRule>> rulesByMaterial = Map.of();
    private volatile boolean hasTaggableRules = false;

    /**
     * Per-player end time (nanoTime) for each material we sent a cooldown for.
     * Used to override vanilla {@code SET_COOLDOWN} packets that would shorten
     * our overlay — e.g. vanilla ender pearl emits a 1-tick cooldown right
     * after our longer one and the client picks the last packet.
     */
    private final Map<UUID, Map<Material, Long>> activeCooldownExpiries = new ConcurrentHashMap<>();

    public CooldownPacketListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.outgoingHandle = PacketEvents.getAPI().getEventManager().registerListener(
                new PacketListenerAbstract(PacketListenerPriority.LOWEST) {
                    @Override
                    public void onPacketSend(PacketSendEvent event) {
                        tagItemsInPacket(event);
                    }
                });
    }

    public void rebuildCache(Map<Material, List<ItemRule>> newIndex) {
        EnumMap<Material, List<CachedRule>> built = new EnumMap<>(Material.class);
        for (Map.Entry<Material, List<ItemRule>> e : newIndex.entrySet()) {
            List<CachedRule> cached = e.getValue().stream()
                    .filter(CooldownPacketListener::isTaggable)
                    .map(CachedRule::of)
                    .toList();
            if (!cached.isEmpty()) built.put(e.getKey(), cached);
        }
        this.rulesByMaterial = built;
        this.hasTaggableRules = !built.isEmpty();
    }

    public boolean hasTaggableRules() {
        return hasTaggableRules;
    }

    private static boolean isTaggable(ItemRule rule) {
        return rule.cooldown().seconds() > 0
                && !rule.cooldown().triggers().isEmpty()
                && !rule.exception().isEmpty();
    }

    public void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(outgoingHandle);
    }

    /**
     * Sends a client-only cooldown overlay for the given material. The
     * server-side cooldown map is intentionally left untouched — vanilla gates
     * many actions (eat, throw, right-click) on that map and would block
     * exception items of the same material. Per-rule rate limiting lives in
     * {@code TriggerListener}.
     */
    public void sendCooldown(Player player, Material material, int ticks) {
        long expiryNanos = System.nanoTime() + ticks * NANOS_PER_TICK;
        activeCooldownExpiries
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(material, expiryNanos);
        NamespacedKey matKey = material.getKey();
        ResourceLocation key = new ResourceLocation(matKey.getNamespace(), matKey.getKey());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerSetCooldown(key, ticks));
    }

    public void forgetPlayer(UUID uuid) {
        activeCooldownExpiries.remove(uuid);
    }

    private void tagItemsInPacket(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_COOLDOWN) {
            if (overrideVanillaCooldown(new WrapperPlayServerSetCooldown(event), player)) {
                event.markForReEncode(true);
            }
            return;
        }

        if (!hasTaggableRules) return;

        boolean changed = switch (event.getPacketType()) {
            case PacketType.Play.Server.SET_SLOT -> {
                WrapperPlayServerSetSlot w = new WrapperPlayServerSetSlot(event);
                yield tagSingle(w.getItem(), player, w::setItem);
            }
            case PacketType.Play.Server.SET_PLAYER_INVENTORY -> {
                WrapperPlayServerSetPlayerInventory w = new WrapperPlayServerSetPlayerInventory(event);
                yield tagSingle(w.getStack(), player, w::setStack);
            }
            case PacketType.Play.Server.SET_CURSOR_ITEM -> {
                WrapperPlayServerSetCursorItem w = new WrapperPlayServerSetCursorItem(event);
                yield tagSingle(w.getStack(), player, w::setStack);
            }
            case PacketType.Play.Server.WINDOW_ITEMS -> tagWindowItems(new WrapperPlayServerWindowItems(event), player);
            default -> false;
        };

        if (changed) event.markForReEncode(true);
    }

    private boolean tagSingle(ItemStack stack, Player player, Consumer<ItemStack> setter) {
        ItemStack tagged = tagExceptionItem(stack, player);
        if (tagged == stack) return false;
        setter.accept(tagged);
        return true;
    }

    private boolean tagWindowItems(WrapperPlayServerWindowItems w, Player player) {
        List<ItemStack> items = w.getItems();
        boolean any = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack original = items.get(i);
            ItemStack tagged = tagExceptionItem(original, player);
            if (tagged != original) {
                items.set(i, tagged);
                any = true;
            }
        }
        Optional<ItemStack> carried = w.getCarriedItem();
        if (carried.isPresent()) {
            ItemStack tagged = tagExceptionItem(carried.get(), player);
            if (tagged != carried.get()) {
                w.setCarriedItem(tagged);
                any = true;
            }
        }
        return any;
    }

    private boolean overrideVanillaCooldown(WrapperPlayServerSetCooldown w, Player player) {
        Map<Material, Long> playerCooldowns = activeCooldownExpiries.get(player.getUniqueId());
        if (playerCooldowns == null || playerCooldowns.isEmpty()) return false;

        ResourceLocation group = w.getCooldownGroup();
        Material material = Registry.MATERIAL.get(new NamespacedKey(group.getNamespace(), group.getKey()));
        if (material == null) return false;

        Long expiry = playerCooldowns.get(material);
        if (expiry == null) return false;

        long remainingNanos = expiry - System.nanoTime();
        if (remainingNanos <= 0) {
            playerCooldowns.remove(material);
            return false;
        }

        int remainingTicks = (int) (remainingNanos / NANOS_PER_TICK);
        if (remainingTicks <= w.getCooldownTicks()) return false;

        w.setCooldownTicks(remainingTicks);
        return true;
    }

    /**
     * Returns a tagged copy when the packet item is an exception, otherwise
     * the original instance. The replacement is a {@code clone()} of the
     * Bukkit stack with our {@code use_cooldown} group applied via Paper's
     * data-component API and converted back through PE's NMS codec — the
     * resulting wire patch matches what the server would emit if the item
     * legitimately carried the component, so client/server hashes agree.
     */
    private ItemStack tagExceptionItem(ItemStack packetStack, Player player) {
        if (packetStack == null || packetStack.isEmpty()) return packetStack;
        Material material = SpigotConversionUtil.toBukkitItemMaterial(packetStack.getType());
        if (material == null) return packetStack;
        List<CachedRule> candidates = rulesByMaterial.get(material);
        if (candidates == null) return packetStack;

        if (candidates.size() == 1
                && candidates.getFirst().rule().exception().playerHasBypassPermission(player)) {
            return packetStack;
        }

        org.bukkit.inventory.ItemStack bukkitStack = SpigotConversionUtil.toBukkitItemStack(packetStack);
        ItemRule rule = plugin.getItems().getItem(bukkitStack).orElse(null);
        if (rule == null) return packetStack;
        if (!rule.worlds().appliesIn(player.getWorld().getName())) return packetStack;
        if (!rule.exception().appliesTo(player, bukkitStack)) return packetStack;
        CachedRule cached = findCached(candidates, rule.key());
        if (cached == null) return packetStack;

        org.bukkit.inventory.ItemStack tagged = bukkitStack.clone();
        tagged.setData(
                DataComponentTypes.USE_COOLDOWN,
                UseCooldown.useCooldown(EXCEPTION_TAG_SECONDS).cooldownGroup(cached.exceptionGroupKey())
        );
        return SpigotConversionUtil.fromBukkitItemStack(tagged);
    }

    private static CachedRule findCached(List<CachedRule> list, String ruleKey) {
        for (CachedRule cached : list) if (cached.rule().key().equals(ruleKey)) return cached;
        return null;
    }

    private static NamespacedKey exceptionGroup(ItemRule rule) {
        return new NamespacedKey("itemlimiter", cleanKey(rule.key()) + "_exception");
    }

    private static String cleanKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == '/';
            out.append(ok ? c : '_');
        }
        return out.toString();
    }

    private record CachedRule(ItemRule rule, NamespacedKey exceptionGroupKey) {
        static CachedRule of(ItemRule rule) {
            return new CachedRule(rule, exceptionGroup(rule));
        }
    }
}
