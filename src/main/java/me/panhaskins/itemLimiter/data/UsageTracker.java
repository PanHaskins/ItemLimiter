package me.panhaskins.itemLimiter.data;

import me.panhaskins.itemLimiter.utils.database.DatabaseManager;
import me.panhaskins.itemLimiter.utils.database.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles statistics tracking for item usage limits.
 */
public class UsageTracker {
    public static final UUID GLOBAL_UUID = new UUID(0L, 0L);

    private final DatabaseManager database;
    private final Plugin plugin;
    private final Logger logger;

    public UsageTracker(Plugin plugin, DatabaseManager database, Logger logger) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
    }

    private <T> CompletableFuture<T> callAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public void incrementUsage(UUID playerId, String itemKey, String limitType) {
        String name = itemKey.toUpperCase();
        callAsync(() -> {
            try {
                List<Map<String, Object>> rows = QueryBuilder.create(database)
                        .selectFrom("usage_stats")
                        .columns("count")
                        .where("uuid", playerId.toString())
                        .where("category", "item")
                        .where("target", name)
                        .where("action", limitType)
                        .fetch();

                if (!rows.isEmpty()) {
                    int current = ((Number) rows.getFirst().get("count")).intValue();
                    QueryBuilder.create(database)
                            .update("usage_stats")
                            .set("count", current + 1)
                            .set("last_used", Instant.now().toEpochMilli())
                            .where("uuid", playerId.toString())
                            .where("category", "item")
                            .where("target", name)
                            .where("action", limitType)
                            .execute();
                } else {
                    QueryBuilder.create(database)
                            .insertInto("usage_stats")
                            .columns("uuid", "category", "target", "action", "count", "last_used")
                            .values(playerId.toString(), "item", name, limitType, 1, Instant.now().toEpochMilli())
                            .execute();
                }
            } catch (QueryBuilder.QueryExecutionException e) {
                logger.log(Level.SEVERE, "Unable to update usage", e);
            }
            return null;
        });
    }

    public CompletableFuture<Integer> getPlayerUsage(UUID playerId, String itemKey, String limitType) {
        String name = itemKey.toUpperCase();
        return callAsync(() -> {
            try {
                List<Map<String, Object>> rows = QueryBuilder.create(database)
                        .selectFrom("usage_stats")
                        .columns("count")
                        .where("uuid", playerId.toString())
                        .where("category", "item")
                        .where("target", name)
                        .where("action", limitType)
                        .fetch();
                if (!rows.isEmpty()) {
                    return ((Number) rows.getFirst().get("count")).intValue();
                }
            } catch (QueryBuilder.QueryExecutionException e) {
                logger.log(Level.SEVERE, "Unable to query usage", e);
            }
            return 0;
        });
    }

    public CompletableFuture<Long> getLastUsed(UUID playerId, String itemKey, String limitType) {
        String name = itemKey.toUpperCase();
        return callAsync(() -> {
            try {
                List<Map<String, Object>> rows = QueryBuilder.create(database)
                        .selectFrom("usage_stats")
                        .columns("last_used")
                        .where("uuid", playerId.toString())
                        .where("category", "item")
                        .where("target", name)
                        .where("action", limitType)
                        .fetch();
                if (!rows.isEmpty()) {
                    Object value = rows.getFirst().get("last_used");
                    if (value instanceof Number num) {
                        return num.longValue();
                    }
                }
            } catch (QueryBuilder.QueryExecutionException e) {
                logger.log(Level.SEVERE, "Unable to query last used", e);
            }
            return 0L;
        });
    }

    public CompletableFuture<Integer> getGlobalUsage(String itemKey, String limitType) {
        return getPlayerUsage(GLOBAL_UUID, itemKey, limitType);
    }
}
