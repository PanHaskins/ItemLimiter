package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.Trigger;
import me.panhaskins.itemLimiter.model.ItemLimiterItem;
import me.panhaskins.itemLimiter.utils.Messager;
import org.bukkit.Material;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TriggerListener implements Listener {

    interface CooldownHelper {
        void sendCooldown(Player player, Material material, int ticks);
        void trackCooldown(UUID playerId, Material material, long expiresAt);
        void onQuit(UUID playerId);
    }

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final String cooldownMsg;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final CooldownHelper packetHelper;

    public TriggerListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        this.cooldownMsg = plugin.getConfigManager()
                .getConfig("messages.yml")
                .getString("inventory.cooldown", "Please wait %seconds%s before using this item.");
        if (plugin.hasPacketEvents()) {
            this.packetHelper = new PacketEventsHelper();
        } else {
            this.packetHelper = null;
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
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        if (packetHelper != null) packetHelper.onQuit(id);
    }

    private boolean handleUse(Player player, ItemStack stack, Trigger trigger) {
        Optional<ItemLimiterItem> optionalItem = items.getItem(stack);
        if (optionalItem.isEmpty()) return false;
        ItemLimiterItem restriction = optionalItem.get();
        if (!restriction.worlds().appliesIn(player.getWorld().getName())) return false;
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
        if (packetHelper != null) {
            packetHelper.trackCooldown(player.getUniqueId(), stack.getType(), now + cd.seconds() * 1000L);
        }
        sendCooldown(player, stack.getType(), cd.seconds() * 20);
        return false;
    }

    private void sendCooldown(Player player, Material material, int ticks) {
        if (packetHelper != null) {
            packetHelper.sendCooldown(player, material, ticks);
        } else {
            player.setCooldown(material, ticks);
        }
    }
}
