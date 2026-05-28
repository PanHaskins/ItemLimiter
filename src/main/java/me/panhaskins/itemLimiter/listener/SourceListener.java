package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.data.ConfigItems;
import me.panhaskins.itemLimiter.data.UsageTracker;
import me.panhaskins.itemLimiter.model.Sources;
import me.panhaskins.itemLimiter.utils.Messager;
import me.panhaskins.itemLimiter.utils.SchedulerUtil;
import me.panhaskins.itemLimiter.model.EnchantRestriction;
import me.panhaskins.itemLimiter.model.PotionRestriction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import me.panhaskins.itemLimiter.model.ItemRule;

import java.util.Locale;

/**
 * Handles all item acquisition sources and removes blocked items.
 */
public class SourceListener implements Listener {

    private final ItemLimiter plugin;
    private final ConfigItems items;
    private final UsageTracker usage;
    private final String blockedMsg;
    private final String craftingWarningMsg;
    private final String inventoryLimitMsg;
    private final Map<Integer, String> notifyMessages;
    private final String alwaysMessage;
    private final Map<UUID, Set<Integer>> pendingCancels = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Enchantment>> pendingPrimaries = new ConcurrentHashMap<>();

    public SourceListener(ItemLimiter plugin) {
        this.plugin = plugin;
        this.items = plugin.getItems();
        this.usage = plugin.getUsageTracker();
        FileConfiguration cfg = plugin.getConfigManager().getConfig("config.yml");
        FileConfiguration msgs = plugin.getConfigManager().getConfig("messages.yml");
        this.blockedMsg = msgs.getString("loot.blocked", "Loot blocked");
        this.craftingWarningMsg = msgs.getString("crafting.limit_warning", "");
        this.inventoryLimitMsg = msgs.getString("inventory.limit_reached", "Restricted item removed");
        ConfigurationSection notifySection = cfg.getConfigurationSection("notification.sources");
        Map<Integer, String> map = new HashMap<>(notifySection != null ? notifySection.getKeys(false).size() : 0);
        String always = null;
        if (notifySection != null) {
            for (String k : notifySection.getKeys(false)) {
                if (k.equalsIgnoreCase("always")) {
                    always = notifySection.getString(k);
                } else {
                    try {
                        int t = Integer.parseInt(k);
                        String m = notifySection.getString(k);
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
        BrewerInventory inventory = event.getContents();
        Block standBlock = inventory.getHolder().getBlock();
        Location loc = standBlock.getLocation();

        ItemStack[] originals = new ItemStack[3];
        for (int i = 0; i < 3; i++) {
            ItemStack slot = inventory.getItem(i);
            originals[i] = slot != null ? slot.clone() : null;
        }
        ItemStack originalIngredient = inventory.getIngredient();
        if (originalIngredient != null) originalIngredient = originalIngredient.clone();

        List<ItemStack> results = event.getResults();
        boolean[] blocked = new boolean[3];
        int blockedCount = 0;
        int activeSlots = 0;

        for (int i = 0; i < results.size(); i++) {
            ItemStack result = results.get(i);
            if (result == null || result.getType().isAir()) continue;
            if (originals[i] == null || originals[i].getType().isAir()) continue;
            activeSlots++;
            if (processItem(result, Sources.BREWING, null, true, standBlock)) {
                blocked[i] = true;
                blockedCount++;
            }
        }

        if (blockedCount == 0) return;

        for (int i = 0; i < results.size(); i++) {
            if (blocked[i]) {
                results.set(i, null);
            }
        }

        if (blockedCount >= activeSlots) {
            ItemStack ingredientToReturn = originalIngredient;
            SchedulerUtil.runForRegion(plugin, loc, () -> {
                for (int i = 0; i < 3; i++) {
                    if (originals[i] != null) {
                        inventory.setItem(i, originals[i]);
                    }
                }
                if (ingredientToReturn != null) {
                    loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), ingredientToReturn);
                }
            });
        } else {
            SchedulerUtil.runForRegion(plugin, loc, () -> {
                for (int i = 0; i < 3; i++) {
                    if (blocked[i] && originals[i] != null) {
                        inventory.setItem(i, originals[i]);
                    }
                }
            });
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
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.removeIf(stack -> processItem(stack, Sources.SHEARING, event.getPlayer()))) {
            event.setDrops(drops);
        }
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
            SchedulerUtil.runForEntity(plugin, player, () -> player.getInventory().removeItem(stack));
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
        ItemStack result = event.getTrade().getResult().clone();
        Player player = event.getPlayer();

        if (ItemUtils.hasOverLimitEnchant(result, items, player)) {
            event.setCancelled(true);
            return;
        }
        if (processItem(result, Sources.TRADING, player)) {
            event.setCancelled(true);
            return;
        }

        ItemRule rule = items.getItem(result).orElse(null);
        if (rule == null) return;
        if (!rule.worlds().appliesIn(player.getWorld().getName())) return;
        if (rule.exception().appliesTo(player, result)) return;

        int limit = rule.limit().inInventory();
        if (limit < 0) return;
        int current = ItemUtils.countItems(player, rule, items, limit);
        if (current + result.getAmount() > limit) {
            event.setCancelled(true);
            player.sendMessage(Messager.translate(inventoryLimitMsg));
        }
    }

    @EventHandler
    public void onTradeSelect(TradeSelectEvent event) {
        org.bukkit.inventory.Merchant merchant = event.getMerchant();
        int index = event.getIndex();
        if (index < 0 || index >= merchant.getRecipeCount()) return;
        org.bukkit.inventory.MerchantRecipe recipe = merchant.getRecipe(index);
        ItemStack result = recipe.getResult().clone();
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;

        if (ItemUtils.hasOverLimitEnchant(result, items, player)) {
            event.setCancelled(true);
            return;
        }
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
        Player enchanter = event.getEnchanter();
        if (processItem(event.getItem(), Sources.ENCHANTING, enchanter, false)) {
            event.setCancelled(true);
            return;
        }
        Set<Integer> cancels = null;
        Map<Integer, Enchantment> primaries = null;
        EnchantmentOffer[] offers = event.getOffers();
        ItemStack target = event.getItem();
        for (int slot = 0; slot < offers.length; slot++) {
            EnchantmentOffer offer = offers[slot];
            if (offer == null) continue;
            String enchantKey = offer.getEnchantment().getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
            if (hasEnchantBypass(enchanter, enchantKey, target)) {
                if (primaries == null) primaries = new HashMap<>(3);
                primaries.put(slot, offer.getEnchantment());
                continue;
            }
            int maxLevel = items.getEnchantRestriction(enchantKey)
                    .map(EnchantRestriction::maxLevel)
                    .orElse(Integer.MAX_VALUE);
            if (maxLevel <= 0 || isEnchantLimitReached(enchanter, enchantKey)) {
                offer.setCost(999);
                if (cancels == null) cancels = new HashSet<>(3);
                cancels.add(slot);
            } else {
                if (offer.getEnchantmentLevel() > maxLevel) {
                    offer.setEnchantmentLevel(maxLevel);
                }
                if (primaries == null) primaries = new HashMap<>(3);
                primaries.put(slot, offer.getEnchantment());
            }
        }
        UUID uid = enchanter.getUniqueId();
        if (cancels != null) pendingCancels.put(uid, cancels); else pendingCancels.remove(uid);
        if (primaries != null) pendingPrimaries.put(uid, primaries); else pendingPrimaries.remove(uid);
    }

    /** Returns true when the {@code _ENCHANT} rule has a bypass that matches the player or stack. */
    private boolean hasEnchantBypass(Player player, String enchantKey, ItemStack stack) {
        return items.getItem(enchantKey)
                .map(rule -> rule.exception().appliesTo(player, stack))
                .orElse(false);
    }

    private boolean isEnchantLimitReached(Player player, String enchantKey) {
        ItemRule rule = items.getItem(enchantKey).orElse(null);
        if (rule == null) return false;
        if (!rule.worlds().appliesIn(player.getWorld().getName())) return false;
        if (rule.exception().appliesTo(player, null)) return false;
        if (!rule.isSourceBlocked(Sources.ENCHANTING)) return false;

        int playerLimit = rule.limit().perPlayer();
        int globalLimit = rule.limit().global();
        if (playerLimit == 0 || globalLimit == 0) return true;
        if (globalLimit > 0 && (playerLimit < 0 || playerLimit > globalLimit)) {
            playerLimit = globalLimit;
        }
        if (playerLimit > 0 && usage.getPlayerUsage(player.getUniqueId(), rule.key(), "sources") >= playerLimit) return true;
        return globalLimit > 0 && usage.getGlobalUsage(rule.key(), "sources") >= globalLimit;
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        UUID uid = player.getUniqueId();

        Set<Integer> cancels = pendingCancels.remove(uid);
        Map<Integer, Enchantment> primaries = pendingPrimaries.remove(uid);
        if (cancels != null && cancels.contains(event.whichButton())) {
            event.setCancelled(true);
            return;
        }

        if (processItem(event.getItem(), Sources.ENCHANTING, player, false)) {
            event.setCancelled(true);
            return;
        }

        Enchantment primary = primaries != null ? primaries.get(event.whichButton()) : null;
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        List<Enchantment> toRemove = new ArrayList<>();
        boolean primaryBlocked = false;
        ItemStack target = event.getItem();
        for (Map.Entry<Enchantment, Integer> entry : toAdd.entrySet()) {
            Enchantment enchant = entry.getKey();
            String enchantKey = enchant.getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
            if (hasEnchantBypass(player, enchantKey, target)) continue;
            int maxLevel = items.getEnchantRestriction(enchantKey)
                    .map(EnchantRestriction::maxLevel)
                    .orElse(0);
            if (maxLevel > 0 && entry.getValue() > maxLevel) {
                entry.setValue(maxLevel);
            }
            if (isEnchantBlocked(enchant, entry.getValue(), player)) {
                if (enchant.equals(primary)) {
                    primaryBlocked = true;
                    break;
                }
                toRemove.add(enchant);
            }
        }

        if (primaryBlocked) {
            event.setCancelled(true);
            return;
        }

        for (Enchantment enchant : toRemove) {
            toAdd.remove(enchant);
        }

        if (toAdd.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        for (Map.Entry<Enchantment, Integer> entry : toAdd.entrySet()) {
            String enchantKey = entry.getKey().getKey().getKey().toUpperCase(Locale.ROOT) + "_ENCHANT";
            // Item-descriptor exception matches the real enchanting target, not the synthetic book
            if (hasEnchantBypass(player, enchantKey, target)) continue;
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            book.setItemMeta(meta);
            processItem(book, Sources.ENCHANTING, player);
        }

        processItem(event.getItem(), Sources.ENCHANTING, player);
    }

    private boolean isEnchantBlocked(Enchantment enchant, int level, Player player) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(meta);
        return processItem(book, Sources.ENCHANTING, player, false);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (processItem(result.clone(), Sources.CRAFTING, player, false)) {
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
        if (processItem(result.clone(), Sources.CRAFTING, null, true, event.getBlock())) event.setCancelled(true);
    }

    private boolean processItem(ItemStack stack, Sources source) {
        return processItem(stack, source, null, true, null);
    }

    private boolean processItem(ItemStack stack, Sources source, Player player) {
        return processItem(stack, source, player, true, null);
    }

    private boolean processItem(ItemStack stack, Sources source, Player player, boolean recordUsage) {
        return processItem(stack, source, player, recordUsage, null);
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
    private boolean processItem(ItemStack stack, Sources source, Player player, boolean recordUsage, Block crafterBlock) {
        if (stack == null) {
            return false;
        }

        ItemRule rule = items.getItem(stack).orElse(null);

        if (rule != null && player != null && !rule.worlds().appliesIn(player.getWorld().getName())) {
            rule = null;
        }
        if (rule != null && player == null && crafterBlock != null
                && !rule.worlds().appliesIn(crafterBlock.getWorld().getName())) {
            rule = null;
        }

        // Enchant caps come from _ENCHANT rules and have their own per-enchant exception logic;
        // run them before the parent rule's bypass so a "DIAMOND_SWORD" bypass doesn't also waive
        // an unrelated SHARPNESS_ENCHANT cap.
        ItemUtils.capEnchantments(stack, items, player);

        // Exception gate for this rule only
        if (rule != null && rule.exception().appliesTo(player, stack)) {
            return false;
        }

        boolean blockedByConfig = rule != null && rule.isSourceBlocked(source);
        if (stack.getType().name().contains("POTION") && stack.getItemMeta() instanceof PotionMeta meta) {
            if (isPotionOverLimit(meta)) return true;

            PotionType base = meta.getBasePotionType();
            if (base != null) {
                List<PotionEffect> potionEffectList = base.getPotionEffects();
                if (!potionEffectList.isEmpty()) {
                    PotionEffect first = potionEffectList.getFirst();
                    capPotionEffect(meta, first.getType(), first.getAmplifier() + 1, first.getDuration());
                }
            }
            for (PotionEffect e : meta.getCustomEffects()) {
                capPotionEffect(meta, e.getType(), e.getAmplifier() + 1, e.getDuration());
            }
            stack.setItemMeta(meta);
        }

        if (rule == null) {
            return false;
        }

        int globalLimit = rule.limit().global();
        int playerLimit = rule.limit().perPlayer();

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

        if (!recordUsage) {
            // Prepare events: check limits without incrementing
            if (player != null && playerLimit > 0
                    && usage.getPlayerUsage(player.getUniqueId(), rule.key(), "sources") >= playerLimit) return true;
            return globalLimit > 0 && usage.getGlobalUsage(rule.key(), "sources") >= globalLimit;
        }

        if (player != null && playerLimit > 0
                && !usage.tryIncrement(player.getUniqueId(), rule.key(), "sources", playerLimit)) {
            return true;
        }

        if (globalLimit > 0 && !usage.tryIncrement(UsageTracker.GLOBAL_UUID, rule.key(), "sources", globalLimit)) {
            // Rollback player increment — global limit reached
            if (player != null) {
                usage.decrementCache(player.getUniqueId(), rule.key(), "sources");
            }
            return true;
        }

        if (player != null && playerLimit > 0 && !craftingWarningMsg.isEmpty()) {
            int remaining = playerLimit - usage.getPlayerUsage(player.getUniqueId(), rule.key(), "sources");
            if (remaining > 0) {
                String msg = craftingWarningMsg
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%item%", rule.key());
                player.sendMessage(Messager.translate(msg));
            }
        }

        if (player != null && playerLimit > 0) {
            int remaining = playerLimit - usage.getPlayerUsage(player.getUniqueId(), rule.key(), "sources");
            String warn = notifyMessages.getOrDefault(remaining, alwaysMessage);
            if (warn != null) {
                String name = plugin.getConfigManager().getConfig("config.yml")
                        .getString("sources." + source.name().toLowerCase(Locale.ROOT), source.name());
                org.bukkit.Location loc = player.getLocation();
                warn = warn.replace("%player%", player.getName())
                        .replace("%item%", rule.key())
                        .replace("%source_name%", name)
                        .replace("%left_sources%", String.valueOf(remaining))
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));
                plugin.getServer().broadcast(Messager.translate(warn));
            }
        } else if (crafterBlock != null) {
            int remaining = globalLimit > 0
                    ? globalLimit - usage.getGlobalUsage(rule.key(), "sources")
                    : -1;
            String warn = remaining >= 0
                    ? notifyMessages.getOrDefault(remaining, alwaysMessage)
                    : alwaysMessage;
            if (warn != null) {
                org.bukkit.Location loc = crafterBlock.getLocation();
                String name = plugin.getConfigManager().getConfig("config.yml")
                        .getString("sources." + source.name().toLowerCase(Locale.ROOT), source.name());
                String unknownPlayer = plugin.getConfigManager().getConfig("config.yml")
                        .getString("placeholders.unknown_player", "Someone");
                warn = warn.replace("%player%", unknownPlayer)
                        .replace("%item%", rule.key())
                        .replace("%source_name%", name)
                        .replace("%left_sources%", remaining >= 0 ? String.valueOf(remaining) : "∞")
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));
                plugin.getServer().broadcast(Messager.translate(warn));
            }
        }

        return false;
    }

