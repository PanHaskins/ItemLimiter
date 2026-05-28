package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.model.PotionRestriction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class PotionEffectListener implements Listener {

    private final ItemLimiter plugin;
    private final Map<UUID, ItemStack> lastConsumed = new ConcurrentHashMap<>();

    public PotionEffectListener(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        lastConsumed.put(event.getPlayer().getUniqueId(), event.getItem());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastConsumed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) return;

        switch (event.getCause()) {
            case COMMAND, PLUGIN, POTION_DRINK, POTION_SPLASH, BEACON, CONDUIT -> {
                return;
            }
            default -> {
            }
        }

        PotionEffect incoming = event.getNewEffect();
        if (incoming == null) return;

        PotionRestriction restriction = plugin.getItems().getPotionRestriction(incoming.getType()).orElse(null);
        if (restriction == null) return;

        ItemStack source = event.getCause() == EntityPotionEffectEvent.Cause.FOOD
                ? lastConsumed.get(player.getUniqueId())
                : null;

        boolean bypass = plugin.getItems().getItem(restriction.name())
                .map(rule -> rule.exception().appliesTo(player, source))
                .orElse(false);
        if (bypass) return;

        if (restriction.maxLevel() <= 0) {
            event.setCancelled(true);
            return;
        }

        int level = incoming.getAmplifier() + 1;
        int duration = incoming.getDuration();
        int cappedLevel = Math.min(level, restriction.maxLevel());
        int maxDurationTicks = restriction.maxDuration() * 20;
        int cappedDuration = (maxDurationTicks > 0 && duration > maxDurationTicks)
                ? maxDurationTicks
                : duration;

        if (cappedLevel != level || cappedDuration != duration) {
            event.setCancelled(true);
            player.addPotionEffect(new PotionEffect(
                    incoming.getType(),
                    cappedDuration,
                    cappedLevel - 1,
                    incoming.isAmbient(),
                    incoming.hasParticles(),
                    incoming.hasIcon()
            ));
        }
    }
}
