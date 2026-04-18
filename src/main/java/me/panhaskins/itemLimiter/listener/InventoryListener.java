package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.model.ItemLimiterItem;
import me.panhaskins.itemLimiter.utils.Messager;
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
import java.util.Optional;
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
        plugin.getServer().getScheduler().runTask(plugin, () -> checkInventory(player, null));
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
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (event.getClickedInventory() == null || event.getClickedInventory().equals(player.getInventory())) return;
            ItemStack clickedStack = event.getCurrentItem();
            if (clickedStack == null || clickedStack.getType().isAir()) return;
            if (handleIncomingItem(player, clickedStack)) event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) &&
                event.getView().getTopInventory() != player.getInventory()) {
            if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE ||
                    event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
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
        boolean placing = event.getInventorySlots().stream().anyMatch(slot -> slot >= playerStart);
        if (!placing) return;
        ItemStack draggedStack = event.getOldCursor();
        if (draggedStack.getType().isAir()) return;
        if (handleIncomingItem(player, draggedStack)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Inventory container = event.getView().getTopInventory() == player.getInventory() ?
                    null : event.getView().getTopInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> checkInventory(player, container));
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
            ItemUtils.enforceEnchantmentLimits(stack, items);
            Optional<ItemLimiterItem> optionalItem = items.getItem(stack);
            if (optionalItem.isEmpty()) continue;
            ItemLimiterItem item = optionalItem.get();
            if (!item.worlds().isRestricted(player.getWorld().getName())) continue;

            int limit = item.limit().inInventory();
            if (limit < 0) continue;

            if (limit == 0) {
                extras.add(stack);
                contents[i] = null;
                continue;
            }

            int allowed = limit - kept.getOrDefault(item.key(), 0);
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
            kept.put(item.key(), kept.getOrDefault(item.key(), 0) + stack.getAmount());
        }

        player.getInventory().setContents(contents);
        for (ItemStack extra : extras) {
            if (returnInv != null) {
                Map<Integer, ItemStack> leftover = returnInv.addItem(extra);
                leftover.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
            } else {
                player.getWorld().dropItem(player.getLocation(), extra);
            }
            sendPickupMessage(player);
        }
    }

    private boolean handleIncomingItem(Player player, ItemStack stack) {
        ItemUtils.enforceEnchantmentLimits(stack, items);
        Optional<ItemLimiterItem> optionalItem = items.getItem(stack);
        if (optionalItem.isEmpty()) return false;
        ItemLimiterItem restriction = optionalItem.get();
        if (!restriction.worlds().isRestricted(player.getWorld().getName())) return false;
        int limit = restriction.limit().inInventory();
        if (limit < 0) return false;
        if (limit == 0) {
            sendPickupMessage(player);
            return true;
        }

        int current = ItemUtils.countItems(player, restriction, items, limit);
        int allowed = limit - current;
        if (allowed <= 0) {
            sendPickupMessage(player);
            return true;
        }

        if (stack.getAmount() > allowed) {
            ItemStack allowedStack = stack.clone();
            allowedStack.setAmount(allowed);
            player.getInventory().addItem(allowedStack);
            stack.setAmount(stack.getAmount() - allowed);
            sendPickupMessage(player);
            return true;
        }
        if (!inventoryWarningMsg.isEmpty()) {
            int remaining = allowed - stack.getAmount();
            if (remaining > 0) {
                String msg = inventoryWarningMsg
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%item%", restriction.key());
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
