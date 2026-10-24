"""Building signs of the villages: a sign stand in front of each kind of building.

A stand has two posts on feet, a cap on each post, a cross bar and a plaque that hangs from two iron hooks. The plaque
has a raised frame and a raised icon on its two faces, on a painted background. Each kind of building has its own
icon, plaque colors and materials of the stand. The model faces north: the front of the plaque is the north face. The
model is 24 pixels high, as the shape of the block (BuildingSignBlock).

build() makes the model of each kind (building_sign_<kind>) for export.py. Run this file to write the other resources
of the signs, in blocks/src/main/resources:
- assets/folkhold_blocks/blockstates/building_sign_<kind>.json: 4 facings (the plaque faces the facing)
- assets/folkhold_blocks/models/item/building_sign_<kind>.json
- data/folkhold_blocks/loot_table/blocks/building_sign_<kind>.json
- the block ids in data/minecraft/tags/block/mineable/axe.json
- the block names in assets/folkhold_blocks/lang/en_us.json and fr_fr.json

    python3 tools/pixvox/building_signs.py

To add a kind: add an entry to KINDS and to ICONS here and to BuildingSignKind in the Java code, then run this file and
export.py.
"""
import json
import os

import numpy as np

from _palette import (ANDESITE, BLACKSTONE, BRICK, DARK_OAK, GOLD, IRON, MORTAR, OAK, OAK_BARK, SPRUCE, SPRUCE_DARK,
                      STONE)
from pixvox import Model, Volume, ramp

NS = "folkhold_blocks"
RESOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blocks", "src", "main", "resources")
OUTLINE = "#281c14"
LIGHT = (-0.5, 1.0, -0.35)

# Icons: 8 x 8 pixels, as the front of the plaque shows them. "." is the background. The other characters are keys of
# the palette of the kind.
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

# For each kind: the colors of the plaque, the palette of the icon, the materials of the stand (see MATERIALS) and the
# names of the block.
KINDS = {
    "house": {
        "background": "#96342c", "trim": "#e2be78",
        "palette": {"r": "#60281e", "w": "#ecdcb4", "d": "#6e4828"},
        "post": "spruce_log", "cap": "stripped_spruce", "bar": "spruce_planks",
        "en": "House Sign", "fr": "Panneau de maison",
    },
    "barracks": {
        "background": "#344e8c", "trim": "#c4c4ce",
        "palette": {"s": "#dce0e6", "g": "#c8a03c", "h": "#6e4828"},
        "post": "stone_bricks", "cap": "polished_andesite", "bar": "dark_oak_planks",
        "en": "Barracks Sign", "fr": "Panneau de caserne",
    },
    "forge": {
        "background": "#c46e28", "trim": "#8c8f96",
        "palette": {"g": "#3c3e44"},
        "post": "bricks", "cap": "stone", "bar": "dark_oak_planks",
        "en": "Forge Sign", "fr": "Panneau de forge",
    },
    "storehouse": {
        "background": "#46783c", "trim": "#d6b870",
        "palette": {"k": "#604022", "c": "#b08048"},
        "post": "spruce_log", "cap": "stripped_spruce", "bar": "spruce_planks",
        "en": "Storehouse Sign", "fr": "Panneau d'entrepôt",
    },
    "bank": {
        "background": "#603c8c", "trim": "#e8be3c",
        "palette": {"o": "#966814", "y": "#fad246"},
        "post": "polished_blackstone", "cap": "gold", "bar": "dark_oak_planks",
        "en": "Bank Sign", "fr": "Panneau de banque",
    },
    "hall": {
        "background": "#283c6e", "trim": "#e8be3c",
        "palette": {"y": "#fad246", "r": "#c82832"},
        "post": "stone_bricks", "cap": "gold", "bar": "spruce_planks",
        "en": "Village Hall Sign", "fr": "Panneau de maison commune",
    },
    "farm": {
        "background": "#78963c", "trim": "#e8d48c",
        "palette": {"y": "#f0ce5a", "g": "#466e28"},
        "post": "oak_log", "cap": "stripped_oak", "bar": "oak_planks",
        "en": "Farm Sign", "fr": "Panneau de ferme",
    },
    "market": {
        "background": "#287878", "trim": "#deb65c",
        "palette": {"k": "#5a3c1e", "b": "#be9664", "y": "#fad246"},
        "post": "spruce_log", "cap": "stripped_spruce", "bar": "spruce_planks",
        "en": "Market Sign", "fr": "Panneau de marché",
    },
}

