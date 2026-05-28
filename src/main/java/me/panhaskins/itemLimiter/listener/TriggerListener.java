package me.panhaskins.itemLimiter.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.ItemRule;
import me.panhaskins.itemLimiter.model.Trigger;
import me.panhaskins.itemLimiter.utils.Messager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class TriggerListener implements Listener {

    private static final long MESSAGE_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final String cooldownMsg;
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> lastMessageNanos = new ConcurrentHashMap<>();

    public TriggerListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        this.cooldownMsg = plugin.getConfigManager()
                .getConfig("messages.yml")
                .getString("inventory.cooldown", "Please wait %seconds%s before using this item.");
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
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack rod = player.getInventory().getItem(hand);
        if (rod.getType() != Material.FISHING_ROD) return;
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
        ItemStack stack = event.getItem();
        if (stack.getType() == Material.SHEARS) {
            if (handleUse(event.getPlayer(), stack, Trigger.SHEAR)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShearEntity(PlayerShearEntityEvent event) {
        ItemStack stack = event.getItem();
        if (stack.getType() == Material.SHEARS) {
            if (handleUse(event.getPlayer(), stack, Trigger.SHEAR)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        lastMessageNanos.remove(id);
        var cooldownPackets = plugin.getCooldownPackets();
        if (cooldownPackets != null) cooldownPackets.forgetPlayer(id);
    }

    private boolean handleUse(Player player, ItemStack stack, Trigger trigger) {
        ItemRule rule = items.getItem(stack).orElse(null);
        if (rule == null) return false;
        if (!rule.worlds().appliesIn(player.getWorld().getName())) return false;
        if (rule.exception().appliesTo(player, stack)) return false;
        if (!rule.shouldTriggerCooldown(trigger)) return false;

        long now = System.nanoTime();
        long cooldownNanos = TimeUnit.SECONDS.toNanos(rule.cooldown().seconds());
        ConcurrentMap<String, Long> map = cooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>());

        long[] activeSince = { -1L };
        map.compute(rule.key(), (k, last) -> {
            if (last != null && now - last < cooldownNanos) {
                activeSince[0] = last;
                return last;
            }
            return now;
        });

        if (activeSince[0] != -1L) {
            long remainingSec = (cooldownNanos - (now - activeSince[0])) / 1_000_000_000L + 1;
            sendThrottledMessage(player, rule.key(), remainingSec);
            return true;
        }

        Material heldMaterial = stack.getType();
        int cooldownTicks = rule.cooldown().seconds() * 20;
        var cooldownPackets = plugin.getCooldownPackets();
        if (cooldownPackets != null) {
            cooldownPackets.sendCooldown(player, heldMaterial, cooldownTicks);
        } else {
            player.setCooldown(heldMaterial, cooldownTicks);
        }
        return false;
    }

    private void sendThrottledMessage(Player player, String ruleKey, long remainingSec) {
        ConcurrentMap<String, Long> msgMap = lastMessageNanos.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>());
        long now = System.nanoTime();
        Long last = msgMap.get(ruleKey);
        if (last != null && now - last < MESSAGE_THROTTLE_NANOS) return;
        msgMap.put(ruleKey, now);
        player.sendMessage(Messager.translate(cooldownMsg.replace("%seconds%", String.valueOf(remainingSec))));
    }
}
