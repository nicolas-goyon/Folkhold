#!/usr/bin/env python3
"""Generate the structure files of the faction village (one culture: spruce, cobblestone, calcite).

Output: world/src/main/resources/data/folkhold_world/structure/village/*.nbt

    python3 tools/structures/village.py

Pieces:
- center.nbt: the walled compound. Palisade, 4 corner towers with an archer post and a low parapet, 2 gates, paths,
  a plaza with the village heart and 4 braziers. 4 jigsaw connectors for the house plots. A margin of 1 block around
  the compound holds the banners on the outer face of the palisade. The other margin positions are empty: the
  terrain stays.
- house_*.nbt: houses for the plots. Each house has a porch row (z = 0) with its jigsaw connector.
- bank.nbt: the village bank. Its walls and its chests resist explosions. The first plot of the center piece
  always gets the bank (pool village/bank). The other plots get houses.

Conventions: y = 0 is the ground layer (foundations, floors, paths). y = 1 is the first layer above ground.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from nbt import Template  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "world", "src", "main", "resources", "data", "folkhold_world", "structure", "village")

NS = "folkhold_world"
WATCH_POST = f"{NS}:watch_post"
HEART = f"{NS}:village_heart"
HOUSE_POOL = f"{NS}:village/houses"
BANK_POOL = f"{NS}:village/bank"
BANK_CHEST = f"{NS}:bank_chest"
BANK_WALL = f"{NS}:bank_wall"
PLOT = f"{NS}:plot"
HOUSE_FRONT = f"{NS}:house_front"
# The village heart gives the banner of the faction to each banner of the village.
BANNER = "minecraft:white_banner"
WALL_BANNER = "minecraft:white_wall_banner"

LOG_Y = {"axis": "y"}


def stairs(facing, half="bottom"):
    return {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


def wall_torch(facing):
    """A wall torch. facing: the direction of the torch, away from the block that holds it."""
    return "minecraft:wall_torch", {"facing": facing}


# Blocks that connect to their neighbors. A template stores the block state, and the game does not update the
# shape of a block from a template. connect_panes sets the connection properties from the neighbors.
PANES = ("minecraft:glass_pane", "minecraft:iron_bars")
# Blocks that are not full. A pane does not connect to them.
NOT_FULL = ("air", "torch", "lantern", "ladder", "door", "stairs", "slab", "carpet", "bed", "fence", "banner",
            "flower_pot", "campfire", "jigsaw", "composter", "anvil", "grindstone", "cauldron", "chest")
SIDES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def is_full(name):
    return name is not None and not any(part in name for part in NOT_FULL)


def connect_panes(t):
    """Sets the north, south, east and west properties of each pane from its neighbors in the template."""
    for (x, y, z), ((name, _props), nbt) in list(t.blocks.items()):
        if name not in PANES:
            continue
        props = {"waterlogged": "false"}
        for side, (dx, dz) in SIDES.items():
            neighbor = t.get(x + dx, y, z + dz) if t.inside(x + dx, y, z + dz) else None
            props[side] = "true" if neighbor in PANES or is_full(neighbor) else "false"
        t.set(x, y, z, name, props, nbt)


def cobble(rng):
    return "minecraft:mossy_cobblestone" if rng.random() < 0.2 else "minecraft:cobblestone"


# ---------------------------------------------------------------- center (walled compound)

SIZE = 39
HEIGHT = 13
MID = SIZE // 2  # 19
GATE = range(MID - 1, MID + 2)  # 18..20
TOWER = 5


def tower(t, rng, tx, tz):
    """Corner tower of 5 x 5 blocks with its lower corner at (tx, tz)."""
    west_side = tx == 0
    north_side = tz == 0
    for dx in range(TOWER):
        for dz in range(TOWER):
            x, z = tx + dx, tz + dz
            t.set(x, 0, z, cobble(rng))
            ring = dx in (0, TOWER - 1) or dz in (0, TOWER - 1)
            corner = dx in (0, TOWER - 1) and dz in (0, TOWER - 1)
            for y in range(1, 7):
                if corner:
                    t.set(x, y, z, "minecraft:spruce_log", LOG_Y)
                elif ring:
                    t.set(x, y, z, cobble(rng) if y <= 2 else "minecraft:spruce_planks")
                else:
                    t.set(x, y, z, "minecraft:air")
            t.set(x, 7, z, "minecraft:spruce_planks")
            # Low parapet: the archer shoots over it. Logs at the corners, slabs between them.
            if ring:
                if corner:
                    t.set(x, 8, z, "minecraft:spruce_log", LOG_Y)
                else:
                    t.set(x, 8, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                t.set(x, 8, z, "minecraft:air")
            t.set(x, 9, z, "minecraft:air")
    # Arrow slits in the two outer faces. The ladder is on the middle of the outer face along z,
    # so the slits of this face are on each side of the ladder.
    outer_x = tx if west_side else tx + TOWER - 1
    outer_z = tz if north_side else tz + TOWER - 1
    t.set(outer_x, 4, tz + 1, "minecraft:air")
    t.set(outer_x, 4, tz + 3, "minecraft:air")
    t.set(tx + 2, 4, outer_z, "minecraft:air")
    # Door to the compound (2 blocks high) in the inner face along x.
    inner_x = tx + TOWER - 1 if west_side else tx
    t.set(inner_x, 1, tz + 2, "minecraft:air")
    t.set(inner_x, 2, tz + 2, "minecraft:air")
    # Ladder on the outer wall, up through the platform.
    ladder_x = tx + 1 if west_side else tx + TOWER - 2
    ladder_facing = "east" if west_side else "west"
    for y in range(1, 8):
        t.set(ladder_x, y, tz + 2, "minecraft:ladder", {"facing": ladder_facing, "waterlogged": "false"})
    # Archer post: the archer stands on the block above.
    t.set(tx + 2, 7, tz + 2, WATCH_POST)
    # Light inside the tower, under the platform (not in the line of fire).
    t.set(tx + 2, 6, tz + 2, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # Light on the platform: a lantern on the inner corner log (the archer shoots to the outside).
    inner_z = tz + TOWER - 1 if north_side else tz
    t.set(inner_x, 9, inner_z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    # Light above the door, on the side of the compound.
    door_side = "east" if west_side else "west"
    t.set(inner_x + (1 if west_side else -1), 3, tz + 2, *wall_torch(door_side))


def palisade_column(t, rng, x, z, pointed):
    t.set(x, 0, z, cobble(rng))
    for y in range(1, 5):
        t.set(x, y, z, "minecraft:spruce_log", LOG_Y)
    if pointed:
        t.set(x, 5, z, "minecraft:spruce_fence")


def gate(t, rng, z, facing):
    """Gate of 3 blocks in the wall at line z, with fence gates and a lintel."""
    for x in GATE:
        t.set(x, 0, z, "minecraft:dirt_path")
        t.set(x, 1, z, "minecraft:spruce_fence_gate",
              {"facing": facing, "in_wall": "false", "open": "false", "powered": "false"})
        t.set(x, 2, z, "minecraft:air")
        t.set(x, 3, z, "minecraft:air")
        t.set(x, 4, z, "minecraft:spruce_planks")
        t.set(x, 5, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (GATE[0] - 1, GATE[-1] + 1):
        t.set(x, 0, z, cobble(rng))
        for y in range(1, 6):
            t.set(x, y, z, "minecraft:stripped_spruce_log", LOG_Y)
        t.set(x, 6, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def lamp_post(t, x, z):
    t.set(x, 1, z, "minecraft:spruce_fence")
    t.set(x, 2, z, "minecraft:spruce_fence")
    t.set(x, 3, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def palisade_torches(t):
    """Wall torches on the inner face of the palisade, between the towers and the gates."""
    for i in range(TOWER + 2, SIZE - TOWER - 1, 6):
        if i in GATE or i in (GATE[0] - 1, GATE[-1] + 1):
            continue
        t.set(i, 3, 1, *wall_torch("south"))
        t.set(i, 3, SIZE - 2, *wall_torch("north"))
        t.set(1, 3, i, *wall_torch("east"))
        t.set(SIZE - 2, 3, i, *wall_torch("west"))


def with_margin(t, margin):
    """A copy of the template, moved by the margin in x and z, in a template that is larger by 2 × margin."""
    sx, sy, sz = t.size
    out = Template(sx + 2 * margin, sy, sz + 2 * margin)
    for (x, y, z), entry in t.blocks.items():
        out.blocks[(x + margin, y, z + margin)] = entry
    return out


def outer_banners(t):
    """Wall banners on the outer face of the palisade. t is the template with a margin of 1 block."""
    last = SIZE + 1  # the margin line on the far side
    spots = [GATE[0] - 1, GATE[-1] + 1, 10, SIZE - 11]
    for i in spots:
        c = i + 1  # palisade coordinate in the template with the margin
        t.set(c, 4, 0, WALL_BANNER, {"facing": "north"})
        t.set(c, 4, last, WALL_BANNER, {"facing": "south"})
    for i in (10, MID, SIZE - 11):
        c = i + 1
        t.set(0, 4, c, WALL_BANNER, {"facing": "west"})
        t.set(last, 4, c, WALL_BANNER, {"facing": "east"})


def path_block(rng):
    roll = rng.random()
    if roll < 0.1:
        return "minecraft:gravel"
    if roll < 0.2:
        return "minecraft:coarse_dirt"
    return "minecraft:dirt_path"


def center():
    rng = random.Random(1)
    t = Template(SIZE, HEIGHT, SIZE)
    # Clear the inside: trees and terrain above ground.
    t.fill(1, 1, 1, SIZE - 2, HEIGHT - 1, SIZE - 2, "minecraft:air")

    # Palisade between the towers.
    for i in range(TOWER, SIZE - TOWER):
        pointed = i % 2 == 0
        for (x, z) in ((i, 0), (i, SIZE - 1), (0, i), (SIZE - 1, i)):
            palisade_column(t, rng, x, z, pointed)

    for tx in (0, SIZE - TOWER):
        for tz in (0, SIZE - TOWER):
            tower(t, rng, tx, tz)

    gate(t, rng, 0, "north")
    gate(t, rng, SIZE - 1, "south")

    # Paths: north-south through the gates, east-west to the house plots.
    for z in range(1, SIZE - 1):
        for x in GATE:
            t.set(x, 0, z, path_block(rng))
    for x in range(TOWER, SIZE - TOWER):
        for z in GATE:
            t.set(x, 0, z, path_block(rng))

    # Plaza with the village heart and 4 braziers.
    lo, hi = MID - 5, MID + 5
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            border = x in (lo, hi) or z in (lo, hi)
            t.set(x, 0, z, "minecraft:stone_bricks" if border else cobble(rng))
    t.set(MID, 0, MID, "minecraft:chiseled_stone_bricks")
    t.set(MID, 1, MID, HEART)
    for dx in (-3, 3):
        for dz in (-3, 3):
            t.set(MID + dx, 0, MID + dz, "minecraft:stone_bricks")
            t.set(MID + dx, 1, MID + dz, "minecraft:campfire",
                  {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})

    for (x, z) in ((MID - 2, 7), (MID + 2, 7), (MID - 2, SIZE - 8), (MID + 2, SIZE - 8),
                   (7, MID - 2), (7, MID + 2), (SIZE - 8, MID - 2), (SIZE - 8, MID + 2)):
        lamp_post(t, x, z)
    palisade_torches(t)

    # House plots: jigsaw connectors next to the north-south path. Houses go into the free space.
    for z in (9, SIZE - 10):
        west_pool = BANK_POOL if z == 9 else HOUSE_POOL
        t.jigsaw(MID - 2, 1, z, "west_up", PLOT, HOUSE_FRONT, west_pool, "minecraft:air")
        t.jigsaw(MID + 2, 1, z, "east_up", PLOT, HOUSE_FRONT, HOUSE_POOL, "minecraft:air")
    t = with_margin(t, 1)
    outer_banners(t)
    return t


# ---------------------------------------------------------------- houses

W = 9   # x: 0..8
D = 9   # z: 0 = porch, 1..8 = body
H = 10


def house_shell(rng, wall_lower, wall_upper, door=True, wide_front=False):
    """Timber frame house: cobblestone base, frame of stripped spruce logs, gable roof over the porch."""
    t = Template(W, H, D)
    t.fill(0, 1, 0, W - 1, H - 1, D - 1, "minecraft:air")
    # Porch and floor.
    for x in range(W):
        t.set(x, 0, 0, "minecraft:cobblestone" if x in (0, W - 1) else "minecraft:spruce_planks")
        for z in range(1, D):
            edge = x in (0, W - 1) or z in (1, D - 1)
            t.set(x, 0, z, cobble(rng) if edge else "minecraft:spruce_planks")
    # Walls.
    for x in range(W):
        for z in range(1, D):
            if not (x in (0, W - 1) or z in (1, D - 1)):
                continue
            corner = x in (0, W - 1) and z in (1, D - 1)
            for y in range(1, 5):
                if corner or y == 4:
                    t.set(x, y, z, "minecraft:stripped_spruce_log",
                          LOG_Y if corner else {"axis": "x" if z in (1, D - 1) else "z"})
                elif y == 1:
                    t.set(x, y, z, wall_lower(rng))
                else:
                    t.set(x, y, z, wall_upper(rng))
    # Windows.
    for z in (3, 6):
        t.set(0, 2, z, "minecraft:glass_pane")
        t.set(W - 1, 2, z, "minecraft:glass_pane")
    for x in (2, 6):
        t.set(x, 2, D - 1, "minecraft:glass_pane")
    # Front opening.
    mid = W // 2
    if wide_front:
        for x in (mid - 1, mid, mid + 1):
            for y in (1, 2, 3):
                t.set(x, y, 1, "minecraft:air")
    elif door:
        t.set(mid, 1, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
        t.set(mid, 2, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
        t.set(mid, 3, 1, "minecraft:stripped_spruce_log", {"axis": "x"})
    # Porch posts.
    for x in (0, W - 1):
        for y in range(1, 5):
            t.set(x, y, 0, "minecraft:spruce_fence")
    # Porch light: 2 wall torches on the front wall.
    for x in (mid - 2, mid + 2):
        t.set(x, 3, 0, *wall_torch("north"))
    # Gable roof (ridge along z), over the porch.
    for step in range(5):
        y = 5 + step
        left, right = step, W - 1 - step
        for z in range(D):
            if left < right:
                t.set(left, y, z, "minecraft:dark_oak_stairs", stairs("east"))
                t.set(right, y, z, "minecraft:dark_oak_stairs", stairs("west"))
            elif left == right:
                t.set(left, y, z, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
        # Gable walls under the roof, at the front and the back of the body.
        for x in range(left + 1, right):
            for z in (1, D - 1):
                t.set(x, y, z, wall_upper(rng))
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def calcite(_rng):
    return "minecraft:calcite"


def lantern(t, x, y, z):
    t.set(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def bed(t, x, z_foot, color="blue"):
    """A bed with the foot at z_foot and the head at z_foot + 1 (toward the back wall)."""
    t.set(x, 1, z_foot, f"minecraft:{color}_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    t.set(x, 1, z_foot + 1, f"minecraft:{color}_bed", {"facing": "south", "part": "head", "occupied": "false"})


def cottage():
    rng = random.Random(2)
    t = house_shell(rng, cobble, calcite)
    bed(t, 2, 6, "red")
    t.set(6, 1, 7, "minecraft:crafting_table")
    t.set(5, 1, 7, "minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
    t.set(4, 0, 4, "minecraft:spruce_planks")
    t.set(4, 1, 4, "minecraft:red_carpet")
    t.set(7, 1, 2, "minecraft:flower_pot")
    lantern(t, 1, 1, 2)
    lantern(t, 7, 1, 7)
    return t


def barracks():
    rng = random.Random(3)
    t = house_shell(rng, cobble, lambda r: "minecraft:spruce_planks")
    for x in (1, 3, 5, 7):
        bed(t, x, 6, "blue")
    t.set(7, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(1, 1, 2, BANNER, {"rotation": "4"})
    lantern(t, 4, 1, 4)
    return t


def forge():
    rng = random.Random(4)
    t = house_shell(rng, cobble, cobble, wide_front=True)
    t.set(2, 1, 5, "minecraft:anvil", {"facing": "east"})
    t.set(5, 1, 7, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    t.set(6, 1, 7, "minecraft:furnace", {"facing": "north", "lit": "false"})
    t.set(7, 1, 7, "minecraft:smithing_table")
    t.set(1, 1, 7, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    t.set(7, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 2)
    return t


def storehouse():
    rng = random.Random(5)
    t = house_shell(rng, cobble, lambda r: "minecraft:spruce_planks")
    for (x, z) in ((1, 7), (2, 7), (1, 6), (7, 7), (7, 6)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for (x, z) in ((6, 7), (6, 6), (5, 7)):
        t.set(x, 1, z, "minecraft:hay_block", LOG_Y)
    t.set(6, 2, 7, "minecraft:hay_block", LOG_Y)
    t.set(1, 1, 3, "minecraft:composter", {"level": "0"})
    lantern(t, 4, 1, 4)
    return t


def bank():
    """The bank: walls of bank wall blocks, 4 bank chests on the back wall, iron bars in the windows."""
    rng = random.Random(6)
    t = house_shell(rng, lambda r: BANK_WALL, lambda r: BANK_WALL)
    for (x, y, z), ((name, _props), nbt) in list(t.blocks.items()):
        if name == "minecraft:glass_pane":
            t.set(x, y, z, "minecraft:iron_bars")
    for x in (2, 3, 5, 6):
        t.set(x, 1, 7, BANK_CHEST)
    t.set(1, 1, 2, BANNER, {"rotation": "4"})
    t.set(7, 1, 2, BANNER, {"rotation": "12"})
    lantern(t, 1, 1, 7)
    lantern(t, 7, 1, 7)
    t.set(4, 0, 4, "minecraft:polished_andesite")
    t.set(4, 1, 4, "minecraft:red_carpet")
    return t


PIECES = {
    "center": center,
    "house_cottage": cottage,
    "house_barracks": barracks,
    "house_forge": forge,
    "house_storehouse": storehouse,
    "bank": bank,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in PIECES.items():
        path = os.path.join(OUT, name + ".nbt")
        template = build()
        connect_panes(template)
        template.write(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
