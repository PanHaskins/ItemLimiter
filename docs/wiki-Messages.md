# Messages

ItemLimiter uses `messages.yml` for player-facing messages and `config.yml` for source notifications and display names.

## Supported Formats

You can use different formats in messages. ItemLimiter uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) internally:

| Format | Example | What it does |
|--------|---------|-------------|
| Legacy `&` codes | `&cRed text` | Colors with `&` |
| Legacy `§` codes | `§bAqua text` | Colors with `§` |
| Hex colors | `&#FF5555Dark red` | Custom colors |
| Gradients | `{#FF0000>}text{#0000FF<}` | Color fade from red to blue |
| MiniMessage | `<gold>Gold text</gold>` | MiniMessage format |

If you have **PlaceholderAPI** installed, you can also use its placeholders (e.g., `%player_name%`).

---

## messages.yml

These messages are shown to players when something is blocked or limited:

```yaml
crafting:
  blocked: "&cYou cannot craft this item."
  limit_reached: "&cYou have reached the crafting limit."
  limit_warning: "&eYou have %remaining% crafts left for %item%."
  world_restricted: "&cYou cannot craft this item here."
anvil:
  blocked: "&cEnchantment level not allowed."
brewing:
  blocked: "&cBrewing blocked. This potion is restricted."
  partial: "&eSome potions could not be brewed due to restrictions."
inventory:
  limit_reached: "<yellow>Restricted item removed from inventory."
  cooldown: "&cPlease wait %seconds%s before using this item."
  limit_warning: "&eYou can only hold %remaining% more %item%."
loot:
  blocked: "&cYou cannot obtain this item."
```

### Placeholders in messages.yml

| Placeholder | What it shows | Where you can use it |
|-------------|-------------|---------------------|
| `%remaining%` | Items left before limit | `crafting.limit_warning`, `inventory.limit_warning` |
| `%item%` | Item name from `items.yml` | `crafting.limit_warning`, `inventory.limit_warning` |
| `%seconds%` | Cooldown time left | `inventory.cooldown` |

---

## config.yml — Source Notifications

The `notification.sources` section shows messages when players get items. You can set messages for specific remaining counts:

```yaml
notification:
  sources:
    "10": "<yellow>%left_sources% items left for %item%."
    "5": "<yellow>%left_sources% items left for %item%."
    "1": "<yellow>%left_sources% item left for %item%."
    always: "<gray><hover:show_text:'X: %x% Y: %y% Z: %z%'>%player%</hover> get %item% from %source_name%."
```

**How it works:**
- `"10"` — shown when 10 items are left
- `"5"` — shown when 5 are left
- `"1"` — shown when only 1 is left (last warning)
- `always` — shown **every time** a player gets the item (good for logging)

These also work with MiniMessage features like `<hover:show_text:'...'>` for hover text.

### Notification Placeholders

| Placeholder | What it shows |
|-------------|-------------|
| `%player%` | Player name (or `unknown_player` from config when no player is known) |
| `%item%` | Item name from `items.yml` |
| `%source_name%` | Source display name (see below) |
| `%left_sources%` | How many items are left (shows infinity if no limit) |
| `%x%`, `%y%`, `%z%` | Player or block source location |

---

## Changing Source Names

You can change what source names look like in messages. Set them in `config.yml` under `sources:` (use lowercase):

```yaml
sources:
  crafting: "Crafting"
  furnace: "Furnace"
  enchanting: "Enchanting"
  smithing: "Smithing"
  block_drops: "Block drops"
  mob_drops: "Mob drops"
  treasure: "Treasure"
  bartering: "Bartering"
  trading: "Trading"
  brewing: "Brewing"
  anvil: "Anvil"
  fishing: "Fishing"
  shearing: "Shearing"
```

These names show up in messages where `%source_name%` is used. Change them to fit your server's style or language.
