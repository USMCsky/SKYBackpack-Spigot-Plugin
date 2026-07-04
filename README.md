# SKYBackpack

SKYBackpack is a simple Spigot plugin that adds a craftable **54-slot virtual backpack** for each player.

## What it does

- Adds a custom **Backpack** item.
- Opens a **54-slot storage inventory** when right-clicked in hand.
- Saves backpack contents per player using their **UUID**.
- Prevents backpacks from being stored inside the backpack inventory.

## Crafting recipe

```text
+---+---+---+
| I | L | I |
+---+---+---+
| L | C | L |
+---+---+---+
| I | L | I |
+---+---+---+

I = Iron Ingot
L = Leather
C = Chest
```

## Usage

1. Craft a backpack.
2. Hold it in your main hand.
3. Right-click to open your personal 54-slot backpack.

## Important behavior

- **Not wearable:** the backpack is not armor or an equipable item; it is only a carried storage item.
- **One backpack storage per player:** all backpack items owned by the same player open the same saved inventory.
- **Multiple backpack items allowed:** players can craft and carry more than one backpack item.
- **Death behavior:** if a player dies, the **stored backpack contents remain saved**. If they lose the backpack item itself, they can craft another backpack and regain access to the same stored contents.

## Compatibility

- **Spigot API:** 1.21
- **Java:** 21
