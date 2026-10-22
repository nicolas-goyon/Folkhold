"""Quest board of a village: a notice board of 3 x 3 blocks with a roof, and the papers of the offers.

The middle part of the block (column 1, row 1) renders the board. The coordinates go from -16 to 32: 3 blocks wide and
3 blocks high around this part. The front faces north.

build() makes these models for export.py:
- quest_board: the board with no paper. Two posts of stripped spruce, two beams of spruce, a board of birch planks
  and a roof of dark oak in three steps.
- quest_board_paper_<n>: paper n (1 to 7) with its pin. The block state adds the papers 1 to "papers" on the board:
  one paper for each offer of the village, in a fixed order.
- quest_board_full: the board with all the papers, for the item.

Run this file to write the other resources of the board, in blocks/src/main/resources/assets/folkhold_blocks:
- blockstates/quest_board.json: a multipart block state
- models/block/quest_board_part.json: the other parts of the block. No elements, only the particle texture.
- models/item/quest_board.json: the board with all the papers, small in the hand and in the inventory

    python3 tools/pixvox/quest_board.py
"""
import json
import os

import numpy as np

from _palette import BIRCH, DARK_OAK, IRON, SPRUCE, SPRUCE_DARK
from pixvox import Model, Sprite, Volume

NS = "folkhold_blocks"
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "blocks", "src", "main", "resources",
                      "assets", NS)
# The position of the voxel (0, 0, 0) of the board, in model units.
ORIGIN = (-16, -16, 0)
# The depth of the pins: in front of the board (z 7), through the papers.
PIN_Z = 6
PIN = "#a8322b"

# The colors of the papers.
LEGEND = {
    "p": "#e8dcbc",  # paper
    "q": "#d6c69f",  # old paper
    "e": "#b8a47a",  # edge and frame
    "t": "#6a5848",  # ink
    "h": "#3e3128",  # dark ink
    "r": "#a8322b",  # wax seal
    "s": "#6e1f1a",  # dark wax
    "l": "#d0574a",  # light wax
    "b": "#4f7fa8",  # water
    "w": "#7aa6c8",  # light water
    "y": "#d9c58c",  # sand
    "g": "#6f8f4a",  # grass
    "f": "#4e6b35",  # forest
    "k": "#8a6a48",  # path
    "x": "#a8322b",  # red mark
    "o": "#3e3128",  # town
}

# A notice: a title, lines of text and a wax seal.
NOTICE = """
.pppppppppppppq.
ppphhhhhhhhhhppe
pppppppppppppppe
pptttttttttttppe
pppppppppppppppe
ppttttttttttpppe
pppppppppppppppe
pptttttttttttppe
pppppppppppppppe
ppttttttpppppppe
pppppppppppppppe
pppppppppplrrppe
ppttttpppprrsppe
pppppppppprsspqe
qppppppppppppqee
.eeeeeeeeeeeeee.
"""

# A notice for a hunt: a title, the picture of a monster and two lines of text.
HUNT = """
.pppppppppppppp.
pppphhhhhhhhpppe
pppppppppppppppe
pppeeeeeeeeepppe
pppeqqqqqqqepppe
pppeqqqhqqqepppe
pppeqqhhhqqepppe
pppeqhqhqhqepppe
pppeqqqhqqqepppe
pppeeeeeeeeepppe
pppppppppppppppe
pptppttttttttppe
pppppppppppppppe
pptppttttttpppqe
qppppppppppppqee
.eeeeeeeeeeeeee.
"""

# A map: a river, a bridge, woods and a path to a red mark.
MAP = """
.pppppppppppppp.
pggpppppbwpppppe
pgfgpppbwpppfgpe
ppgppppbwppgfgpe
pppkkppbwpppgppe
ppkppkpbwppppppe
pkppppkbwkkppppe
pkpppppbwppkpppe
ppkppppwbpppkppe
pppkppwbppppkppe
ppppkpwbppppxpxe
pgppkwbppppppxpe
fgfppwbpppppxpxe
pgppwbpppppppppe
qpppwbpppppppqee
.eeeeeeeeeeeeee.
"""

# A sea chart: the sea, a coast with woods, a town and the route of a ship.
CHART = """
.pppppppppppppp.
peeeeeeeeeeeeeee
pebbbbwbbbyyggee
pebwbbbbbyygfgee
pebbbbbbyygggfee
pebbbwbbyggfggee
pebbbbbyygggggee
pebxbbbygggoggee
pebbxbbyggxgggee
pewbbxxyxxgffgee
pebbbbbbyygggfee
pebwbbbbbyyggfee
pebbbbwbbbyyggee
pebbbbbbbbbyygee
peeeeeeeeeeeeeee
.qqqqqqqqqqqqqq.
"""

