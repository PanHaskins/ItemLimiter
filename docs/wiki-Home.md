# ItemLimiter Wiki

Welcome to the ItemLimiter wiki! Here you can learn how to set up and use the plugin.

## Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| [**Paper**](https://papermc.io/) (or Purpur, Folia) | 1.21+ | Spigot is NOT supported |
| **Java** | 21+ | Needed for Paper 1.21 |
| [**PacketEvents**](https://github.com/retrooper/packetevents) | Latest | *Optional* — shows cooldown bar on the client |
| [**PlaceholderAPI**](https://www.spigotmc.org/resources/placeholderapi.6245/) | 2.11+ | *Optional* — adds placeholders to messages |

## Installation

1. Download the latest JAR from [GitHub Releases](https://github.com/PanHaskins/ItemLimiter/releases)
2. Put the JAR file into your server's `plugins/` folder
3. Start or restart the server
4. The plugin creates four files in `plugins/ItemLimiter/`:
   - `items.yml` — your item rules (empty at first, you add items here)
   - `config.yml` — database and source name settings
   - `messages.yml` — messages shown to players
   - `examples.yml` — example configs to help you (not used by the plugin)
5. Open `examples.yml` to see all options, then edit `items.yml` to add your rules
6. Restart the server to apply changes

### Quick Example

```yaml
# items.yml
ENDER_PEARL:
  cooldown:
    time: 30
    trigger:
      - THROW
```

This adds a 30-second cooldown after throwing an Ender Pearl.

## How the Plugin Works

ItemLimiter has three systems. You can use them together or separately for each item:

| System | What it does | Config key |
|--------|-------------|------------|
| **Source Blocking** | Stops players from getting items in certain ways | `blacklist_sources` |
| **Limits** | Sets how many items a player can have or get | `limit` |
| **Cooldowns** | Adds a wait time between item uses | `cooldown` |

These three systems run in order:

1. **SourceListener** — Checks crafting, trading, loot, etc. Blocks items from blocked sources.
2. **InventoryListener** — Checks inventory on pickup, click, and join. Removes items over the limit.
3. **TriggerListener** — Checks player actions like attack, throw, eat, etc. Starts cooldowns.

You can use any system alone. For example, you can add cooldowns without limits, or limits without source blocking.

## Pages

- **[Items Configuration](Items-Configuration)** — How to edit `items.yml`
- **[Sources & Triggers](Sources-and-Triggers)** — All item sources and cooldown triggers
- **[Enchantments & Potions](Enchantments-and-Potions)** — How to restrict enchantments and potions
- **[Database Setup](Database-Setup)** — MySQL and SQLite setup
- **[Messages](Messages)** — How to change player messages
