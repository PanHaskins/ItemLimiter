# Enchantments & Potions

ItemLimiter can limit enchantments and potion effects with extra options that regular items don't have.

---

## Enchantment Rules

Enchantment names use this format: `NAME_ENCHANT`

The name before `_ENCHANT` must match a [Bukkit Enchantment](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/enchantments/Enchantment.html) name (e.g., `SHARPNESS`, `MENDING`, `EFFICIENCY`). You can find all enchantment names in the [Spigot API docs](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/enchantments/Enchantment.html).

```yaml
SHARPNESS_ENCHANT:
  max_level: 3                # Max Sharpness III (blocks IV and V)
  limit:
    in_inventory: 1           # Only 1 item with Sharpness in inventory
  blacklist_sources:
    - ENCHANTING              # Can't get from enchanting table
    - ANVIL                   # Can't add with anvil
    - TREASURE                # Can't find in loot
    - TRADING                 # Can't buy from villagers
```

### `max_level`

Sets the highest enchantment level allowed:
- `max_level: 3` — allows levels I, II, III; blocks IV and V
- `max_level: 0` — bans the enchantment completely

### Enchantment Sources

You can block enchantments from these sources:

| Source | What it means |
|--------|--------------|
| `ENCHANTING` | Enchanting Table |
| `ANVIL` | Adding or combining enchants in Anvil |
| `TREASURE` | Enchanted items found in loot chests |
| `TRADING` | Enchanted items bought from villagers |
| `FISHING` | Enchanted items caught while fishing |
| `MOB_DROPS` | Enchanted items dropped by mobs |

### Examples

**Ban Mending completely:**
```yaml
MENDING_ENCHANT:
  max_level: 0
```

**Allow Efficiency up to III, only from enchanting table:**
```yaml
EFFICIENCY_ENCHANT:
  max_level: 3
  blacklist_sources:
    - ANVIL
    - TREASURE
    - TRADING
    - FISHING
    - MOB_DROPS
```

**Limit Sharpness — max level I, only 1 per player:**
```yaml
SHARPNESS_ENCHANT:
  max_level: 1
  limit:
    in_inventory: 1
    sources:
      per_player: 1
      global: -1
  blacklist_sources:
    - TREASURE
    - ENCHANTING
    - ANVIL
    - FISHING
```

---

## Potion Rules

Potion names use this format: `NAME_POTION`

The name before `_POTION` must match a [Bukkit PotionEffectType](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html) name (e.g., `STRENGTH`, `SPEED`, `INVISIBILITY`). You can find all potion names in the [Spigot API docs](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html).

> **Note:** One potion rule covers **all forms** of that potion — regular, splash, and lingering are all included.

```yaml
STRENGTH_POTION:
  max_level: 1                # Max Strength I (blocks Strength II)
  max_duration: 180           # Max 3 minutes (blocks longer potions)
  limit:
    in_inventory: 2           # Max 2 strength potions in inventory
    sources:
      per_player: 5           # Each player can get max 5
  blacklist_sources:
    - BREWING                 # Can't brew
    - TREASURE                # Can't find in loot
```

### `max_level`

Sets the highest potion level allowed:
- `max_level: 1` — only base potions (e.g., Strength I)
- `max_level: 2` — allows up to level II
- `max_level: 0` — bans the potion completely

### `max_duration`

Sets the longest potion time allowed (in seconds):
- `max_duration: 180` — blocks potions longer than 3 minutes
- `max_duration: 0` or not set — no time limit

### Potion Sources

| Source | What it means |
|--------|--------------|
| `BREWING` | Brewing Stand |
| `TREASURE` | Potions found in loot chests |
| `MOB_DROPS` | Potions dropped by mobs (e.g., witches) |
| `BARTERING` | Potions from Piglin bartering |

### Examples

**Ban Invisibility potions:**
```yaml
INVISIBILITY_POTION:
  max_level: 0
```

**Limit Turtle Master to level I, max 30 seconds:**
```yaml
TURTLE_MASTER_POTION:
  max_level: 1
  max_duration: 30
  blacklist_sources:
    - BREWING
```

**Allow Speed potions but limit how many:**
```yaml
SPEED_POTION:
  max_level: 2
  limit:
    in_inventory: 5
    sources:
      per_player: 20
      global: -1
```