# The papers on the front, in the order of the offers: x, y, width, height, depth, art. The first papers are in the
# middle of the board, so a board with few offers looks complete.
PAPERS = [
    (4, 7, 8, 9, 6.8, NOTICE),
    (-7, 6, 8, 9, 6.7, MAP),
    (15, 6, 8, 9, 6.7, HUNT),
    (9, -3, 7, 8, 6.6, CHART),
    (0, -3, 7, 8, 6.8, NOTICE),
    (18, -2, 7, 7, 6.8, HUNT),
    (-9, -2, 7, 7, 6.6, MAP),
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


def pin_position(paper):
    """The position of the pin of a paper, in model units: at the top of the paper, near its middle."""
    x, y, width, height, _, _ = paper
    return int(np.floor(x + width / 2 - 0.5)), y + height - 2, PIN_Z


def board_volume(pins=False):
    """The volume of the board (48 x 41 x 16 voxels, at ORIGIN). With pins, it also has the pins of all the papers."""
    v = Volume(48, 41, 16)
    x, y, z = v.X, v.Y, v.Z
    posts = v.m_box(2, 0, 6, 4, 36, 9) | v.m_box(43, 0, 6, 45, 36, 9)
    beams = v.m_box(2, 10, 6, 45, 11, 9) | v.m_box(2, 33, 6, 45, 34, 9)
    board = v.m_box(5, 12, 7, 42, 32, 8)
    roof = v.m_box(0, 35, 1, 47, 36, 14) | v.m_box(0, 37, 3, 47, 38, 12) | v.m_box(0, 39, 5, 47, 40, 10)
    # All the parts are solid before the paint: a top under an other part does not catch the light.
    v.set(posts | beams | board | roof, BIRCH[2])

    # Posts of stripped spruce, with the grain along the post.
    v.noise(SPRUCE, cells=(16, 2, 8), octaves=2, seed=3, mask=posts, weights=[0, 2, 4, 3, 1])
    # Beams of spruce planks, with the grain along the beam and a joint above each side of the middle part.
    beams = beams & ~posts
    v.noise(SPRUCE_DARK, cells=(2, 12, 8), octaves=2, seed=5, mask=beams, weights=[0, 2, 4, 3, 1])
    v.set(beams & ((x == 16) | (x == 31)), SPRUCE_DARK[0])
    v.shift_ramp(beams & v.m_exposed("up"), SPRUCE_DARK, 1)

    # Board of vertical birch planks with dark seams, symmetric around the middle. Each plank has its own tone.
    v.noise(BIRCH, cells=(24, 2, 8), octaves=2, seed=7, mask=board, weights=[0, 2, 5, 2, 0])
    plank = (x - 1) // 5
    for index, step in enumerate((0, 1, 0, -1, 1, 0, -1, 1, 0)):
        if step:
            v.shift_ramp(board & (plank == index), BIRCH, step)
    v.set(board & ((x - 1) % 5 == 0), BIRCH[0])
    # Iron nails at the two ends of each plank, on the two faces.
    nails = board & ((x - 1) % 5 == 2) & ((y == 13) | (y == 31))
    v.set(nails, IRON[2])

    # Roof of dark oak shingles. Each step has a shadow row at its bottom and joints in staggered rows.
    v.noise(DARK_OAK, cells=(12, 4, 4), octaves=2, seed=9, mask=roof, weights=[0, 1, 5, 2, 0])
    row = (y - 35) // 2
    v.set(roof & ((x + 2 * (row % 2)) % 4 == 0), DARK_OAK[1])
    v.set(roof & ((y - 35) % 2 == 0) & ~v.m_exposed("up"), DARK_OAK[0])
    v.shift_ramp(roof & (y == 40), DARK_OAK, 1)

    if pins:
        for paper in PAPERS:
            px, py, pz = pin_position(paper)
            v.set(v.m_box(px - ORIGIN[0], py - ORIGIN[1], pz, px - ORIGIN[0], py - ORIGIN[1], pz), PIN)
    return v


def add_paper(model, paper):
    """Adds a paper to a model: a plane on the front of the board, with the shade of the board."""
    x, y, width, height, depth, art = paper
    model.add_plane(Sprite.from_ascii(art, LEGEND), center=(x + width / 2, depth), y=y, width=width, height=height,
                    angle=0, shade=True)


def build():
    models = [Model("quest_board", board_volume(), origin=ORIGIN, ambient_occlusion=False)]
    for n, paper in enumerate(PAPERS, start=1):
        pin = Volume(1, 1, 1)
        pin.set(None, PIN)
        model = Model(f"quest_board_paper_{n}", pin, origin=pin_position(paper), ambient_occlusion=False)
        add_paper(model, paper)
        models.append(model)
    full = Model("quest_board_full", board_volume(pins=True), origin=ORIGIN, ambient_occlusion=False)
    for paper in PAPERS:
        add_paper(full, paper)
    models.append(full)
    return models


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
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def write_resources():
    """Writes the resources of the board that do not come from PixVox."""
    write("blockstates/quest_board.json", block_state())
    write("models/block/quest_board_part.json", {"textures": {"particle": f"{NS}:block/quest_board/atlas"}})
    write("models/item/quest_board.json", {"parent": f"{NS}:block/quest_board_full", "display": DISPLAY})
    print("wrote the resources of the quest board")


if __name__ == "__main__":
    write_resources()
