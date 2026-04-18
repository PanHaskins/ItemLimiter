<div align="center">

![ItemLimiter Banner](docs/images/ItemLimiter-banner.png)

**Restrict and balance vanilla Minecraft for your SMP server.**
Cooldowns, inventory caps, acquisition limits, and source blocking — all in simple YAML.

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square)](https://adoptium.net/)
[![Paper 1.21+](https://img.shields.io/badge/Paper-1.21+-blue?style=flat-square)](https://papermc.io/)
[![Release](https://img.shields.io/github/v/release/PanHaskins/ItemLimiter?style=flat-square)](https://github.com/PanHaskins/ItemLimiter/releases)
[![Wiki](https://img.shields.io/badge/docs-Wiki-success?style=flat-square)](https://github.com/PanHaskins/ItemLimiter/wiki)

</div>

---

## Three systems, one plugin

<p align="center">
  <img src="docs/images/limited-cooldown.gif" width="390" height="320" alt="Cooldowns — delay reuse of tools, weapons and consumables" />
  <img src="docs/images/limited-inventory.gif" width="390" height="320" alt="Inventory limits — cap how many of an item a player can carry" />
  <img src="docs/images/limited-sources.gif" width="390" height="320" alt="Source blocking — disable crafting, trading, loot and other sources" />
</p>


Each item in `items.yml` picks any mix of the three. Leave out what you don't need.

## Also supported

- **Acquisition caps** — per-player and global limits, persisted across restarts
- **Enchantment rules** — max level + per-source blocking (anvil, table, loot, trading…)
- **Potion rules** — level + duration caps, covers splash / lingering / long / strong in one entry
- **World restrictions** — whitelist or blacklist per item
- **MySQL / SQLite** — pick either, data survives restarts
- **MiniMessage** — gradients, hex, legacy codes, PlaceholderAPI
- **Client cooldown bar** — via optional [PacketEvents](https://github.com/retrooper/packetevents)

## Quick start

```yaml
# plugins/ItemLimiter/items.yml
ENDER_PEARL:
  limit:
    in_inventory: 8
  cooldown:
    time: 60
    trigger:
      - THROW
  blacklist_sources:
    - MOB_DROPS
    - TRADING
    - BARTERING
```

1. Grab the JAR from [Releases](https://github.com/PanHaskins/ItemLimiter/releases)
2. Drop it in `plugins/`, start the server
3. Edit `plugins/ItemLimiter/items.yml`, restart

## Requirements

| | Version | Notes |
|---|---|---|
| **Paper** (Purpur, Folia) | 1.21+ | Spigot not supported |
| **Java** | 21+ | |
| **PacketEvents** | latest | *optional* — client cooldown bar |
| **PlaceholderAPI** | 2.11+ | *optional* — placeholders in messages |

## Documentation

Full configuration reference lives on the **[Wiki](https://github.com/PanHaskins/ItemLimiter/wiki)**:

- [Items Configuration](https://github.com/PanHaskins/ItemLimiter/wiki/Items-Configuration) → every option, explained
- [Sources & Triggers](https://github.com/PanHaskins/ItemLimiter/wiki/Sources-and-Triggers) → all 13 sources, all 8 triggers
- [Enchantments & Potions](https://github.com/PanHaskins/ItemLimiter/wiki/Enchantments-and-Potions) → level and duration caps
- [Database Setup](https://github.com/PanHaskins/ItemLimiter/wiki/Database-Setup) → MySQL / SQLite
- [Messages](https://github.com/PanHaskins/ItemLimiter/wiki/Messages) → MiniMessage, placeholders, notifications