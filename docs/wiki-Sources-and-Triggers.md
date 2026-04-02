# Sources & Triggers

This page lists all item sources and cooldown triggers in ItemLimiter.

---

## Sources

Sources are the ways a player can get an item. You can block sources with `blacklist_sources` and limit how many times a player can get an item with `limit.sources`.

### Regular Item Sources

| Source | What it covers |
|--------|---------------|
| `CRAFTING` | Crafting Table, Crafter block |
| `FURNACE` | Furnace, Blast Furnace, Smoker |
| `SMITHING` | Smithing Table |
| `ENCHANTING` | Enchanting Table |
| `BREWING` | Brewing Stand |
| `ANVIL` | Anvil (repair, combine, rename) |
| `TRADING` | Villager and Wandering Trader |
| `BARTERING` | Piglin bartering |
| `MOB_DROPS` | Drops from mobs and animals |
| `BLOCK_DROPS` | Drops from broken blocks |
| `TREASURE` | Loot chests, dungeons, structures |
| `FISHING` | Fishing rod catches |
| `SHEARING` | Shearing sheep, mooshrooms, snow golems, bogged |

### Enchantment Sources

Enchantments (`_ENCHANT` items) can only use these sources:

| Source | What it means |
|--------|--------------|
| `ENCHANTING` | Enchanting Table |
| `ANVIL` | Adding or combining in Anvil |
| `TREASURE` | Enchanted items in loot |
| `TRADING` | Enchanted items from villagers |
| `FISHING` | Enchanted items from fishing |
| `MOB_DROPS` | Enchanted items dropped by mobs |

### Potion Sources

Potions (`_POTION` items) can only use these sources:

| Source | What it means |
|--------|--------------|
| `BREWING` | Brewing Stand |
| `TREASURE` | Potions in loot chests |
| `MOB_DROPS` | Potions dropped by mobs (e.g., witches) |
| `BARTERING` | Potions from Piglin bartering |

---

## Triggers

Triggers are actions that start a cooldown. You set them in `cooldown.trigger`.

| Trigger | When it starts | Good for |
|---------|---------------|----------|
| `DAMAGE` | Player hits a mob, animal, or player | Swords, axes, maces, tridents |
| `CONSUME` | Player eats food or drinks a potion | Golden apples, potions, chorus fruit |
| `THROW` | Player throws something | Ender pearls, snowballs, eggs, tridents, splash/lingering potions |
| `BLOCK_BREAK` | Player breaks a block with the item | Pickaxes, shovels, axes, hoes |
| `BLOCK_PLACE` | Player places a block | TNT, obsidian, any block |
| `FISHING` | Player uses a fishing rod | Fishing rods |
| `SHEAR` | Player shears something | Shears |
| `INTERACT` | Player right-clicks with the item | Flint & steel, bone meal, shears, buckets |

### How Triggers Work

1. A player does an action (e.g., throws an Ender Pearl)
2. The plugin checks if the item has a cooldown with that trigger
3. If the player is on cooldown -> the action is **stopped** and the player gets a message
4. If NOT on cooldown -> the action works and the cooldown timer starts

### Multiple Triggers

An item can have more than one trigger. The cooldown starts on the **first action** and blocks **all triggers** until it ends:

```yaml
DIAMOND_AXE:
  cooldown:
    time: 2
    trigger:
      - DAMAGE        # Hitting mobs
      - BLOCK_BREAK   # Breaking blocks
      - INTERACT      # Stripping logs, etc.
```

After hitting a mob with this axe, the player can't hit, break, or interact with it for 2 seconds.

---

## Sources vs. Triggers — What's the Difference?

These are two different things:

| | What it controls | Config key |
|--|-----------------|------------|
| **Sources** | How an item is **gotten** | `blacklist_sources`, `limit.sources` |
| **Triggers** | How an item is **used** | `cooldown.trigger` |

A source is checked when an item enters the game (crafting, looting, trading). A trigger is checked when a player uses the item. You can use both on the same item:

```yaml
ENDER_PEARL:
  blacklist_sources:           # Controls GETTING the item
    - TRADING
    - BARTERING
  cooldown:                    # Controls USING the item
    time: 60
    trigger:
      - THROW
```

This blocks getting Ender Pearls from trading and bartering, AND adds a 60-second cooldown when throwing them. The two systems work on their own.
