package me.panhaskins.itemLimiter.utils.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fluent SQL builder supporting CREATE TABLE, DROP TABLE, INSERT, UPDATE, DELETE and SELECT operations.
 * <p>
 * Usage example:
 * <pre>{@code
 * // CREATE TABLE example
 * QueryBuilder.create(dbManager)
 *     .createTable("users",
 *         "uuid VARCHAR(36) PRIMARY KEY",
 *         "name TEXT NOT NULL");
 *
 * // DROP TABLE example
 * QueryBuilder.create(dbManager)
 *     .dropTable("users");
 *
 * // INSERT example
 * int count = QueryBuilder.create(dbManager)
 *     .insertInto("users")
 *     .columns("uuid", "name")
 *     .values(playerUuid, playerName)
 *     .execute();
 *
 * // UPDATE example
 * int updated = QueryBuilder.create(dbManager)
 *     .update("users")
 *     .set("name", newName)
 *     .where("uuid", playerUuid)
 *     .execute();
 *
 * // DELETE example
 * int deleted = QueryBuilder.create(dbManager)
 *     .deleteFrom("users")
 *     .where("uuid", playerUuid)
 *     .execute();
 *
 * // SELECT example
 * List<Map<String, Object>> rows = QueryBuilder.create(dbManager)
 *     .selectFrom("users")
 *     .columns("name")
 *     .count("uuid")
 *     .groupBy("name")
 *     .fetch();
 * }</pre>
 */
public class QueryBuilder {
    private enum Action { INSERT, UPDATE, DELETE, SELECT }

    private final DatabaseManager db;
    private Action action;
    private String table;
    private final List<String> columns = new ArrayList<>();
    private final List<List<Object>> rows = new ArrayList<>();
    private final Map<String, Object> updates = new LinkedHashMap<>();
    private final Map<String, Object> conditions = new LinkedHashMap<>();
    private final List<String> groupBy = new ArrayList<>();
    private String orderBy;
    private Integer limit;

