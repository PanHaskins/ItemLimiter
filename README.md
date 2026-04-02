# ItemLimiter

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square)](https://adoptium.net/)
[![Paper 1.21](https://img.shields.io/badge/Paper-1.21+-blue?style=flat-square)](https://papermc.io/)
[![GitHub Release](https://img.shields.io/github/v/release/PanHaskins/ItemLimiter?style=flat-square)](https://github.com/PanHaskins/ItemLimiter/releases)

**Restrict and balance vanilla Minecraft features for your SMP server.** Control cooldowns, inventory limits, acquisition caps, and item sources — all from simple YAML config files.

---

## Features

* **Cooldowns** — add delays between uses of any tool, weapon, or item (e.g., Mace 15s on hit, Ender Pearl 60s on throw)
* **Inventory Limits** — cap how many of an item a player can carry at once
* **Acquisition Limits** — restrict how many items can be crafted or obtained, per player or globally across the server
* **Source Blocking** — disable specific ways to obtain items (crafting, trading, mob drops, fishing, treasure, and 13 sources total)
* **Potion Restrictions** — limit potion amplifier levels and durations
* **Enchantment Restrictions** — cap enchantment levels and block specific enchant sources
* **World Restrictions** — apply rules only in specific worlds (blacklist or whitelist mode)
* **Database Support** — MySQL and SQLite for persistent usage tracking across restarts
* **Client Cooldown Sync** — visual cooldown display on the client via PacketEvents (optional)
* **MiniMessage Support** — fully customizable messages with gradients, hex colors, and PlaceholderAPI variables

## Quick Start

1. Download the latest release from [GitHub Releases](https://github.com/PanHaskins/ItemLimiter/releases)
2. Drop the `.jar` into your server's `plugins/` folder
3. Start (or restart) the server — config files will be generated
4. Edit `plugins/ItemLimiter/items.yml` to configure your restrictions
5. Restart the server to apply changes

> **Note:** ItemLimiter currently has no commands or permissions. All configuration is done through YAML files.

## Requirements

| Requirement | Version |
|-------------|---------|
| **Paper** (or forks like Purpur) | 1.21+ |
| **Java** | 21+ |
| **PacketEvents** *(optional)* | Latest — enables client-side cooldown display |
| **PlaceholderAPI** *(optional)* | 2.11+ — enables placeholder variables in messages |

## Configuration

ItemLimiter uses four configuration files in `plugins/ItemLimiter/`:

| File | Purpose |
|------|---------|
| [`items.yml`](https://github.com/PanHaskins/ItemLimiter/wiki/Items-Configuration) | Define restrictions for items, potions, and enchantments |
| `config.yml` | Database settings, notification thresholds, and source display names |
| `messages.yml` | Player-facing messages (MiniMessage format) |
| `examples.yml` | Reference examples for all configuration options |

> **Full configuration guide:** See the [Wiki](https://github.com/PanHaskins/ItemLimiter/wiki) for detailed documentation on every option.

### Example: Limiting Ender Pearls

```yaml
# items.yml
ENDER_PEARL:
  limit:
    in_inventory: 8           # Max 8 in inventory
  cooldown:
    time: 60                  # 60-second cooldown
    trigger:
      - THROW                 # Triggered on throw
  blacklist_sources:
    - TRADING                 # Can't get from villagers
    - BARTERING               # Can't get from piglins
```

## How It Works

ItemLimiter processes items through three independent systems that work together:

```
Player action
    |
    v
[SourceListener]  — Was the item obtained from a blocked source? -> Block it
    |
    v
[InventoryListener] — Does the player have too many? -> Remove excess
    |
    v
[TriggerListener] — Is the item on cooldown? -> Cancel the action
```

Each item in `items.yml` can use any combination of these systems. You only configure what you need — unused sections are simply omitted.

## Building from Source

```bash
mvn clean package
```

The shaded JAR will be in `target/`.

## Documentation

Visit the [Wiki](https://github.com/PanHaskins/ItemLimiter/wiki) for full documentation:

- [Items Configuration](https://github.com/PanHaskins/ItemLimiter/wiki/Items-Configuration)
- [Sources & Triggers](https://github.com/PanHaskins/ItemLimiter/wiki/Sources-and-Triggers)
- [Enchantments & Potions](https://github.com/PanHaskins/ItemLimiter/wiki/Enchantments-and-Potions)
- [Database Setup](https://github.com/PanHaskins/ItemLimiter/wiki/Database-Setup)
- [Messages](https://github.com/PanHaskins/ItemLimiter/wiki/Messages)
