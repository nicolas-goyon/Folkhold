#!/usr/bin/env python3
"""Generate the models and the block states of the quest board.

Output (in blocks/src/main/resources/assets/folkhold_blocks):
- models/block/quest_board.json: the board with no paper. The part in the middle (column 1, row 1) renders it, so
  the coordinates go from -16 to 32: 3 blocks wide and 3 blocks high around this part. The front faces north.
- models/block/quest_board_paper_<n>.json: paper n (1 to PAPERS), with its pin. The block state adds the papers 1
  to "papers" on the board: one paper for each offer of the village, in a fixed order.
- models/block/quest_board_part.json: the other parts. No elements, only the particle texture.
- models/block/quest_board_full.json and models/item/quest_board.json: the board with all the papers, small in the
  hand and in the inventory.
- blockstates/quest_board.json: a multipart block state.

    python3 tools/models/quest_board.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..",
                    "blocks", "src", "main", "resources", "assets", "folkhold_blocks")
NS = "folkhold_blocks"

TEXTURES = {
    "particle": "minecraft:block/spruce_planks",
    "post": "minecraft:block/stripped_spruce_log",
    "beam": "minecraft:block/spruce_planks",
    "board": "minecraft:block/birch_planks",
    "roof": "minecraft:block/dark_oak_planks",
    "paper": "minecraft:item/paper",
    "map": "minecraft:item/map",
    "filled_map": "minecraft:item/filled_map",
    "pin": "minecraft:block/red_terracotta",
}

# The papers on the front, in the order of the offers: x, y, width, height, depth, texture. The first papers are in
# the middle of the board, so a board with few offers looks complete.
PAPERS = [
    (4, 7, 8, 9, 6.8, "paper"),
    (-7, 6, 8, 9, 6.7, "map"),
    (15, 6, 8, 9, 6.7, "paper"),
    (9, -3, 7, 8, 6.6, "filled_map"),
    (0, -3, 7, 8, 6.8, "paper"),
    (18, -2, 7, 7, 6.8, "paper"),
    (-9, -2, 7, 7, 6.6, "map"),
]

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


def paper(x, y, width, height, depth, texture):
    """A paper on the front of the board, and a pin at its top."""
    pin_x = x + width / 2 - 0.5
    return [
        sheet(x, y, width, height, depth, texture),
        box((pin_x, y + height - 1.5, depth - 0.4), (pin_x + 1, y + height - 0.5, depth), "pin",
            ("north", "east", "west", "up", "down")),
    ]


def board_elements():
    return [
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
    ]


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.22, 0.22, 0.22]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.12, 0.12, 0.12]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 10, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.16, 0.16, 0.16]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.16, 0.16, 0.16]},
}


def model(elements, display=False):
    data = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": TEXTURES,
        "elements": elements,
    }
    if display:
        data["display"] = DISPLAY
    return data


def full_model():
    elements = board_elements()
    for spec in PAPERS:
        elements += paper(*spec)
    return model(elements, display=True)


def block_state():
    """Multipart: the board and its papers on the middle part, an empty model on the other parts."""
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    parts = []
    for facing, rotation in rotations.items():
        render = {"column": "1", "row": "1", "facing": facing}
        board = {"model": f"{NS}:block/quest_board"}
        if rotation:
            board["y"] = rotation
        parts.append({"when": render, "apply": board})
        for n in range(1, len(PAPERS) + 1):
            when = dict(render)
            when["papers"] = "|".join(str(count) for count in range(n, len(PAPERS) + 1))
            apply = {"model": f"{NS}:block/quest_board_paper_{n}"}
            if rotation:
                apply["y"] = rotation
            parts.append({"when": when, "apply": apply})
    parts.append({"when": {"OR": [{"column": "0|2"}, {"row": "0|2"}]},
                  "apply": {"model": f"{NS}:block/quest_board_part"}})
    return {"multipart": parts}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    write("models/block/quest_board.json", model(board_elements()))
    for n, spec in enumerate(PAPERS, start=1):
        write(f"models/block/quest_board_paper_{n}.json", model(paper(*spec)))
    write("models/block/quest_board_full.json", full_model())
    write("models/block/quest_board_part.json", {"textures": {"particle": TEXTURES["particle"]}})
    write("models/item/quest_board.json", {"parent": f"{NS}:block/quest_board_full"})
    write("blockstates/quest_board.json", block_state())


if __name__ == "__main__":
    main()
