# Item Format

Reference for every field accepted by an item descriptor. Used inside `exception.items.*` (and any future item-descriptor section).

The format is **DeluxeMenus-compatible**: most field names match the same plugin.

A descriptor matches an item when **every field you set** is satisfied. Fields you omit are ignored, the candidate item may have anything in them.

---

## Quick example

```yaml
MyItem:
  material: "DIAMOND_SWORD"
  display_name: "&6Hero Blade"
  lore:
    - "&7Forged for champions"
  custom_model_data: 1234
  unbreakable: true
  enchantments:
    - "sharpness;5"
```

---

## Field reference

| Field | Type | What it does |
|-------|------|--------------|
| [`material`](#material) | string | Item type. Vanilla name or external-plugin prefix. |
| [`display_name`](#display_name) / `name` | string | Exact display name. Supports `&` and MiniMessage. |
| [`item_name`](#item_name) | string | Exact item-name component (1.20.5+). |
| [`lore`](#lore) | string list | Lines that must appear in the item's lore. |
| [`custom_model_data`](#custom_model_data) / `model_data` | int | Exact custom model data value. |
| [`item_model`](#item_model) | namespaced key | Exact `item_model` component (1.21.4+). |
| [`unbreakable`](#unbreakable) | bool | Must match the item's unbreakable flag. |
| [`durability`](#durability) | int | Exact damage value. |
| [`item_flags`](#item_flags) | string list | All listed `ItemFlag`s must be set. |
| [`enchantments`](#enchantments) | string list | Required enchantments, level is a minimum. |
| [`stored_enchants`](#stored_enchants) | string list | Same, but for enchanted books. |
| [`rgb`](#rgb) | `R,G,B` | Exact leather-armor colour. |
| [`potion_color`](#potion_color) | `R,G,B` | Exact potion bottle colour. |
| [`base_potion`](#base_potion) | string | Base potion type. |
| [`potion_effects`](#potion_effects) | string list | Required custom potion effects. |
| [`banner_meta`](#banner_meta) | string list | All listed banner patterns must be present. |
| [`trim_material`](#trim_material) | string | Required armor-trim material. |
| [`trim_pattern`](#trim_pattern) | string | Required armor-trim pattern. |
| [`skull_owner`](#skull_owner) | string | Player name of the skull owner. |

---

## `material`

Vanilla item type or an external-plugin reference. Inside `exception.items.*` this field is **optional**, but at least one other identifying field must then be set.

| Prefix | Source | Example |
|--------|--------|---------|
| *(none)* | Vanilla `Material` | `DIAMOND_SWORD` |
| `basehead-` | Textured player head (base64) | `basehead-eyJ0ZXh0dXJlcyI6...` |
| `hdb-` | HeadDatabase | `hdb-12345` |
| `itemsadder-` | ItemsAdder (`namespace:id`) | `itemsadder-myitems:ruby` |
| `oraxen-` | Oraxen | `oraxen-magic_apple` |
| `nexo-` | Nexo | `nexo-magic_apple` |
| `mmoitems-` | MMOItems (`TYPE:ID`) | `mmoitems-SWORD:CUTLASS` |

> External-plugin descriptors only match when that plugin is installed. Otherwise the descriptor logs a warning at load and never matches at runtime.

```yaml
material: "GOLDEN_APPLE"
# or
material: "oraxen-magic_apple"
```

## `display_name`

Exact match against the item's displayed name. The `name` alias works the same.

```yaml
display_name: "&6&lSpecial Apple"
```

Comparison is done on plain text: colour codes don't have to match the source format, only the rendered text does.

## `item_name`

Exact match against the `item_name` component (the non-italic name, 1.20.5+).

```yaml
item_name: "Hero Blade"
```

## `lore`

Each listed line must appear **somewhere** in the item's lore. Order doesn't matter and the item can have extra lines.

```yaml
lore:
  - "&7Forged for champions"
  - "&7Season 2 reward"
```

## `custom_model_data`

Exact match against the first numeric value of the item's `custom_model_data` component. The `model_data` alias works the same.

```yaml
custom_model_data: 1001
```

## `item_model`

Exact match against the item-model component (1.21.4+). Use a namespaced key.

```yaml
item_model: "mypack:hero_blade"
```

## `unbreakable`

```yaml
unbreakable: true   # must be unbreakable
unbreakable: false  # must NOT be unbreakable
```

## `durability`

Exact damage value (0 = full durability).

```yaml
durability: 0
```

## `item_flags`

Every listed [`ItemFlag`](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/inventory/ItemFlag.html) must be present on the item. Extras on the item are allowed.

```yaml
item_flags:
  - HIDE_ENCHANTS
  - HIDE_ATTRIBUTES
```

## `enchantments`

Format: `name;level`. The level is a **minimum**: an item with a higher level still matches. The item may also carry other enchantments. Names use minecraft registry IDs (`sharpness`, `unbreaking`, `mending`, ...).

```yaml
enchantments:
  - "sharpness;3"      # Sharpness III or higher
  - "unbreaking"       # any level of Unbreaking
```

## `stored_enchants`

Same format as `enchantments`, but matches against enchanted-book stored enchants.

```yaml
stored_enchants:
  - "mending;1"
```

## `rgb`

Exact RGB colour for leather armor (`R,G,B`, each 0–255).

```yaml
rgb: "255,128,0"
```

## `potion_color`

Exact RGB colour of the potion bottle.

```yaml
potion_color: "100,200,255"
```

## `base_potion`

Base [`PotionType`](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionType.html) of the item.

```yaml
base_potion: "STRENGTH"
```

## `potion_effects`

Custom potion effects added to the bottle. Format: `effect;duration;amplifier` (duration in ticks, amplifier 0 = level I). All listed effects must be present; duration and amplifier are minimums.

```yaml
potion_effects:
  - "speed;600;1"           # Speed II for 30s or longer
  - "jump_boost;200;0"      # Jump Boost I for 10s or longer
```

## `banner_meta`

Banner patterns that must be on the banner. Format: `color;pattern` (DyeColor + PatternType).

```yaml
banner_meta:
  - "RED;stripe_top"
  - "WHITE;cross"
```

## `trim_material`

Armor-trim material (e.g. `gold`, `netherite`, `diamond`).

```yaml
trim_material: "gold"
```

## `trim_pattern`

Armor-trim pattern (e.g. `sentry`, `coast`, `wild`).

```yaml
trim_pattern: "sentry"
```

## `skull_owner`

Required owner name for a player head. Case-insensitive.

```yaml
material: "PLAYER_HEAD"
skull_owner: "Notch"
```

---

## Full example

```yaml
NETHERITE_CHESTPLATE:
  limit:
    in_inventory: 1
  exception:
    items:
      EpicArmor:
        material: "NETHERITE_CHESTPLATE"
        display_name: "&6Champion's Plate"
        lore:
          - "&7Awarded for Season 2"
          - "&7Bound to player"
        custom_model_data: 5001
        unbreakable: true
        item_flags:
          - HIDE_ATTRIBUTES
        enchantments:
          - "protection;4"
          - "unbreaking;3"
        trim_material: "gold"
        trim_pattern: "sentry"
```
