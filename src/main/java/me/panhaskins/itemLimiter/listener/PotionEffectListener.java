package me.panhaskins.itemLimiter.listener;

import me.panhaskins.itemLimiter.ItemLimiter;
import me.panhaskins.itemLimiter.model.PotionRestriction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;

public class PotionEffectListener implements Listener {

    private final ItemLimiter plugin;

    public PotionEffectListener(ItemLimiter plugin) {
        this.plugin = plugin;
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

        Optional<PotionRestriction> opt = plugin.getItems().getPotionRestriction(incoming.getType());
        if (opt.isEmpty()) return;
        PotionRestriction res = opt.get();

        if (res.maxLevel() <= 0) {
            event.setCancelled(true);
            return;
        }

        int level = incoming.getAmplifier() + 1;
        int duration = incoming.getDuration();
        int cappedLevel = Math.min(level, res.maxLevel());
        int maxDurationTicks = res.maxDuration() * 20;
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
