package me.panhaskins.itemLimiter.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {

    private SchedulerUtil() {}

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    /**
     * Runs {@code task} on the entity's scheduler. If the entity has already
     * been retired (e.g. the player logged out), {@code retired} is run
     * immediately instead so callers can release any state they had reserved
     * for the scheduled task.
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (entity.getScheduler().run(plugin, t -> task.run(), retired) == null) {
            retired.run();
        }
    }

    public static void runForEntityDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
    }

    public static void runForRegion(Plugin plugin, Location loc, Runnable task) {
        plugin.getServer().getRegionScheduler().run(plugin, loc, t -> task.run());
    }
}
