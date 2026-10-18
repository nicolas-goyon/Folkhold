#!/usr/bin/env python3
"""Generate the resources of the building signs of the block library (folkhold_blocks).

A building sign is a sign stand in front of a building of the village: two posts, a cross bar and a hanging plaque
with an icon. Each kind of building has its own sign: icon, plaque colors and post materials.

    python3 tools/textures/building_signs.py

Output (blocks/src/main/resources):
- assets/folkhold_blocks/textures/block/building_sign/<kind>.png: the plaque (16 x 16, the model uses x 2..13 and
  y 2..13: a dark outline, a trim ring and the icon on the background, 8 x 8);
- assets/folkhold_blocks/models/block/building_sign_<kind>.json: the sign stand (1.5 blocks high) and the relief of
  the plaque; models/item/building_sign_<kind>.json;
- assets/folkhold_blocks/blockstates/building_sign_<kind>.json: 4 facings (the plaque faces the facing);
- data/folkhold_blocks/loot_table/blocks/building_sign_<kind>.json;
- the block ids in data/minecraft/tags/block/mineable/axe.json;
- the block names in assets/folkhold_blocks/lang/en_us.json and fr_fr.json.

To add a kind: add an entry to KINDS here and to BuildingSignKind in the Java code, then run this script.

Requires Pillow.
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "blocks", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "folkhold_blocks")
DATA = os.path.join(ROOT, "data")
NS = "folkhold_blocks"

OUTLINE = (40, 28, 20, 255)

# Icons: 8 x 8 pixels. "." is the background. The other characters are keys of the palette of the kind.
ICONS = {
    "house": [
        "...rr...",
        "..rrrr..",
        ".rrrrrr.",
        "rrrrrrrr",
        ".wwwwww.",
        ".wwddww.",
        ".wwddww.",
        ".wwddww.",
    ],
    "barracks": [
        "s......s",
        ".s....s.",
        "..s..s..",
        "...ss...",
        "...ss...",
        "..g..g..",
        ".h....h.",
        "h......h",
    ],
    "forge": [
        "........",
        "gggggg..",
        ".ggggggg",
        "..gggg..",
        "...gg...",
        "..gggg..",
        ".gggggg.",
        "........",
    ],
    "storehouse": [
        "kkkkkkkk",
        "kcccccck",
        "kckcckck",
        "kcckkcck",
        "kcckkcck",
        "kckcckck",
        "kcccccck",
        "kkkkkkkk",
    ],
    "bank": [
        "........",
        "..oooo..",
        ".oyyyyo.",
        ".oooooo.",
        ".oyyyyo.",
        ".oooooo.",
        ".oyyyyo.",
        ".oooooo.",
    ],
    "hall": [
        "........",
        "y..yy..y",
        "y..yy..y",
        "yy.yy.yy",
        "yyyyyyyy",
        "yrryyrry",
        "yyyyyyyy",
        "........",
    ],
    "farm": [
        "..y..y..",
        ".yy.yy..",
        "..yy.yy.",
        "...yy...",
        "...g....",
        "..gg.g..",
        "...g.g..",
        "...gg...",
    ],
    "market": [
        "...kk...",
        "..k..k..",
        "...bb...",
        "..bbbb..",
        ".bbyybb.",
        ".bbybbb.",
        ".bbyybb.",
        "..bbbb..",
    ],
}

# For each kind: plaque colors, icon palette, block textures of the stand, and the names.
KINDS = {
    "house": {
        "background": (150, 52, 44), "trim": (226, 190, 120),
        "palette": {"r": (96, 40, 30), "w": (236, 220, 180), "d": (110, 72, 40)},
        "post": "minecraft:block/spruce_log", "cap": "minecraft:block/stripped_spruce_log",
        "bar": "minecraft:block/spruce_planks",
        "en": "House Sign", "fr": "Panneau de maison",
    },
    "barracks": {
        "background": (52, 78, 140), "trim": (196, 196, 206),
        "palette": {"s": (220, 224, 230), "g": (200, 160, 60), "h": (110, 72, 40)},
        "post": "minecraft:block/stone_bricks", "cap": "minecraft:block/polished_andesite",
        "bar": "minecraft:block/dark_oak_planks",
        "en": "Barracks Sign", "fr": "Panneau de caserne",
    },
    "forge": {
        "background": (196, 110, 40), "trim": (70, 70, 74),
        "palette": {"g": (60, 62, 68)},
        "post": "minecraft:block/bricks", "cap": "minecraft:block/stone",
        "bar": "minecraft:block/dark_oak_planks",
        "en": "Forge Sign", "fr": "Panneau de forge",
    },
    "storehouse": {
        "background": (70, 120, 60), "trim": (214, 184, 112),
        "palette": {"k": (96, 64, 34), "c": (176, 128, 72)},
        "post": "minecraft:block/spruce_log", "cap": "minecraft:block/stripped_spruce_log",
        "bar": "minecraft:block/spruce_planks",
        "en": "Storehouse Sign", "fr": "Panneau d'entrepôt",
    },
    "bank": {
        "background": (96, 60, 140), "trim": (232, 190, 60),
        "palette": {"o": (150, 104, 20), "y": (250, 210, 70)},
        "post": "minecraft:block/polished_blackstone", "cap": "minecraft:block/gold_block",
        "bar": "minecraft:block/dark_oak_planks",
        "en": "Bank Sign", "fr": "Panneau de banque",
    },
    "hall": {
        "background": (40, 60, 110), "trim": (232, 190, 60),
        "palette": {"y": (250, 210, 70), "r": (200, 40, 50)},
        "post": "minecraft:block/stone_bricks", "cap": "minecraft:block/gold_block",
        "bar": "minecraft:block/spruce_planks",
        "en": "Village Hall Sign", "fr": "Panneau de maison commune",
    },
    "farm": {
        "background": (120, 150, 60), "trim": (232, 212, 140),
        "palette": {"y": (240, 206, 90), "g": (70, 110, 40)},
        "post": "minecraft:block/oak_log", "cap": "minecraft:block/stripped_oak_log",
        "bar": "minecraft:block/oak_planks",
        "en": "Farm Sign", "fr": "Panneau de ferme",
    },
    "market": {
        "background": (40, 120, 120), "trim": (222, 182, 92),
        "palette": {"k": (90, 60, 30), "b": (190, 150, 100), "y": (250, 210, 70)},
        "post": "minecraft:block/spruce_log", "cap": "minecraft:block/stripped_spruce_log",
        "bar": "minecraft:block/spruce_planks",
        "en": "Market Sign", "fr": "Panneau de marché",
    },
}

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def block_id(kind):
    return f"building_sign_{kind}"


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as file:
        json.dump(value, file, indent=2, ensure_ascii=False)
        file.write("\n")


def plaque(kind, spec):
    """The plaque: a dark outline (x and y 2 and 13), a trim ring (3 and 12), the icon on the background (4..11)."""
    rgba = lambda color: tuple(color[:3]) + (255,)
    image = Image.new("RGBA", (16, 16), rgba(OUTLINE))
    for x in range(3, 13):
        for y in range(3, 13):
            image.putpixel((x, y), rgba(spec["trim"]))
    for x in range(4, 12):
        for y in range(4, 12):
            image.putpixel((x, y), rgba(spec["background"]))
    icon = ICONS[kind]
    if len(icon) != 8 or any(len(row) != 8 for row in icon):
        raise ValueError(f"The icon of {kind} must have 8 x 8 pixels")
    for y, row in enumerate(icon):
        for x, key in enumerate(row):
            if key != ".":
                image.putpixel((4 + x, 4 + y), rgba(spec["palette"][key]))
    return image


def box(start, end, texture, faces=("north", "south", "east", "west", "up", "down"), uv=None):
    element = {"from": start, "to": end, "faces": {}}
    for face in faces:
        entry = {"texture": texture}
        if uv is not None and face in uv:
            entry["uv"] = uv[face]
        element["faces"][face] = entry
    return element


# The plaque element: x 2..14, y 6..18, z 7.5..8.5. Texture pixel (u, v) of the plaque area (2..13) is at
# x = 15 - u on the north face and x = u on the south face, and at y = 19 - v.
PLAQUE_FRONT = 7.5
PLAQUE_BACK = 8.5
RELIEF = 1.0


def relief_mask(kind):
    """The raised pixels of the plaque: the frame (outline and trim ring) and the pixels of the icon."""
    mask = set()
    for u in range(2, 14):
        for v in range(2, 14):
            if u in (2, 3, 12, 13) or v in (2, 3, 12, 13):
                mask.add((u, v))
    for y, row in enumerate(ICONS[kind]):
        for x, key in enumerate(row):
            if key != ".":
                mask.add((4 + x, 4 + y))
    return mask


def rectangles(mask):
    """Splits the pixels into rectangles (u0, v0, u1, v1), with u1 and v1 exclusive."""
    left = set(mask)
    result = []
    for v in range(16):
        for u in range(16):
            if (u, v) not in left:
                continue
            u1 = u
            while (u1, v) in left:
                u1 += 1
            v1 = v + 1
            while all((x, v1) in left for x in range(u, u1)):
                v1 += 1
            for x in range(u, u1):
                for y in range(v, v1):
                    left.discard((x, y))
            result.append((u, v, u1, v1))
    return result


def relief_elements(kind):
    """The raised parts on the two faces of the plaque. The faces show the plaque texture at the same pixels."""
    elements = []
    for (u0, v0, u1, v1) in rectangles(relief_mask(kind)):
        y0, y1 = 20 - v1, 20 - v0
        top = [u0, v0, u1, v0 + 1]
        bottom = [u0, v1 - 1, u1, v1]
        # Front: the north face. The pixel u is at x = 15 - u.
        elements.append(box([16 - u1, y0, PLAQUE_FRONT - RELIEF], [16 - u0, y1, PLAQUE_FRONT], "#plaque",
                            faces=("north", "east", "west", "up", "down"), uv={
                                "north": [u0, v0, u1, v1],
                                "east": [u0, v0, u0 + 1, v1],
                                "west": [u1 - 1, v0, u1, v1],
                                "up": top,
                                "down": bottom,
                            }))
        # Back: the south face. The pixel u is at x = u.
        elements.append(box([u0, y0, PLAQUE_BACK], [u1, y1, PLAQUE_BACK + RELIEF], "#plaque",
                            faces=("south", "east", "west", "up", "down"), uv={
                                "south": [u0, v0, u1, v1],
                                "east": [u1 - 1, v0, u1, v1],
                                "west": [u0, v0, u0 + 1, v1],
                                "up": top,
                                "down": bottom,
                            }))
    return elements


def stand_model(kind):
    """The sign stand of a kind, facing north: the plaque front is the north face."""
    elements = [
        # Feet and posts.
        box([0, 0, 5], [3, 1, 11], "#post"),
        box([13, 0, 5], [16, 1, 11], "#post"),
        box([0, 1, 7], [2, 22, 9], "#post"),
        box([14, 1, 7], [16, 22, 9], "#post"),
        # Caps on the posts.
        box([0, 22, 6], [2, 24, 10], "#cap"),
        box([14, 22, 6], [16, 24, 10], "#cap"),
        # Cross bar.
        box([2, 20, 7], [14, 22, 9], "#bar"),
        # Hooks.
        box([4, 18, 7.5], [5, 20, 8.5], "#cap"),
        box([11, 18, 7.5], [12, 20, 8.5], "#cap"),
        # Plaque: the icon on the two large faces, the outline color on the edges.
        box([2, 6, PLAQUE_FRONT], [14, 18, PLAQUE_BACK], "#plaque", uv={
            "north": [2, 2, 14, 14],
            "south": [2, 2, 14, 14],
            "east": [2, 2, 3, 14],
            "west": [2, 2, 3, 14],
            "up": [2, 2, 14, 3],
            "down": [2, 13, 14, 14],
        }),
    ] + relief_elements(kind)
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "elements": elements,
    }


def update_tag(path, ids):
    """Puts the sign ids in the tag. An id that is in the tag keeps its place; the others go at the end."""
    with open(path, encoding="utf-8") as file:
        tag = json.load(file)
    values = [value for value in tag["values"] if not value.startswith(f"{NS}:building_sign_") or value in ids]
    tag["values"] = values + [value for value in ids if value not in values]
    write_json(path, tag)


def update_lang(path, names):
    """Puts the sign names in the lang file. A key that is in the file keeps its place; the others go at the end."""
    with open(path, encoding="utf-8") as file:
        lang = json.load(file)
    lang = {key: (names[key] if key in names else value) for key, value in lang.items()
            if not key.startswith(f"block.{NS}.building_sign_") or key in names}
    for key, value in names.items():
        lang.setdefault(key, value)
    write_json(path, lang)


def main():
    base = os.path.join(ASSETS, "models", "block", "building_sign.json")
    if os.path.exists(base):
        os.remove(base)
    for kind, spec in KINDS.items():
        name = block_id(kind)
        texture_dir = os.path.join(ASSETS, "textures", "block", "building_sign")
        os.makedirs(texture_dir, exist_ok=True)
        plaque(kind, spec).save(os.path.join(texture_dir, kind + ".png"))
        model = stand_model(kind)
        model["textures"] = {
            "particle": spec["post"],
            "post": spec["post"],
            "cap": spec["cap"],
            "bar": spec["bar"],
            "plaque": f"{NS}:block/building_sign/{kind}",
        }
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{NS}:block/{name}"})
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {
            "variants": {
                f"facing={facing}": ({"model": f"{NS}:block/{name}", "y": rotation} if rotation
                                     else {"model": f"{NS}:block/{name}"})
                for facing, rotation in FACINGS.items()
            },
        })
        write_json(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": f"{NS}:blocks/{name}",
        })
    ids = [f"{NS}:{block_id(kind)}" for kind in KINDS]
    update_tag(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "axe.json"), ids)
    for language in ("en", "fr"):
        file_name = "en_us.json" if language == "en" else "fr_fr.json"
        update_lang(os.path.join(ASSETS, "lang", file_name),
                    {f"block.{NS}.{block_id(kind)}": spec[language] for kind, spec in KINDS.items()})
    print(f"wrote {len(KINDS)} building signs")


if __name__ == "__main__":
    main()
