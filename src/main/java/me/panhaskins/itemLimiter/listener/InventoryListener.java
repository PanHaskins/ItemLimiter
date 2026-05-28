package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.ItemRule;
import me.panhaskins.itemLimiter.utils.Messager;
import me.panhaskins.itemLimiter.utils.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import me.panhaskins.itemLimiter.utils.ItemUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles inventory based restrictions such as pickup limits and join checks.
 */
public class InventoryListener implements Listener {

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final String inventoryLimitMsg;
    private final String inventoryWarningMsg;
    private final Map<UUID, Long> pickupCooldowns = new ConcurrentHashMap<>();

    public InventoryListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        var msgs = plugin.getConfigManager().getConfig("messages.yml");
        this.inventoryLimitMsg = msgs.getString("inventory.limit_reached", "Restricted item removed");
        this.inventoryWarningMsg = msgs.getString("inventory.limit_warning", "");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerUtil.runForEntity(plugin, player, () -> checkInventory(player, null));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pickupCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack itemStack = event.getItem().getItemStack();
        if (handleIncomingItem(player, itemStack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        Inventory playerInv = player.getInventory();
        InventoryAction action = event.getAction();

        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (clicked == playerInv) return;
            ItemStack clickedStack = event.getCurrentItem();
            if (clickedStack == null || clickedStack.getType().isAir()) return;
            if (handleIncomingItem(player, clickedStack)) event.setCancelled(true);
            return;
        }

        if (clicked == playerInv && event.getView().getTopInventory() != playerInv) {
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                ItemStack cursorStack = event.getCursor();
                if (cursorStack.getType().isAir()) return;
                if (handleIncomingItem(player, cursorStack)) event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory() == player.getInventory()) return;
        int playerStart = event.getView().getTopInventory().getSize();
        boolean placing = false;
        for (int slot : event.getInventorySlots()) {
            if (slot >= playerStart) { placing = true; break; }
        }
        if (!placing) return;
        ItemStack draggedStack = event.getOldCursor();
        if (draggedStack.getType().isAir()) return;
        if (handleIncomingItem(player, draggedStack)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Inventory top = event.getView().getTopInventory();
            Inventory container = top == player.getInventory() ? null : top;
            SchedulerUtil.runForEntity(plugin, player, () -> checkInventory(player, container));
        }
    }

    /**
     * Ensures a player's inventory does not exceed configured item limits.
     * In_inventory values of {@code 0} completely ban the item, negative values
     * allow unlimited amounts.
     */
    private void checkInventory(Player player, Inventory returnInv) {
        ItemStack[] contents = player.getInventory().getContents();
        Map<String, Integer> kept = new HashMap<>(contents.length);
        List<ItemStack> extras = new ArrayList<>(contents.length);

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;
            ItemUtils.capEnchantments(stack, items, player);
            ItemRule rule = items.getItem(stack).orElse(null);
            if (rule == null) continue;
            if (!rule.worlds().appliesIn(player.getWorld().getName())) continue;
            if (rule.exception().appliesTo(player, stack)) continue;

            int limit = rule.limit().inInventory();
            if (limit < 0) continue;
            String key = rule.key();

            if (limit == 0) {
                extras.add(stack);
                contents[i] = null;
                continue;
            }

            int allowed = limit - kept.getOrDefault(key, 0);
            if (allowed <= 0) {
                extras.add(stack);
                contents[i] = null;
                continue;
            }

            if (stack.getAmount() > allowed) {
                ItemStack extra = stack.clone();
                extra.setAmount(stack.getAmount() - allowed);
                extras.add(extra);
                stack.setAmount(allowed);
            }
            kept.merge(key, stack.getAmount(), Integer::sum);
        }

        player.getInventory().setContents(contents);
        for (ItemStack extra : extras) {
            if (returnInv != null) {
                Map<Integer, ItemStack> leftover = returnInv.addItem(extra);
                leftover.values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            } else {
                player.getWorld().dropItem(player.getLocation(), extra);
            }
        }
        if (!extras.isEmpty()) sendPickupMessage(player);
    }

    private boolean handleIncomingItem(Player player, ItemStack stack) {
        ItemUtils.capEnchantments(stack, items, player);
        ItemRule rule = items.getItem(stack).orElse(null);
        if (rule == null) return false;
        if (!rule.worlds().appliesIn(player.getWorld().getName())) return false;
        if (rule.exception().appliesTo(player, stack)) return false;
        int limit = rule.limit().inInventory();
        if (limit < 0) return false;
        if (limit == 0) {
            sendPickupMessage(player);
            return true;
        }

        int current = ItemUtils.countItems(player, rule, items, limit);
        int allowed = limit - current;
        if (allowed <= 0) {
            sendPickupMessage(player);
            return true;
        }

        if (stack.getAmount() > allowed) {
            ItemStack allowedStack = stack.clone();
            allowedStack.setAmount(allowed);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(allowedStack);
            int actuallyAdded = allowed;
            for (ItemStack remaining : leftover.values()) {
                actuallyAdded -= remaining.getAmount();
            }
            stack.setAmount(stack.getAmount() - actuallyAdded);
            sendPickupMessage(player);
            return true;
        }
        if (!inventoryWarningMsg.isEmpty()) {
            int remaining = allowed - stack.getAmount();
            if (remaining > 0) {
                String msg = inventoryWarningMsg
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%item%", rule.key());
                player.sendMessage(Messager.translate(msg));
            }
        }
        return false;
    }

    private void sendPickupMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = pickupCooldowns.get(player.getUniqueId());
        if (last == null || now - last >= 2000L) {
            pickupCooldowns.put(player.getUniqueId(), now);
            player.sendMessage(Messager.translate(inventoryLimitMsg));
        }
    }
}
