package me.panhaskins.itemLimiter.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCooldown;
import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.Trigger;
import me.panhaskins.itemLimiter.model.ItemLimiterItem;
import me.panhaskins.itemLimiter.utils.Messager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies and visualizes item usage cooldowns.
 */
public class TriggerListener implements Listener {

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final String cooldownMsg;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<Material, ItemType> typeCache;
    private final Map<UUID, Map<ItemType, Long>> cooldownExpiry;

    public TriggerListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        this.cooldownMsg = plugin.getConfigManager()
                .getConfig("config.yml")
                .getString("messages.cooldown", "Please wait %seconds%s before using this item.");
        if (plugin.hasPacketEvents()) {
            this.typeCache = new EnumMap<>(Material.class);
            for (Field field : ItemTypes.class.getFields()) {
                if (field.getType() == ItemType.class) {
                    Material material = Material.matchMaterial(field.getName());
                    if (material != null) {
                        try {
                            typeCache.put(material, (ItemType) field.get(null));
                        } catch (IllegalAccessException ignored) {
                        }
                    }
                }
            }
            this.cooldownExpiry = new ConcurrentHashMap<>();
            PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
                @Override
                public void onPacketSend(PacketSendEvent event) {
                    if (event.getPacketType() != PacketType.Play.Server.SET_COOLDOWN) return;
                    Player player = (Player) event.getPlayer();
                    if (player == null) return;
                    Map<ItemType, Long> expiry = cooldownExpiry.get(player.getUniqueId());
                    if (expiry == null) return;
                    WrapperPlayServerSetCooldown wrapper = new WrapperPlayServerSetCooldown(event);
                    Long expiresAt = expiry.get(wrapper.getItem());
                    if (expiresAt == null) return;
                    long remaining = expiresAt - System.currentTimeMillis();
                    if (remaining <= 0) {
                        expiry.remove(wrapper.getItem());
                        return;
                    }
                    int ticks = (int) (remaining / 50);
                    if (ticks > wrapper.getCooldownTicks()) {
                        wrapper.setCooldownTicks(ticks);
                    }
                }
            });
        } else {
            this.typeCache = null;
            this.cooldownExpiry = null;
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null) return;
        if (handleUse(event.getPlayer(), stack, Trigger.INTERACT))
            event.setCancelled(true);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (handleUse(event.getPlayer(), event.getItem(), Trigger.CONSUME))
            event.setCancelled(true);

    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack stack = player.getInventory().getItem(EquipmentSlot.HAND);
            if (handleUse(player, stack, Trigger.DAMAGE)) event.setCancelled(true);
        }

    }

    @EventHandler
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (handleUse(event.getPlayer(), event.getItemStack(), Trigger.THROW)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack bow = event.getBow();
            if (bow == null || (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW)) return;
            if (handleUse(player, bow, Trigger.THROW)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFishing(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItem(EquipmentSlot.HAND);
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (handleUse(player, rod, Trigger.THROW)) event.setCancelled(true);
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY ||
                   event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (handleUse(player, rod, Trigger.FISHING)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(EquipmentSlot.HAND);
        if (handleUse(event.getPlayer(), stack, Trigger.BLOCK_BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHarvest(PlayerHarvestBlockEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(EquipmentSlot.HAND);
        if (stack.getType().name().endsWith("_HOE")) {
            if (handleUse(event.getPlayer(), stack, Trigger.BLOCK_BREAK)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        if (!stack.getType().isBlock()) return;
        if (handleUse(event.getPlayer(), stack, Trigger.BLOCK_PLACE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShearBlock(PlayerShearBlockEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(EquipmentSlot.HAND);
        if (stack.getType() == Material.SHEARS) {
            if (handleUse(event.getPlayer(), stack, Trigger.SHEAR)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShearEntity(PlayerShearEntityEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(EquipmentSlot.HAND);
        if (stack.getType() == Material.SHEARS) {
            if (handleUse(event.getPlayer(), stack, Trigger.SHEAR)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        if (cooldownExpiry != null) cooldownExpiry.remove(event.getPlayer().getUniqueId());
    }

    private boolean handleUse(Player player, ItemStack stack, Trigger trigger) {
        Optional<ItemLimiterItem> optionalItem = items.getItem(stack);
        if (optionalItem.isEmpty()) return false;
        ItemLimiterItem restriction = optionalItem.get();
        if (!restriction.shouldTriggerCooldown(trigger)) return false;
        ItemLimiterItem.Cooldown cd = restriction.cooldown();
        Map<String, Long> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = map.get(restriction.key());
        if (last != null && now - last < cd.seconds() * 1000L) {
            long remaining = cd.seconds() - ((now - last) / 1000);
            String msg = cooldownMsg.replace("%seconds%", String.valueOf(remaining));
            player.sendMessage(Messager.translate(msg));
            return true;
        }
        map.put(restriction.key(), now);
        if (cooldownExpiry != null) {
            ItemType type = typeCache.get(stack.getType());
            if (type != null) {
                cooldownExpiry.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(type, now + cd.seconds() * 1000L);
            }
        }
        sendCooldown(player, stack.getType(), cd.seconds() * 20);
        return false;
    }

    private void sendCooldown(Player player, Material material, int ticks) {
        if (plugin.hasPacketEvents()) {
            ItemType type = typeCache.get(material);
            if (type != null) {
                WrapperPlayServerSetCooldown packet = new WrapperPlayServerSetCooldown(type, ticks);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
            }
        } else player.setCooldown(material, ticks);
    }
}