# The materials of the stands: the style of the surface and the colors.
MATERIALS = {
    "spruce_log": ("bark", SPRUCE_DARK),
    "stripped_spruce": ("grain", SPRUCE),
    "spruce_planks": ("grain", SPRUCE),
    "oak_log": ("bark", OAK_BARK),
    "stripped_oak": ("grain", OAK),
    "oak_planks": ("grain", OAK),
    "dark_oak_planks": ("grain", DARK_OAK),
    "stone_bricks": ("stone_bricks", STONE),
    "bricks": ("bricks", BRICK),
    "stone": ("rough", STONE),
    "polished_andesite": ("polished", ANDESITE),
    "polished_blackstone": ("polished", BLACKSTONE),
    "gold": ("metal", GOLD),
}


def paint(v, mask, material, *, seed, along="y"):
    """Paints a part of the stand with a material of MATERIALS. The wood grain goes along the axis "x" or "y"."""
    style, colors = MATERIALS[material]
    v.set(mask, colors[2])
    if style == "bark":
        v.noise(colors, cells=(8, 2, 8), octaves=2, seed=seed, mask=mask, weights=[2, 3, 3, 2, 0])
    elif style == "grain":
        cells = (1, 8, 8) if along == "x" else (8, 1, 8)
        v.noise(colors, cells=cells, octaves=2, seed=seed, mask=mask, weights=[0, 2, 4, 3, 1])
    elif style in ("stone_bricks", "bricks"):
        # Courses of 4 voxels: a joint under each course. The joints of the bricks are grey mortar.
        v.noise(colors, cells=4, octaves=2, seed=seed, mask=mask, weights=[0, 2, 4, 2, 0])
        v.set(mask & ((v.Y + 1) % 4 == 0), colors[0] if style == "stone_bricks" else MORTAR[0])
    elif style == "rough":
        v.noise(colors, cells=4, octaves=3, seed=seed, mask=mask)
    elif style == "polished":
        v.noise(colors, cells=(4, 6, 4), octaves=1, seed=seed, mask=mask, weights=[0, 1, 6, 1, 0])
    elif style == "metal":
        v.shade(mask, colors, light=LIGHT, contrast=1.2, bias=0.1)
    else:
        raise ValueError(f"Unknown style of material: {style}")
    if style != "metal":
        # The tops catch the light.
        v.shift_ramp(mask & v.m_exposed("up"), colors, 1)


def plaque(v, kind):
    """Paints the plaque: the board (z 7 and 8) and the relief of each face (z 6 at the front, z 9 at the back).

    A pixel (u, row) of the plaque is at y = 19 - row. It is at x = 15 - u on the front (north face) and at x = u on
    the back (south face). The plaque has the pixels 2 to 13: an outline, a trim ring and the icon on the background
    (4 to 11).
    """
    spec = KINDS[kind]
    icon = np.array([list(line) for line in ICONS[kind]])
    if icon.shape != (8, 8):
        raise ValueError(f"The icon of {kind} must have 8 x 8 pixels")
    unknown = set(icon.ravel()) - set(spec["palette"]) - {"."}
    if unknown:
        raise ValueError(f"The icon of {kind} uses keys that are not in its palette: {sorted(unknown)}")
    background = ramp(spec["background"], n=3, spread=0.10, hue_shift=6)
    trim = ramp(spec["trim"], n=3, spread=0.20, hue_shift=8)
    x, y, z = v.X, v.Y, v.Z
    row = 19 - y
    for board_z, relief_z, u in ((7, 6, 15 - x), (8, 9, x)):
        inside = (u >= 2) & (u <= 13) & (row >= 2) & (row <= 13)
        # The distance to the edge of the plaque: 0 is the outline, 1 is the trim ring, 2 and more is the background.
        ring = np.minimum.reduce([u - 2, 13 - u, row - 2, 13 - row])
        board = inside & (z == board_z)
        relief = inside & (z == relief_z)
        keys = icon[np.clip(row - 4, 0, 7), np.clip(u - 4, 0, 7)]
        marks = (ring >= 2) & (keys != ".")
        layer = board | (relief & ((ring <= 1) | marks))
        v.set(layer & (ring == 0), OUTLINE)
        # The trim ring has a bevel: light on the top and on the left, dark on the bottom and on the right.
        trim_ring = layer & (ring == 1)
        v.set(trim_ring, trim[1])
        v.set(trim_ring & (((row == 3) & (u < 12)) | ((u == 3) & (row < 12))), trim[2])
        v.set(trim_ring & (((row == 12) & (u > 3)) | ((u == 12) & (row > 3))), trim[0])
        paper = board & (ring >= 2)
        v.set(paper, background[1])
        v.noise(background, cells=4, octaves=2, seed=len(kind), mask=paper, weights=[1, 4, 1])
        for key, color in spec["palette"].items():
            v.set(layer & marks & (keys == key), color)


