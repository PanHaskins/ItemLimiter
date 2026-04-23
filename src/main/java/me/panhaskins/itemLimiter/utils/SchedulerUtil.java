package me.panhaskins.itemLimiter.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {

    private SchedulerUtil() {}

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    public static void runForRegion(Plugin plugin, Location loc, Runnable task) {
        plugin.getServer().getRegionScheduler().run(plugin, loc, t -> task.run());
    }
}
