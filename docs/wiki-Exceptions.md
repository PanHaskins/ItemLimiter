# Exceptions

An **exception** is a bypass for a rule. When an exception matches, the rule is fully skipped: no usage tracking, no cooldown, no inventory cap, no source blacklist, no enchant/potion cap.

Add an `exception` block to any item rule:

```yaml
SHARPNESS_ENCHANT:
  max_level: 3
  exception:
    permission: "itemlimiter.bypass.sharpness"
    items:
      AdminBlade:
        material: "DIAMOND_SWORD"
        display_name: "&6Admin Blade"
```

You can use just `permission`, just `items`, or both. If either matches, the rule is skipped.

---

## Permissions

If the player holds **any** of the listed permissions, the rule is bypassed.

`permission` accepts a single string, a list, or both. All values are merged and deduplicated.

```yaml
exception:
  permission: "itemlimiter.bypass.pearl"            # single
  permissions:                                      # list (merged with above)
    - "itemlimiter.bypass.*"
    - "staff.bypass"
```

> **Note:** Permission bypass is checked first and short-circuits the whole rule. No item match is performed if the player has the permission.

---

## Items

Each entry under `items:` is an [item descriptor](Item-Format). The plugin walks them **top to bottom** and stops on the first match, so put cheap, common entries first for the fastest lookup.

```yaml
exception:
  items:
    EventMace:                  # tried first
      material: "MACE"
      display_name: "&dEvent Mace"
    LegacyMace:                 # tried only if EventMace didn't match
      material: "MACE"
      lore:
        - "&7Season 1 reward"
```

### How matching works

A descriptor matches when **every field you set** is satisfied by the candidate item. Fields you don't write are ignored. This means you only need to list the parts that uniquely identify your item, the rest can be anything.

```yaml
items:
  AnyRewardItem:
    lore:
      - "&7Server reward"        # any item carrying this lore line matches
```

> **Material is optional in `exception.items.*`** (and only there). If you omit it, at least one other identifying field (`display_name`, `lore`, `custom_model_data`, `enchantments`, `item_model`, `skull_owner`, etc.) must be set. This stops match-everything bypasses.

### Lore matching

Lore is matched **line by line, in any order**. Every line you list must appear somewhere in the item's lore, but the item can have extra lines and the order doesn't have to match.

```yaml
items:
  RewardSword:
    material: "DIAMOND_SWORD"
    lore:
      - "&7Reward item"
      - "&7Season 2"
```

This matches any diamond sword whose lore contains **both** of those lines, regardless of position or extra lines around them.

### Enchantments

Listed enchantments must be present at **at least** the requested level. The item can have other enchantments too.

```yaml
items:
  EnchantedAxe:
    material: "DIAMOND_AXE"
    enchantments:
      - "sharpness;3"             # Sharpness III or higher
      - "unbreaking;1"             # Unbreaking I or higher
```

### Full field reference

See [Item Format](Item-Format) for every field a descriptor accepts and how each one matches.

---

## Full example

```yaml
ENDER_PEARL:
  cooldown:
    time: 60
    trigger: [THROW]
  exception:
    permission: "itemlimiter.bypass.pearl"
    items:
      ShopPearl:
        material: "ENDER_PEARL"
        lore:
          - "&7Bought from /shop"
      QuestPearl:
        material: "ENDER_PEARL"
        custom_model_data: 200
```
