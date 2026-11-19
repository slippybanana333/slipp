#!/usr/bin/env python3
"""
Generates every resource file the mod needs that isn't Java:
  textures, item definitions, models, blockstate, recipes, loot tables, tags, lang.

Run from the repo root:   python3 tools/gen_assets.py

When you add a new essence / boss / sigil / rune, add it to the lists below, add the matching
enum entry / boss definition in Java, then re-run this script.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "tuffsmp"

# ---------------------------------------------------------------------------------------------
# Content tables  (id, display name, colour, lore)
# ---------------------------------------------------------------------------------------------
LEVEL1 = [  # potion-effect essences, dropped by Tier 1 bosses
    ("verdant", "Verdant Essence", 0x55C455, "Permanent Regeneration"),
    ("ember", "Ember Essence", 0xFF6A2A, "Permanent Fire Resistance"),
    ("frost", "Frost Essence", 0x8FD9FF, "Permanent Resistance"),
    ("storm", "Storm Essence", 0xB89CFF, "Permanent Speed"),
    ("tide", "Tide Essence", 0x2FA7C9, "Permanent Water Breathing"),
    ("gilded", "Gilded Essence", 0xFFD23F, "Permanent Haste"),
]
LEVEL2 = [  # ability essences, dropped by Tier 2 (altar) bosses
    ("rift", "Rift Essence", 0xA020F0, "Unlocks the Blink ability"),
    ("wraith", "Wraith Essence", 0x6A6A9A, "Unlocks the Dash ability"),
    ("titan", "Titan Essence", 0xD0D0D0, "Unlocks the Slam ability"),
    ("gale", "Gale Essence", 0x9BF0D0, "Unlocks the Leap ability"),
    ("fury", "Fury Essence", 0xD0141C, "Unlocks the Overcharge ability"),
]
MAGIC = ("magic", "Magic Essence", 0xFF5CF0)

# Tier 1 summon items: (boss id, item name, colour, thematic ingredient)
SIGILS = [
    ("verdant", "Mossback Sigil", 0x55C455, "minecraft:moss_block"),
    ("ember", "Cinderfang Sigil", 0xFF6A2A, "minecraft:magma_block"),
    ("frost", "Rimebound Sigil", 0x8FD9FF, "minecraft:packed_ice"),
    ("storm", "Stormcaller Sigil", 0xB89CFF, "minecraft:lightning_rod"),
    ("tide", "Tidewrecked Sigil", 0x2FA7C9, "minecraft:prismarine"),
    ("gilded", "Gilded Sigil", 0xFFD23F, "minecraft:gold_block"),
]
# Tier 2 altar offerings
RUNES = [
    ("rift", "Rift Rune", 0xA020F0, "minecraft:ender_eye"),
    ("wraith", "Wraith Rune", 0x6A6A9A, "minecraft:crying_obsidian"),
    ("titan", "Titan Rune", 0xD0D0D0, "minecraft:iron_block"),
    ("gale", "Gale Rune", 0x9BF0D0, "minecraft:phantom_membrane"),
    ("fury", "Fury Rune", 0xD0141C, "minecraft:blaze_rod"),
]

SIGIL_LORE = "Right-click the ground to awaken a boss. No announcement is made."
RUNE_LORE = "Right-click a Boss Altar to begin a summoning the whole server will hear."


# ---------------------------------------------------------------------------------------------
# Pixel helpers
# ---------------------------------------------------------------------------------------------
def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def shade(c, f):
    r, g, b = rgb(c) if isinstance(c, int) else c[:3]
    return (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))), 255)


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def essence_texture(color, glow=False):
    """A faceted crystal."""
    img = new()
    px = img.load()
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx) / 6.2, abs(y - cy) / 7.4
            if dx + dy <= 1.0:
                # facets: lit from the upper left
                lit = 1.25 - (x - cx) * 0.05 - (y - cy) * 0.05
                if (x < cx) != (y < cy):
                    lit -= 0.18
                px[x, y] = shade(color, lit)
    # outline
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                continue
            edge = any(
                0 <= x + ox < 16 and 0 <= y + oy < 16 and px[x + ox, y + oy][3] == 0
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ) or x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = shade(color, 0.55)
    # sparkle
    for (x, y) in ((6, 4), (5, 5), (7, 5), (6, 6)):
        if px[x, y][3]:
            px[x, y] = (255, 255, 255, 255) if (x, y) == (6, 5) or (x, y) == (6, 4) else shade(color, 1.5)
    px[6, 5] = (255, 255, 255, 255)
    return img


def magic_texture():
    """A swirling multi-colour orb."""
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d <= 6.6:
                ang = math.atan2(dy, dx) + d * 0.55
                hue = (ang / (2 * math.pi)) % 1.0
                r = 0.5 + 0.5 * math.sin(2 * math.pi * hue)
                g = 0.5 + 0.5 * math.sin(2 * math.pi * (hue + 1 / 3))
                b = 0.5 + 0.5 * math.sin(2 * math.pi * (hue + 2 / 3))
                f = 1.15 - d / 14
                px[x, y] = (int(min(255, (120 + 135 * r) * f)), int(min(255, (90 + 110 * g) * f)),
                            int(min(255, (150 + 105 * b) * f)), 255)
            if 6.0 < d <= 7.2 and px[x, y][3] == 0:
                px[x, y] = (60, 20, 90, 255)
            elif 6.2 < d <= 7.2:
                px[x, y] = (60, 20, 90, 255)
    for (x, y) in ((5, 4), (6, 4), (5, 5)):
        px[x, y] = (255, 255, 255, 255)
    return img


def sigil_texture(color):
    """A dark disc with a glowing star glyph."""
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.2:
                px[x, y] = shade((40, 36, 48), 1.0 + (7.5 - d) * 0.03)
            if 5.6 <= d <= 7.2:
                px[x, y] = shade(color, 0.7 if d > 6.6 else 1.0)
    # four-point star
    for i in range(-4, 5):
        k = 1.0 - abs(i) / 5
        px[8 + i if 0 <= 8 + i < 16 else 8, 7] = shade(color, 0.9 + k * 0.6)
        px[7, 8 + i if 0 <= 8 + i < 16 else 8] = shade(color, 0.9 + k * 0.6)
    px[7, 7] = (255, 255, 255, 255)
    px[8, 8] = shade(color, 1.4)
    return img


def rune_texture(color):
    """A stone tablet with a carved, glowing glyph."""
    img = new()
    px = img.load()
    rnd = random.Random(color)
    for y in range(1, 15):
        for x in range(3, 13):
            base = 70 + rnd.randint(-8, 8)
            px[x, y] = (base, base, base + 6, 255)
    for x in range(3, 13):
        px[x, 1] = (110, 110, 120, 255)
        px[x, 14] = (40, 40, 46, 255)
    for y in range(1, 15):
        px[3, y] = (100, 100, 110, 255)
        px[12, y] = (42, 42, 48, 255)
    glyph = [(7, 4), (8, 4), (6, 5), (9, 5), (6, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 8), (6, 9), (9, 9),
             (6, 10), (9, 10), (7, 11), (8, 11)]
    for g in glyph:
        px[g] = shade(color, 1.25)
    for (x, y) in ((7, 4), (8, 11)):
        px[x, y] = (255, 255, 255, 255)
    return img


def altar_side_texture():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            v = 22 + rnd.randint(-5, 6)
            px[x, y] = (v + 6, v, v + 18, 255)
    for x in range(16):
        px[x, 0] = (60, 40, 90, 255)
        px[x, 15] = (14, 8, 26, 255)
    for y in range(16):
        px[0, y] = (50, 34, 78, 255)
        px[15, y] = (14, 8, 26, 255)
    glow = (190, 90, 255, 255)
    for y in range(4, 12):
        px[7, y] = glow
        px[8, y] = shade(glow, 0.8)
    for x in range(5, 11):
        px[x, 7] = glow
        px[x, 8] = shade(glow, 0.8)
    return img


def altar_top_texture():
    img = altar_side_texture()
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 <= d <= 6.2:
                px[x, y] = (190, 90, 255, 255)
            elif d < 4.0:
                px[x, y] = (30, 12, 50, 255)
    for i in range(-3, 4):
        px[8 + i, 8] = (230, 160, 255, 255)
        px[8, 8 + i] = (230, 160, 255, 255)
    return img


def altar_bottom_texture():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    rnd = random.Random(11)
    for y in range(16):
        for x in range(16):
            v = 16 + rnd.randint(-3, 4)
            px[x, y] = (v + 4, v, v + 12, 255)
    return img


# ---------------------------------------------------------------------------------------------
# Emit
# ---------------------------------------------------------------------------------------------
lang = {}


def item_files(item_id, name, tex, lore_note=None):
    write_json(f"assets/{NS}/items/{item_id}.json",
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"}})
    write_json(f"assets/{NS}/models/item/{item_id}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item_id}"}})
    save(tex, f"assets/{NS}/textures/item/{item_id}.png")
    lang[f"item.{NS}.{item_id}"] = name


for (eid, name, color, _) in LEVEL1 + LEVEL2:
    item_files(f"{eid}_essence", name, essence_texture(color))
item_files("magic_essence", MAGIC[1], magic_texture())
for (bid, name, color, _) in SIGILS:
    item_files(f"{bid}_sigil", name, sigil_texture(color))
for (bid, name, color, _) in RUNES:
    item_files(f"{bid}_rune", name, rune_texture(color))

# Boss altar block + item
write_json(f"assets/{NS}/blockstates/boss_altar.json", {"variants": {"": {"model": f"{NS}:block/boss_altar"}}})
write_json(f"assets/{NS}/models/block/boss_altar.json", {
    "parent": "minecraft:block/cube_bottom_top",
    "textures": {
        "top": f"{NS}:block/boss_altar_top",
        "bottom": f"{NS}:block/boss_altar_bottom",
        "side": f"{NS}:block/boss_altar_side",
    },
})
write_json(f"assets/{NS}/items/boss_altar.json",
           {"model": {"type": "minecraft:model", "model": f"{NS}:block/boss_altar"}})
save(altar_top_texture(), f"assets/{NS}/textures/block/boss_altar_top.png")
save(altar_side_texture(), f"assets/{NS}/textures/block/boss_altar_side.png")
save(altar_bottom_texture(), f"assets/{NS}/textures/block/boss_altar_bottom.png")
lang[f"block.{NS}.boss_altar"] = "Boss Altar"

# Key bindings (client)
lang[f"key.{NS}.use_ability"] = "Use Ability"
lang[f"key.{NS}.cycle_ability"] = "Cycle Ability"
lang[f"key.category.{NS}.main"] = "Tuff SMP"

write_json(f"assets/{NS}/lang/en_us.json", dict(sorted(lang.items())))

# Recipes ------------------------------------------------------------------------------------
def shaped(rid, pattern, key, result):
    write_json(f"data/{NS}/recipe/{rid}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": pattern,
        "key": key,
        "result": {"id": f"{NS}:{result}", "count": 1},
    })


for (bid, _, _, thematic) in SIGILS:
    shaped(f"{bid}_sigil", ["MIM", "IDI", "MIM"],
           {"M": thematic, "I": "minecraft:iron_ingot", "D": "minecraft:diamond"}, f"{bid}_sigil")
for (bid, _, _, thematic) in RUNES:
    shaped(f"{bid}_rune", ["NTN", "TDT", "NTN"],
           {"N": "minecraft:netherite_scrap", "T": thematic, "D": "minecraft:diamond_block"}, f"{bid}_rune")
shaped("boss_altar", ["OCO", "OEO", "OOO"],
       {"O": "minecraft:obsidian", "C": "minecraft:crying_obsidian", "E": "minecraft:enchanting_table"}, "boss_altar")

# Loot tables + tags -----------------------------------------------------------------------
write_json(f"data/{NS}/loot_table/blocks/boss_altar.json", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1.0,
        "bonus_rolls": 0.0,
        "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"{NS}:boss_altar"}],
    }],
    "random_sequence": f"{NS}:blocks/boss_altar",
})
# Bosses/minions use this so vanilla drops never stack on top of our own reward system.
write_json(f"data/{NS}/loot_table/empty.json", {"type": "minecraft:entity", "pools": []})
write_json("data/minecraft/tags/block/mineable/pickaxe.json", {"values": [f"{NS}:boss_altar"]})
write_json("data/minecraft/tags/block/needs_diamond_tool.json", {"values": [f"{NS}:boss_altar"]})

print("assets generated:", len(lang), "lang entries")