    private void capPotionEffect(PotionMeta meta, PotionEffectType type, int level, int duration) {
        items.getPotionRestriction(type).ifPresent(restriction -> {
            if (restriction.maxLevel() <= 0) {
                meta.removeCustomEffect(type);
                return;
            }

            int cappedLevel = level;
            int cappedDuration = duration;

            if (level > restriction.maxLevel()) {
                cappedLevel = restriction.maxLevel();
            }
            int maxDurationTicks = restriction.maxDuration() * 20;
            if (maxDurationTicks > 0 && duration > maxDurationTicks) {
                cappedDuration = maxDurationTicks;
            }

            if (cappedLevel != level || cappedDuration != duration) {
                meta.removeCustomEffect(type);
                meta.addCustomEffect(new PotionEffect(type, cappedDuration, cappedLevel - 1), true);
            }
        });
    }

    private boolean isPotionOverLimit(PotionMeta meta) {
        PotionType base = meta.getBasePotionType();
        if (base != null) {
            for (PotionEffect e : base.getPotionEffects()) {
                if (isEffectOverLimit(e.getType(), e.getAmplifier() + 1)) {
                    return true;
                }
            }
        }
        for (PotionEffect e : meta.getCustomEffects()) {
            if (isEffectOverLimit(e.getType(), e.getAmplifier() + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEffectOverLimit(PotionEffectType type, int level) {
        Optional<PotionRestriction> potionRestriction = items.getPotionRestriction(type);
        if (potionRestriction.isEmpty()) return false;
        int max = potionRestriction.get().maxLevel();
        return max <= 0 || level > max;
    }

}
