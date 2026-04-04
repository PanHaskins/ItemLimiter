# Items Configuration

The `items.yml` file is where you set all item rules. Each item has its own section.

## Item Name Format

The item name tells the plugin what type it is:

| Ending | Type | Example |
|--------|------|---------|
| *(nothing)* | Regular item or block | `ENDER_PEARL`, `GOLDEN_APPLE`, `TNT` |
| `_POTION` | Potion effect | `STRENGTH_POTION`, `SPEED_POTION` |
| `_ENCHANT` | Enchantment | `SHARPNESS_ENCHANT`, `MENDING_ENCHANT` |

Regular items use [Bukkit Material](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html) names.
Enchantments use [Bukkit Enchantment](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/enchantments/Enchantment.html) names with `_ENCHANT` at the end.
Potions use [Minecraft potion type](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionType.html) names with `_POTION` at the end (e.g., `STRENGTH_POTION`, `SWIFTNESS_POTION`). One entry covers all variants (normal, splash, lingering, long, strong). See [Enchantments & Potions](Enchantments-and-Potions) for the full list.

> See [Enchantments & Potions](Enchantments-and-Potions) for more details on enchantment and potion rules.

## Full Item Structure

```yaml
ITEM_NAME:
  limit:
    in_inventory: <number>      # Max items in inventory
    sources:
      per_player: <number>      # Max one player can get (-1 = no limit)
      global: <number>          # Max for the whole server (-1 = no limit)
  cooldown:
    time: <seconds>             # How long the cooldown lasts
    trigger:                    # What starts the cooldown
      - TRIGGER_NAME
  blacklist_sources:            # Where the item CANNOT come from
    - SOURCE_NAME
  worlds:
    blacklist: <true/false>     # true = block in listed worlds; false = allow ONLY in listed worlds
    list:
      - "world_name"
```

**All sections are optional.** Only add what you need. If you skip a limit, it defaults to no limit (`-1`).

---

## Limits

### `in_inventory`

Sets the max number of this item a player can have in their inventory. The plugin checks this when:
- A player picks up an item
- A player clicks in their inventory
- A player joins the server

If a player has too many, the extra items are removed and the player gets a message.

```yaml
GOLDEN_APPLE:
  limit:
    in_inventory: 4   # Player can hold max 4 golden apples
```

### `sources.per_player`

Sets how many times one player can get this item. This is saved in the database. Use `-1` or leave it out for no limit.

> **Important:** `per_player` doesn't work for all sources. See the [Sources table](Sources-and-Triggers#regular-item-sources) for which sources support it.

### `sources.global`

Sets how many times this item can be gotten by ALL players on the server. Works for all sources. If you set both `per_player` and `global`, the **lower number** wins.

```yaml
NETHERITE_INGOT:
  limit:
    sources:
      per_player: 2    # Each player can get max 2
      global: 10        # The whole server can only get 10
```

**How it works:** When a player tries to get an item:
1. Is the source blocked? -> Stop (checked first)
2. Did the player reach their `per_player` limit? -> Stop
3. Did the server reach the `global` limit? -> Stop
4. If none of these -> Allow it and add 1 to both counters

---

## Cooldowns

Adds a wait time between item uses. The player cannot use the item again until the cooldown is over.

```yaml
MACE:
  cooldown:
    time: 15            # 15-second cooldown
    trigger:
      - DAMAGE          # Starts when hitting something
```

### Multiple Triggers

An item can have more than one trigger. The cooldown starts on ANY of them:

```yaml
DIAMOND_AXE:
  cooldown:
    time: 2
    trigger:
      - DAMAGE          # Hitting mobs
      - BLOCK_BREAK     # Breaking blocks
      - INTERACT        # Stripping logs, etc.
```

After hitting a mob with this axe, the player can't hit, break, or interact with it for 2 seconds.

### Client-Side Display

If [PacketEvents](https://github.com/retrooper/packetevents) is installed, players see the vanilla cooldown bar on their hotbar. Without it, cooldowns still work server-side but the bar won't show.

### All Triggers

| Trigger | What it does | Used for |
|---------|-------------|----------|
| `DAMAGE` | Player hits a mob, animal, or player | Swords, axes, maces, tridents |
| `CONSUME` | Player eats or drinks | Food, potions, golden apples |
| `THROW` | Player throws something | Ender pearls, snowballs, eggs, splash potions, tridents |
| `BLOCK_BREAK` | Player breaks a block with the item | Pickaxes, shovels, axes, hoes |
| `BLOCK_PLACE` | Player places a block | TNT, obsidian, any block |
| `FISHING` | Player uses a fishing rod | Fishing rods |
| `SHEAR` | Player shears something | Shears |
| `INTERACT` | Player right-clicks with the item | Flint & steel, bone meal, shears, buckets |

> See [Sources & Triggers](Sources-and-Triggers) for more details.

---

## Source Blocking

Stops players from getting items in certain ways:

```yaml
DIAMOND_SWORD:
  blacklist_sources:
    - CRAFTING          # Can't craft diamond swords
    - TRADING           # Can't buy from villagers
    - TREASURE          # Can't find in loot chests
```

When a source is blocked, the action is stopped and the player gets a message.

> See [Sources & Triggers](Sources-and-Triggers) for all 13 sources.

---

## World Restrictions

You can make rules work only in some worlds:

```yaml
ELYTRA:
  worlds:
    blacklist: true       # Block in these worlds
    list:
      - "world_the_end"
```

- `blacklist: true` — rules work everywhere **except** the listed worlds
- `blacklist: false` — rules work **only in** the listed worlds (whitelist mode)

If you skip `worlds`, the rules work in all worlds.

---

## Using All Systems Together

You can mix all systems on one item. Here is a full example:

```yaml
MACE:
  limit:
    in_inventory: 1           # Only 1 mace in inventory
    sources:
      per_player: 1           # Each player can only get 1
      global: 5               # Max 5 maces on the whole server
  cooldown:
    time: 15                  # 15s cooldown between hits
    trigger:
      - DAMAGE
  blacklist_sources:
    - CRAFTING                # Can't craft it — must find it
    - TRADING
```

This makes a rare weapon: only 5 on the server, 1 per player, can't be crafted, and has a 15-second hit cooldown.

## Default Examples

These are the items that come with the plugin:

```yaml
MACE:
  limit:
    in_inventory: 1
    sources:
      per_player: 1
      global: 1
  cooldown:
    time: 15
    trigger:
      - DAMAGE
  blacklist_sources:
    - CRAFTING

ENDER_PEARL:
  limit:
    in_inventory: 8
  cooldown:
    time: 60
    trigger:
      - THROW

GOLDEN_APPLE:
  limit:
    in_inventory: 20
  cooldown:
    time: 30
    trigger:
      - CONSUME
```
