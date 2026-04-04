package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.data.UsageTracker;
import me.panhaskins.itemLimiter.model.Sources;
import me.panhaskins.itemLimiter.utils.Messager;
import me.panhaskins.itemLimiter.model.EnchantRestriction;
import me.panhaskins.itemLimiter.model.PotionRestriction;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.world.LootGenerateEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import me.panhaskins.itemLimiter.utils.ItemUtils;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import me.panhaskins.itemLimiter.model.ItemLimiterItem;

import java.util.Locale;

/**
 * Handles all item acquisition sources and removes blocked items.
 */
public class SourceListener implements Listener {

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final UsageTracker usage;
    private final String blockedMsg;
    private final Map<Integer, String> notifyMessages;
    private final String alwaysMessage;

    public SourceListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        this.usage = plugin.getUsageTracker();
        FileConfiguration cfg = plugin.getConfigManager().getConfig("config.yml");
        this.blockedMsg = plugin.getConfigManager().getConfig("messages.yml")
                .getString("loot.blocked", "Loot blocked");
        ConfigurationSection notifySec = cfg.getConfigurationSection("notification.sources");
        Map<Integer, String> map = new HashMap<>(notifySec != null ? notifySec.getKeys(false).size() : 0);
        String always = null;
        if (notifySec != null) {
            for (String k : notifySec.getKeys(false)) {
                if (k.equalsIgnoreCase("always")) {
                    always = notifySec.getString(k);
                } else {
                    try {
                        int t = Integer.parseInt(k);
                        String m = notifySec.getString(k);
                        if (m != null) map.put(t, m);
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        this.notifyMessages = map;
        this.alwaysMessage = always;
    }


    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Item item)
            if (processItem(item.getItemStack(), Sources.FISHING, event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Messager.translate(blockedMsg));
            }
    }

    @EventHandler
    public void onBarter(PiglinBarterEvent event) {
        event.getOutcome().removeIf(stack -> processItem(stack, Sources.BARTERING));
    }

    @EventHandler
    public void onBrew(BrewEvent event) {
        List<ItemStack> results = event.getResults();
        for (int i = 0; i < results.size(); i++) {
            if (processItem(results.get(i), Sources.BREWING)) {
                results.set(i, null);
            }
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        Player player = event.getEntity() instanceof Player p ? p : null;
        event.getLoot().removeIf(stack -> processItem(stack, Sources.TREASURE, player));
    }

    @EventHandler
    public void onBlockDrops(BlockDropItemEvent event) {
        event.getItems().removeIf(i -> processItem(i.getItemStack(), Sources.BLOCK_DROPS, event.getPlayer()));
    }

    @EventHandler
    public void onPlayerShear(PlayerShearEntityEvent event) {
        event.getDrops().removeIf(stack -> processItem(stack, Sources.SHEARING, event.getPlayer()));
    }

    @EventHandler
    public void onBlockShear(BlockShearEntityEvent event) {
        event.getDrops().removeIf(stack -> processItem(stack, Sources.SHEARING));
    }

    @EventHandler
    public void onMobDrops(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Mob mob)) return;
        Item item = event.getItemDrop();
        if (processItem(item.getItemStack(), Sources.MOB_DROPS, mob.getKiller())) {
            item.remove();
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        ItemStack stack = new ItemStack(event.getItemType(), event.getItemAmount());
        Player player = event.getPlayer();
        if (processItem(stack, Sources.FURNACE, player)) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> player.getInventory().removeItem(stack));
            event.setExpToDrop(0);
        }
    }

    @EventHandler
    public void onPrepareSmith(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (processItem(result, Sources.SMITHING, player, false)) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onSmith(SmithItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (processItem(result, Sources.SMITHING, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVillagerTrade(PlayerTradeEvent event) {
        ItemStack result = event.getTrade().getResult();
        if (processItem(result, Sources.TRADING, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTradeSelect(TradeSelectEvent event) {
        org.bukkit.inventory.MerchantRecipe recipe = event.getInventory().getSelectedRecipe();
        if (recipe == null) return;
        ItemStack result = recipe.getResult();
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (processItem(result, Sources.TRADING, player, false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (processItem(result, Sources.ANVIL, player, false)) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onAnvilResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (processItem(result, Sources.ANVIL, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (processItem(item, Sources.ENCHANTING, event.getEnchanter(), false)) {
            event.setCancelled(true);
        }
        for (EnchantmentOffer offer : event.getOffers()) {
            if (offer == null) continue;
            String key = offer.getEnchantment().getKey().getKey().toUpperCase(Locale.ROOT);
            items.getEnchantRestriction(key + "_ENCHANT").ifPresent(res -> applyOfferRestriction(offer, res));
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (processItem(event.getItem(), Sources.ENCHANTING, event.getEnchanter())) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (processItem(result.clone(), Sources.CRAFTING, player, false, false)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.isCancelled()) return;
        ItemStack result = event.getRecipe().getResult();
        if (result.getType().isAir()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (processItem(result.clone(), Sources.CRAFTING, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.isCancelled()) return;
        ItemStack result = event.getResult();
        if (result.getType().isAir()) return;
        if (processItem(result.clone(), Sources.CRAFTING, null, true, true, event.getBlock())) event.setCancelled(true);
    }

    private boolean processItem(ItemStack stack, Sources source) {
        return processItem(stack, source, null, true, true);
    }

    private boolean processItem(ItemStack stack, Sources source, Player player) {
        return processItem(stack, source, player, true, true);
    }

    private boolean processItem(ItemStack stack, Sources source, Player player, boolean recordUsage) {
        return processItem(stack, source, player, recordUsage, true);
    }

    /**
     * Validates and optionally records acquisition of an item from a given source.
     * <p>
     * blacklist_sources define the baseline ban. When an item source is blacklisted,
     * the per-player and global values in {@code limit.sources} act as allowances
     * that can override the blacklist up to the specified amounts.
     * A value of {@code 0} means no acquisitions are allowed, a negative value
     * indicates unlimited allowance. If the item is not blacklisted the source
     * limits are ignored.
     */
    private boolean processItem(ItemStack stack, Sources source, Player player, boolean recordUsage, boolean checkBlacklist) {
        return processItem(stack, source, player, recordUsage, checkBlacklist, null);
    }

    private boolean processItem(ItemStack stack, Sources source, Player player, boolean recordUsage, boolean checkBlacklist, Block crafterBlock) {
        if (stack == null) {
            return false;
        }

        Optional<ItemLimiterItem> optionalItem = items.getItem(stack);
        ItemLimiterItem item = optionalItem.orElse(null);

        if (item != null && player != null && !item.worlds().appliesIn(player.getWorld().getName())) {
            item = null;
        }
        if (item != null && player == null && crafterBlock != null
                && !item.worlds().appliesIn(crafterBlock.getWorld().getName())) {
            item = null;
        }

        boolean blockedByConfig = item != null && item.isSourceBlocked(source);

        ItemUtils.sanitizeEnchantments(stack, items);
        if (stack.getType().name().contains("POTION") && stack.getItemMeta() instanceof PotionMeta meta) {
            if (isPotionInvalid(meta)) return true;

            PotionType base = meta.getBasePotionType();
            List<PotionEffect> potionEffectList = base.getPotionEffects();
            if (!potionEffectList.isEmpty()) {
                PotionEffect first = potionEffectList.getFirst();
                adjustPotion(meta, first.getType(), first.getAmplifier() + 1, first.getDuration());
            }
            for (PotionEffect e : meta.getCustomEffects()) {
                adjustPotion(meta, e.getType(), e.getAmplifier() + 1, e.getDuration());
            }
            stack.setItemMeta(meta);
        }

        if (item == null) {
            return false;
        }

        int globalLimit = item.limit().global();
        int playerLimit = item.limit().perPlayer();

        // If the source is not blocked we ignore limit tracking
        if (!blockedByConfig) {
            return false;
        }

        if (globalLimit == 0 || playerLimit == 0) {
            return true;
        }

        if (globalLimit > 0 && (playerLimit < 0 || playerLimit > globalLimit)) {
            playerLimit = globalLimit;
        }

        if (player != null && playerLimit > 0) {
            if (usage.tryIncrement(player.getUniqueId(), item.key(), "sources", playerLimit)) {
                return true;
            }
        }

        if (globalLimit > 0) {
            if (usage.tryIncrement(UsageTracker.GLOBAL_UUID, item.key(), "sources", globalLimit)) {
                return true;
            }
        }

        if (recordUsage) {
            if (player != null && playerLimit > 0) {
                int remaining = playerLimit - usage.getPlayerUsage(player.getUniqueId(), item.key(), "sources");
                String warn = notifyMessages.getOrDefault(remaining, alwaysMessage);
                if (warn != null) {
                    String name = plugin.getConfigManager().getConfig("config.yml")
                            .getString("sources." + source.name().toLowerCase(Locale.ROOT), source.name());
                    org.bukkit.Location loc = player.getLocation();
                    warn = warn.replace("%player%", player.getName())
                            .replace("%item%", item.key())
                            .replace("%source_name%", name)
                            .replace("%left_sources%", String.valueOf(remaining))
                            .replace("%x%", String.valueOf(loc.getBlockX()))
                            .replace("%y%", String.valueOf(loc.getBlockY()))
                            .replace("%z%", String.valueOf(loc.getBlockZ()));
                    plugin.getServer().broadcast(Messager.translate(warn));
                }
            } else if (crafterBlock != null) {
                int remaining = globalLimit > 0
                        ? globalLimit - usage.getGlobalUsage(item.key(), "sources")
                        : -1;
                String warn = remaining >= 0
                        ? notifyMessages.getOrDefault(remaining, alwaysMessage)
                        : alwaysMessage;
                if (warn != null) {
                    org.bukkit.Location loc = crafterBlock.getLocation();
                    String name = plugin.getConfigManager().getConfig("config.yml")
                            .getString("sources." + source.name().toLowerCase(Locale.ROOT), source.name());
                    warn = warn.replace("%player%", "AutoCrafter")
                            .replace("%item%", item.key())
                            .replace("%source_name%", name)
                            .replace("%left_sources%", remaining >= 0 ? String.valueOf(remaining) : "∞")
                            .replace("%x%", String.valueOf(loc.getBlockX()))
                            .replace("%y%", String.valueOf(loc.getBlockY()))
                            .replace("%z%", String.valueOf(loc.getBlockZ()));
                    plugin.getServer().broadcast(Messager.translate(warn));
                }
            }
        }

        return false;
    }



    private void adjustPotion(PotionMeta meta, PotionEffectType type, int level, int duration) {
        items.getPotionRestriction(type).ifPresent(res -> {
            if (res.maxLevel() <= 0) {
                meta.removeCustomEffect(type);
                return;
            }

            int cappedLevel = level;
            int cappedDuration = duration;

            if (level > res.maxLevel()) {
                cappedLevel = res.maxLevel();
            }
            int maxDurationTicks = res.maxDuration() * 20;
            if (maxDurationTicks > 0 && duration > maxDurationTicks) {
                cappedDuration = maxDurationTicks;
            }

            if (cappedLevel != level || cappedDuration != duration) {
                meta.removeCustomEffect(type);
                meta.addCustomEffect(new PotionEffect(type, cappedDuration, cappedLevel - 1), true);
            }
        });
    }

    private boolean isPotionInvalid(PotionMeta meta) {
        PotionType base = meta.getBasePotionType();
        for (PotionEffect e : base.getPotionEffects()) {
            if (effectExceedsLimit(e.getType(), e.getAmplifier() + 1)) {
                return true;
            }
        }
        for (PotionEffect e : meta.getCustomEffects()) {
            if (effectExceedsLimit(e.getType(), e.getAmplifier() + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean effectExceedsLimit(PotionEffectType type, int level) {
        Optional<PotionRestriction> potionRestriction = items.getPotionRestriction(type);
        if (potionRestriction.isEmpty()) return false;
        int max = potionRestriction.get().maxLevel();
        return max <= 0 || level > max;
    }


    private void applyOfferRestriction(EnchantmentOffer offer, EnchantRestriction res) {
        if (res.maxLevel() <= 0 || offer.getEnchantmentLevel() > res.maxLevel()) {
            if (res.maxLevel() > 0) {
                offer.setEnchantmentLevel(res.maxLevel());
            } else {
                Enchantment replacement = randomAllowedEnchantment();
                if (replacement == null) {
                    offer.setEnchantment(null);
                    offer.setEnchantmentLevel(0);
                } else {
                    offer.setEnchantment(replacement);
                    offer.setEnchantmentLevel(1);
                }
            }
        }
    }

    private Enchantment randomAllowedEnchantment() {
        List<Enchantment> allowed = Arrays.stream(Enchantment.values())
                .filter(enchantment -> {
                    String key = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
                    Optional<EnchantRestriction> restriction = items.getEnchantRestriction(key + "_ENCHANT");
                    return restriction.isEmpty() || restriction.get().maxLevel() > 0;
                })
                .toList();

        if (allowed.isEmpty()) {
            return null;
        }
        return allowed.get(ThreadLocalRandom.current().nextInt(allowed.size()));
    }
}
