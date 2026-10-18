#!/usr/bin/env python3
"""Generate the models and the block state of the brazier of the block library (folkhold_blocks).

The brazier is a stone bowl of embers on a square pedestal, 1.5 blocks high: a plinth, a carved column, a collar, and
a bowl with 4 walls. The embers fill the bowl. A lit brazier has glowing embers and two crossed planes of small
flames.

Output (in blocks/src/main/resources/assets/folkhold_blocks):
- models/block/brazier.json: the brazier that does not burn (dark embers);
- models/block/brazier_lit.json: the lit brazier (glowing embers and flames);
- models/item/brazier.json: the item shows the lit brazier;
- blockstates/brazier.json: the model for each value of "lit".

    python3 tools/models/brazier.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..",
                    "blocks", "src", "main", "resources", "assets", "folkhold_blocks")
NS = "folkhold_blocks"

TEXTURES = {
    "particle": "minecraft:block/stone_bricks",
    "plinth": "minecraft:block/stone_bricks",
    "column": "minecraft:block/chiseled_stone_bricks",
    "bowl": "minecraft:block/smooth_stone",
}
EMBERS = "minecraft:block/coal_block"
GLOWING_EMBERS = "minecraft:block/magma"
FIRE = "minecraft:block/campfire_fire"

FACES = ("north", "south", "east", "west", "up", "down")


def face_size(face, frm, to):
    """The width and the height of a face, in pixels."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    if face in ("north", "south"):
        return dx, dy
    if face in ("east", "west"):
        return dz, dy
    return dx, dz


def box(frm, to, texture, faces=FACES):
    """An element with the same texture on its faces. The UV stays in the texture."""
    element = {"from": list(frm), "to": list(to), "faces": {}}
    for face in faces:
        width, height = face_size(face, frm, to)
        element["faces"][face] = {"uv": [0, 0, min(16, round(width, 3)), min(16, round(height, 3))],
                                  "texture": "#" + texture}
    return element


def flame(angle):
    """A vertical plane of flames over the embers, turned by the angle around the vertical axis."""
    return {
        "from": [2, 19, 8], "to": [14, 27, 8],
        "rotation": {"origin": [8, 19, 8], "axis": "y", "angle": angle, "rescale": True},
        "shade": False,
        "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#fire"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#fire"},
        },
    }


def body():
    return [
        # Plinth, column and collar.
        box((2, 0, 2), (14, 3, 14), "plinth"),
        box((4.5, 3, 4.5), (11.5, 12, 11.5), "column", ("north", "south", "east", "west")),
        box((3, 12, 3), (13, 14, 13), "bowl"),
        # Bowl: the floor and the 4 walls.
        box((1, 14, 1), (15, 16, 15), "bowl", ("north", "south", "east", "west", "down")),
        box((1, 16, 1), (15, 22, 3), "bowl"),
        box((1, 16, 13), (15, 22, 15), "bowl"),
        box((1, 16, 3), (3, 22, 13), "bowl", ("east", "west", "up")),
        box((13, 16, 3), (15, 22, 13), "bowl", ("east", "west", "up")),
        # Embers in the bowl.
        box((3, 16, 3), (13, 20, 13), "embers", ("up",)),
    ]


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
}


def model(lit):
    textures = dict(TEXTURES)
    textures["embers"] = GLOWING_EMBERS if lit else EMBERS
    elements = body()
    if lit:
        textures["fire"] = FIRE
        elements += [flame(45), flame(-45)]
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": textures,
        "elements": elements,
        "display": DISPLAY,
    }


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    write("models/block/brazier.json", model(False))
    write("models/block/brazier_lit.json", model(True))
    write("models/item/brazier.json", {"parent": f"{NS}:block/brazier_lit"})
    write("blockstates/brazier.json", {"variants": {
        "lit=false": {"model": f"{NS}:block/brazier"},
        "lit=true": {"model": f"{NS}:block/brazier_lit"},
    }})


if __name__ == "__main__":
    main()
