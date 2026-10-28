#!/usr/bin/env python3
"""Generate the structure files of the faction village and the data files of its structure.

Output:
- world/src/main/resources/data/folkhold_world/structure/village/*.nbt: the pieces of the village structure.
- world/src/main/resources/data/folkhold_world/structure/village/<variant>/*.nbt: the pieces of the other biome
  variants (see VARIANTS).
- world/src/main/resources/data/folkhold_world/structure/village/<culture>/[<variant>/]*.nbt: the pieces of the style
  of a culture (see STYLES), with the same biome variants.
- world/src/main/resources/data/folkhold_world/worldgen/: the structure (structure/village.json), the template pools
  (template_pool/village/) and the processor lists (processor_list/village/) of each variant.
- world/src/main/resources/data/folkhold_world/tags/worldgen/biome/has_village.json: the biomes of the village, from
  the biomes of the variants.
- world/src/gametest/resources/data/folkhold_world/structure/village/center.nbt: the compound of the GameTests.

    python3 tools/structures/village.py

The village structure (type folkhold_world:fortified_village) puts the pieces of the jigsaw pools together, then it
adds the palisade, the towers and the gates around them. Pieces:
- plaza.nbt: the start piece. The village heart, 4 braziers, the alarm bell, the hall sign, the quest board and lamp
  posts. 4 jigsaw connectors for the streets: the street to the north has the plot of the bank.
- street.nbt, street_bank.nbt, street_end.nbt: the parts of a street. The paths follow the terrain. A segment has a
  plot on each side, and a connector for the end of the street. The end has a lamp post on each edge.
- house_*.nbt: houses for the plots. Each house has a porch row (z = 0) with its jigsaw connector.
- bank.nbt: the village bank. Its walls and its chests resist explosions.
- tower.nbt: a tower of the palisade, with the stair along the palisade. The outside of the template is to the west
  and to the north: the shape of a corner tower. The towers between the corners use the same template.
- gate.nbt: a gate of 3 blocks in the palisade. The outside of the template is to the north.
The palisade comes from code: it follows the terrain.

The compound of the GameTests (center.nbt) is a village in one piece: palisade, 4 corner towers, 2 gates, paths, a
plaza with the village heart and 4 house plots.

Conventions: y = 0 is the ground layer (foundations, floors, paths). y = 1 is the first layer above ground.
The compound gets a foundation of FOUNDATION layers under its ground layer (see with_foundation).
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from nbt import Template  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "world", "src")
DATA = os.path.join(ROOT, "main", "resources", "data", "folkhold_world")
OUT = os.path.join(DATA, "structure", "village")
WORLDGEN = os.path.join(DATA, "worldgen")
TEST_OUT = os.path.join(ROOT, "gametest", "resources", "data", "folkhold_world", "structure", "village")

# The namespace of the structure, the pools and the jigsaw names, and the namespace of the Folkhold blocks.
NS = "folkhold_world"
BLOCKS = "folkhold_blocks"
WATCH_POST = f"{BLOCKS}:watch_post"
HEART = f"{BLOCKS}:village_heart"
HOUSE_POOL = f"{NS}:village/houses"
BANK_POOL = f"{NS}:village/bank"
BANK_CHEST = f"{BLOCKS}:bank_chest"
BANK_WALL = f"{BLOCKS}:bank_wall"
PLOT = f"{NS}:plot"
HOUSE_FRONT = f"{NS}:house_front"
# The jigsaw names and the pools of the streets.
STREET = f"{NS}:street"
STREET_NEXT = f"{NS}:street_next"
PLAZA_EXIT = f"{NS}:plaza_exit"
STREET_ENDS_POOL = f"{NS}:village/street_ends"
FIRST_STREETS_POOL = f"{NS}:village/streets_first"
BANK_STREETS_POOL = f"{NS}:village/streets_bank"
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
            "flower_pot", "campfire", "brazier", "jigsaw", "composter", "anvil", "grindstone", "cauldron", "chest")
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
HEIGHT = 14
MID = SIZE // 2  # 19
GATE = range(MID - 1, MID + 2)  # 18..20
TOWER = 5
# The platform of a tower: the archer post is in this layer, and the archer stands on it.
PLATFORM_Y = 7
# The corner posts of a tower go up to this layer. The roof starts above it.
POST_TOP_Y = 10


def tower(t, rng, tx, tz):
    """Corner tower of 5 x 5 blocks with its lower corner at (tx, tz).

    - The base: a cobblestone course, then spruce planks, with a spruce log at each corner. The corner logs go up to
      the roof. A beam of stripped spruce logs is around the platform.
    - The platform: the archer post in the middle, a low parapet of slabs (the archer shoots over it), and a roof of
      dark oak on the corner posts.
    - The stair: 2 rows of stairs along the palisade, from the ground of the compound to an opening in the parapet
      (see tower_stair). Mobs walk up the stairs with no jump.
    - A small room in the base, with a door to the compound.
    """
    west_side = tx == 0
    north_side = tz == 0
    inner_x = tx + TOWER - 1 if west_side else tx
    # The 2 rows of the stair, next to the palisade, and the row of the door, away from the stair.
    stair_rows = (tz + 1, tz + 2) if north_side else (tz + 3, tz + 2)
    door_z = tz + 3 if north_side else tz + 1
    for dx in range(TOWER):
        for dz in range(TOWER):
            x, z = tx + dx, tz + dz
            t.set(x, 0, z, cobble(rng))
            ring = dx in (0, TOWER - 1) or dz in (0, TOWER - 1)
            corner = dx in (0, TOWER - 1) and dz in (0, TOWER - 1)
            for y in range(1, PLATFORM_Y):
                if corner:
                    t.set(x, y, z, "minecraft:spruce_log", LOG_Y)
                elif ring:
                    t.set(x, y, z, cobble(rng) if y <= 2 else "minecraft:spruce_planks")
                else:
                    t.set(x, y, z, "minecraft:air")
            if corner:
                for y in range(PLATFORM_Y, POST_TOP_Y + 1):
                    t.set(x, y, z, "minecraft:spruce_log", LOG_Y)
                continue
            if ring:
                axis = "x" if dz in (0, TOWER - 1) else "z"
                t.set(x, PLATFORM_Y, z, "minecraft:stripped_spruce_log", {"axis": axis})
                # Low parapet: the archer shoots over it.
                t.set(x, PLATFORM_Y + 1, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                t.set(x, PLATFORM_Y, z, "minecraft:spruce_planks")
                t.set(x, PLATFORM_Y + 1, z, "minecraft:air")
            for y in range(PLATFORM_Y + 2, POST_TOP_Y + 1):
                t.set(x, y, z, "minecraft:air")
    tower_roof(t, tx, tz)
    # Arrow slits in the two outer faces.
    outer_x = tx if west_side else tx + TOWER - 1
    outer_z = tz if north_side else tz + TOWER - 1
    t.set(outer_x, 4, tz + 1, "minecraft:air")
    t.set(outer_x, 4, tz + 3, "minecraft:air")
    t.set(tx + 2, 4, outer_z, "minecraft:air")
    # Door to the compound (2 blocks high) in the inner face along x, and a barrel in the room.
    t.set(inner_x, 1, door_z, "minecraft:air")
    t.set(inner_x, 2, door_z, "minecraft:air")
    t.set(tx + 2, 1, stair_rows[0], "minecraft:barrel", {"facing": "up", "open": "false"})
    tower_stair(t, rng, inner_x, west_side, stair_rows)
    # Archer post in the middle of the platform: the archer stands on it.
    t.set(tx + 2, PLATFORM_Y, tz + 2, WATCH_POST)
    # Light of the room, under the archer post.
    t.set(tx + 2, PLATFORM_Y - 1, tz + 2, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # Light of the platform: a lantern under the roof, in the inner corner (the archer shoots to the outside).
    lamp_x = tx + 3 if west_side else tx + 1
    lamp_z = tz + 3 if north_side else tz + 1
    t.set(lamp_x, POST_TOP_Y + 1, lamp_z, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # Light above the door, on the side of the compound.
    door_side = "east" if west_side else "west"
    t.set(inner_x + (1 if west_side else -1), 3, door_z, *wall_torch(door_side))


def roof_stairs(dx, dz, low, high):
    """The dark oak stairs of a ring of a pyramid roof, at (dx, dz) of the ring from low to high.

    Each stair faces the middle of the roof. A corner of the ring is an outer corner: its high quarter is toward the
    middle.
    """
    west, east, north, south = dx == low, dx == high, dz == low, dz == high
    if north and west:
        facing, shape = "south", "outer_left"
    elif north and east:
        facing, shape = "south", "outer_right"
    elif south and west:
        facing, shape = "north", "outer_right"
    elif south and east:
        facing, shape = "north", "outer_left"
    elif west:
        facing, shape = "east", "straight"
    elif east:
        facing, shape = "west", "straight"
    elif north:
        facing, shape = "south", "straight"
    else:
        facing, shape = "north", "straight"
    return "minecraft:dark_oak_stairs", {"facing": facing, "half": "bottom", "shape": shape, "waterlogged": "false"}


def tower_roof(t, tx, tz):
    """Pyramid roof of dark oak on the corner posts of a tower: a ring of 5 x 5, a ring of 3 x 3 and a slab."""
    for ring in range(2):
        y = POST_TOP_Y + 1 + ring
        low, high = ring, TOWER - 1 - ring
        for dx in range(low, high + 1):
            for dz in range(low, high + 1):
                if dx in (low, high) or dz in (low, high):
                    t.set(tx + dx, y, tz + dz, *roof_stairs(dx, dz, low, high))
                else:
                    t.set(tx + dx, y, tz + dz, "minecraft:air")
    t.set(tx + 2, POST_TOP_Y + 3, tz + 2, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})


# The number of stairs from the ground of the compound to the platform of a tower.
STAIR_STEPS = PLATFORM_Y


def tower_stair(t, rng, inner_x, west_side, rows):
    """Straight stair of 2 rows along the palisade, from the ground of the compound to the platform of a tower.

    The stair k (1 to STAIR_STEPS) is in layer k, on a base of cobblestone and spruce planks. The highest stair is
    next to the tower, and an opening in the parapet leads to the platform. Mobs walk up the stairs with no jump,
    and nothing is above the stairs. A path is at the foot of the stair.
    """
    toward = -1 if west_side else 1
    facing = "west" if west_side else "east"
    for z in rows:
        for k in range(1, STAIR_STEPS + 1):
            x = inner_x - toward * (STAIR_STEPS + 1 - k)
            for y in range(1, k):
                t.set(x, y, z, cobble(rng) if y == 1 else "minecraft:spruce_planks")
            t.set(x, k, z, "minecraft:spruce_stairs", stairs(facing))
            for y in range(k + 1, k + 4):
                t.set(x, y, z, "minecraft:air")
        t.set(inner_x - toward * (STAIR_STEPS + 1), 0, z, "minecraft:dirt_path")
        # The opening in the parapet, above the beam of the platform.
        t.set(inner_x, PLATFORM_Y + 1, z, "minecraft:air")


def in_tower_stair(x, z):
    """True if (x, z) is under a stair of a tower: no torch on the palisade there."""
    rows = (1, 2, SIZE - 3, SIZE - 2)
    span = range(TOWER, TOWER + STAIR_STEPS)
    return z in rows and (x in span or SIZE - 1 - x in span)


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
    """Wall torches on the inner face of the palisade, between the towers and the gates, not under the stairs."""
    for i in range(TOWER + 2, SIZE - TOWER - 1, 6):
        if i in GATE or i in (GATE[0] - 1, GATE[-1] + 1):
            continue
        if not in_tower_stair(i, 1):
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


FOUNDATION = 3
SOLID_GROUND = ("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks",
                "minecraft:chiseled_stone_bricks")


def with_foundation(t, margin):
    """A copy of the template on a foundation of FOUNDATION layers, under the compound (not under the margin).

    The ground layer of the compound becomes complete: grass blocks between the paths and the buildings. Under
    the ground layer, the foundation is dirt, and cobblestone under the stone blocks (walls, towers, plaza). The
    foundation fills the holes of the terrain under the village. The structure starts FOUNDATION blocks lower, so
    the ground layer stays at the level of the terrain.
    """
    sx, sy, sz = t.size
    out = Template(sx, sy + FOUNDATION, sz)
    for (x, y, z), entry in t.blocks.items():
        out.blocks[(x, y + FOUNDATION, z)] = entry
    for x in range(margin, sx - margin):
        for z in range(margin, sz - margin):
            ground = out.get(x, FOUNDATION, z)
            if ground is None:
                out.set(x, FOUNDATION, z, "minecraft:grass_block", {"snowy": "false"})
            below = "minecraft:cobblestone" if ground in SOLID_GROUND else "minecraft:dirt"
            for y in range(FOUNDATION):
                out.set(x, y, z, below)
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
    # Alarm bell on the plaza, next to the paths. The heart rings it in an alert.
    t.set(MID - 4, 0, MID - 2, "minecraft:stone_bricks")
    t.set(MID - 4, 1, MID - 2, "minecraft:bell", {"attachment": "floor", "facing": "north", "powered": "false"})
    for dx in (-3, 3):
        for dz in (-3, 3):
            t.set(MID + dx, 0, MID + dz, "minecraft:stone_bricks")
            t.set(MID + dx, 1, MID + dz, f"{BLOCKS}:brazier", {"lit": "false"})

    # Village hall sign on the plaza, between the heart and the north gate, out of the paths.
    building_sign(t, MID + 2, 1, MID - 4, "hall")
    # Quest board at the west end of the east-west path, facing east to the plaza: 3 columns x 3 rows across the
    # path. The columns go in the clockwise direction of the facing (south for east).
    for column in range(3):
        for row in range(3):
            t.set(TOWER - 1, 1 + row, MID - 1 + column, f"{BLOCKS}:quest_board",
                  {"facing": "east", "column": str(column), "row": str(row), "papers": "0"})
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
    return with_foundation(t, 1)


# ---------------------------------------------------------------- pieces of the village structure

PLAZA = 15
PLAZA_MID = PLAZA // 2  # 7
PLAZA_HEIGHT = 6
# The streets are 5 blocks wide: a path of 3 blocks between two edges of terrain.
STREET_WIDTH = 5
STREET_LENGTH = 13
STREET_END_LENGTH = 5
# A house is 9 blocks wide: 4 blocks on each side of its connector.
HOUSE_HALF_WIDTH = 4


def plaza():
    """The start piece: the plaza of the village heart, with a connector for a street on each side."""
    rng = random.Random(11)
    t = Template(PLAZA, PLAZA_HEIGHT, PLAZA)
    t.fill(0, 1, 0, PLAZA - 1, PLAZA_HEIGHT - 1, PLAZA - 1, "minecraft:air")
    lo, hi = 2, PLAZA - 3
    for x in range(PLAZA):
        for z in range(PLAZA):
            if lo <= x <= hi and lo <= z <= hi:
                border = x in (lo, hi) or z in (lo, hi)
                t.set(x, 0, z, "minecraft:stone_bricks" if border else cobble(rng))
            elif abs(x - PLAZA_MID) <= 1 or abs(z - PLAZA_MID) <= 1:
                t.set(x, 0, z, path_block(rng))
            else:
                t.set(x, 0, z, "minecraft:grass_block", {"snowy": "false"})
    t.set(PLAZA_MID, 0, PLAZA_MID, "minecraft:chiseled_stone_bricks")
    t.set(PLAZA_MID, 1, PLAZA_MID, HEART)
    # Alarm bell, next to the street to the west. The heart rings it in an alert.
    t.set(PLAZA_MID - 4, 0, PLAZA_MID - 2, "minecraft:stone_bricks")
    t.set(PLAZA_MID - 4, 1, PLAZA_MID - 2, "minecraft:bell",
          {"attachment": "floor", "facing": "north", "powered": "false"})
    for dx in (-3, 3):
        for dz in (-3, 3):
            t.set(PLAZA_MID + dx, 0, PLAZA_MID + dz, "minecraft:stone_bricks")
            t.set(PLAZA_MID + dx, 1, PLAZA_MID + dz, f"{BLOCKS}:brazier", {"lit": "false"})
    building_sign(t, PLAZA_MID + 2, 1, PLAZA_MID - 4, "hall")
    # Quest board in the south-east part of the plaza, facing west to the heart: 3 columns x 3 rows. The columns go
    # in the clockwise direction of the facing (north for west).
    for column in range(3):
        for row in range(3):
            t.set(hi, 1 + row, hi - column, f"{BLOCKS}:quest_board",
                  {"facing": "west", "column": str(column), "row": str(row), "papers": "0"})
    for (x, z) in ((1, 1), (PLAZA - 2, 1), (1, PLAZA - 2)):
        lamp_post(t, x, z)
    # The connectors of the streets, in the middle of each side. The street to the north has the plot of the bank.
    last = PLAZA - 1
    t.jigsaw(PLAZA_MID, 0, 0, "north_up", PLAZA_EXIT, STREET, BANK_STREETS_POOL, "minecraft:dirt_path")
    t.jigsaw(PLAZA_MID, 0, last, "south_up", PLAZA_EXIT, STREET, FIRST_STREETS_POOL, "minecraft:dirt_path")
    t.jigsaw(0, 0, PLAZA_MID, "west_up", PLAZA_EXIT, STREET, FIRST_STREETS_POOL, "minecraft:dirt_path")
    t.jigsaw(last, 0, PLAZA_MID, "east_up", PLAZA_EXIT, STREET, FIRST_STREETS_POOL, "minecraft:dirt_path")
    return t


def street_path(t, rng, length):
    """The path of a street: 3 blocks wide, between two edges where the terrain stays."""
    for z in range(length):
        for x in range(1, STREET_WIDTH - 1):
            t.set(x, 0, z, path_block(rng))


def street(bank=False):
    """A segment of street along z, with a plot on each side and a connector for the end of the street.

    The segment follows the terrain (projection terrain_matching). The segment to the north has the plot of the bank
    on its west side.
    """
    rng = random.Random(12 if bank else 13)
    t = Template(STREET_WIDTH, 4, STREET_LENGTH)
    street_path(t, rng, STREET_LENGTH)
    mid = STREET_WIDTH // 2
    t.jigsaw(mid, 0, 0, "north_up", STREET, "minecraft:empty", "minecraft:empty", "minecraft:dirt_path")
    t.jigsaw(mid, 0, STREET_LENGTH - 1, "south_up", STREET_NEXT, STREET, STREET_ENDS_POOL, "minecraft:dirt_path")
    # The plots are near the end of the segment: the houses of 2 streets side by side do not touch.
    plot_z = STREET_LENGTH - 1 - HOUSE_HALF_WIDTH
    t.jigsaw(0, 1, plot_z, "west_up", PLOT, HOUSE_FRONT, BANK_POOL if bank else HOUSE_POOL, "minecraft:air")
    t.jigsaw(STREET_WIDTH - 1, 1, plot_z, "east_up", PLOT, HOUSE_FRONT, HOUSE_POOL, "minecraft:air")
    # A lamp post at the far end, on the east edge.
    lamp_post(t, STREET_WIDTH - 1, STREET_LENGTH - 1)
    return t


def street_end():
    """The end of a street: a short path with a lamp post on each edge. It has no connector for another segment.

    The village structure adds the path from the end of the street to the gate.
    """
    rng = random.Random(14)
    t = Template(STREET_WIDTH, 4, STREET_END_LENGTH)
    street_path(t, rng, STREET_END_LENGTH)
    mid = STREET_WIDTH // 2
    t.jigsaw(mid, 0, 0, "north_up", STREET, "minecraft:empty", "minecraft:empty", "minecraft:dirt_path")
    lamp_post(t, 0, STREET_END_LENGTH - 1)
    lamp_post(t, STREET_WIDTH - 1, STREET_END_LENGTH - 1)
    return t


# The tower piece: the tower of 5 x 5 blocks and its stair of STAIR_STEPS stairs and a path, along the north side.
TOWER_PIECE_LENGTH = TOWER + STAIR_STEPS + 1


def tower_piece():
    """A tower of the palisade with its stair. The outside of the template is to the west and to the north.

    The village structure turns the template for each side. The palisade continues along the north side of the
    stair (z = 0, from x = TOWER) and, at a corner, along the west side of the tower (x = 0, from z = TOWER).
    """
    rng = random.Random(15)
    t = Template(TOWER_PIECE_LENGTH, HEIGHT, TOWER)
    tower(t, rng, 0, 0)
    return t


GATE_WIDTH = 5
GATE_DEPTH = 3
GATE_HEIGHT = 7


def gate_piece():
    """A gate of 3 blocks in the palisade, with its 2 posts. The outside of the template is to the north (z = 0).

    The palisade line is z = 1. The path goes through the gate, from z = 0 to z = 2.
    """
    rng = random.Random(16)
    t = Template(GATE_WIDTH, GATE_HEIGHT, GATE_DEPTH)
    line = 1
    for x in range(1, GATE_WIDTH - 1):
        for z in range(GATE_DEPTH):
            t.set(x, 0, z, "minecraft:dirt_path")
            for y in range(1, 4):
                t.set(x, y, z, "minecraft:air")
        t.set(x, 1, line, "minecraft:spruce_fence_gate",
              {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
        t.set(x, 4, line, "minecraft:spruce_planks")
        t.set(x, 5, line, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (0, GATE_WIDTH - 1):
        t.set(x, 0, line, cobble(rng))
        for y in range(1, 6):
            t.set(x, y, line, "minecraft:stripped_spruce_log", LOG_Y)
        t.set(x, 6, line, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    return t


# ---------------------------------------------------------------- houses

W = 9   # x: 0..8
D = 9   # z: 0 = porch, 1..8 = body
H = 10


def building_sign(t, x, y, z, kind, facing="north"):
    """The building sign of a kind (see tools/pixvox/building_signs.py)."""
    t.set(x, y, z, f"{BLOCKS}:building_sign_{kind}", {"facing": facing})


def house_shell(rng, wall_lower, wall_upper, sign, door=True, wide_front=False):
    """Timber frame house: cobblestone base, frame of stripped spruce logs, gable roof over the porch.

    The building sign of the kind stands on the porch, next to the east porch post.
    """
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
    building_sign(t, W - 2, 1, 0, sign)
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
    t = house_shell(rng, cobble, calcite, "house")
    # 3 beds: each bed gives a place to an inhabitant of the village.
    for x in (1, 2, 3):
        bed(t, x, 6, "red")
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
    t = house_shell(rng, cobble, lambda r: "minecraft:spruce_planks", "barracks")
    for x in (1, 3, 5, 7):
        bed(t, x, 6, "blue")
    t.set(7, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(1, 1, 2, BANNER, {"rotation": "4"})
    lantern(t, 4, 1, 4)
    return t


def forge():
    rng = random.Random(4)
    t = house_shell(rng, cobble, cobble, "forge", wide_front=True)
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
    t = house_shell(rng, cobble, lambda r: "minecraft:spruce_planks", "storehouse")
    for (x, z) in ((1, 7), (2, 7), (1, 6), (7, 7), (7, 6)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for (x, z) in ((6, 7), (6, 6), (5, 7)):
        t.set(x, 1, z, "minecraft:hay_block", LOG_Y)
    t.set(6, 2, 7, "minecraft:hay_block", LOG_Y)
    t.set(1, 1, 3, "minecraft:composter", {"level": "0"})
    lantern(t, 4, 1, 4)
    return t


def bank():
    """The bank: a small stone treasury, different from the houses.

    - Walls, flat roof and crenellated parapet of bank wall blocks: they resist explosions.
    - Front portico (z = 0..1) with two columns, an entablature and the bank sign.
    - Door in the middle of the front wall, narrow windows with iron bars.
    - Inside: a counter with iron bars (the teller windows) and the vault with the 4 bank chests on the back wall.
    """
    t = Template(W, H, D)
    t.fill(0, 1, 0, W - 1, H - 1, D - 1, "minecraft:air")
    front, back = 2, D - 1
    roof = 6
    # Floors: the portico, then the hall of the bank.
    for x in range(W):
        for z in range(0, front):
            t.set(x, 0, z, "minecraft:polished_deepslate")
        for z in range(front, D):
            edge = x in (0, W - 1) or z in (front, back)
            t.set(x, 0, z, BANK_WALL if edge else "minecraft:deepslate_tiles")
    # Walls and flat roof.
    for x in range(W):
        for z in range(front, D):
            if x in (0, W - 1) or z in (front, back):
                for y in range(1, roof):
                    t.set(x, y, z, BANK_WALL)
            t.set(x, roof, z, BANK_WALL)
    # Crenellated parapet.
    for x in range(W):
        for z in range(front, D):
            if (x in (0, W - 1) or z in (front, back)) and (x + z) % 2 == 0:
                t.set(x, roof + 1, z, BANK_WALL)
    # Door and windows.
    mid = W // 2
    t.set(mid, 1, front, "minecraft:dark_oak_door",
          {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(mid, 2, front, "minecraft:dark_oak_door",
          {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    for x in (2, W - 3):
        t.set(x, 2, front, "minecraft:iron_bars")
        t.set(x, 3, front, "minecraft:iron_bars")
    for z in (4, 6):
        for x in (0, W - 1):
            t.set(x, 2, z, "minecraft:iron_bars")
            t.set(x, 3, z, "minecraft:iron_bars")
    # Portico: two columns, the entablature, the parapet edge, lights and banners.
    for x in (1, W - 2):
        t.set(x, 1, 0, "minecraft:chiseled_deepslate")
        for y in range(2, roof - 1):
            t.set(x, y, 0, "minecraft:polished_basalt", LOG_Y)
    for x in range(W):
        for z in range(0, front):
            t.set(x, roof - 1, z, "minecraft:polished_deepslate")
        t.set(x, roof, 0, "minecraft:deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
        t.set(x, roof, 1, "minecraft:deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (2, W - 3):
        t.set(x, roof - 2, 1, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    for x in (mid - 1, mid + 1):
        t.set(x, 3, 1, WALL_BANNER, {"facing": "north"})
    building_sign(t, W - 3, 1, 0, "bank")
    # Inside: carpet to the counter, the counter with the teller windows, the vault.
    for z in range(front + 1, 5):
        t.set(mid, 1, z, "minecraft:red_carpet")
    for x in range(1, W - 1):
        if x != mid:
            t.set(x, 1, 5, "minecraft:polished_deepslate_slab", {"type": "top", "waterlogged": "false"})
            t.set(x, 2, 5, "minecraft:iron_bars")
    for x in (2, 3, 5, 6):
        t.set(x, 1, back - 1, BANK_CHEST)
    lantern(t, 1, 1, front + 1)
    lantern(t, W - 2, 1, front + 1)
    t.set(mid, roof - 1, 6, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # Jigsaw connector in the portico, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


# ---------------------------------------------------------------- Valeholm style (Norse)

# A deep long house: the gable end and the door face the street (z = 0 is the porch), the body goes deep into the plot.
LW = 9    # x: 0..8
LD = 17   # z: 0 = porch, 1..16 = body
LH = 16
# A side long house: the long side and the door face the street (z = 0 is the porch), the body goes along the street.
SL = 17   # x: 0..16
SD = 10   # z: 0 = porch, 1..9 = body
# The walls go up to this layer. The roof starts above it.
LONG_WALL_TOP = 3
# A Norse street: a segment of 21 blocks with 2 plots on each side, for the long houses.
NORSE_STREET_LENGTH = 21
NORSE_PLOTS = (6, 16)


def steep_roof_top(across, width):
    """The layer of the top block of a steep roof over a row across the house: the roof goes up by 2 blocks for each
    block to the ridge."""
    k = min(across, width - 1 - across)
    return LONG_WALL_TOP + 2 + 2 * k


def unlit_campfire(facing="north"):
    return "minecraft:campfire", {"facing": facing, "lit": "false", "signal_fire": "false", "waterlogged": "false"}


def long_house_shell(rng, sign, wall_lower, door=True, wide_front=False):
    """A deep Norse long house: a base of cobblestone, walls of horizontal spruce logs between posts of stripped spruce
    logs, a steep roof of dark oak with a ridge along the house, and carved horns at the ends of the ridge.

    The building sign of the kind stands on the porch, next to the east porch post. A hearth of stone bricks is in the
    middle of the body, with a campfire that is not lit.
    """
    t = Template(LW, LH, LD)
    t.fill(0, 1, 0, LW - 1, LH - 1, LD - 1, "minecraft:air")
    mid = LW // 2
    # Porch and floor.
    for x in range(LW):
        t.set(x, 0, 0, "minecraft:cobblestone" if x in (0, LW - 1) else "minecraft:spruce_planks")
        for z in range(1, LD):
            edge = x in (0, LW - 1) or z in (1, LD - 1)
            t.set(x, 0, z, cobble(rng) if edge else "minecraft:spruce_planks")
    # Walls: posts at the corners and every 4 blocks along the sides, logs between them.
    for x in range(LW):
        for z in range(1, LD):
            side = x in (0, LW - 1)
            end = z in (1, LD - 1)
            if not (side or end):
                continue
            post = (side and end) or (side and (z - 1) % 4 == 0) or (end and x == mid)
            for y in range(1, LONG_WALL_TOP + 1):
                if post:
                    t.set(x, y, z, "minecraft:stripped_spruce_log", LOG_Y)
                elif y == 1:
                    t.set(x, y, z, wall_lower(rng))
                else:
                    t.set(x, y, z, "minecraft:spruce_log", {"axis": "z" if side else "x"})
    # Small windows on the sides.
    for z in (3, 7, 11, 15):
        for x in (0, LW - 1):
            t.set(x, 2, z, "minecraft:glass_pane")
    # Front opening.
    if wide_front:
        for x in (mid - 1, mid, mid + 1):
            for y in (1, 2):
                t.set(x, y, 1, "minecraft:air")
    elif door:
        t.set(mid, 1, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
        t.set(mid, 2, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
        t.set(mid, 3, 1, "minecraft:stripped_spruce_log", {"axis": "x"})
    # Porch posts.
    for x in (0, LW - 1):
        for y in range(1, LONG_WALL_TOP + 1):
            t.set(x, y, 0, "minecraft:spruce_fence")
    # Porch light: 2 wall torches on the front wall.
    for x in (mid - 2, mid + 2):
        t.set(x, 2, 0, *wall_torch("north"))
    # Steep roof (ridge along z), over the porch.
    for x in range(LW):
        top = steep_roof_top(x, LW)
        for z in range(LD):
            t.set(x, top - 1, z, "minecraft:dark_oak_planks")
            if x == mid:
                t.set(x, top, z, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                t.set(x, top, z, "minecraft:dark_oak_stairs", stairs("east" if x < mid else "west"))
        # Gable walls under the roof, at the front and the back of the body.
        if 0 < x < LW - 1:
            for y in range(LONG_WALL_TOP + 1, top - 1):
                for z in (1, LD - 1):
                    t.set(x, y, z, "minecraft:spruce_planks")
    # Carved horns at the 2 ends of the ridge.
    for z in (0, LD - 1):
        for x in (mid - 1, mid + 1):
            t.set(x, steep_roof_top(x, LW) + 1, z, "minecraft:spruce_fence")
            t.set(x, steep_roof_top(x, LW) + 2, z, "minecraft:spruce_fence")
        t.set(mid, steep_roof_top(mid, LW) + 1, z, "minecraft:spruce_fence")
    # Long hearth in the middle of the body.
    hearth = LD // 2
    for z in range(hearth - 2, hearth + 3):
        t.set(mid, 0, z, "minecraft:stone_bricks")
    t.set(mid, 1, hearth, *unlit_campfire())
    t.set(mid, steep_roof_top(mid, LW) - 2, hearth + 4, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    building_sign(t, LW - 2, 1, 0, sign)
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def side_house_shell(rng, sign, wall_lower):
    """A side Norse long house: the same walls and roof as a deep long house, with the ridge along the street and the
    door in the middle of the long side. A small porch with 2 posts is in front of the door. The hearth is in the
    middle of the body, along the ridge.
    """
    t = Template(SL, LH, SD)
    t.fill(0, 1, 1, SL - 1, LH - 1, SD - 1, "minecraft:air")
    mid = SL // 2
    ridge = (SD + 1) // 2   # the body is z = 1..SD - 1
    # Porch in front of the door.
    for x in range(mid - 3, mid + 4):
        t.set(x, 0, 0, "minecraft:cobblestone" if x in (mid - 3, mid + 3) else "minecraft:spruce_planks")
        for y in range(1, 4):
            t.set(x, y, 0, "minecraft:air")
    # Floor.
    for x in range(SL):
        for z in range(1, SD):
            edge = x in (0, SL - 1) or z in (1, SD - 1)
            t.set(x, 0, z, cobble(rng) if edge else "minecraft:spruce_planks")
    # Walls.
    for x in range(SL):
        for z in range(1, SD):
            long_side = z in (1, SD - 1)
            end = x in (0, SL - 1)
            if not (long_side or end):
                continue
            post = (long_side and end) or (long_side and x % 4 == 0) or (end and z == ridge)
            for y in range(1, LONG_WALL_TOP + 1):
                if post:
                    t.set(x, y, z, "minecraft:stripped_spruce_log", LOG_Y)
                elif y == 1:
                    t.set(x, y, z, wall_lower(rng))
                else:
                    t.set(x, y, z, "minecraft:spruce_log", {"axis": "x" if long_side else "z"})
    # Windows on the long sides.
    for x in (2, 6, SL - 7, SL - 3):
        for z in (1, SD - 1):
            if x not in (mid - 1, mid, mid + 1):
                t.set(x, 2, z, "minecraft:glass_pane")
    # Door in the middle of the front side.
    t.set(mid, 1, 1, "minecraft:spruce_door",
          {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(mid, 2, 1, "minecraft:spruce_door",
          {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    t.set(mid, 3, 1, "minecraft:stripped_spruce_log", {"axis": "x"})
    for x in (mid - 3, mid + 3):
        for y in range(1, LONG_WALL_TOP + 1):
            t.set(x, y, 0, "minecraft:spruce_fence")
    for x in (mid - 2, mid + 2):
        t.set(x, 2, 0, *wall_torch("north"))
    # Steep roof (ridge along x). The eave goes over the porch.
    for z in range(1, SD):
        top = steep_roof_top(z - 1, SD - 1)
        for x in range(SL):
            t.set(x, top - 1, z, "minecraft:dark_oak_planks")
            if z == ridge:
                t.set(x, top, z, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                t.set(x, top, z, "minecraft:dark_oak_stairs", stairs("south" if z < ridge else "north"))
        if 1 < z < SD - 1:
            for y in range(LONG_WALL_TOP + 1, top - 1):
                for x in (0, SL - 1):
                    t.set(x, y, z, "minecraft:spruce_planks")
    for x in range(SL):
        t.set(x, LONG_WALL_TOP + 1, 0, "minecraft:dark_oak_stairs", stairs("south"))
    # Carved horns at the 2 ends of the ridge.
    for x in (0, SL - 1):
        for z in (ridge - 1, ridge + 1):
            t.set(x, steep_roof_top(z - 1, SD - 1) + 1, z, "minecraft:spruce_fence")
            t.set(x, steep_roof_top(z - 1, SD - 1) + 2, z, "minecraft:spruce_fence")
        t.set(x, steep_roof_top(ridge - 1, SD - 1) + 1, ridge, "minecraft:spruce_fence")
    # Long hearth along the ridge.
    for x in range(mid - 3, mid + 4):
        t.set(x, 0, ridge, "minecraft:stone_bricks")
    t.set(mid, 1, ridge, *unlit_campfire("east"))
    t.set(mid + 4, steep_roof_top(ridge - 1, SD - 1) - 2, ridge, "minecraft:lantern",
          {"hanging": "true", "waterlogged": "false"})
    building_sign(t, mid + 2, 1, 0, sign)
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def norse_cottage():
    rng = random.Random(12)
    t = long_house_shell(rng, "house", cobble)
    # 3 beds: each bed gives a place to an inhabitant of the village.
    for x in (1, 2, LW - 2):
        bed(t, x, LD - 4, "blue")
    t.set(LW - 2, 1, 3, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"})
    t.set(1, 1, 3, "minecraft:crafting_table")
    t.set(LW - 2, 1, 9, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(1, 1, 9, "minecraft:loom", {"facing": "east"})
    lantern(t, 1, 1, 6)
    return t


def norse_cottage_side():
    rng = random.Random(22)
    t = side_house_shell(rng, "house", cobble)
    # 3 beds along the back wall.
    for x in (2, 3, SL - 3):
        bed(t, x, SD - 3, "blue")
    t.set(1, 1, 2, "minecraft:crafting_table")
    t.set(SL - 2, 1, 2, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"})
    t.set(SL - 2, 1, SD - 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    lantern(t, 1, 1, SD - 2)
    return t


def norse_barracks():
    rng = random.Random(13)
    t = long_house_shell(rng, "barracks", cobble)
    for x in (1, 2, LW - 3, LW - 2):
        bed(t, x, LD - 4, "red")
    for z in (3, 5):
        t.set(1, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(LW - 2, 1, 3, BANNER, {"rotation": "4"})
    for z in (6, 10):
        t.set(1, 1, z, "minecraft:spruce_fence")
        t.set(LW - 2, 1, z, "minecraft:spruce_fence")
    lantern(t, 1, 1, 8)
    return t


def norse_forge():
    rng = random.Random(14)
    t = long_house_shell(rng, "forge", cobble, wide_front=True)
    t.set(2, 1, 4, "minecraft:anvil", {"facing": "east"})
    t.set(LW - 2, 1, LD - 3, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.set(LW - 2, 1, LD - 4, "minecraft:furnace", {"facing": "west", "lit": "false"})
    t.set(1, 1, LD - 3, "minecraft:smithing_table")
    t.set(1, 1, LD - 4, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    t.set(LW - 2, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 7)
    return t


def norse_storehouse():
    rng = random.Random(15)
    t = long_house_shell(rng, "storehouse", cobble)
    for z in range(LD - 6, LD - 1):
        t.set(1, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        t.set(LW - 2, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for (x, z) in ((1, 3), (1, 4), (LW - 2, 3)):
        t.set(x, 1, z, "minecraft:hay_block", LOG_Y)
    t.set(1, 2, 3, "minecraft:hay_block", LOG_Y)
    t.set(LW - 2, 1, 4, "minecraft:composter", {"level": "0"})
    lantern(t, 1, 1, 7)
    return t


def norse_storehouse_side():
    rng = random.Random(25)
    t = side_house_shell(rng, "storehouse", cobble)
    for x in range(1, 6):
        t.set(x, 1, SD - 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    for x in range(SL - 6, SL - 1):
        t.set(x, 1, SD - 2, "minecraft:hay_block", LOG_Y)
    t.set(1, 1, 2, "minecraft:composter", {"level": "0"})
    lantern(t, SL - 2, 1, 2)
    return t


def norse_street(bank=False):
    """A Norse street along z: a segment of NORSE_STREET_LENGTH blocks with 2 plots on each side (NORSE_PLOTS), for
    the long houses. The segment to the north has the plot of the bank on its west side, near the plaza."""
    rng = random.Random(32 if bank else 33)
    t = Template(STREET_WIDTH, 4, NORSE_STREET_LENGTH)
    street_path(t, rng, NORSE_STREET_LENGTH)
    mid = STREET_WIDTH // 2
    t.jigsaw(mid, 0, 0, "north_up", STREET, "minecraft:empty", "minecraft:empty", "minecraft:dirt_path")
    t.jigsaw(mid, 0, NORSE_STREET_LENGTH - 1, "south_up", STREET_NEXT, STREET, STREET_ENDS_POOL, "minecraft:dirt_path")
    for i, z in enumerate(NORSE_PLOTS):
        west = BANK_POOL if bank and i == 0 else HOUSE_POOL
        t.jigsaw(0, 1, z, "west_up", PLOT, HOUSE_FRONT, west, "minecraft:air")
        t.jigsaw(STREET_WIDTH - 1, 1, z, "east_up", PLOT, HOUSE_FRONT, HOUSE_POOL, "minecraft:air")
    lamp_post(t, STREET_WIDTH - 1, NORSE_PLOTS[0] + 5)
    lamp_post(t, 0, NORSE_STREET_LENGTH - 1)
    return t

PIECES = {
    "plaza": plaza,
    "street": street,
    "street_bank": lambda: street(bank=True),
    "street_end": street_end,
    "tower": tower_piece,
    "gate": gate_piece,
    "house_cottage": cottage,
    "house_barracks": barracks,
    "house_forge": forge,
    "house_storehouse": storehouse,
    "bank": bank,
}


# The styles of the cultures: the pieces that a culture changes. The other pieces are the pieces of PIECES. The
# villages of a culture with no style here use PIECES.
STYLES = {
    "valeholm": {
        "street": norse_street,
        "street_bank": lambda: norse_street(bank=True),
        "house_cottage": norse_cottage,
        "house_cottage_side": norse_cottage_side,
        "house_barracks": norse_barracks,
        "house_forge": norse_forge,
        "house_storehouse": norse_storehouse,
        "house_storehouse_side": norse_storehouse_side,
    },
}
# The houses of the pool of the houses of each style, with their weights. The jigsaw tries the houses in a random
# order with these weights: a house that touches another piece goes to the next house, and a plot with no house that
# fits stays free. The styles that are not here use HOUSES.
HOUSES = [(3, "house_cottage"), (2, "house_barracks"), (1, "house_forge"), (2, "house_storehouse")]
STYLE_HOUSES = {
    "valeholm": [(2, "house_cottage"), (2, "house_cottage_side"), (2, "house_barracks"), (1, "house_forge"),
                 (1, "house_storehouse"), (1, "house_storehouse_side")],
}


# The pieces of the GameTests.
TEST_PIECES = {
    "center": center,
}


# ---------------------------------------------------------------- biome variants

# The blocks of a wood that the pieces use.
WOOD_BLOCKS = ("planks", "log", "fence", "fence_gate", "door", "slab", "stairs")


def wood(old, new):
    """The replacements of the blocks of a wood by the blocks of another wood: planks, logs, stripped logs, fences,
    fence gates, doors, slabs and stairs."""
    blocks = {f"minecraft:{old}_{kind}": f"minecraft:{new}_{kind}" for kind in WOOD_BLOCKS}
    blocks[f"minecraft:stripped_{old}_log"] = f"minecraft:stripped_{new}_log"
    return blocks


def plain(name):
    """The replacement by a block with no properties, for example a log (axis) by a stone."""
    return name, ()


# The desert: sandstone for the wood and the stones of the base variant, and jungle wood for the doors and the fence
# gates. A sandstone wall replaces a fence.
DESERT = {
    "minecraft:spruce_planks": "minecraft:smooth_sandstone",
    "minecraft:spruce_log": plain("minecraft:cut_sandstone"),
    "minecraft:stripped_spruce_log": plain("minecraft:cut_sandstone"),
    "minecraft:spruce_slab": "minecraft:smooth_sandstone_slab",
    "minecraft:spruce_stairs": "minecraft:smooth_sandstone_stairs",
    "minecraft:spruce_fence": plain("minecraft:sandstone_wall"),
    "minecraft:spruce_fence_gate": "minecraft:jungle_fence_gate",
    "minecraft:spruce_door": "minecraft:jungle_door",
    "minecraft:cobblestone": "minecraft:sandstone",
    "minecraft:mossy_cobblestone": "minecraft:sandstone",
    "minecraft:stone_bricks": "minecraft:cut_sandstone",
    "minecraft:chiseled_stone_bricks": "minecraft:chiseled_sandstone",
}
# The swamp: mangrove wood, and mud bricks for the stones of the base variant.
SWAMP = {
    **wood("spruce", "mangrove"),
    "minecraft:cobblestone": "minecraft:mud_bricks",
    "minecraft:mossy_cobblestone": "minecraft:packed_mud",
    "minecraft:stone_bricks": "minecraft:mud_bricks",
}


class Variant:
    """A biome variant of the village: the same pieces, with other materials.

    The base variant has no replacements: its pieces, pools and processor lists are in the folders of the village. The
    files of another variant are in a subfolder with the name of the variant. The jigsaw connectors of its pieces use
    its pools.

    A replacement is the name of a block with the same properties, or a pair (name, properties) with other properties
    (see plain).
    """

    def __init__(self, name, biomes, blocks, culture=None):
        self.name = name
        self.biomes = biomes
        self.blocks = blocks
        self.culture = culture

    @property
    def base(self):
        return not self.blocks

    def of(self, culture):
        """The same variant for the style of a culture."""
        return Variant(self.name, self.biomes, self.blocks, culture)

    def path(self, name):
        """The path of a file of the village for this variant, with no namespace and no extension. The files of the
        style of a culture are in a folder with the name of the culture."""
        parts = ["village"]
        if self.culture:
            parts.append(self.culture)
        if not self.base:
            parts.append(self.name)
        return "/".join(parts + [name])

    def id(self, name):
        """The id of a file of the village for this variant: a piece, a pool or a processor list."""
        return f"{NS}:{self.path(name)}"

    def replace(self, name, props):
        """The name and the properties of a block in this variant."""
        new = self.blocks.get(name, name)
        return new if isinstance(new, tuple) else (new, props)

    def block(self, name):
        return self.replace(name, None)[0]

    def state(self, name, props=None):
        """A block state as in the JSON files of the game."""
        name, props = self.replace(name, props)
        state = {"Name": name}
        if props:
            state["Properties"] = props
        return state

    def apply(self, t):
        """The template with the materials of this variant. The jigsaw connectors use the pools of the variant."""
        out = Template(*t.size)
        prefix = f"{NS}:village/"
        for pos, ((name, props), nbt) in t.blocks.items():
            if nbt is not None and nbt.get("id") == "minecraft:jigsaw" and nbt["pool"].startswith(prefix):
                nbt = dict(nbt, pool=self.id(nbt["pool"][len(prefix):]))
            out.blocks[pos] = (self.replace(name, props), nbt)
        return out


# The variants of the village, with their biomes. The tag of the biomes of the village has the biomes of all the
# variants. In a biome of the tag that no other variant has, the village uses the base variant.
BASE = Variant("taiga", ["minecraft:taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:windswept_hills",
                        "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills"], {})
VARIANTS = (
    BASE,
    Variant("plains", ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                       "minecraft:cherry_grove", "minecraft:forest"],
            wood("spruce", "oak")),
    Variant("birch", ["minecraft:birch_forest"], wood("spruce", "birch")),
    Variant("savanna", ["minecraft:savanna"], wood("spruce", "acacia")),
    Variant("desert", ["minecraft:desert"], DESERT),
    Variant("swamp", ["minecraft:swamp", "minecraft:mangrove_swamp"], SWAMP),
)

LOG_UP = {"axis": "y"}


def element(variant, piece, projection, processors):
    return {
        "element_type": "minecraft:single_pool_element",
        "location": variant.id(piece),
        "projection": projection,
        "processors": variant.id(processors),
    }


def pool(elements, fallback="minecraft:empty"):
    return {"fallback": fallback, "elements": [{"weight": weight, "element": e} for weight, e in elements]}


def pools(v):
    """The template pools of a variant, by name."""
    def house(piece):
        return element(v, piece, "rigid", "foundation")

    def street(piece):
        return element(v, piece, "terrain_matching", "street")

    return {
        "plaza": pool([(1, house("plaza"))]),
        "houses": pool([(weight, house(piece)) for weight, piece in STYLE_HOUSES.get(v.culture, HOUSES)]
                       + [(1, {"element_type": "minecraft:empty_pool_element"})]),
        "homes": pool([(1, house("house_cottage"))]),
        "bank": pool([(1, house("bank"))]),
        "streets_first": pool([(1, street("street"))], v.id("street_ends")),
        "streets_bank": pool([(1, street("street_bank"))], v.id("street_ends")),
        "street_ends": pool([(1, street("street_end"))]),
    }


def processor_lists(v):
    """The processor lists of a variant, by name: the foundation of the buildings, and the streets."""
    planks = v.state("minecraft:spruce_planks")
    street_rules = [{
        "input_predicate": {"predicate_type": "minecraft:block_match", "block": path},
        "location_predicate": {"predicate_type": "minecraft:block_match", "block": "minecraft:water"},
        "output_state": planks,
    } for path in ("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt")]
    return {
        "foundation": {"processors": [{
            "processor_type": f"{NS}:foundation",
            "block": v.state("minecraft:cobblestone"),
            "max_depth": 32,
            "stilt": v.state("minecraft:spruce_log", LOG_UP),
        }]},
        "street": {"processors": [
            {"processor_type": "minecraft:gravity", "heightmap": "WORLD_SURFACE_WG", "offset": -1},
            {"processor_type": "minecraft:rule", "rules": street_rules},
        ]},
    }


def village_settings(v):
    """The settings of the village that change with the variant: the start pool, the wall and the homes."""
    return {
        "start_pool": v.id("plaza"),
        "wall": {
            "margin": 4,
            "tower_spacing": 40,
            "tower": v.id("tower"),
            "gate": v.id("gate"),
            "processors": v.id("foundation"),
            "palisade": {
                "log": v.state("minecraft:spruce_log", LOG_UP),
                "base": v.state("minecraft:cobblestone"),
                "point": v.state("minecraft:spruce_fence"),
                "foundation": v.state("minecraft:cobblestone"),
            },
            "pier": {
                "deck": v.state("minecraft:spruce_planks"),
                "post": v.state("minecraft:spruce_log", LOG_UP),
                "rail": v.state("minecraft:spruce_fence"),
            },
        },
        "homes": v.id("homes"),
    }


def all_variants():
    """The variants of all the cultures, then the variants of the styles of the cultures."""
    return list(VARIANTS) + [v.of(culture) for culture in STYLES for v in VARIANTS]


def structure():
    """The village structure: the base variant, then the list of the other variants with their biomes. A variant of
    the style of a culture has the culture. The base variant of a style has no biome: it is the variant of the culture
    in the biomes of no other variant of the culture."""
    base = village_settings(BASE)
    variants = []
    for v in all_variants():
        if v.base and not v.culture:
            continue
        settings = village_settings(v)
        entry = {"biomes": [] if v.base else v.biomes, "start_pool": settings["start_pool"],
                 "wall": settings["wall"], "homes": settings["homes"]}
        if v.culture:
            entry["culture"] = v.culture
        variants.append(entry)
    return {
        "type": f"{NS}:fortified_village",
        "biomes": f"#{NS}:has_village",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": base["start_pool"],
        "size": 2,
        "max_distance_from_center": 64,
        "wall": base["wall"],
        "homes": base["homes"],
        "variants": variants,
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as out:
        out.write(json.dumps(data, indent=2) + "\n")
    print("wrote", os.path.normpath(path))


def main():
    for v in all_variants():
        folder = os.path.join(DATA, "structure", *v.path("").split("/"))
        os.makedirs(folder, exist_ok=True)
        style = STYLES.get(v.culture, {})
        for name, build in {**PIECES, **style}.items():
            path = os.path.join(folder, name + ".nbt")
            template = build()
            connect_panes(template)
            v.apply(template).write(path)
            print("wrote", os.path.normpath(path))
        for name, data in pools(v).items():
            write_json(os.path.join(WORLDGEN, "template_pool", *v.path(name).split("/")) + ".json", data)
        for name, data in processor_lists(v).items():
            write_json(os.path.join(WORLDGEN, "processor_list", *v.path(name).split("/")) + ".json", data)
    write_json(os.path.join(WORLDGEN, "structure", "village.json"), structure())
    write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_village.json"),
               {"values": [biome for v in VARIANTS for biome in v.biomes]})
    os.makedirs(TEST_OUT, exist_ok=True)
    for name, build in TEST_PIECES.items():
        path = os.path.join(TEST_OUT, name + ".nbt")
        template = build()
        connect_panes(template)
        template.write(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
