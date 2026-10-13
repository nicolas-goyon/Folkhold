#!/usr/bin/env python3
"""Generate the models and the block states of the quest board.

Output (in world/src/main/resources/assets/folkhold_world):
- models/block/quest_board.json: the complete board. The part in the middle (column 1, row 1) renders it, so the
  coordinates go from -16 to 32: 3 blocks wide and 3 blocks high around this part. The front faces north.
- models/block/quest_board_part.json: the other parts. No elements, only the particle texture.
- models/item/quest_board.json: the complete board, small in the hand and in the inventory.
- blockstates/quest_board.json: one variant for each direction, column and row.

    python3 tools/models/quest_board.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..",
                    "world", "src", "main", "resources", "assets", "folkhold_world")
NS = "folkhold_world"

TEXTURES = {
    "particle": "minecraft:block/spruce_planks",
    "post": "minecraft:block/stripped_spruce_log",
    "beam": "minecraft:block/spruce_planks",
    "board": "minecraft:block/birch_planks",
    "roof": "minecraft:block/dark_oak_planks",
    "paper": "minecraft:item/paper",
    "map": "minecraft:item/map",
    "filled_map": "minecraft:item/filled_map",
}

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
    """An element with the same texture on its faces. The UV stays in the texture: a large face stretches it."""
    element = {"from": list(frm), "to": list(to), "faces": {}}
    for face in faces:
        width, height = face_size(face, frm, to)
        element["faces"][face] = {"uv": [0, 0, min(16, round(width, 3)), min(16, round(height, 3))],
                                  "texture": "#" + texture}
    return element


def sheet(x, y, width, height, depth, texture):
    """A paper or a map on the front of the board (the north face)."""
    element = {"from": [x, y, depth], "to": [x + width, y + height, depth + 0.1], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#" + texture},
    }}
    return element


def board_model():
    elements = [
        # Two posts.
        box((-14, -16, 6), (-11, 21, 10), "post"),
        box((27, -16, 6), (30, 21, 10), "post"),
        # The board between two beams.
        box((-14, -6, 6), (30, -4, 10), "beam"),
        box((-11, -4, 7), (27, 17, 9), "board"),
        box((-14, 17, 6), (30, 19, 10), "beam"),
        # Gable roof in three steps.
        box((-16, 19, 1), (32, 21, 15), "roof"),
        box((-16, 21, 3), (32, 23, 13), "roof"),
        box((-16, 23, 5), (32, 25, 11), "roof"),
        # Papers and maps on the front.
        sheet(-10, 7, 6, 8, 6.8, "paper"),
        sheet(-3, 4, 10, 10, 6.7, "map"),
        sheet(8, 8, 6, 8, 6.8, "paper"),
        sheet(15, 2, 8, 8, 6.7, "filled_map"),
        sheet(-9, -3, 6, 8, 6.6, "paper"),
        sheet(4, -3, 6, 6, 6.8, "paper"),
        sheet(12, -2, 5, 7, 6.6, "paper"),
        sheet(20, 10, 6, 6, 6.8, "map"),
        sheet(23, -3, 3, 4, 6.7, "paper"),
    ]
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": TEXTURES,
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.22, 0.22, 0.22]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.12, 0.12, 0.12]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 10, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.15, 0.15, 0.15]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                     "scale": [0.15, 0.15, 0.15]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0],
                                      "scale": [0.16, 0.16, 0.16]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0],
                                     "scale": [0.16, 0.16, 0.16]},
        },
    }


def block_state():
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for column in range(3):
        for facing, rotation in rotations.items():
            for row in range(3):
                key = f"column={column},facing={facing},row={row}"
                if column == 1 and row == 1:
                    variant = {"model": f"{NS}:block/quest_board"}
                    if rotation:
                        variant["y"] = rotation
                else:
                    variant = {"model": f"{NS}:block/quest_board_part"}
                variants[key] = variant
    return {"variants": variants}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    write("models/block/quest_board.json", board_model())
    write("models/block/quest_board_part.json", {"textures": {"particle": TEXTURES["particle"]}})
    write("models/item/quest_board.json", {"parent": f"{NS}:block/quest_board"})
    write("blockstates/quest_board.json", block_state())


if __name__ == "__main__":
    main()
