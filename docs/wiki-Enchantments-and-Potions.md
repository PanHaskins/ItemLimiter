# Enchantments & Potions

ItemLimiter can limit enchantments and potion effects with extra options that regular items don't have.

---

## Enchantment Rules

Enchantment names use this format: `NAME_ENCHANT`

The name before `_ENCHANT` must match a [Bukkit Enchantment](https://jd.papermc.io/paper/1.21/org/bukkit/enchantments/Enchantment.html) name (e.g., `SHARPNESS`, `MENDING`, `EFFICIENCY`).

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

Enchantments can use: `ENCHANTING`, `ANVIL`, `TREASURE`, `TRADING`, `FISHING`, `MOB_DROPS`. See [Sources & Triggers](Sources-and-Triggers) for details.

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

### Enchanting Table Behavior

- **Banned or exhausted slot** — shown as locked (grey, unclickable, same as "not enough XP"). No cost, no accidental click.
- **Over-cap level** — tooltip rewrites to the capped level at the original XP cost. Clicking applies the capped level.
- **Primary (hover enchant)** — always applied at the level shown. If it becomes invalid between hover and click, the event is cancelled (no XP/lapis used).
- **Surprise secondaries** (the `??? ???` line) — banned or exhausted ones are silently stripped from the result; the primary and other allowed secondaries go through.

---

## Potion Rules

Potion names use this format: `NAME_POTION`

The name before `_POTION` must match a [PotionType](https://jd.papermc.io/paper/1.21/org/bukkit/potion/PotionType.html) name (e.g., `STRENGTH`, `SWIFTNESS`, `INVISIBILITY`).

> **Note:** One potion rule covers **all forms** and **all variants** of that potion — regular, splash, lingering, long-duration, and strong (level II) are all included under a single entry.

### Available Potion Names

| Config Name | In-Game Potion |
|-------------|---------------|
| `STRENGTH_POTION` | Potion of Strength |
| `SWIFTNESS_POTION` | Potion of Speed |
| `LEAPING_POTION` | Potion of Jump Boost |
| `REGENERATION_POTION` | Potion of Regeneration |
| `HEALING_POTION` | Potion of Instant Health |
| `HARMING_POTION` | Potion of Instant Damage |
| `POISON_POTION` | Potion of Poison |
| `SLOWNESS_POTION` | Potion of Slowness |
| `WEAKNESS_POTION` | Potion of Weakness |
| `FIRE_RESISTANCE_POTION` | Potion of Fire Resistance |
| `WATER_BREATHING_POTION` | Potion of Water Breathing |
| `INVISIBILITY_POTION` | Potion of Invisibility |
| `NIGHT_VISION_POTION` | Potion of Night Vision |
| `TURTLE_MASTER_POTION` | Potion of the Turtle Master |
| `SLOW_FALLING_POTION` | Potion of Slow Falling |
| `WIND_CHARGED_POTION` | Wind Charged effect |
| `WEAVING_POTION` | Weaving effect |
| `OOZING_POTION` | Oozing effect |
| `INFESTED_POTION` | Infested effect |
| `BAD_OMEN_POTION` | Bad Omen — triggers a raid on village entry |
| `RAID_OMEN_POTION` | Raid Omen — makes the next player-triggered raid ominous |
| `TRIAL_OMEN_POTION` | Trial Omen — turns nearby Trial Spawners ominous |

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

Potions can use: `BREWING`, `TREASURE`, `MOB_DROPS`, `BARTERING`. See [Sources & Triggers](Sources-and-Triggers) for details and `per_player`/`global` support.

### Applied Effects (non-item sources)

`max_level` and `max_duration` also cap potion effects that arrive directly on a player without going through an item in the inventory. This is how effects like **Wind Charged**, **Weaving**, **Oozing**, **Infested**, **Raid Omen**, and **Trial Omen** reach players — they come from arrows, mob attacks, food, or death auras rather than drinkable potions.

Paths covered:

| Source | Example |
|--------|---------|
| Tipped arrows | Skeleton, player bow/crossbow, dispenser |
| Area Effect Clouds | Lingering potions, dragon breath |
| Mob attacks | Cave Spider Poison, Husk Hunger, Witch self-buffs, Warden Darkness |
| Food | Suspicious Stew, Golden Apple, Ominous Bottle |
| Raid | Patrol Captain kill → Bad Omen |
| Death auras | Bogged / Breeze etc. applying Weaving, Wind Charged, Oozing, Infested on hit |
| Totem of Undying | Regeneration, Absorption, Fire Resistance |

Paths **not** touched (bypass):

- `/effect` commands — admin tooling stays unrestricted
- Splash and drink potions — already covered by the brewing / item pipeline above
- Beacon and Conduit Power — server infrastructure effects

**Banned** effects (`max_level: 0`) never apply. **Capped** effects are downgraded to the configured `max_level` and `max_duration` before they take hold. Enforcement is silent — no chat message on cancel or cap. Only players are affected; mob-vs-mob effects are out of scope.

> **Note:** The three omen effects are distinct in vanilla and have different mechanics (raid trigger, raid intensifier, trial-chamber intensifier), so each has its own config key. When Bad Omen converts to Raid Omen on village entry, vanilla re-applies the effect through an internal path that falls under the `/effect` bypass — the listener cannot block the conversion itself. Restrict `BAD_OMEN_POTION` at the source (Patrol Captain, Ominous Bottle) so Bad Omen never arrives in the first place.

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

**Allow Swiftness (Speed) potions but limit how many:**
```yaml
SWIFTNESS_POTION:
  max_level: 2
  limit:
    in_inventory: 5
    sources:
      per_player: 20
      global: -1
```
