# Database Setup

ItemLimiter saves player data (like how many items they got) in a database. This data stays between server restarts.

## SQLite (Default)

SQLite works right away with no setup. The database file is at `plugins/ItemLimiter/database.db`.

```yaml
# config.yml
database:
  type: "sqlite"
  sqlite:
    file: "database.db"
```

**Good for:** Single servers, small and medium servers.

## MySQL

For bigger servers or server networks, you can use MySQL:

```yaml
# config.yml
database:
  type: "mysql"
  mysql:
    host: "localhost:3306"
    username: "your_username"
    password: "your_password"
    database: "itemlimiter"
```

Note: `host` and `port` go together in one string (e.g., `"localhost:3306"`).

**Good for:** Server networks (BungeeCord, Velocity), big servers with many players.

## What Data is Saved

The `usage_stats` table saves:

| Column | What it stores |
|--------|---------------|
| `uuid` | Player UUID |
| `category` | Type: item, enchant, or potion |
| `target` | Item name from `items.yml` |
| `action` | The source or trigger |
| `count` | How many times |
| `last_used` | When it was last used |

This data is used for `limit.sources.per_player` and `limit.sources.global`.

## Good to Know

- The database is created on first start — you don't need to make it yourself
- A database is **always** running — SQLite is used by default
- If you switch from SQLite to MySQL, your old data is **not** moved
- Database work runs in the background — it does not slow down the server
- **Backups:** For SQLite, copy `plugins/ItemLimiter/database.db`. For MySQL, use your MySQL backup tools