def sign_volume(kind):
    """The volume of the sign of a kind."""
    spec = KINDS[kind]
    v = Volume(16, 24, 16)
    seed = sum(map(ord, kind))
    feet = v.m_box(0, 0, 5, 2, 0, 10) | v.m_box(13, 0, 5, 15, 0, 10)
    posts = v.m_box(0, 1, 7, 1, 21, 8) | v.m_box(14, 1, 7, 15, 21, 8)
    caps = v.m_box(0, 22, 6, 1, 23, 9) | v.m_box(14, 22, 6, 15, 23, 9)
    bar = v.m_box(2, 20, 7, 13, 21, 8)
    hooks = v.m_box(4, 18, 7, 4, 19, 8) | v.m_box(11, 18, 7, 11, 19, 8)
    plaque(v, kind)
    # All the parts are solid before the paint: a top under an other part does not catch the light.
    v.set(feet | posts | caps | bar | hooks, IRON[1])
    paint(v, feet | posts, spec["post"], seed=seed)
    paint(v, caps, spec["cap"], seed=seed + 1)
    paint(v, bar, spec["bar"], seed=seed + 2, along="x")
    # The hooks: an iron ring under the bar.
    v.set(hooks, IRON[1])
    v.set(hooks & (v.Y == 18), IRON[3])
    return v


def build():
    return [Model(block_id(kind), sign_volume(kind), inventory_scale=0.8, inventory_center=True) for kind in KINDS]


FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def block_id(kind):
    return f"building_sign_{kind}"


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(value, file, indent=2, ensure_ascii=False)
        file.write("\n")


def update_tag(path, ids):
    """Puts the sign ids in the tag. An id that is in the tag keeps its place. The other ids go at the end."""
    with open(path, encoding="utf-8") as file:
        tag = json.load(file)
    values = [value for value in tag["values"] if not value.startswith(f"{NS}:building_sign_") or value in ids]
    tag["values"] = values + [value for value in ids if value not in values]
    write_json(path, tag)


def update_lang(path, names):
    """Puts the sign names in the lang file. A key that is in the file keeps its place. The other keys go at the end."""
    with open(path, encoding="utf-8") as file:
        lang = json.load(file)
    lang = {key: (names[key] if key in names else value) for key, value in lang.items()
            if not key.startswith(f"block.{NS}.building_sign_") or key in names}
    for key, value in names.items():
        lang.setdefault(key, value)
    write_json(path, lang)


def write_resources():
    """Writes the resources of the signs that do not come from PixVox."""
    assets = os.path.join(RESOURCES, "assets", NS)
    data = os.path.join(RESOURCES, "data")
    for kind in KINDS:
        name = block_id(kind)
        write_json(os.path.join(assets, "models", "item", name + ".json"), {"parent": f"{NS}:block/{name}"})
        write_json(os.path.join(assets, "blockstates", name + ".json"), {
            "variants": {
                f"facing={facing}": ({"model": f"{NS}:block/{name}", "y": rotation} if rotation
                                     else {"model": f"{NS}:block/{name}"})
                for facing, rotation in FACINGS.items()
            },
        })
        write_json(os.path.join(data, NS, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": f"{NS}:blocks/{name}",
        })
    ids = [f"{NS}:{block_id(kind)}" for kind in KINDS]
    update_tag(os.path.join(data, "minecraft", "tags", "block", "mineable", "axe.json"), ids)
    for language, file_name in (("en", "en_us.json"), ("fr", "fr_fr.json")):
        update_lang(os.path.join(assets, "lang", file_name),
                    {f"block.{NS}.{block_id(kind)}": spec[language] for kind, spec in KINDS.items()})
    print(f"wrote the resources of {len(KINDS)} building signs")


if __name__ == "__main__":
    write_resources()
