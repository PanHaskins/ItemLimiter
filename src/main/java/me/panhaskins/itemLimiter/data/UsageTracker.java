package me.panhaskins.itemLimiter.data;

import me.panhaskins.itemLimiter.utils.database.DatabaseManager;
import me.panhaskins.itemLimiter.utils.database.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsageTracker {
    public static final UUID GLOBAL_UUID = new UUID(0L, 0L);

    private final DatabaseManager database;
    private final Plugin plugin;
    private final Logger logger;
    private final ConcurrentHashMap<String, AtomicInteger> cache = new ConcurrentHashMap<>();

    public UsageTracker(Plugin plugin, DatabaseManager database, Logger logger) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
    }

    private String cacheKey(UUID uuid, String target, String action) {
        return uuid.toString() + ":" + target.toUpperCase() + ":" + action;
    }

    private <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
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

    public void loadCache() {
        try {
            List<Map<String, Object>> rows = QueryBuilder.create(database)
                    .selectFrom("usage_stats")
                    .columns("uuid", "target", "action", "count")
                    .fetch();
            for (Map<String, Object> row : rows) {
                String uuid = (String) row.get("uuid");
                String target = (String) row.get("target");
                String action = (String) row.get("action");
                int count = ((Number) row.get("count")).intValue();
                String key = uuid + ":" + target + ":" + action;
                cache.put(key, new AtomicInteger(count));
            }
        } catch (QueryBuilder.QueryExecutionException e) {
            logger.log(Level.SEVERE, "Unable to preload usage cache", e);
        }
    }

    public void incrementUsage(UUID playerId, String itemKey, String limitType) {
        String name = itemKey.toUpperCase();
        String key = cacheKey(playerId, name, limitType);
        cache.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        runAsync(() -> {
            try {
                upsert(playerId.toString(), name, limitType);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unable to update usage", e);
            }
            return null;
        });
    }

    public int getPlayerUsage(UUID playerId, String itemKey, String limitType) {
        AtomicInteger val = cache.get(cacheKey(playerId, itemKey, limitType));
        return val != null ? val.get() : 0;
    }

    public int getGlobalUsage(String itemKey, String limitType) {
        return getPlayerUsage(GLOBAL_UUID, itemKey, limitType);
    }

    /**
     * Atomically checks if current usage is below limit and increments if allowed.
     * Returns true if the increment was accepted (usage was below limit).
     * Returns false if limit was already reached.
     */
    public boolean tryIncrement(UUID playerId, String itemKey, String limitType, int limit) {
        if (limit <= 0) return true;
        String key = cacheKey(playerId, itemKey.toUpperCase(), limitType);
        AtomicInteger counter = cache.computeIfAbsent(key, k -> new AtomicInteger(0));
        int result = counter.getAndUpdate(current -> current < limit ? current + 1 : current);
        if (result >= limit) return true;

        runAsync(() -> {
            try {
                upsert(playerId.toString(), itemKey.toUpperCase(), limitType);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unable to update usage", e);
            }
            return null;
        });
        return false;
    }

    public CompletableFuture<Long> getLastUsed(UUID playerId, String itemKey, String limitType) {
        String name = itemKey.toUpperCase();
        return runAsync(() -> {
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

    public void clearPlayer(UUID playerId) {
        String prefix = playerId.toString() + ":";
        cache.keySet().removeIf(s -> s.startsWith(prefix));
    }

    public void saveAll() {
        boolean isSqlite = "SQLITE".equals(database.getType());
        String sql = isSqlite
                ? "INSERT INTO usage_stats (uuid,category,target,action,count,last_used) VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(uuid,category,target,action) DO UPDATE SET count=?, last_used=?"
                : "INSERT INTO usage_stats (uuid,category,target,action,count,last_used) VALUES (?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE count=?, last_used=?";
        long now = Instant.now().toEpochMilli();

        try (Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Map.Entry<String, AtomicInteger> entry : cache.entrySet()) {
                String[] parts = entry.getKey().split(":", 3);
                if (parts.length != 3) continue;
                int count = entry.getValue().get();
                ps.setString(1, parts[0]);
                ps.setString(2, "item");
                ps.setString(3, parts[1]);
                ps.setString(4, parts[2]);
                ps.setInt(5, count);
                ps.setLong(6, now);
                ps.setInt(7, count);
                ps.setLong(8, now);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Unable to save usage cache", e);
        }
    }

    private void upsert(String uuid, String target, String action) {
        boolean isSqlite = "SQLITE".equals(database.getType());
        String sql = isSqlite
                ? "INSERT INTO usage_stats (uuid,category,target,action,count,last_used) VALUES (?,?,?,?,1,?) "
                + "ON CONFLICT(uuid,category,target,action) DO UPDATE SET count=count+1, last_used=?"
                : "INSERT INTO usage_stats (uuid,category,target,action,count,last_used) VALUES (?,?,?,?,1,?) "
                + "ON DUPLICATE KEY UPDATE count=count+1, last_used=?";
        long now = Instant.now().toEpochMilli();
        try (Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, "item");
            ps.setString(3, target);
            ps.setString(4, action);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new QueryBuilder.QueryExecutionException("Upsert failed", e);
        }
    }

}