    private QueryBuilder(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "DatabaseManager cannot be null");
    }

    /** Creates a new QueryBuilder instance. */
    public static QueryBuilder create(DatabaseManager db) {
        return new QueryBuilder(db);
    }

    /**
     * Creates a table with specified column definitions.
     * @param table             table name
     * @param columnDefinitions e.g. "id INT PRIMARY KEY", "name TEXT"
     */
    public void createTable(String table, String... columnDefinitions) {
        String cols = String.join(", ", columnDefinitions);
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" + cols + ")";
        executeDDL(sql, "create table");
    }

    /** Drops a table if exists. */
    public void dropTable(String table) {
        String sql = "DROP TABLE IF EXISTS " + table;
        executeDDL(sql, "drop table");
    }

    private void executeDDL(String sql, String action) {
        try (Connection conn = db.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new QueryExecutionException("Failed to " + action + ": " + sql, e);
        }
    }

    /** Begins an INSERT statement on table. */
    public QueryBuilder insertInto(String table) {
        this.action = Action.INSERT;
        this.table = table;
        return this;
    }

    /** Specifies columns for INSERT. */
    public QueryBuilder columns(String... cols) {
        this.columns.addAll(Arrays.asList(cols));
        return this;
    }

    /** Adds a row of values for INSERT. */
    public QueryBuilder values(Object... vals) {
        this.rows.add(Arrays.asList(vals));
        return this;
    }

    /** Begins an UPDATE statement on table. */
    public QueryBuilder update(String table) {
        this.action = Action.UPDATE;
        this.table = table;
        return this;
    }

    /** Adds a SET clause for UPDATE. */
    public QueryBuilder set(String column, Object value) {
        this.updates.put(column, value);
        return this;
    }

    /** Begins a DELETE statement on table. */
    public QueryBuilder deleteFrom(String table) {
        this.action = Action.DELETE;
        this.table = table;
        return this;
    }

    /** Begins a SELECT statement on table. */
    public QueryBuilder selectFrom(String table) {
        this.action = Action.SELECT;
        this.table = table;
        return this;
    }

    /** Adds COUNT(*) to selected columns. */
    public QueryBuilder count() {
        this.columns.add("COUNT(*)");
        return this;
    }

    /** Adds COUNT(column) to selected columns. */
    public QueryBuilder count(String column) {
        this.columns.add("COUNT(" + column + ")");
        return this;
    }

    /** Adds AVG(column) to selected columns. */
    public QueryBuilder avg(String column) {
        this.columns.add("AVG(" + column + ")");
        return this;
    }

    /** Adds SUM(column) to selected columns. */
    public QueryBuilder sum(String column) {
        this.columns.add("SUM(" + column + ")");
        return this;
    }

    /** Adds MIN(column) to selected columns. */
    public QueryBuilder min(String column) {
        this.columns.add("MIN(" + column + ")");
        return this;
    }

    /** Adds MAX(column) to selected columns. */
    public QueryBuilder max(String column) {
        this.columns.add("MAX(" + column + ")");
        return this;
    }

    /** Adds GROUP BY clause. */
    public QueryBuilder groupBy(String... cols) {
        this.groupBy.addAll(Arrays.asList(cols));
        return this;
    }

    /** Adds ORDER BY clause. */
    public QueryBuilder orderBy(String column) {
        this.orderBy = column;
        return this;
    }

    /** Limits number of returned rows. */
    public QueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    /** Adds a WHERE clause condition. */
    public QueryBuilder where(String column, Object value) {
        this.conditions.put(column, value);
        return this;
    }

    /** Executes the built SQL (INSERT/UPDATE/DELETE). */
    public int execute() {
        String sql = buildSQL();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            switch (action) {
                case INSERT:
                    for (List<Object> row : rows)
                        for (Object v : row) ps.setObject(idx++, v);
                    break;
                case UPDATE:
                    for (Object v : updates.values()) ps.setObject(idx++, v);
                    // fallthrough
                case DELETE:
                    for (Object v : conditions.values()) ps.setObject(idx++, v);
                    break;
                default:
                    throw new QueryExecutionException("Unknown action");
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new QueryExecutionException("Failed to execute: " + sql, e);
        }
    }

    /** Executes a SELECT query and returns all rows as a list of maps. */
    public List<Map<String, Object>> fetch() {
        if (action != Action.SELECT)
            throw new QueryExecutionException("Action is not SELECT");

        String sql = buildSQL();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object v : conditions.values()) {
                ps.setObject(idx++, v);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    result.add(row);
                }
                return result;
            }
        } catch (SQLException e) {
            throw new QueryExecutionException("Failed to execute: " + sql, e);
        }
    }

    private String buildSQL() {
        if (action == null || table == null)
            throw new QueryExecutionException("Action or table not set");

        switch (action) {
            case INSERT:
                if (columns.isEmpty() || rows.isEmpty())
                    throw new QueryExecutionException("INSERT missing columns or values");
                String cols = String.join(", ", columns);
                String placeholder = columns.stream().map(c -> "?")
                        .collect(Collectors.joining(", ", "(", ")"));
                String vals = rows.stream().map(r -> placeholder)
                        .collect(Collectors.joining(", "));
                return "INSERT INTO " + table + " (" + cols + ") VALUES " + vals;
            case UPDATE:
                if (updates.isEmpty())
                    throw new QueryExecutionException("UPDATE missing set clauses");
                String set = updates.keySet().stream()
                        .map(k -> k + "=?")
                        .collect(Collectors.joining(", "));
                String where = conditions.isEmpty() ? "" : " WHERE " +
                        conditions.keySet().stream()
                                .map(k -> k + "=?")
                                .collect(Collectors.joining(" AND "));
                return "UPDATE " + table + " SET " + set + where;
            case DELETE:
                String w = conditions.isEmpty() ? "" : " WHERE " +
                        conditions.keySet().stream()
                                .map(k -> k + "=?")
                                .collect(Collectors.joining(" AND "));
                return "DELETE FROM " + table + w;
            case SELECT:
                String selectCols = columns.isEmpty() ? "*" : String.join(", ", columns);
                String whereClause = conditions.isEmpty() ? "" : " WHERE " +
                        conditions.keySet().stream()
                                .map(k -> k + "=?")
                                .collect(Collectors.joining(" AND "));
                String group = groupBy.isEmpty() ? "" : " GROUP BY " + String.join(", ", groupBy);
                String order = orderBy == null ? "" : " ORDER BY " + orderBy;
                String lim = limit == null ? "" : " LIMIT " + limit;
                return "SELECT " + selectCols + " FROM " + table + whereClause + group + order + lim;
            default:
                throw new QueryExecutionException("Unsupported action");
        }
    }

    /** Exception for SQL execution errors. */
    public static class QueryExecutionException extends RuntimeException {
        public QueryExecutionException(String message) { super(message); }
        public QueryExecutionException(String message, Throwable cause) { super(message, cause); }
    }
}
