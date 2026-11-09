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
- street.nbt, street_bank.nbt, street_jobs.nbt, street_end.nbt: the parts of a street. The paths follow the terrain. A
  segment has a plot on each side, and a connector for the end of the street. The end has a lamp post on each edge.
  The street to the south (street_jobs) has the plots of the first house-workshops (see first_jobs).
- house_*.nbt: houses for the plots. Each house has a porch row (z = 0) with its jigsaw connector.
- bank.nbt: the village bank. Its walls and its chests resist explosions.
- job_<id>.nbt: the building of a job (see JOBS), in the pool village/jobs. Each building has the job sign of its job.
  The pools village/jobs_food and village/jobs_start have the first house-workshops of the generation.
- market.nbt: the market square, in the pool village/market. It takes the plot of one house, and it has the market
  sign in front of the market post (see market).
- tower.nbt: a tower of the palisade, with the stair along the palisade. The outside of the template is to the west
  and to the north: the shape of a corner tower. The towers between the corners use the same template.
- gate.nbt: a gate of 3 blocks in the palisade. The outside of the template is to the north.
The palisade comes from code: it follows the terrain.

The compound of the GameTests (center.nbt) is a village in one piece: palisade, 4 corner towers, 2 gates, paths, a
plaza with the village heart and 4 house plots.

Conventions: y = 0 is the ground layer (foundations, floors, paths). y = 1 is the first layer above ground.
The compound gets a foundation of FOUNDATION layers under its ground layer (see with_foundation).
"""
import dataclasses
import functools
import json
import os
import random
import sys
from collections.abc import Callable

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
JOB_STREETS_POOL = f"{NS}:village/streets_jobs"
# The pools of the first house-workshops of a village (see first_jobs).
FOOD_JOBS_POOL = f"{NS}:village/jobs_food"
START_JOBS_POOL = f"{NS}:village/jobs_start"
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
    # The connectors of the streets, in the middle of each side. The street to the north has the plot of the bank, and
    # the street to the south has the plots of the first house-workshops. The jigsaw places the plots of the street to
    # the south first (placement priority): a large house of a next street (13 x 13 in Sunreach, a side long house in
    # Valeholm) does not take the space of a house-workshop.
    last = PLAZA - 1
    t.jigsaw(PLAZA_MID, 0, 0, "north_up", PLAZA_EXIT, STREET, BANK_STREETS_POOL, "minecraft:dirt_path")
    t.jigsaw(PLAZA_MID, 0, last, "south_up", PLAZA_EXIT, STREET, JOB_STREETS_POOL, "minecraft:dirt_path",
             placement_priority=1)
    t.jigsaw(0, 0, PLAZA_MID, "west_up", PLAZA_EXIT, STREET, FIRST_STREETS_POOL, "minecraft:dirt_path")
    t.jigsaw(last, 0, PLAZA_MID, "east_up", PLAZA_EXIT, STREET, FIRST_STREETS_POOL, "minecraft:dirt_path")
    return t


def street_path(t, rng, length):
    """The path of a street: 3 blocks wide, between two edges where the terrain stays."""
    for z in range(length):
        for x in range(1, STREET_WIDTH - 1):
            t.set(x, 0, z, path_block(rng))


def street(bank=False, jobs=False):
    """A segment of street along z, with a plot on each side and a connector for the end of the street.

    The segment follows the terrain (projection terrain_matching). The segment to the north has the plot of the bank
    on its west side. With jobs, the segment has the plots of the first house-workshops: the pool jobs_food on the
    west side and the pool jobs_start on the east side.
    """
    rng = random.Random(12 if bank else 13)
    t = Template(STREET_WIDTH, 4, STREET_LENGTH)
    street_path(t, rng, STREET_LENGTH)
    mid = STREET_WIDTH // 2
    t.jigsaw(mid, 0, 0, "north_up", STREET, "minecraft:empty", "minecraft:empty", "minecraft:dirt_path")
    t.jigsaw(mid, 0, STREET_LENGTH - 1, "south_up", STREET_NEXT, STREET, STREET_ENDS_POOL, "minecraft:dirt_path")
    # The plots are near the end of the segment: the houses of 2 streets side by side do not touch.
    plot_z = STREET_LENGTH - 1 - HOUSE_HALF_WIDTH
    west = BANK_POOL if bank else FOOD_JOBS_POOL if jobs else HOUSE_POOL
    t.jigsaw(0, 1, plot_z, "west_up", PLOT, HOUSE_FRONT, west, "minecraft:air")
    t.jigsaw(STREET_WIDTH - 1, 1, plot_z, "east_up", PLOT, HOUSE_FRONT, START_JOBS_POOL if jobs else HOUSE_POOL,
             "minecraft:air")
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


def house_shell(rng, wall_lower, wall_upper, sign, door=True, wide_front=False, depth=D):
    """Timber frame house: cobblestone base, frame of stripped spruce logs, gable roof over the porch.

    The building sign of the kind stands on the porch, next to the east porch post. With no sign kind, the porch has
    no sign. The body goes from z = 1 to z = depth - 1. A depth less than D leaves the back of the plot empty.
    """
    t = Template(W, H, D)
    t.fill(0, 1, 0, W - 1, H - 1, D - 1, "minecraft:air")
    # Porch and floor.
    for x in range(W):
        t.set(x, 0, 0, "minecraft:cobblestone" if x in (0, W - 1) else "minecraft:spruce_planks")
        for z in range(1, depth):
            edge = x in (0, W - 1) or z in (1, depth - 1)
            t.set(x, 0, z, cobble(rng) if edge else "minecraft:spruce_planks")
    # Walls.
    for x in range(W):
        for z in range(1, depth):
            if not (x in (0, W - 1) or z in (1, depth - 1)):
                continue
            corner = x in (0, W - 1) and z in (1, depth - 1)
            for y in range(1, 5):
                if corner or y == 4:
                    t.set(x, y, z, "minecraft:stripped_spruce_log",
                          LOG_Y if corner else {"axis": "x" if z in (1, depth - 1) else "z"})
                elif y == 1:
                    t.set(x, y, z, wall_lower(rng))
                else:
                    t.set(x, y, z, wall_upper(rng))
    # Windows.
    for z in (3, 6):
        if z < depth - 1:
            t.set(0, 2, z, "minecraft:glass_pane")
            t.set(W - 1, 2, z, "minecraft:glass_pane")
    for x in (2, 6):
        t.set(x, 2, depth - 1, "minecraft:glass_pane")
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
        for z in range(depth):
            if left < right:
                t.set(left, y, z, "minecraft:dark_oak_stairs", stairs("east"))
                t.set(right, y, z, "minecraft:dark_oak_stairs", stairs("west"))
            elif left == right:
                t.set(left, y, z, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
        # Gable walls under the roof, at the front and the back of the body.
        for x in range(left + 1, right):
            for z in (1, depth - 1):
                t.set(x, y, z, wall_upper(rng))
    if sign:
        building_sign(t, W - 2, 1, 0, sign)
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def calcite(_rng):
    return "minecraft:calcite"


def lantern(t, x, y, z):
    t.set(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def bed(t, x, z_foot, color="blue", facing="south"):
    """A bed with the foot at (x, z_foot) and the head 1 block in the direction of facing: toward the back wall for
    "south", toward the east wall for "east"."""
    dx, dz = SIDES[facing]
    t.set(x, 1, z_foot, f"minecraft:{color}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
    t.set(x + dx, 1, z_foot + dz, f"minecraft:{color}_bed", {"facing": facing, "part": "head", "occupied": "false"})


def cottage():
    rng = random.Random(2)
    t = house_shell(rng, cobble, calcite, "house")
    # 3 beds: each bed gives a place to an inhabitant of the village. A free floor block between 2 beds lets a
    # villager walk to the head of each bed.
    for x in (1, 3, 5):
        bed(t, x, 6, "red")
    t.set(6, 1, 7, "minecraft:crafting_table")
    t.set(7, 1, 6, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"})
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


# The job sign of the blacksmith, on its house-workshop (the piece job_blacksmith).
BLACKSMITH = ("blacksmith", "minecraft:anvil")


def forge(home=False):
    """The forge: an open front, an anvil, a blast furnace, a furnace, a smithing table, a grindstone (the work block
    of the vanilla weaponsmith) and a water cauldron to cool the metal. The building sign of the forge stands next to
    the east porch post. The forge has no beds and no job sign: it is a building of the village, not a home with a job
    post.

    With home, the forge is the house-workshop of the blacksmith (the piece job_blacksmith): the building sign is the
    house sign, the job sign stands next to the west porch post, and 2 beds stand on the side walls (see home_beds)."""
    rng = random.Random(4)
    t = house_shell(rng, cobble, cobble, "house" if home else "forge", wide_front=True)
    if home:
        job_sign(t, 1, 1, 0, *BLACKSMITH)
    for y in range(1, 5):
        connect_fences(t, y)
    t.set(2, 1, 5, "minecraft:anvil", {"facing": "east"})
    t.set(5, 1, 7, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    t.set(6, 1, 7, "minecraft:furnace", {"facing": "north", "lit": "false"})
    t.set(7, 1, 7, "minecraft:smithing_table")
    t.set(1, 1, 7, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    t.set(7, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 2)
    if home:
        home_beds(t, (1, 4), (7, 4))
    return t


def storehouse():
    rng = random.Random(5)
    t = house_shell(rng, cobble, lambda r: "minecraft:spruce_planks", "storehouse")
    # Each barrel has a free floor block next to it: a villager can walk to it.
    for (x, z) in ((1, 7), (2, 6), (1, 6), (6, 6), (7, 6)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for (x, z) in ((6, 7), (7, 7), (5, 7)):
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


def long_house_shell(rng, sign, wall_lower, door=True, wide_front=False, depth=LD, hearth=True):
    """A deep Norse long house: a base of cobblestone, walls of horizontal spruce logs between posts of stripped spruce
    logs, a steep roof of dark oak with a ridge along the house, and carved horns at the ends of the ridge.

    The building sign of the kind stands on the porch, next to the east porch post. With hearth, a hearth of stone
    bricks is in the middle of the body, with a campfire that is not lit. The body goes from z = 1 to z = depth - 1. A
    depth less than LD leaves the back of the plot empty.

    The ridge is a row of planks with no slab on it, and the middle horn stands on these planks. A builder stands on a
    scaffolding in the house to place the ridge (see ConstructionPlanner). A slab on the ridge has no position to stand
    for a builder: a builder does not stand on the 1-block strips of the roof stairs.
    """
    t = Template(LW, LH, LD)
    t.fill(0, 1, 0, LW - 1, LH - 1, LD - 1, "minecraft:air")
    mid = LW // 2
    # Porch and floor.
    for x in range(LW):
        t.set(x, 0, 0, "minecraft:cobblestone" if x in (0, LW - 1) else "minecraft:spruce_planks")
        for z in range(1, depth):
            edge = x in (0, LW - 1) or z in (1, depth - 1)
            t.set(x, 0, z, cobble(rng) if edge else "minecraft:spruce_planks")
    # Walls: posts at the corners and every 4 blocks along the sides, logs between them.
    for x in range(LW):
        for z in range(1, depth):
            side = x in (0, LW - 1)
            end = z in (1, depth - 1)
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
        if z < depth - 1:
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
        for z in range(depth):
            t.set(x, top - 1, z, "minecraft:dark_oak_planks")
            if x != mid:
                t.set(x, top, z, "minecraft:dark_oak_stairs", stairs("east" if x < mid else "west"))
        # Gable walls under the roof, at the front and the back of the body.
        if 0 < x < LW - 1:
            for y in range(LONG_WALL_TOP + 1, top - 1):
                for z in (1, depth - 1):
                    t.set(x, y, z, "minecraft:spruce_planks")
    # Carved horns at the 2 ends of the ridge.
    for z in (0, depth - 1):
        for x in (mid - 1, mid + 1):
            t.set(x, steep_roof_top(x, LW) + 1, z, "minecraft:spruce_fence")
            t.set(x, steep_roof_top(x, LW) + 2, z, "minecraft:spruce_fence")
        t.set(mid, steep_roof_top(mid, LW), z, "minecraft:spruce_fence")
    # Long hearth in the middle of the body, and a lantern under the ridge.
    middle = depth // 2
    if hearth:
        for z in range(middle - 2, middle + 3):
            t.set(mid, 0, z, "minecraft:stone_bricks")
        t.set(mid, 1, middle, *unlit_campfire())
    t.set(mid, steep_roof_top(mid, LW) - 2, middle + 4 if hearth else middle, "minecraft:lantern",
          {"hanging": "true", "waterlogged": "false"})
    building_sign(t, LW - 2, 1, 0, sign)
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def side_house_shell(rng, sign, wall_lower):
    """A side Norse long house: the same walls and roof as a deep long house, with the ridge along the street and the
    door in the middle of the long side. A small porch with 2 posts is in front of the door. The hearth is in the
    middle of the body, along the ridge. The ridge is a row of planks with no slab on it, as on a deep long house.
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
            if z != ridge:
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
        t.set(x, steep_roof_top(ridge - 1, SD - 1), ridge, "minecraft:spruce_fence")
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
    # 3 beds along the back wall, with a free floor block next to the head of each bed.
    for x in (2, 4, SL - 3):
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
    for y in range(1, LONG_WALL_TOP + 1):
        connect_fences(t, y)
    forge_tools(t, LW, LD)
    return t


def forge_tools(t, width, depth):
    """The tools of a forge in a house of width x depth blocks with an open front: an anvil, a blast furnace, a
    furnace, a smithing table, a grindstone (the work block of the vanilla weaponsmith), a water cauldron and a
    lantern. The Valeholm forge (norse_forge) and the blacksmith of the styles (style_smithy) use these tools. The
    places of the beds of a house-workshop (the beds of WorkshopStyle) stay free."""
    t.set(2, 1, 4, "minecraft:anvil", {"facing": "east"})
    t.set(width - 2, 1, depth - 3, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.set(width - 2, 1, depth - 4, "minecraft:furnace", {"facing": "west", "lit": "false"})
    t.set(1, 1, depth - 3, "minecraft:smithing_table")
    t.set(1, 1, depth - 4, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    t.set(width - 2, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 7)


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


def norse_street(bank=False, jobs=False):
    """A Norse street along z: a segment of NORSE_STREET_LENGTH blocks with 2 plots on each side (NORSE_PLOTS), for
    the long houses. The segment to the north has the plot of the bank on its west side, near the plaza. With jobs,
    the segment has the plots of the first house-workshops: near the plaza, the pool jobs_food on the west side and the
    pool jobs_start on the east side; far from the plaza, the pool jobs_start on the west side."""
    rng = random.Random(32 if bank else 33)
    t = Template(STREET_WIDTH, 4, NORSE_STREET_LENGTH)
    street_path(t, rng, NORSE_STREET_LENGTH)
    mid = STREET_WIDTH // 2
    t.jigsaw(mid, 0, 0, "north_up", STREET, "minecraft:empty", "minecraft:empty", "minecraft:dirt_path")
    t.jigsaw(mid, 0, NORSE_STREET_LENGTH - 1, "south_up", STREET_NEXT, STREET, STREET_ENDS_POOL, "minecraft:dirt_path")
    # The jigsaw fills the plots of the house-workshops first (selection priority): a side long house on the other
    # plot of the street does not take their space.
    for i, z in enumerate(NORSE_PLOTS):
        if jobs:
            west, east = (FOOD_JOBS_POOL, START_JOBS_POOL) if i == 0 else (START_JOBS_POOL, HOUSE_POOL)
        else:
            west, east = BANK_POOL if bank and i == 0 else HOUSE_POOL, HOUSE_POOL
        t.jigsaw(0, 1, z, "west_up", PLOT, HOUSE_FRONT, west, "minecraft:air",
                 selection_priority=int(jobs and west != HOUSE_POOL))
        t.jigsaw(STREET_WIDTH - 1, 1, z, "east_up", PLOT, HOUSE_FRONT, east, "minecraft:air",
                 selection_priority=int(jobs and east != HOUSE_POOL))
    lamp_post(t, STREET_WIDTH - 1, NORSE_PLOTS[0] + 5)
    lamp_post(t, 0, NORSE_STREET_LENGTH - 1)
    return t

# ---------------------------------------------------------------- Sunreach style (desert and Mediterranean)

# A flat house: the door faces the street (z = 0 is the porch with its awning).
FW = 9    # x: 0..8
FD = 11   # z: 0 = porch, 1..10 = body
# A courtyard house: rooms around a courtyard open to the sky.
CW = 13   # x: 0..12
CD = 13   # z: 0 = porch, 1..12 = body
FH = 7
# The walls go up to this layer. The flat roof is the layer above it, and the parapet the layer above the roof.
FLAT_WALL_TOP = 3


def awning(t, mid):
    """A striped awning of wool over the porch, on 2 posts."""
    for x in range(mid - 2, mid + 3):
        t.set(x, FLAT_WALL_TOP, 0, "minecraft:orange_wool" if (x - mid) % 2 == 0 else "minecraft:white_wool")
    for x in (mid - 2, mid + 2):
        for y in range(1, FLAT_WALL_TOP):
            t.set(x, y, 0, "minecraft:spruce_fence")


def flat_house_shell(rng, sign, width, depth, wide_front=False, courtyard=False):
    """A Sunreach house: walls of smooth sandstone with pillars of cut sandstone at the corners, a band of terracotta,
    a flat roof with a parapet, small windows with iron bars and a striped awning over the door.

    With a courtyard, the rooms go around a courtyard open to the sky, with a basin in the middle: the inner walls have
    an opening on each side.
    """
    t = Template(width, FH, depth)
    t.fill(0, 1, 1, width - 1, FH - 1, depth - 1, "minecraft:air")
    mid = width // 2
    # Porch.
    for x in range(mid - 2, mid + 3):
        t.set(x, 0, 0, "minecraft:smooth_sandstone")
        for y in range(1, FLAT_WALL_TOP + 1):
            t.set(x, y, 0, "minecraft:air")
    # Floor.
    for x in range(width):
        for z in range(1, depth):
            edge = x in (0, width - 1) or z in (1, depth - 1)
            t.set(x, 0, z, "minecraft:sandstone" if edge else "minecraft:smooth_sandstone")
    # Outer walls.
    for x in range(width):
        for z in range(1, depth):
            if not (x in (0, width - 1) or z in (1, depth - 1)):
                continue
            corner = x in (0, width - 1) and z in (1, depth - 1)
            for y in range(1, FLAT_WALL_TOP + 1):
                if corner:
                    t.set(x, y, z, "minecraft:cut_sandstone")
                elif y == FLAT_WALL_TOP:
                    t.set(x, y, z, "minecraft:terracotta")
                else:
                    t.set(x, y, z, "minecraft:smooth_sandstone")
    # Courtyard: inner walls with an opening on each side, and a basin.
    inner = None
    if courtyard:
        inner = (4, 5, width - 5, depth - 5)   # min x, min z, max x, max z of the open court: rooms of 2 blocks
        x0, z0, x1, z1 = inner
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                wall = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
                if not wall:
                    continue
                opening = x == (x0 + x1) // 2 or z == (z0 + z1) // 2
                for y in range(1, FLAT_WALL_TOP + 1):
                    if opening and y < FLAT_WALL_TOP:
                        continue
                    t.set(x, y, z, "minecraft:terracotta" if y == FLAT_WALL_TOP else "minecraft:smooth_sandstone")
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, 0, z, "minecraft:cut_sandstone")
        t.set(cx, 0, cz, "minecraft:smooth_sandstone")
        t.set(cx, 1, cz, "minecraft:water_cauldron", {"level": "3"})
        for x, z in ((x0, z0), (x1, z1)):
            t.set(x, 1, z, "minecraft:potted_cactus")
    # Windows with iron bars.
    for z in range(3, depth - 2, 4):
        for x in (0, width - 1):
            t.set(x, 2, z, "minecraft:iron_bars")
    for x in (2, width - 3):
        t.set(x, 2, depth - 1, "minecraft:iron_bars")
    # Front opening.
    if wide_front:
        for x in (mid - 1, mid, mid + 1):
            for y in (1, 2):
                t.set(x, y, 1, "minecraft:air")
    else:
        t.set(mid, 1, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
        t.set(mid, 2, 1, "minecraft:spruce_door",
              {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    awning(t, mid)
    # Flat roof and parapet. The courtyard stays open.
    for x in range(width):
        for z in range(1, depth):
            if inner and inner[0] <= x <= inner[2] and inner[1] <= z <= inner[3]:
                continue
            t.set(x, FLAT_WALL_TOP + 1, z, "minecraft:smooth_sandstone")
            if x in (0, width - 1) or z in (1, depth - 1):
                if (x + z) % 2 == 0:
                    t.set(x, FLAT_WALL_TOP + 2, z, "minecraft:sandstone_wall")
                else:
                    t.set(x, FLAT_WALL_TOP + 2, z, "minecraft:smooth_sandstone_slab",
                          {"type": "bottom", "waterlogged": "false"})
    # Lights: on the front wall, next to the door. An open front has no wall there: the torches go outside the awning.
    for x in ((mid - 3, mid + 3) if wide_front else (mid - 1, mid + 1)):
        t.set(x, 2, 0, *wall_torch("north"))
    building_sign(t, mid + 1, 1, 0, sign)
    # Jigsaw connector in the porch, facing out (north).
    t.jigsaw(mid, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


def sun_cottage():
    rng = random.Random(42)
    t = flat_house_shell(rng, "house", FW, FD)
    # 3 beds: each bed gives a place to an inhabitant of the village. A free floor block next to the head of each bed
    # lets a villager walk to it.
    for x in (1, 3, FW - 2):
        bed(t, x, FD - 3, "orange")
    t.set(1, 1, 2, "minecraft:crafting_table")
    t.set(FW - 2, 1, 2, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"})
    t.set(1, 1, 5, "minecraft:decorated_pot", {"cracked": "false", "facing": "east", "waterlogged": "false"})
    t.set(FW - 2, 1, 5, "minecraft:flower_pot")
    lantern(t, 1, 1, 4)
    lantern(t, FW - 2, 1, 6)
    return t


def sun_cottage_courtyard():
    rng = random.Random(43)
    t = flat_house_shell(rng, "house", CW, CD, courtyard=True)
    # 3 beds in the back rooms, with a free floor block next to the head of each bed.
    for x in (1, 3, CW - 2):
        bed(t, x, CD - 3, "orange")
    t.set(1, 1, 2, "minecraft:crafting_table")
    t.set(CW - 2, 1, 2, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"})
    t.set(1, 1, 6, "minecraft:decorated_pot", {"cracked": "false", "facing": "east", "waterlogged": "false"})
    lantern(t, 1, 1, 4)
    lantern(t, CW - 2, 1, 6)
    return t


def sun_barracks():
    rng = random.Random(44)
    t = flat_house_shell(rng, "barracks", CW, CD, courtyard=True)
    # A free floor block next to the head of each bed lets a villager walk to it.
    for x in (1, 3, CW - 4, CW - 2):
        bed(t, x, CD - 3, "red")
    t.set(1, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(CW - 2, 1, 2, BANNER, {"rotation": "4"})
    t.set(1, 1, 6, "minecraft:barrel", {"facing": "up", "open": "false"})
    lantern(t, 1, 1, 4)
    lantern(t, CW - 2, 1, 6)
    return t


def sun_forge():
    rng = random.Random(45)
    t = flat_house_shell(rng, "forge", FW, FD, wide_front=True)
    for y in range(1, FLAT_WALL_TOP):
        connect_fences(t, y)
    t.set(2, 1, 4, "minecraft:anvil", {"facing": "east"})
    t.set(FW - 2, 1, FD - 2, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.set(FW - 2, 1, FD - 3, "minecraft:furnace", {"facing": "west", "lit": "false"})
    t.set(1, 1, FD - 2, "minecraft:smithing_table")
    t.set(1, 1, FD - 3, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    t.set(FW - 2, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 6)
    return t


def sun_storehouse():
    rng = random.Random(46)
    t = flat_house_shell(rng, "storehouse", CW, CD, courtyard=True)
    for x in range(1, 5):
        t.set(x, 1, CD - 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    for x in range(CW - 5, CW - 1):
        t.set(x, 1, CD - 2, "minecraft:decorated_pot", {"cracked": "false", "facing": "north", "waterlogged": "false"})
    for z in (2, 3):
        t.set(1, 1, z, "minecraft:hay_block", LOG_Y)
    t.set(CW - 2, 1, 2, "minecraft:composter", {"level": "0"})
    lantern(t, 1, 1, 5)
    return t

# ---------------------------------------------------------------- maritime pieces

def weathered(rng):
    """The lower wall of a maritime building: mossy cobblestone and stone. The processor of the maritime variants
    replaces the stone by the mossy stone of Immersive Weathering, when the mod is present."""
    return "minecraft:mossy_cobblestone" if rng.random() < 0.6 else "minecraft:stone"


def planks(_rng):
    return "minecraft:spruce_planks"


def stone_base(t):
    """A row of stone slabs on the porch, between the posts: the quay of a maritime building."""
    for x in range(1, W - 1):
        if t.get(x, 0, 0) == "minecraft:spruce_planks":
            t.set(x, 0, 0, "minecraft:stone_slab", {"type": "double", "waterlogged": "false"})


def iron_windows(t):
    """Iron bars in the side windows: the processor of the maritime variants rusts them, when Immersive Weathering is
    present."""
    for z in (3, 6):
        t.set(0, 2, z, "minecraft:iron_bars")
        t.set(W - 1, 2, z, "minecraft:iron_bars")


def fishing_hut():
    """The home of a family of fishers: 2 beds, the barrels of the catch, a smoker and the nets on the walls. The job sign
    of the fisher stands next to the west porch post: the hut is also the building of the fisher."""
    rng = random.Random(21)
    t = house_shell(rng, weathered, planks, "house")
    iron_windows(t)
    # A free floor block between the 2 beds lets a villager walk to the head of each bed.
    for x in (1, 3):
        bed(t, x, 6, "cyan")
    t.set(7, 1, 7, "minecraft:smoker", {"facing": "north", "lit": "false"})
    t.set(6, 1, 7, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(7, 1, 5, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(7, 1, 2, "minecraft:water_cauldron", {"level": "3"})
    # The nets: cobwebs on the back wall, over the 2 beds and the free floor block between them.
    for x in (1, 2, 3):
        t.set(x, 3, 7, "minecraft:cobweb")
    lantern(t, 4, 1, 7)
    lantern(t, 1, 1, 2)
    stone_base(t)
    job_sign(t, 1, 1, 0, "fisher", "minecraft:cod")
    return t


def fish_market():
    """The fish market: an open hall with the stalls of the catch."""
    rng = random.Random(22)
    t = house_shell(rng, weathered, planks, "market", wide_front=True)
    iron_windows(t)
    # The stalls: a counter of slabs, with the barrels and the water tanks of the catch.
    for x in (1, 2, 6, 7):
        t.set(x, 1, 4, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
    for x in (1, 7):
        t.set(x, 1, 5, "minecraft:water_cauldron", {"level": "3"})
    for (x, z) in ((1, 7), (2, 7), (6, 7), (7, 7)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(4, 1, 7, "minecraft:smoker", {"facing": "north", "lit": "false"})
    t.set(2, 1, 5, "minecraft:blue_carpet")
    t.set(6, 1, 5, "minecraft:blue_carpet")
    lantern(t, 4, 1, 4)
    stone_base(t)
    return t


def smokehouse():
    """The smokehouse: the smokers and the racks of the fish of the village."""
    rng = random.Random(23)
    t = house_shell(rng, weathered, weathered, "storehouse")
    iron_windows(t)
    for x in (2, 4, 6):
        t.set(x, 1, 7, "minecraft:smoker", {"facing": "north", "lit": "false"})
    # The racks: chains from the roof beam, over the smokers.
    for x in (2, 4, 6):
        t.set(x, 3, 6, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    # The barrels in the back corners have a free floor block in front of them: a villager can walk to them.
    for (x, z) in ((1, 7), (7, 7), (1, 5), (7, 5)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(1, 1, 2, "minecraft:composter", {"level": "0"})
    lantern(t, 4, 1, 3)
    stone_base(t)
    return t

# ---------------------------------------------------------------- job buildings

JOB_SIGN = f"{BLOCKS}:job_sign"
# A job building: a shed on the street (z = 0 is the porch, z = 1..SHED_DEPTH - 1 is the body) and a garden behind it,
# with a fence around the garden.
SHED_DEPTH = 5
GARDEN_Z = range(SHED_DEPTH, D - 1)
GARDEN_X = range(1, W - 1)
# The water of a field, in the middle of the garden.
WATER = (W // 2, SHED_DEPTH + 1)
GRASS = ("minecraft:grass_block", {"snowy": "false"})
FARMLAND = ("minecraft:farmland", {"moisture": "7"})
# The direction of a cocoa pod to its log, for a pod on each side of the log.
TOWARD = {"north": "south", "south": "north", "west": "east", "east": "west"}


# The beds of a house-workshop: each job building is the home of a household that does the job. Each job building has
# HOME_BEDS beds, and each bed gives a place to an inhabitant of the village (see VillageBeds).
HOME_BEDS = 2
HOME_BED_COLOR = "brown"


def home_beds(t, *feet, facing="south"):
    """The HOME_BEDS beds of a house-workshop: the foot (x, z) of each bed, and the direction of its head. A foot
    (x, z, facing) gives the direction of its bed. A bed stands next to a free floor block, so that an inhabitant can
    go to it."""
    assert len(feet) == HOME_BEDS, feet
    for foot in feet:
        bed(t, foot[0], foot[1], HOME_BED_COLOR, foot[2] if len(foot) > 2 else facing)


def job_sign(t, x, y, z, job, icon, facing="north"):
    """The job sign of a building: the job id (the name of the job file) and the icon item (see JobSignBlockEntity)."""
    t.set(x, y, z, JOB_SIGN, {"facing": facing}, {"id": JOB_SIGN, "job": job, "icon": icon})


def block(entry):
    """The name and the properties of a block: a name alone, or a pair (name, properties)."""
    return entry if isinstance(entry, tuple) else (entry, None)


def job_building(rng, job, icon, ground, plants, work=("minecraft:composter", {"level": "0"})):
    """The house-workshop of a job of the fields and the gardens: a timber frame shed and a garden behind it.

    - The shed: the house shell (see house_shell) with a body of SHED_DEPTH - 1 rows, a door on the street and a back
      door to the garden. The job sign stands on the porch next to the west porch post, and the house sign next to the
      east porch post.
    - The work blocks: the work block of the vanilla profession of the job (a composter for the farmer) in the west
      half of the shed, and a barrel next to the door.
    - The home: 2 beds side by side in the east half of the shed, with the heads to the east. The path from the door to
      the back door (x = 4) goes along the feet of the beds.
    - The garden: ground(x, z) gives the ground block of each position, and plants(x, z) gives the block above it, or
      None. A fence goes around the garden, with a lantern on each back corner.
    """
    t = house_shell(rng, cobble, planks, "house", depth=SHED_DEPTH)
    mid = W // 2
    back = SHED_DEPTH - 1
    for y, half in ((1, "lower"), (2, "upper")):
        t.set(mid, y, back, "minecraft:spruce_door",
              {"facing": "south", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    t.set(mid, 3, back, "minecraft:stripped_spruce_log", {"axis": "x"})
    t.set(1, 1, back - 1, *work)
    t.set(mid - 1, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    # The lantern stands on the barrel: the head of each bed has a free floor block next to it.
    lantern(t, mid - 1, 2, 2)
    home_beds(t, (mid + 1, 2), (mid + 1, 3), facing="east")
    job_sign(t, 1, 1, 0, job, icon)
    for x in range(W):
        for z in range(SHED_DEPTH, D):
            if x in GARDEN_X and z in GARDEN_Z:
                t.set(x, 0, z, *block(ground(x, z)))
                plant = plants(x, z)
                if plant:
                    t.set(x, 1, z, *block(plant))
            else:
                t.set(x, 0, z, cobble(rng))
                t.set(x, 1, z, "minecraft:spruce_fence")
    for x in (0, W - 1):
        lantern(t, x, 2, D - 1)
    # The fences of the garden and the porch posts.
    for y in range(1, 5):
        connect_fences(t, y)
    return t


# The full blocks that a fence connects to: the blocks of the shell, and the stone and the earth of the job buildings.
# A fence also connects to a fence, and not to a plant or a fruit.
FENCE_SUPPORTS = ("cobblestone", "_log", "_planks", "_ore", ":stone", ":andesite", ":sand", ":gravel", ":clay",
                  ":dirt", "sandstone")


def connect_fences(t, y):
    """Sets the north, south, east and west properties of each spruce fence of a layer from its neighbors: a fence,
    a fence gate or a block of the shell."""
    for (x, fy, z), ((name, _props), nbt) in list(t.blocks.items()):
        if fy != y or name != "minecraft:spruce_fence":
            continue
        props = {"waterlogged": "false"}
        for side, (dx, dz) in SIDES.items():
            other = t.get(x + dx, y, z + dz) if t.inside(x + dx, y, z + dz) else None
            connects = other is not None and other.endswith(("_fence", "_fence_gate") + FENCE_SUPPORTS)
            props[side] = "true" if connects else "false"
        t.set(x, y, z, name, props, nbt)


# The blocks over a wall that raise its post (the block tag minecraft:wall_post_override).
WALL_POST_OVERRIDE = ("torch", "_sign", "_banner", "pressure_plate", "tripwire")


def covers(above, side=None):
    """True if the bottom of the block above a wall covers a side of the wall (side), or its post (no side), as in the
    game (WallBlock). above is the name and the properties of the block above the wall. The bottom of a carpet is a
    full face: a carpet covers the sides and the post. A block of wool (the awning of a Sunreach house) covers them
    too."""
    name, props = above
    if name.endswith("_wall"):
        return props[side] != "none" if side else props["up"] == "true" or any(props[s] != "none" for s in SIDES)
    if name.endswith("_slab") or name.endswith("_stairs"):
        return props.get("type", props.get("half")) == "bottom"
    if name == "minecraft:lantern" and props.get("hanging") == "false":
        return side is None
    if name.endswith(("_carpet", "_wool")):
        return True
    return name.endswith(FENCE_SUPPORTS)


def connect_walls(t):
    """Sets the sides and the post of each wall from its neighbors, as the game does (WallBlock).

    - A wall connects to a wall, a pane, a fence gate across the side, and a full block (FENCE_SUPPORTS).
    - A side is tall when the block above the wall covers it, else low.
    - The post goes up at an end, at a corner, under a raised post, under a torch, a sign or a banner, and under a
      block that covers the post. The post of a straight wall with 2 tall sides does not go up.

    The walls go from the top to the bottom: the shape of a wall depends on the wall above it.
    """
    walls = sorted((pos for pos, ((name, _), _) in t.blocks.items() if name.endswith("_wall")), key=lambda p: -p[1])
    for x, y, z in walls:
        (name, _), nbt = t.blocks[(x, y, z)]
        entry = t.blocks.get((x, y + 1, z))
        above = (entry[0][0], dict(entry[0][1])) if entry else ("minecraft:air", {})
        props = {}
        for side, (dx, dz) in SIDES.items():
            entry = t.blocks.get((x + dx, y, z + dz))
            other, other_props = (entry[0][0], dict(entry[0][1])) if entry else ("minecraft:air", {})
            across = other.endswith("_fence_gate") and (other_props["facing"] in ("north", "south")) == bool(dx)
            connects = other.endswith(("_wall",) + FENCE_SUPPORTS) or other in PANES or across
            props[side] = ("tall" if covers(above, side) else "low") if connects else "none"
        none = {side: props[side] == "none" for side in SIDES}
        if above[0].endswith("_wall") and above[1]["up"] == "true":
            up = True
        elif all(none.values()) or none["north"] != none["south"] or none["east"] != none["west"]:
            up = True
        elif props["north"] == props["south"] == "tall" or props["east"] == props["west"] == "tall":
            up = False
        else:
            up = above[0].startswith("minecraft:") and above[0].endswith(WALL_POST_OVERRIDE) or covers(above)
        t.set(x, y, z, name, {**props, "up": "true" if up else "false", "waterlogged": "false"}, nbt)


def shaped(t):
    """The template with the shapes of the game: each fence connects to its neighbors (see connect_fences), and each
    wall of a biome variant gets its shape after the block replacement (see connect_walls). Thus the fences of a pen
    are walls with no gaps in the desert."""
    for y in range(1, t.size[1]):
        connect_fences(t, y)
    t.shape_walls = True
    return t


# The water of a field: a waterlogged slab. The slab holds the water on uneven ground, and the foundation goes under
# it. Farmland and sugar cane get their water from the slab.
WATER_SLAB = ("minecraft:spruce_slab", {"type": "bottom", "waterlogged": "true"})


def field_ground(x, z, water=WATER):
    """Farmland around the water of the field, and a path of coarse dirt from the back door to the water: a builder
    stands on the path to add the water."""
    if (x, z) == water:
        return WATER_SLAB
    return "minecraft:coarse_dirt" if x == water[0] else FARMLAND


def crop(name, rng, top=7):
    """A crop of a random age, near the end of its growth."""
    return name, {"age": str(rng.randint(top - 3, top))}


def farm():
    """The building of the farmer: a field of wheat, carrots, potatoes and beetroots around the water, and hay bales
    in the shed."""
    rng = random.Random(51)
    rows = {1: "wheat", 2: "wheat", 3: "carrots", 5: "potatoes", 6: "beetroots", 7: "beetroots"}

    def plants(x, z):
        if x == WATER[0]:
            return None
        return crop(f"minecraft:{rows[x]}", rng, 3 if rows[x] == "beetroots" else 7)

    t = job_building(rng, "farmer", "minecraft:wheat", field_ground, plants)
    for (x, y) in ((1, 1), (2, 1), (1, 2)):
        t.set(x, y, 2, "minecraft:hay_block", LOG_Y)
    return t


def garden():
    """The building of the gardener: pumpkins and melons on their stems, sugar cane next to the water, and sweet
    berry bushes on the side edges, out of the path.

    The sugar cane is 1 block high, with no block next to it: a builder places it before the water, and no later
    block next to it makes it check its water.
    """
    rng = random.Random(52)
    mid, row = WATER
    stems = {(2, row - 1), (2, row + 1), (W - 3, row - 1), (W - 3, row + 1)}

    def ground(x, z):
        if x == mid:
            return field_ground(x, z)
        # A fruit on farmland makes the farmland dirt: the fruits stand on grass.
        return FARMLAND if (x, z) in stems else GRASS

    def plants(x, z):
        side = x - mid
        fruit = "pumpkin" if side < 0 else "melon"
        if z == row:
            if abs(side) == 3:
                return "minecraft:sweet_berry_bush", {"age": "3"}
            if abs(side) == 1:
                return "minecraft:sugar_cane", {"age": "0"}
            return None
        if abs(side) == 3:
            return f"minecraft:{fruit}"
        if abs(side) == 2:
            return f"minecraft:attached_{fruit}_stem", {"facing": "west" if side < 0 else "east"}
        return None

    t = job_building(rng, "gardener", "minecraft:pumpkin", ground, plants)
    t.set(1, 1, 2, "minecraft:pumpkin")
    t.set(2, 1, 2, "minecraft:melon")
    return t


def jungle_garden():
    """The building of the gardener of the jungle: cocoa pods on 2 posts of jungle logs, melons and ferns."""
    rng = random.Random(53)
    mid = W // 2
    posts = ((2, WATER[1]), (W - 3, WATER[1]))

    def ground(x, z):
        if (x, z) == WATER:
            return WATER_SLAB
        return FARMLAND if x == mid else GRASS

    def plants(x, z):
        if (x, z) == WATER:
            return None
        if (x, z) in posts:
            return "minecraft:jungle_log", LOG_Y
        if x == mid:
            return "minecraft:attached_melon_stem", {"facing": "west" if z < WATER[1] else "east"}
        if (x - mid, z - WATER[1]) in ((-1, -1), (1, 1)):
            return "minecraft:melon"
        if abs(x - mid) == 2:
            return None
        return "minecraft:fern" if rng.random() < 0.5 else None

    t = job_building(rng, "gardener_jungle", "minecraft:cocoa_beans", ground, plants)
    # The posts: 3 jungle logs high, with a cocoa pod on each side. A pod faces its log.
    for px, pz in posts:
        for y in (2, 3):
            t.set(px, y, pz, "minecraft:jungle_log", LOG_Y)
        for side, (dx, dz) in SIDES.items():
            t.set(px + dx, 2, pz + dz, "minecraft:cocoa", {"age": str(rng.randint(1, 2)), "facing": TOWARD[side]})
    t.set(1, 1, 2, "minecraft:melon")
    return t


FLOWERS = ("minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower")


def forage_ground(x, z, width=W):
    """The ground of the yard of the forager in a building of this width: podzol in the west half, grass in the east
    half, and a path of coarse dirt in the middle."""
    if x == width // 2:
        return "minecraft:coarse_dirt"
    return "minecraft:podzol" if x < width // 2 else GRASS


def forage_plants(x, z, width=W):
    """The finds of the forager in a building of this width: mushrooms on the podzol and wild flowers on the grass."""
    if x == width // 2:
        return None
    if x < width // 2:
        return "minecraft:brown_mushroom" if (x + z) % 2 == 0 else "minecraft:red_mushroom"
    return FLOWERS[(x + z) % len(FLOWERS)]


def forager_hut():
    """The building of the forager: mushrooms on podzol, wild flowers on grass, and the finds in a barrel."""
    rng = random.Random(54)
    t = job_building(rng, "forager", "minecraft:red_mushroom", forage_ground, forage_plants)
    t.set(1, 1, 2, "minecraft:moss_block")
    t.set(2, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    return t


def apiary():
    """The building of the beekeeper: 2 beehives among the flowers. A lit campfire under each hive gives the smoke that
    calms the bees."""
    rng = random.Random(55)
    hives = ((2, WATER[1]), (W - 3, WATER[1]))
    flowers = ("minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower", "minecraft:oxeye_daisy",
               "minecraft:allium")

    def plants(x, z):
        if (x, z) in hives:
            return "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                          "waterlogged": "false"}
        return flowers[rng.randrange(len(flowers))] if (x + z) % 2 == 0 else None

    t = job_building(rng, "beekeeper", "minecraft:honeycomb", lambda x, z: GRASS, plants)
    for x, z in hives:
        t.set(x, 2, z, "minecraft:beehive", {"facing": "south", "honey_level": str(rng.randint(2, 5))})
    return t


def job_house(rng, job, icon, wall_upper, beds, wide_front=False):
    """The house-workshop of a job in a house: the house shell with a door on the street, or an open shop front. The
    job sign stands on the porch next to the west porch post, and the house sign next to the east porch post. beds
    gives the foot of each bed of the home (see home_beds): the head is 1 block to the back wall."""
    t = house_shell(rng, cobble, wall_upper, "house", wide_front=wide_front)
    home_beds(t, *beds)
    job_sign(t, 1, 1, 0, job, icon)
    for y in range(1, 5):
        connect_fences(t, y)
    return t


def oven(t, x):
    """A brick oven on the back wall: a furnace in bricks, with a chimney of bricks through the roof."""
    z = D - 2
    t.set(x, 1, z, "minecraft:furnace", {"facing": "north", "lit": "false"})
    for dx in (-1, 0, 1):
        t.set(x + dx, 2, z, "minecraft:bricks")
        if dx:
            t.set(x + dx, 1, z, "minecraft:bricks")
    for y in range(3, H):
        t.set(x + 1, y, z, "minecraft:bricks")


BARREL = ("minecraft:barrel", {"facing": "up", "open": "false"})
TOP_SLAB = ("minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})


def bakery():
    """The building of the baker: a brick oven, a crafting table to knead the dough, barrels of flour, the wheat on
    hay bales and a cake on the counter."""
    rng = random.Random(56)
    t = job_house(rng, "baker", "minecraft:bread", planks, ((1, 3), (7, 3)))
    oven(t, 4)
    t.set(1, 1, 7, "minecraft:crafting_table")
    for x, z in ((1, 5), (2, 7)):
        t.set(x, 1, z, *BARREL)
    for x, y, z in ((7, 1, 7), (7, 1, 6), (7, 2, 7)):
        t.set(x, y, z, "minecraft:hay_block", LOG_Y)
    t.set(7, 1, 2, *TOP_SLAB)
    t.set(7, 2, 2, "minecraft:cake", {"bites": "0"})
    lantern(t, 1, 1, 2)
    return t


def pastry_shop():
    """The building of the pastry cook: an open shop front, a counter with cakes, a brick oven, pumpkins and barrels
    of sugar."""
    rng = random.Random(57)
    t = job_house(rng, "pastry_cook", "minecraft:pumpkin_pie", calcite, ((2, 5), (6, 4)), wide_front=True)
    oven(t, 4)
    # The counter behind the open front, with a free passage in the middle.
    for x in (1, 2, 6, 7):
        t.set(x, 1, 3, *TOP_SLAB)
    for x in (2, 6):
        t.set(x, 2, 3, "minecraft:candle_cake", {"lit": "false"})
    for x, y, z in ((1, 1, 7), (2, 1, 7), (1, 2, 7)):
        t.set(x, y, z, "minecraft:pumpkin")
    for x, z in ((7, 7), (7, 6)):
        t.set(x, 1, z, *BARREL)
    lantern(t, 1, 1, 5)
    return t


def butchery():
    """The building of the butcher: 2 smokers (the work block of the vanilla butcher), a block of coal, a chopping
    block, and a rack of chains on a beam across the room. The chains leave 2 blocks of space under them."""
    rng = random.Random(58)
    t = job_house(rng, "butcher", "minecraft:cooked_beef", planks, ((1, 4), (7, 4)))
    for x in (5, 6):
        t.set(x, 1, 7, "minecraft:smoker", {"facing": "north", "lit": "false"})
    t.set(7, 1, 7, "minecraft:coal_block")
    for x in range(1, W - 1):
        t.set(x, 4, 5, "minecraft:stripped_spruce_log", {"axis": "x"})
    for x in (2, 3, 6):
        t.set(x, 3, 5, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    t.set(2, 1, 3, "minecraft:stripped_oak_log", LOG_Y)
    for x, z in ((1, 7), (1, 6)):
        t.set(x, 1, z, *BARREL)
    t.set(7, 1, 3, "minecraft:water_cauldron", {"level": "3"})
    lantern(t, 1, 1, 2)
    return t


def ranch():
    """The building of the rancher: a shed with a smoker (the work block of the vanilla butcher) and a pen behind it,
    with hay and a water trough. The doors of the shed and the fence keep the animals in the pen.

    The template has no animals: a template with entities adds them again at each placement (the village structure
    and each construction of the builders). The job produces its goods in the economy, and a player can bring
    animals into the pen.
    """
    rng = random.Random(59)

    def ground(x, z):
        return "minecraft:coarse_dirt" if (x, z) == (W // 2, SHED_DEPTH) else GRASS

    def plants(x, z):
        # The hay and the trough are not next to the fence: a fence connects to a full block.
        if (x, z) == (2, SHED_DEPTH + 1):
            return "minecraft:hay_block", LOG_Y
        if (x, z) == (W - 3, SHED_DEPTH + 1):
            return "minecraft:water_cauldron", {"level": "3"}
        return None

    t = job_building(rng, "rancher", "minecraft:lead", ground, plants,
                     work=("minecraft:smoker", {"facing": "east", "lit": "false"}))
    t.set(1, 1, 2, "minecraft:hay_block", LOG_Y)
    return t


def fisher_hut():
    """The building of the fisher: a shed with barrels (the work block of the vanilla fisherman), the nets and a
    tub, and a yard with a pond for the live catch. The pond is a row of waterlogged slabs, with a path of coarse
    dirt next to it: a builder stands on the path to add the water."""
    rng = random.Random(60)
    pond = range(2, W - 2)
    row = D - 2

    def ground(x, z):
        if z == row and x in pond:
            return WATER_SLAB
        return "minecraft:coarse_dirt" if z == row - 1 or x == W // 2 else GRASS

    def plants(x, z):
        return BARREL if (x, z) in ((2, SHED_DEPTH), (W - 3, SHED_DEPTH)) else None

    t = job_building(rng, "fisher", "minecraft:cod", ground, plants, work=BARREL)
    t.set(1, 1, 2, "minecraft:water_cauldron", {"level": "3"})
    # The nets: cobwebs high on the side wall, out of the way.
    for z in (2, 3):
        t.set(1, 3, z, "minecraft:cobweb")
    return t


# The woods of the jobs of the wood: the wood of each job id.
LUMBERJACKS = {"lumberjack": "oak", **{f"lumberjack_{wood}": wood for wood in (
    "acacia", "birch", "cherry", "dark_oak", "jungle", "mangrove", "spruce")}}
SAWYERS = {"sawyer": "oak", "sawyer_spruce": "spruce"}
# The potted sapling of each wood: a sapling in a pot does not grow into a tree.
POTTED = {"mangrove": "minecraft:potted_mangrove_propagule"}


def job_material(t, x, y, z, name, props=None):
    """A block of the material of the job. The biome variants do not change it: the lumberjack of spruce keeps its
    spruce logs in the plains, and the mason keeps its stone bricks in the desert."""
    t.set(x, y, z, name, props)
    t.fixed.add((x, y, z))


def lumber_yard(job, wood):
    """The building of a lumberjack: the shed with a grindstone for the axes and a potted sapling, and a yard with
    piles of logs of the wood of the job and a chopping block.

    All the lumberjacks have this shape. Only the wood of the logs, of the chopping block and of the sapling changes.
    The yard has no sapling in the ground: a sapling grows into a tree.
    """
    log = f"minecraft:{wood}_log"
    rng = random.Random(61)

    def ground(x, z):
        return GRASS if x in (1, 2, W - 2) else "minecraft:coarse_dirt"

    t = job_building(rng, job, log, ground, lambda x, z: None,
                     work=("minecraft:grindstone", {"face": "floor", "facing": "north"}))
    t.set(1, 1, 2, POTTED.get(wood, f"minecraft:potted_{wood}_sapling"))
    # The west pile: 2 rows of logs along the fence, the row on the fence 2 logs high.
    for z in GARDEN_Z:
        for x, top in ((1, 2), (2, 1)):
            for y in range(1, top + 1):
                job_material(t, x, y, z, log, {"axis": "z"})
    # The chopping block: a log on its end. The split wood: stripped logs in the back corner.
    job_material(t, W - 3, 1, WATER[1], log, LOG_Y)
    for x, y in ((W - 3, 1), (W - 2, 1), (W - 2, 2)):
        job_material(t, x, y, D - 2, f"minecraft:stripped_{wood}_log", {"axis": "x"})
    t.set(W - 2, 1, SHED_DEPTH, POTTED.get(wood, f"minecraft:potted_{wood}_sapling"))
    # The fences next to the piles connect to the logs.
    connect_fences(t, 1)
    return t


def sawmill(job, wood):
    """The building of a sawyer: a house with a saw bench (a stonecutter between the logs and the slabs), a pile of
    logs, a stack of planks, a crafting table and a barrel.

    All the sawyers have this shape. Only the wood of the logs, the planks and the slabs changes.
    """
    rng = random.Random(62)
    t = job_house(rng, job, f"minecraft:{wood}_planks", planks, ((2, 3), (6, 3)))
    bench = 5
    for x in (2, 3):
        job_material(t, x, 1, bench, f"minecraft:{wood}_log", {"axis": "x"})
    t.set(4, 1, bench, "minecraft:stonecutter", {"facing": "north"})
    for x in (5, 6):
        job_material(t, x, 1, bench, f"minecraft:{wood}_slab", {"type": "bottom", "waterlogged": "false"})
    # The logs to saw (west) and the planks (east) on the back wall. The middle of the room stays free.
    for x, y in ((1, 1), (2, 1), (3, 1), (1, 2), (2, 2)):
        job_material(t, x, y, D - 2, f"minecraft:{wood}_log", {"axis": "x"})
    for x, y, z in ((6, 1, D - 2), (7, 1, D - 2), (7, 2, D - 2), (7, 1, D - 3)):
        job_material(t, x, y, z, f"minecraft:{wood}_planks")
    t.set(1, 1, 3, "minecraft:crafting_table")
    t.set(W - 2, 1, 2, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 1, 3)
    return t


def mine():
    """The building of the miner: the shed with a smithing table (the work block of the vanilla toolsmith), and a
    yard with a rock face at the back. A timber frame in the rock face makes the mouth of the mine, with a lantern under
    the beam. A rail goes from the back door to the mouth. A pile of cobblestone stands next to the rail.

    The rock face has stone, andesite and ores. The cobblestone of the pile stays cobblestone in all the variants.
    """
    rng = random.Random(63)
    mid = W // 2

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == mid else "minecraft:gravel"

    def plants(x, z):
        return ("minecraft:rail", {"shape": "north_south", "waterlogged": "false"}) if x == mid else None

    t = job_building(rng, "miner", "minecraft:iron_pickaxe", ground, plants, work=("minecraft:smithing_table", None))
    rock = {1: ("minecraft:stone", "minecraft:andesite"), 2: ("minecraft:coal_ore", "minecraft:stone"),
            W - 3: ("minecraft:iron_ore", "minecraft:stone"), W - 2: ("minecraft:andesite", "minecraft:copper_ore")}
    for x, column in rock.items():
        for y, name in enumerate(column, 1):
            t.set(x, y, D - 2, name)
    # The mouth: 2 posts and a beam in the rock face, and the rock behind it in place of the back fence.
    for x in (mid - 1, mid + 1):
        for y in (1, 2):
            t.set(x, y, D - 2, "minecraft:spruce_log", LOG_Y)
    for x in (mid - 1, mid, mid + 1):
        t.set(x, 3, D - 2, "minecraft:spruce_log", {"axis": "x"})
        for y in (1, 2, 3):
            t.set(x, y, D - 1, "minecraft:stone")
    t.set(mid, 2, D - 2, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    for x, z in ((1, SHED_DEPTH), (2, SHED_DEPTH), (1, SHED_DEPTH + 1)):
        job_material(t, x, 1, z, "minecraft:cobblestone")
    t.set(1, 1, 2, *BARREL)
    connect_fences(t, 1)
    return t


def dig_yard():
    """The building of the digger: the shed with a stonecutter (the work block of the vanilla mason), and a yard with
    heaps of sand, gravel, clay and dirt along the fence. A path of coarse dirt goes from the back door to the back
    fence."""
    rng = random.Random(64)
    heaps = {(1, D - 2): "sand", (2, D - 2): "sand", (1, D - 3): "sand", (3, D - 2): "gravel",
             (W - 4, D - 2): "clay", (W - 3, D - 2): "dirt", (W - 2, D - 2): "dirt", (W - 2, D - 3): "gravel"}

    def plants(x, z):
        return f"minecraft:{heaps[(x, z)]}" if (x, z) in heaps else None

    t = job_building(rng, "digger", "minecraft:iron_shovel", lambda x, z: "minecraft:coarse_dirt", plants,
                     work=("minecraft:stonecutter", {"facing": "east"}))
    t.set(1, 2, D - 2, "minecraft:sand")
    t.set(W - 2, 2, D - 2, "minecraft:dirt")
    t.set(1, 1, 2, *BARREL)
    connect_fences(t, 1)
    return t


def stone_bricks(_rng):
    return "minecraft:stone_bricks"


def mason_workshop():
    """The building of the mason: a house of stone bricks with a stonecutter (the work block of the vanilla mason) and
    a block of stone to cut, a brick kiln on the back wall, and stacks of stone bricks, bricks, smooth stone and
    terracotta. The stacks of stone bricks stay stone bricks in all the variants."""
    rng = random.Random(65)
    t = job_house(rng, "mason", "minecraft:stone_bricks", stone_bricks, ((1, 4), (6, 4)))
    oven(t, 4)
    for x, y, z in ((1, 1, D - 2), (1, 2, D - 2), (2, 1, D - 2), (1, 1, D - 3)):
        job_material(t, x, y, z, "minecraft:stone_bricks")
    for x, y, z in ((W - 2, 1, D - 2), (W - 2, 2, D - 2)):
        t.set(x, y, z, "minecraft:bricks")
    t.set(W - 3, 1, D - 2, "minecraft:smooth_stone")
    t.set(W - 2, 1, D - 3, "minecraft:terracotta")
    t.set(2, 1, 4, "minecraft:stonecutter", {"facing": "east"})
    t.set(2, 1, 3, "minecraft:stone")
    t.set(W - 2, 1, 3, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 1, 5)
    return t


# The metals of the smelters: the raw blocks of the ores, and the weighted pressure plate that shows the ingots.
SMELTERS = {
    "smelter": ("minecraft:iron_ingot", ("minecraft:raw_iron_block", "minecraft:raw_copper_block"),
                "minecraft:heavy_weighted_pressure_plate"),
    "gold_smelter": ("minecraft:gold_ingot", ("minecraft:raw_gold_block", "minecraft:raw_gold_block"),
                     "minecraft:light_weighted_pressure_plate"),
}


def smeltery(job):
    """The building of a smelter: a brick house with 2 blast furnaces (the work block of the vanilla armorer) and a
    furnace in a brick oven, the raw ore and a block of coal, a water cauldron to cool the metal, and the ingots on a
    counter. The smelter also makes glass: its building has a barrel of sand.

    All the smelters have this shape. Only the ore and the ingots change: the weighted pressure plates show the
    ingots (iron for the smelter, gold for the smelter of gold).
    """
    icon, ores, plate = SMELTERS[job]
    rng = random.Random(66)
    t = job_house(rng, job, icon, lambda r: "minecraft:bricks", ((1, 3), (7, 4)))
    oven(t, 4)
    for x in (2, W - 3):
        t.set(x, 1, D - 2, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    for z, ore in zip((D - 2, D - 3), ores):
        t.set(1, 1, z, ore)
    t.set(1, 1, D - 4, "minecraft:coal_block")
    if job == "smelter":
        t.set(W - 2, 1, 2, *BARREL)
    t.set(W - 2, 1, D - 3, "minecraft:water_cauldron", {"level": "3"})
    for x in (W - 3, W - 2):
        t.set(x, 1, 3, *TOP_SLAB)
        t.set(x, 2, 3, plate, {"power": "0"})
    lantern(t, 1, 1, 2)
    return t


def tinker_workshop():
    """The building of the tinker: a house with a smithing table (the work block of the vanilla toolsmith), blocks of
    iron and copper, a crafting table, a short track of rails, and a counter with lanterns and a lightning rod (the
    goods of the job)."""
    rng = random.Random(67)
    t = job_house(rng, "tinker", "minecraft:bucket", planks, ((2, 5), (7, 2)))
    t.set(W - 3, 1, D - 2, "minecraft:smithing_table")
    t.set(1, 1, D - 2, "minecraft:iron_block")
    t.set(1, 2, D - 2, "minecraft:waxed_copper_block")
    t.set(1, 1, D - 3, "minecraft:waxed_copper_block")
    t.set(2, 1, D - 2, "minecraft:crafting_table")
    t.set(W - 2, 1, D - 2, *BARREL)
    for z in range(3, 6):
        t.set(1, 1, z, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"})
    for x in (W - 3, W - 2):
        t.set(x, 1, 4, *TOP_SLAB)
    lantern(t, W - 3, 2, 4)
    t.set(W - 2, 2, 4, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 2, D - 2)
    return t


def fletcher_range():
    """The building of the fletcher: the shed with a fletching table (the work block of the vanilla fletcher), and a
    yard with 2 targets on hay bales and a heap of gravel (the flint of the arrows). A path of coarse dirt goes from
    the back door to the back fence. The targets and the hay bales are not next to the fence."""
    rng = random.Random(68)
    targets = (2, W - 3)
    row = SHED_DEPTH + 1

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == W // 2 else GRASS

    def plants(x, z):
        if x in targets and z == row:
            return "minecraft:hay_block", LOG_Y
        return "minecraft:gravel" if (x, z) == (1, D - 2) else None

    t = job_building(rng, "fletcher", "minecraft:arrow", ground, plants, work=("minecraft:fletching_table", None))
    for x in targets:
        t.set(x, 2, row, "minecraft:target", {"power": "0"})
    t.set(1, 1, 2, *BARREL)
    connect_fences(t, 1)
    return t


def carpenter_workshop():
    """The building of the carpenter: a house with 2 crafting tables, a bench of oak slabs, a stack of oak planks, and
    chests and barrels (the goods of the job). The oak planks and the oak slabs stay oak in all the variants."""
    rng = random.Random(69)
    t = job_house(rng, "carpenter", "minecraft:crafting_table", planks, ((2, 5), (6, 5)))
    t.set(2, 1, 4, "minecraft:crafting_table")
    t.set(W - 3, 1, 4, "minecraft:crafting_table")
    for x in (3, W - 2):
        job_material(t, x, 1, 4, "minecraft:oak_slab", {"type": "top", "waterlogged": "false"})
    for x, y, z in ((W - 2, 1, D - 2), (W - 2, 2, D - 2), (W - 3, 1, D - 2), (W - 2, 1, D - 3)):
        job_material(t, x, y, z, "minecraft:oak_planks")
    for x in (1, 2):
        t.set(x, 1, D - 2, "minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
    t.set(W - 4, 1, D - 2, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 1, 2)
    return t


def tannery():
    """The building of the tanner: the shed with a cauldron (the work block of the vanilla leatherworker), and a yard
    with 2 vats of water (water cauldrons) and a rack of fences with hides (brown carpets) on it. A path of coarse dirt
    goes from the back door to the back fence. The vats are not next to the fence."""
    rng = random.Random(70)
    vats = ((2, SHED_DEPTH + 1), (3, SHED_DEPTH + 1))
    rack = ((W - 3, SHED_DEPTH + 1), (W - 3, D - 2))

    def ground(x, z):
        return "minecraft:coarse_dirt" if x in (W // 2, W - 3) else GRASS

    def plants(x, z):
        if (x, z) in vats:
            return "minecraft:water_cauldron", {"level": "3"}
        return "minecraft:spruce_fence" if (x, z) in rack else None

    t = job_building(rng, "tanner", "minecraft:leather", ground, plants, work=("minecraft:cauldron", None))
    for x, z in rack:
        t.set(x, 2, z, "minecraft:brown_carpet")
    t.set(1, 1, 2, *BARREL)
    connect_fences(t, 1)
    return t


LOOM = ("minecraft:loom", {"facing": "north"})
WATER_CAULDRON = ("minecraft:water_cauldron", {"level": "3"})


def sheepfold():
    """The building of the shepherd: the shed with a loom (the work block of the vanilla shepherd) and bales of wool,
    and a pen behind it with hay, a water trough and a salt lick (a block of calcite). The doors of the shed and the
    fence keep the sheep in the pen. The template has no animals (see ranch). The hay, the trough and the salt lick are
    not next to the fence."""
    rng = random.Random(71)
    row = SHED_DEPTH + 1

    def ground(x, z):
        return "minecraft:coarse_dirt" if (x, z) == (W // 2, SHED_DEPTH) else GRASS

    def plants(x, z):
        return {(2, row): ("minecraft:hay_block", LOG_Y), (W - 3, row): WATER_CAULDRON,
                (W // 2, row): "minecraft:calcite"}.get((x, z))

    t = job_building(rng, "shepherd", "minecraft:shears", ground, plants, work=LOOM)
    t.set(1, 1, 2, "minecraft:white_wool")
    t.set(1, 2, 2, "minecraft:white_wool")
    t.set(2, 1, 2, "minecraft:white_wool")
    return t


def weaving_house():
    """The building of the weaver: a house with 2 looms (the work block of the vanilla shepherd), stacks of white wool,
    a crafting table for the beds, a barrel, and a counter with rolls of cloth (white carpets)."""
    rng = random.Random(72)
    t = job_house(rng, "weaver", "minecraft:white_carpet", planks, ((1, 3), (2, 3)))
    for x in (2, W - 3):
        t.set(x, 1, D - 2, *LOOM)
    for x, y, z in ((1, 1, D - 2), (1, 2, D - 2), (1, 1, D - 3)):
        t.set(x, y, z, "minecraft:white_wool")
    t.set(W - 2, 1, D - 2, "minecraft:crafting_table")
    t.set(W - 2, 1, D - 4, *BARREL)
    for x in (W - 3, W - 2):
        t.set(x, 1, 4, *TOP_SLAB)
        t.set(x, 2, 4, "minecraft:white_carpet")
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 1, 2)
    return t


def dye_works():
    """The building of the dyer: the shed with a loom (the work block of the vanilla shepherd) and bales of dyed wool,
    and a yard with beds of dandelions, poppies and cornflowers (the colors of the dyes) and 2 dye vats (water
    cauldrons). A path of coarse dirt goes from the back door to the back fence. The vats are not next to the fence."""
    rng = random.Random(73)
    flowers = {1: "minecraft:dandelion", 2: "minecraft:poppy", W - 3: "minecraft:poppy", W - 2: "minecraft:cornflower"}
    vats = ((3, SHED_DEPTH + 1), (W - 4, SHED_DEPTH + 1))

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == W // 2 else GRASS

    def plants(x, z):
        return WATER_CAULDRON if (x, z) in vats else flowers.get(x)

    t = job_building(rng, "dyer", "minecraft:red_dye", ground, plants, work=LOOM)
    t.set(1, 1, 2, "minecraft:red_wool")
    t.set(1, 2, 2, "minecraft:yellow_wool")
    t.set(2, 1, 2, "minecraft:blue_wool")
    return t


def paper_mill():
    """The building of the papermaker: a house with a lectern (the work block of the vanilla librarian), 2 vats of
    pulp (water cauldrons), barrels of sugar cane, a crafting table, bookshelves, and a counter with sheets of paper
    to dry (white carpets). The path from the door to the lectern stays free."""
    rng = random.Random(74)
    t = job_house(rng, "papermaker", "minecraft:paper", planks, ((1, 3, "east"), (7, 4)))
    t.set(W // 2, 1, D - 3, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    for x in (3, W - 4):
        t.set(x, 1, D - 2, *WATER_CAULDRON)
    for z in (D - 3, D - 4):
        t.set(1, 1, z, *BARREL)
    t.set(2, 1, D - 2, "minecraft:crafting_table")
    for x, y, z in ((W - 2, 1, D - 2), (W - 2, 2, D - 2), (W - 2, 1, D - 3)):
        t.set(x, y, z, "minecraft:bookshelf")
    for x in (1, 2):
        t.set(x, 1, 4, *TOP_SLAB)
        t.set(x, 2, 4, "minecraft:white_carpet")
    lantern(t, 1, 1, 2)
    lantern(t, W - 2, 1, 2)
    return t


# ---------------------------------------------------------------- house-workshops of the Valeholm style

# A Norse house-workshop is a deep long house of LW x LD blocks: it fits each plot of a Norse street, as a long house.
# The jobs of the fields, the gardens, the animals, the lumberjacks, the miner, the digger, the fletcher, the tanner,
# the shepherd and the dyer have a short long house on the street (z = 1..NORSE_SHED - 1) and a fenced yard behind it.
NORSE_SHED = 9
NORSE_YARD_Z = range(NORSE_SHED, LD - 1)
NORSE_YARD_X = range(1, LW - 1)
# The water of a Norse field, in the middle of the yard: it gives water to all the farmland of the yard.
NORSE_WATER = (LW // 2, NORSE_SHED + 3)
# The beds of a Norse long house: on the 2 side walls, behind the hearth, with the heads to the back wall.
NORSE_BEDS = ((1, 10), (LW - 2, 10))
COMPOSTER = ("minecraft:composter", {"level": "0"})


def norse_yard_workshop(rng, job, icon, ground, plants, work=COMPOSTER):
    """The house-workshop of a job with a yard in the Valeholm style (the beekeeper, the fisher, and the jobs with a
    yard of style_jobs: the farmer, the gardeners, the forager, the rancher, the lumberjacks, the miner, the digger,
    the fletcher, the tanner, the shepherd and the dyer): a short long house on the street and a fenced yard behind
    it.

    - The house: the long house shell (see long_house_shell) with no hearth, a door on the street and a back door to
      the yard. The job sign stands on the porch next to the west porch post, and the house sign next to the east
      porch post.
    - The work blocks: the work block of the vanilla profession of the job in the west back corner, and a barrel next
      to the door.
    - The home: 2 beds side by side in the east half, with the heads to the east. The path from the door to the back
      door (x = 4) goes along the feet of the beds.
    - The yard: ground(x, z) gives the ground block of each position, and plants(x, z) gives the block above it, or
      None. A fence goes around the yard, with a lantern on each back corner.

    The caller adds the blocks of the job, then gives the template to shaped.
    """
    t = long_house_shell(rng, "house", cobble, depth=NORSE_SHED, hearth=False)
    mid = LW // 2
    back = NORSE_SHED - 1
    for y, half in ((1, "lower"), (2, "upper")):
        t.set(mid, y, back, "minecraft:spruce_door",
              {"facing": "south", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    t.set(mid, 3, back, "minecraft:stripped_spruce_log", {"axis": "x"})
    t.set(1, 1, back - 1, *work)
    t.set(mid - 1, 1, 2, *BARREL)
    lantern(t, LW - 2, 1, 2)
    home_beds(t, (mid + 1, back - 3), (mid + 1, back - 2), facing="east")
    job_sign(t, 1, 1, 0, job, icon)
    for x in range(LW):
        for z in range(NORSE_SHED, LD):
            if x in NORSE_YARD_X and z in NORSE_YARD_Z:
                t.set(x, 0, z, *block(ground(x, z)))
                plant = plants(x, z)
                if plant:
                    t.set(x, 1, z, *block(plant))
            else:
                t.set(x, 0, z, cobble(rng))
                t.set(x, 1, z, "minecraft:spruce_fence")
    for x in (0, LW - 1):
        lantern(t, x, 2, LD - 1)
    return t


def norse_apiary():
    """The Valeholm building of the beekeeper: 2 beehives on stands of stripped logs among the flowers, and a path of
    coarse dirt. The yard has no campfire: a campfire burns, and the heart takes it as a brazier."""
    rng = random.Random(65)
    mid, row = NORSE_WATER
    hives = ((2, row), (LW - 3, row))
    flowers = FLOWERS + ("minecraft:oxeye_daisy", "minecraft:allium")

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == mid else GRASS

    def plants(x, z):
        if (x, z) in hives:
            return "minecraft:stripped_spruce_log", LOG_Y
        if x == mid or (x + z) % 2:
            return None
        return flowers[rng.randrange(len(flowers))]

    t = norse_yard_workshop(rng, "beekeeper", "minecraft:honeycomb", ground, plants)
    for x, z in hives:
        t.set(x, 2, z, "minecraft:beehive", {"facing": "south", "honey_level": str(rng.randint(2, 5))})
    t.set(1, 1, 2, *BARREL)
    return shaped(t)


def norse_fisher_hut():
    """The Valeholm building of the fisher: a house with barrels (the work block of the vanilla fisherman) and a tub,
    and a yard with a pond for the live catch. The pond is a row of waterlogged slabs, with a path of coarse dirt next
    to it: a builder stands on the path to add the water."""
    rng = random.Random(67)
    pond = range(2, LW - 2)
    row = LD - 3

    def ground(x, z):
        if z == row and x in pond:
            return WATER_SLAB
        return "minecraft:coarse_dirt" if z == row - 1 or x == LW // 2 else GRASS

    def plants(x, z):
        return BARREL if (x, z) in ((2, NORSE_SHED), (LW - 3, NORSE_SHED)) else None

    t = norse_yard_workshop(rng, "fisher", "minecraft:cod", ground, plants, work=BARREL)
    t.set(1, 1, 2, *WATER_CAULDRON)
    return shaped(t)


def norse_workshop(rng, job, icon, beds=NORSE_BEDS, wide_front=False, wall_lower=cobble):
    """The house-workshop of a job in a Valeholm long house (the baker, the pastry cook, the butcher, and the jobs
    with no yard of style_jobs: the sawyers, the mason, the smelters, the blacksmith, the tinker, the carpenter, the
    weaver and the papermaker): the long house shell with the hearth (see long_house_shell), a door on the street or
    an open shop front. wall_lower gives the blocks of the lowest layer of the walls. The job sign stands on the porch
    next to the west porch post, and the house sign next to the east porch post. beds gives the foot of each bed of the
    home (see home_beds): the beds stand on the side walls behind the hearth (NORSE_BEDS). The caller adds the blocks
    of the job, then gives the template to shaped."""
    t = long_house_shell(rng, "house", wall_lower, wide_front=wide_front)
    home_beds(t, *beds)
    job_sign(t, 1, 1, 0, job, icon)
    return t


def norse_oven(t):
    """A brick oven on the back wall of a long house: a furnace in bricks, with a chimney of bricks through the steep
    roof. The chimney takes the place of the roof blocks, so the roof has no hole. The oven is east of the middle: the
    chimney does not touch the carved horns of the ridge."""
    x, z = LW // 2 + 1, LD - 2
    t.set(x, 1, z, "minecraft:furnace", {"facing": "north", "lit": "false"})
    for dx in (-1, 0, 1):
        t.set(x + dx, 2, z, "minecraft:bricks")
        if dx:
            t.set(x + dx, 1, z, "minecraft:bricks")
    for y in range(3, steep_roof_top(x + 1, LW) + 2):
        t.set(x + 1, y, z, "minecraft:bricks")


def norse_bakery():
    """The Valeholm building of the baker: a brick oven, a crafting table to knead the dough, barrels of flour, the
    wheat on hay bales and a cake on the counter."""
    rng = random.Random(68)
    t = norse_workshop(rng, "baker", "minecraft:bread")
    norse_oven(t)
    t.set(1, 1, LD - 2, "minecraft:crafting_table")
    for x, z in ((1, LD - 4), (2, LD - 2)):
        t.set(x, 1, z, *BARREL)
    for x, y, z in ((LW - 2, 1, LD - 2), (LW - 2, 1, LD - 3), (LW - 2, 2, LD - 3)):
        t.set(x, y, z, "minecraft:hay_block", LOG_Y)
    t.set(LW - 2, 1, 4, *TOP_SLAB)
    t.set(LW - 2, 2, 4, "minecraft:cake", {"bites": "0"})
    lantern(t, 1, 1, 2)
    return shaped(t)


def norse_pastry_shop():
    """The Valeholm building of the pastry cook: an open shop front, a counter with cakes, a brick oven, pumpkins and
    barrels of sugar."""
    rng = random.Random(69)
    t = norse_workshop(rng, "pastry_cook", "minecraft:pumpkin_pie", wide_front=True)
    norse_oven(t)
    # The counter behind the open front, with a free passage in the middle.
    for x in (1, 2, LW - 3, LW - 2):
        t.set(x, 1, 3, *TOP_SLAB)
    for x in (2, LW - 3):
        t.set(x, 2, 3, "minecraft:candle_cake", {"lit": "false"})
    for x, y in ((1, 1), (2, 1), (2, 2)):
        t.set(x, y, LD - 2, "minecraft:pumpkin")
    for z in (LD - 3, LD - 4):
        t.set(LW - 2, 1, z, *BARREL)
    lantern(t, 1, 1, 5)
    return shaped(t)


def norse_butchery():
    """The Valeholm building of the butcher: 2 smokers (the work block of the vanilla butcher), a block of coal, a
    chopping block, and a rack of chains on a beam across the room. The chains leave 2 blocks of space under them."""
    rng = random.Random(70)
    t = norse_workshop(rng, "butcher", "minecraft:cooked_beef")
    for x in (5, 6):
        t.set(x, 1, LD - 2, "minecraft:smoker", {"facing": "north", "lit": "false"})
    t.set(LW - 2, 1, LD - 2, "minecraft:coal_block")
    for x in range(1, LW - 1):
        t.set(x, 4, 12, "minecraft:stripped_spruce_log", {"axis": "x"})
    for x in (2, 3, 6):
        t.set(x, 3, 12, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    t.set(2, 1, 4, "minecraft:stripped_oak_log", LOG_Y)
    for z in (LD - 2, LD - 3):
        t.set(1, 1, z, *BARREL)
    t.set(LW - 2, 1, 4, *WATER_CAULDRON)
    lantern(t, 1, 1, 2)
    return shaped(t)


# ---------------------------------------------------------------- house-workshops of the styles of the cultures

RAIL = ("minecraft:rail", {"shape": "north_south", "waterlogged": "false"})


@dataclasses.dataclass(frozen=True)
class SmelteryLayout:
    """The places of the blocks of a smeltery of a style (see style_smeltery). The back row is the row in front of the
    back wall.

    - furnaces: the x of the 2 blast furnaces on the back row.
    - ore_x: the x of the 2 blocks of raw ore on the back row and the row in front of it.
    - coal: (x, z) of the block of coal.
    - cauldron: (x, z) of the water cauldron. The barrel of sand of the smelter is 1 block in front of it (z - 1).
    - lanterns: (x, y, z) of each lantern after the lantern next to the door.
    """
    furnaces: tuple[int, int]
    ore_x: int
    coal: tuple[int, int]
    cauldron: tuple[int, int]
    lanterns: tuple[tuple[int, int, int], ...]


@dataclasses.dataclass(frozen=True)
class WorkshopStyle:
    """The style of the house-workshops of a culture (VALEHOLM, SUNREACH): the values that the builders of the styles
    (see style_jobs) take from the style.

    - width, depth: the size of a piece along the street and away from the street.
    - shed: the first row of the yard of a house-workshop with a yard. The house is z = 1..shed - 1.
    - water: (x, z) of the water of a field, in the middle of the yard.
    - seed: the builders add it to their seeds, so that each style has its own random blocks.
    - yard_workshop(rng, job, icon, ground, plants, work): the house and the yard of a job with a yard.
    - workshop(rng, job, icon, beds, wide_front, wall_lower): the house of a job with no yard.
    - oven(t): a brick oven with a chimney on the back wall of a house with no yard.
    - beds: the foot of each home bed of a house with no yard (see home_beds).
    - sawmill_lantern: (x, y, z) of the lantern of a sawmill after the lantern next to the door.
    - mason_cutter: (x, z) of the stonecutter of the mason. The block of stone to cut is in front of it (z - 1).
    - mason_lantern: (x, y, z) of the lantern of the mason after the lantern next to the door.
    - smeltery: the places of the blocks of a smeltery (see SmelteryLayout).
    """
    width: int
    depth: int
    shed: int
    water: tuple[int, int]
    seed: int
    yard_workshop: Callable[..., Template]
    workshop: Callable[..., Template]
    oven: Callable[[Template], None]
    beds: tuple[tuple[int, int], ...]
    sawmill_lantern: tuple[int, int, int]
    mason_cutter: tuple[int, int]
    mason_lantern: tuple[int, int, int]
    smeltery: SmelteryLayout


def style_farm(s):
    """The building of the farmer in the style s: a field of wheat, carrots, potatoes and beetroots around the water,
    and hay bales in the house."""
    rng = random.Random(s.seed + 61)
    rows = {1: "wheat", 2: "wheat", 3: "carrots", 5: "potatoes", 6: "beetroots", 7: "beetroots"}

    def plants(x, z):
        if x == s.water[0]:
            return None
        return crop(f"minecraft:{rows[x]}", rng, 3 if rows[x] == "beetroots" else 7)

    t = s.yard_workshop(rng, "farmer", "minecraft:wheat", functools.partial(field_ground, water=s.water), plants)
    for x, y in ((1, 1), (2, 1), (1, 2)):
        t.set(x, y, 2, "minecraft:hay_block", LOG_Y)
    return shaped(t)


def style_garden(s):
    """The building of the gardener in the style s: 2 rows of pumpkins and melons on their stems, and sugar cane next
    to the water. The sugar cane is 1 block high, with no block next to it (see garden)."""
    rng = random.Random(s.seed + 62)
    mid, row = s.water
    stems = (row - 2, row + 2)

    def ground(x, z):
        if x == mid:
            return field_ground(x, z, s.water)
        return FARMLAND if abs(x - mid) == 2 and z in stems else GRASS

    def plants(x, z):
        side = x - mid
        if z == row and abs(side) == 1:
            return "minecraft:sugar_cane", {"age": "0"}
        if z not in stems:
            return None
        fruit = "pumpkin" if side < 0 else "melon"
        if abs(side) == 3:
            return f"minecraft:{fruit}"
        if abs(side) == 2:
            return f"minecraft:attached_{fruit}_stem", {"facing": "west" if side < 0 else "east"}
        return None

    t = s.yard_workshop(rng, "gardener", "minecraft:pumpkin", ground, plants)
    t.set(1, 1, 2, "minecraft:pumpkin")
    t.set(2, 1, 2, "minecraft:melon")
    return shaped(t)


def style_jungle_garden(s):
    """The building of the gardener of the jungle in the style s: cocoa pods on 2 posts of jungle logs, 2 melons on
    their stems next to the water, and ferns along the sides of the yard."""
    rng = random.Random(s.seed + 63)
    mid, row = s.water
    posts = ((2, row), (s.width - 3, row))
    melons = {(mid, row - 1): "west", (mid, row + 1): "east"}

    def ground(x, z):
        if (x, z) == s.water:
            return WATER_SLAB
        if x == mid:
            return FARMLAND if (x, z) in melons else "minecraft:coarse_dirt"
        return GRASS

    def plants(x, z):
        if (x, z) in posts:
            return "minecraft:jungle_log", LOG_Y
        if (x, z) in melons:
            return "minecraft:attached_melon_stem", {"facing": melons[(x, z)]}
        if (x - mid, z - row) in ((-1, -1), (1, 1)):
            return "minecraft:melon"
        if abs(x - mid) == 3 and z != row:
            return "minecraft:fern" if rng.random() < 0.5 else None
        return None

    t = s.yard_workshop(rng, "gardener_jungle", "minecraft:cocoa_beans", ground, plants)
    # The posts: 3 jungle logs high, with a cocoa pod on each side. A pod faces its log.
    for px, pz in posts:
        for y in (2, 3):
            t.set(px, y, pz, "minecraft:jungle_log", LOG_Y)
        for side, (dx, dz) in SIDES.items():
            t.set(px + dx, 2, pz + dz, "minecraft:cocoa", {"age": str(rng.randint(1, 2)), "facing": TOWARD[side]})
    t.set(1, 1, 2, "minecraft:melon")
    return shaped(t)


def style_forager_hut(s):
    """The building of the forager in the style s: mushrooms on podzol, wild flowers on grass (see forage_plants),
    and the finds in a barrel."""
    rng = random.Random(s.seed + 64)
    t = s.yard_workshop(rng, "forager", "minecraft:red_mushroom", functools.partial(forage_ground, width=s.width),
                        functools.partial(forage_plants, width=s.width))
    t.set(1, 1, 2, *BARREL)
    t.set(2, 1, 2, "minecraft:moss_block")
    return shaped(t)


def style_ranch(s):
    """The building of the rancher in the style s: a house with a smoker (the work block of the vanilla butcher) and a
    pen behind it, with hay and a water trough. The doors of the house and the edge of the yard keep the animals in
    the pen. The template has no animals (see ranch)."""
    rng = random.Random(s.seed + 66)
    row = s.shed + 2
    # A hay bale in the first row of the pen, and a hay bale in the back row of a deep pen. The hay and the trough are
    # not next to the edge of the yard: a fence or a wall connects to a full block.
    hay = {(2, row)} | ({(2, s.depth - 3)} if s.depth - 3 > row + 1 else set())

    def ground(x, z):
        return "minecraft:coarse_dirt" if (x, z) == (s.width // 2, s.shed) else GRASS

    def plants(x, z):
        if (x, z) in hay:
            return "minecraft:hay_block", LOG_Y
        if (x, z) == (s.width - 3, row):
            return WATER_CAULDRON
        return None

    t = s.yard_workshop(rng, "rancher", "minecraft:lead", ground, plants,
                        work=("minecraft:smoker", {"facing": "east", "lit": "false"}))
    t.set(1, 1, 2, "minecraft:hay_block", LOG_Y)
    return shaped(t)


def style_lumber_yard(s, job, wood):
    """The building of a lumberjack in the style s: a house with a grindstone for the axes and a potted sapling, and a
    yard with a pile of logs of the wood of the job along the west side, a chopping block and the split wood (see
    lumber_yard).

    All the lumberjacks have this shape. Only the wood of the logs, of the chopping block and of the sapling changes.
    """
    log = f"minecraft:{wood}_log"
    sapling = POTTED.get(wood, f"minecraft:potted_{wood}_sapling")
    rng = random.Random(s.seed + 71)

    def ground(x, z):
        return GRASS if x in (1, 2, s.width - 2) else "minecraft:coarse_dirt"

    t = s.yard_workshop(rng, job, log, ground, lambda x, z: None,
                        work=("minecraft:grindstone", {"face": "floor", "facing": "north"}))
    t.set(1, 1, 2, sapling)
    # The west pile: 2 rows of logs along the side of the yard, the row on the side 2 logs high.
    for z in range(s.shed + 1, s.depth - 2):
        for x, top in ((1, 2), (2, 1)):
            for y in range(1, top + 1):
                job_material(t, x, y, z, log, {"axis": "z"})
    # The chopping block: a log on its end. The split wood: stripped logs in the east back corner.
    job_material(t, s.width - 3, 1, s.water[1], log, LOG_Y)
    for x, y in ((s.width - 3, 1), (s.width - 2, 1), (s.width - 2, 2)):
        job_material(t, x, y, s.depth - 2, f"minecraft:stripped_{wood}_log", {"axis": "x"})
    t.set(s.width - 2, 1, s.shed, sapling)
    return shaped(t)


def style_sawmill(s, job, wood):
    """The building of a sawyer in the style s: a house with a saw bench across the room behind the beds (a
    stonecutter between the logs and the slabs), a pile of logs and a stack of planks on the back wall, a crafting
    table and a barrel (see sawmill).

    All the sawyers have this shape. Only the wood of the logs, the planks and the slabs changes.
    """
    rng = random.Random(s.seed + 72)
    t = s.workshop(rng, job, f"minecraft:{wood}_planks", s.beds)
    w, back = s.width, s.depth - 2
    bench = s.depth - 4
    for x in (2, 3):
        job_material(t, x, 1, bench, f"minecraft:{wood}_log", {"axis": "x"})
    t.set(4, 1, bench, "minecraft:stonecutter", {"facing": "north"})
    for x in (5, 6):
        job_material(t, x, 1, bench, f"minecraft:{wood}_slab", {"type": "bottom", "waterlogged": "false"})
    # The logs to saw (west) and the planks (east) on the back wall. The passages at the ends of the bench stay free.
    for x, y in ((1, 1), (2, 1), (3, 1), (1, 2), (2, 2)):
        job_material(t, x, y, back, f"minecraft:{wood}_log", {"axis": "x"})
    for x, y, z in ((w - 3, 1, back), (w - 2, 1, back), (w - 2, 2, back), (w - 2, 1, back - 1)):
        job_material(t, x, y, z, f"minecraft:{wood}_planks")
    t.set(1, 1, 3, "minecraft:crafting_table")
    t.set(w - 2, 1, 2, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, *s.sawmill_lantern)
    return shaped(t)


def style_mine(s):
    """The building of the miner in the style s: a house with a smithing table (the work block of the vanilla
    toolsmith), and a yard with a rock face at the back (see mine). A timber frame in the rock face makes the mouth of
    the mine, with a lantern under the beam. A rail goes from the back door to the mouth. A pile of cobblestone stands
    next to the rail. The cobblestone of the pile stays cobblestone in all the variants."""
    rng = random.Random(s.seed + 73)
    w = s.width
    mid = w // 2
    face = s.depth - 2

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == mid else "minecraft:gravel"

    def plants(x, z):
        return RAIL if x == mid else None

    t = s.yard_workshop(rng, "miner", "minecraft:iron_pickaxe", ground, plants,
                        work=("minecraft:smithing_table", None))
    # The rock face: 3 blocks high, with stone at its foot on the 2 sides.
    rock = {1: ("minecraft:stone", "minecraft:andesite", "minecraft:stone"),
            2: ("minecraft:coal_ore", "minecraft:stone", "minecraft:andesite"),
            w - 3: ("minecraft:iron_ore", "minecraft:stone", "minecraft:stone"),
            w - 2: ("minecraft:andesite", "minecraft:copper_ore", "minecraft:stone")}
    for x, column in rock.items():
        for y, name in enumerate(column, 1):
            t.set(x, y, face, name)
    for x in (1, w - 2):
        t.set(x, 1, face - 1, "minecraft:stone")
    # The mouth: 2 posts and a beam in the rock face, and the rock behind it in place of the back edge of the yard.
    for x in (mid - 1, mid + 1):
        for y in (1, 2):
            t.set(x, y, face, "minecraft:spruce_log", LOG_Y)
    for x in (mid - 1, mid, mid + 1):
        t.set(x, 3, face, "minecraft:spruce_log", {"axis": "x"})
        for y in (1, 2, 3):
            t.set(x, y, s.depth - 1, "minecraft:stone")
    t.set(mid, 2, face, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    for x, z in ((1, s.shed), (2, s.shed), (1, s.shed + 1)):
        job_material(t, x, 1, z, "minecraft:cobblestone")
    t.set(1, 1, 2, *BARREL)
    return shaped(t)


def style_dig_yard(s):
    """The building of the digger in the style s: a house with a stonecutter (the work block of the vanilla mason),
    and a yard with heaps of sand, gravel, clay and dirt along the sides (see dig_yard). A heap of clay and a heap of
    sand stand in the row in front of the middle of the yard. A path of coarse dirt goes from the back door to the back
    of the yard."""
    rng = random.Random(s.seed + 74)
    w, back = s.width, s.depth - 2
    front = s.water[1] - 1
    heaps = {(1, back): "sand", (2, back): "sand", (1, back - 1): "sand", (3, back): "gravel",
             (w - 4, back): "clay", (w - 3, back): "dirt", (w - 2, back): "dirt", (w - 2, back - 1): "gravel",
             (1, front): "clay", (w - 2, front): "sand"}

    def plants(x, z):
        return f"minecraft:{heaps[(x, z)]}" if (x, z) in heaps else None

    t = s.yard_workshop(rng, "digger", "minecraft:iron_shovel", lambda x, z: "minecraft:coarse_dirt", plants,
                        work=("minecraft:stonecutter", {"facing": "east"}))
    t.set(1, 2, back, "minecraft:sand")
    t.set(w - 2, 2, back, "minecraft:dirt")
    t.set(1, 1, 2, *BARREL)
    return shaped(t)


def style_mason(s):
    """The building of the mason in the style s: a house with a stonecutter (the work block of the vanilla mason) and
    a block of stone to cut in front of it, a brick kiln with a chimney (the oven of the style), and stacks of stone
    bricks, bricks, smooth stone and terracotta on the back wall (see mason_workshop). In a long house, the lowest layer
    of the walls is stone bricks. The stacks of stone bricks stay stone bricks in all the variants."""
    rng = random.Random(s.seed + 75)
    t = s.workshop(rng, "mason", "minecraft:stone_bricks", s.beds, wall_lower=stone_bricks)
    s.oven(t)
    w, back = s.width, s.depth - 2
    for x, y, z in ((1, 1, back), (1, 2, back), (2, 1, back), (1, 1, back - 1)):
        job_material(t, x, y, z, "minecraft:stone_bricks")
    for y in (1, 2):
        t.set(w - 2, y, back, "minecraft:bricks")
    t.set(w - 2, 1, back - 1, "minecraft:smooth_stone")
    t.set(w - 2, 1, back - 2, "minecraft:terracotta")
    cx, cz = s.mason_cutter
    t.set(cx, 1, cz, "minecraft:stonecutter", {"facing": "east"})
    t.set(cx, 1, cz - 1, "minecraft:stone")
    t.set(w - 2, 1, 3, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, *s.mason_lantern)
    return shaped(t)


def style_smeltery(s, job):
    """The building of a smelter in the style s: a house with 2 blast furnaces (the work block of the vanilla armorer)
    next to a furnace in a brick oven with a chimney (the oven of the style), the raw ore and a block of coal, a water
    cauldron to cool the metal, and the ingots on a counter (see smeltery). In a long house, the lowest layer of the
    walls is bricks. The smelter also makes glass: its building has a barrel of sand in front of the cauldron.

    All the smelters have this shape. Only the ore and the ingots change.
    """
    icon, ores, plate = SMELTERS[job]
    rng = random.Random(s.seed + 76)
    layout = s.smeltery
    t = s.workshop(rng, job, icon, s.beds, wall_lower=lambda r: "minecraft:bricks")
    s.oven(t)
    back = s.depth - 2
    for x in layout.furnaces:
        t.set(x, 1, back, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    for z, ore in zip((back, back - 1), ores):
        t.set(layout.ore_x, 1, z, ore)
    t.set(layout.coal[0], 1, layout.coal[1], "minecraft:coal_block")
    cx, cz = layout.cauldron
    t.set(cx, 1, cz, *WATER_CAULDRON)
    if job == "smelter":
        t.set(cx, 1, cz - 1, *BARREL)
    for x in (s.width - 3, s.width - 2):
        t.set(x, 1, 3, *TOP_SLAB)
        t.set(x, 2, 3, plate, {"power": "0"})
    lantern(t, 1, 1, 2)
    for pos in layout.lanterns:
        lantern(t, *pos)
    return shaped(t)


def style_smithy(s):
    """The building of the blacksmith in the style s: a forge (see forge_tools) as a house-workshop, with an open
    front."""
    rng = random.Random(s.seed + 77)
    t = s.workshop(rng, *BLACKSMITH, s.beds, wide_front=True)
    forge_tools(t, s.width, s.depth)
    return shaped(t)


def style_tinker(s):
    """The building of the tinker in the style s: a house with a smithing table (the work block of the vanilla
    toolsmith), blocks of iron and copper, a crafting table, a barrel, a short track of rails, and a counter with a
    lantern and a lightning rod (the goods of the job). See tinker_workshop."""
    rng = random.Random(s.seed + 78)
    t = s.workshop(rng, "tinker", "minecraft:bucket", s.beds)
    w, back = s.width, s.depth - 2
    t.set(w - 3, 1, back, "minecraft:smithing_table")
    t.set(1, 1, back, "minecraft:iron_block")
    t.set(1, 2, back, "minecraft:waxed_copper_block")
    t.set(1, 1, back - 1, "minecraft:waxed_copper_block")
    t.set(2, 1, back, "minecraft:crafting_table")
    t.set(w - 2, 1, back, *BARREL)
    # The track: 3 rails at most along the west wall. It stops in front of the west bed.
    for z in range(3, min(6, s.beds[0][1])):
        t.set(1, 1, z, *RAIL)
    for x in (w - 3, w - 2):
        t.set(x, 1, 4, *TOP_SLAB)
    lantern(t, w - 3, 2, 4)
    t.set(w - 2, 2, 4, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    lantern(t, 1, 1, 2)
    lantern(t, w - 2, 1, back - 2)
    return shaped(t)


def style_fletcher_range(s):
    """The building of the fletcher in the style s: a house with a fletching table (the work block of the vanilla
    fletcher), and a yard with 2 targets on hay bales at the back and a heap of gravel (the flint of the arrows). A path
    of coarse dirt goes from the back door to the back of the yard. The targets are not next to the edge of the yard
    (see fletcher_range)."""
    rng = random.Random(s.seed + 79)
    targets = (2, s.width - 3)
    row = s.depth - 3

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == s.width // 2 else GRASS

    def plants(x, z):
        if x in targets and z == row:
            return "minecraft:hay_block", LOG_Y
        return "minecraft:gravel" if (x, z) == (1, s.depth - 2) else None

    t = s.yard_workshop(rng, "fletcher", "minecraft:arrow", ground, plants, work=("minecraft:fletching_table", None))
    for x in targets:
        t.set(x, 2, row, "minecraft:target", {"power": "0"})
    t.set(1, 1, 2, *BARREL)
    return shaped(t)


def style_carpenter(s):
    """The building of the carpenter in the style s: a house with 2 crafting tables and a bench of oak slabs near the
    door, a stack of oak planks, 2 chests and a barrel on the back wall (see carpenter_workshop). The oak planks and the
    oak slabs stay oak in all the variants."""
    rng = random.Random(s.seed + 80)
    t = s.workshop(rng, "carpenter", "minecraft:crafting_table", s.beds)
    w, back = s.width, s.depth - 2
    for x in (2, w - 3):
        t.set(x, 1, 4, "minecraft:crafting_table")
    for x in (3, w - 2):
        job_material(t, x, 1, 4, "minecraft:oak_slab", {"type": "top", "waterlogged": "false"})
    for x, y, z in ((w - 2, 1, back), (w - 2, 2, back), (w - 3, 1, back), (w - 2, 1, back - 1)):
        job_material(t, x, y, z, "minecraft:oak_planks")
    for x in (2, 3):
        t.set(x, 1, back, "minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
    t.set(1, 1, back, *BARREL)
    lantern(t, 1, 1, 2)
    lantern(t, w - 2, 1, 2)
    return shaped(t)


def style_tannery(s):
    """The building of the tanner in the style s: a house with a cauldron (the work block of the vanilla
    leatherworker), and a yard with 2 vats of water (water cauldrons) and a rack of fences with hides (brown carpets)
    on it. A path of coarse dirt goes from the back door to the back of the yard. The vats and the rack are not next to
    the edge of the yard (see tannery): the rack has 3 fences, or 2 fences in a short yard."""
    rng = random.Random(s.seed + 81)
    vats = ((2, s.shed + 2), (3, s.shed + 2))
    rack = tuple((s.width - 3, z) for z in range(s.shed + 2, min(s.shed + 5, s.depth - 2)))

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == s.width // 2 else GRASS

    def plants(x, z):
        if (x, z) in vats:
            return WATER_CAULDRON
        return "minecraft:spruce_fence" if (x, z) in rack else None

    t = s.yard_workshop(rng, "tanner", "minecraft:leather", ground, plants, work=("minecraft:cauldron", None))
    for x, z in rack:
        t.set(x, 2, z, "minecraft:brown_carpet")
    t.set(1, 1, 2, *BARREL)
    return shaped(t)


def style_sheepfold(s):
    """The building of the shepherd in the style s: a house with a loom (the work block of the vanilla shepherd) and
    bales of wool, and a pen behind it with 2 hay bales, a water trough and a salt lick (a block of calcite) in the
    first row and in the back row of the pen. The doors of the house and the edge of the yard keep the sheep in the
    pen. The template has no animals (see ranch). The hay, the trough and the salt lick are not next to the edge of
    the yard."""
    rng = random.Random(s.seed + 82)
    row, last = s.shed + 2, s.depth - 3
    pen = {(2, row): ("minecraft:hay_block", LOG_Y), (2, last): ("minecraft:hay_block", LOG_Y),
           (s.width - 3, row): WATER_CAULDRON, (s.width - 3, last): "minecraft:calcite"}

    def ground(x, z):
        return "minecraft:coarse_dirt" if (x, z) == (s.width // 2, s.shed) else GRASS

    t = s.yard_workshop(rng, "shepherd", "minecraft:shears", ground, lambda x, z: pen.get((x, z)), work=LOOM)
    t.set(1, 1, 2, "minecraft:white_wool")
    t.set(1, 2, 2, "minecraft:white_wool")
    t.set(2, 1, 2, "minecraft:white_wool")
    return shaped(t)


def style_weaving_house(s):
    """The building of the weaver in the style s: a house with 2 looms (the work block of the vanilla shepherd) and
    stacks of white wool on the back wall, a crafting table for the beds, a barrel, and a counter with rolls of cloth
    (white carpets) near the door (see weaving_house)."""
    rng = random.Random(s.seed + 83)
    t = s.workshop(rng, "weaver", "minecraft:white_carpet", s.beds)
    w, back = s.width, s.depth - 2
    for x in (2, w - 3):
        t.set(x, 1, back, *LOOM)
    for x, y, z in ((1, 1, back), (1, 2, back), (1, 1, back - 1)):
        t.set(x, y, z, "minecraft:white_wool")
    t.set(w - 2, 1, back, "minecraft:crafting_table")
    t.set(w - 2, 1, back - 2, *BARREL)
    for x in (w - 3, w - 2):
        t.set(x, 1, 4, *TOP_SLAB)
        t.set(x, 2, 4, "minecraft:white_carpet")
    lantern(t, 1, 1, 2)
    lantern(t, w - 2, 1, 2)
    return shaped(t)


def style_dye_works(s):
    """The building of the dyer in the style s: a house with a loom (the work block of the vanilla shepherd) and bales
    of dyed wool, and a yard with beds of dandelions, poppies and cornflowers (the colors of the dyes) along the sides
    and 2 dye vats (water cauldrons). A path of coarse dirt goes from the back door to the back of the yard. The vats
    are not next to the edge of the yard (see dye_works)."""
    rng = random.Random(s.seed + 84)
    w = s.width
    flowers = {1: "minecraft:dandelion", 2: "minecraft:poppy", w - 3: "minecraft:poppy", w - 2: "minecraft:cornflower"}
    vats = ((3, s.shed + 2), (w - 4, s.shed + 2))

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == w // 2 else GRASS

    def plants(x, z):
        return WATER_CAULDRON if (x, z) in vats else flowers.get(x)

    t = s.yard_workshop(rng, "dyer", "minecraft:red_dye", ground, plants, work=LOOM)
    t.set(1, 1, 2, "minecraft:red_wool")
    t.set(1, 2, 2, "minecraft:yellow_wool")
    t.set(2, 1, 2, "minecraft:blue_wool")
    return shaped(t)


def style_paper_mill(s):
    """The building of the papermaker in the style s: a house with a lectern (the work block of the vanilla librarian)
    between 2 vats of pulp (water cauldrons), barrels of sugar cane, a crafting table, bookshelves, and a counter with
    sheets of paper to dry (white carpets) near the door (see paper_mill). A free block stays next to each work
    block."""
    rng = random.Random(s.seed + 85)
    t = s.workshop(rng, "papermaker", "minecraft:paper", s.beds)
    w, back = s.width, s.depth - 2
    mid = w // 2
    t.set(mid, 1, back - 1, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    for x in (mid - 1, mid + 1):
        t.set(x, 1, back, *WATER_CAULDRON)
    t.set(1, 1, back, "minecraft:crafting_table")
    for z in (back - 1, back - 2):
        t.set(1, 1, z, *BARREL)
    for x, y, z in ((w - 2, 1, back), (w - 2, 2, back), (w - 2, 1, back - 1)):
        t.set(x, y, z, "minecraft:bookshelf")
    for x in (1, 2):
        t.set(x, 1, 4, *TOP_SLAB)
        t.set(x, 2, 4, "minecraft:white_carpet")
    lantern(t, 1, 1, 2)
    lantern(t, w - 2, 1, 2)
    return shaped(t)


def style_jobs(s):
    """The house-workshops of the style s from the builders of the styles, by job: all the jobs of JOBS except the
    beekeeper, the fisher, the baker, the pastry cook and the butcher. Each style has builders of its own for these
    5 jobs."""
    return {
        "farmer": functools.partial(style_farm, s),
        "gardener": functools.partial(style_garden, s),
        "gardener_jungle": functools.partial(style_jungle_garden, s),
        "forager": functools.partial(style_forager_hut, s),
        "rancher": functools.partial(style_ranch, s),
        **{job: functools.partial(style_lumber_yard, s, job, wood) for job, wood in LUMBERJACKS.items()},
        **{job: functools.partial(style_sawmill, s, job, wood) for job, wood in SAWYERS.items()},
        "miner": functools.partial(style_mine, s),
        "digger": functools.partial(style_dig_yard, s),
        "mason": functools.partial(style_mason, s),
        **{job: functools.partial(style_smeltery, s, job) for job in SMELTERS},
        "blacksmith": functools.partial(style_smithy, s),
        "tinker": functools.partial(style_tinker, s),
        "fletcher": functools.partial(style_fletcher_range, s),
        "carpenter": functools.partial(style_carpenter, s),
        "tanner": functools.partial(style_tannery, s),
        "shepherd": functools.partial(style_sheepfold, s),
        "weaver": functools.partial(style_weaving_house, s),
        "dyer": functools.partial(style_dye_works, s),
        "papermaker": functools.partial(style_paper_mill, s),
    }


# The Valeholm style of the house-workshops: Norse long houses of LW x LD blocks (see WorkshopStyle).
VALEHOLM = WorkshopStyle(
    width=LW, depth=LD, shed=NORSE_SHED, water=NORSE_WATER, seed=0, yard_workshop=norse_yard_workshop,
    workshop=norse_workshop, oven=norse_oven, beds=NORSE_BEDS,
    sawmill_lantern=(LW - 2, 1, 4), mason_cutter=(2, 4), mason_lantern=(LW - 2, 1, 6),
    smeltery=SmelteryLayout(furnaces=(2, 3), ore_x=1, coal=(1, LD - 4), cauldron=(LW - 2, LD - 3), lanterns=()))

# The house-workshops of the Valeholm style, by job: each job of JOBS has a piece of the style.
NORSE_JOBS = {
    **style_jobs(VALEHOLM),
    "beekeeper": norse_apiary,
    "fisher": norse_fisher_hut,
    "baker": norse_bakery,
    "pastry_cook": norse_pastry_shop,
    "butcher": norse_butchery,
}


# ---------------------------------------------------------------- house-workshops of the Sunreach style

# A Sunreach house-workshop is FW blocks wide, as the flat houses of the style, and CD blocks deep, as the courtyard
# houses of the style. The farmer, the gardeners, the forager, the beekeeper, the rancher, the fisher, the lumberjacks,
# the miner, the digger, the fletcher, the tanner, the shepherd and the dyer have a flat house on the street
# (z = 1..SUN_SHED - 1) and a walled yard behind it.
SUN_SHED = 7
SUN_YARD_Z = range(SUN_SHED, CD - 1)
SUN_YARD_X = range(1, FW - 1)
# The water of a Sunreach field, in the middle of the yard: it gives water to all the farmland of the yard.
SUN_WATER = (FW // 2, SUN_SHED + 2)
# The low wall around a Sunreach yard. The wall connects to its neighbors as in the game (see shaped).
SUN_WALL = "minecraft:sandstone_wall"


def sun_workshop_shell(rng, depth=CD, wide_front=False):
    """The shell of a Sunreach house-workshop: the flat house shell (see flat_house_shell) with a body from z = 1 to
    z = depth - 1, in a template of FW x FH x CD blocks. The house sign stands on the porch east of the door, and the
    job sign of the caller west of the door."""
    shell = flat_house_shell(rng, "house", FW, depth, wide_front=wide_front)
    t = Template(FW, FH, CD)
    t.fill(0, 1, 0, FW - 1, FH - 1, CD - 1, "minecraft:air")
    t.blocks.update(shell.blocks)
    return t


def sun_yard_workshop(rng, job, icon, ground, plants, work=COMPOSTER):
    """The house-workshop of a job with a yard in the Sunreach style (the beekeeper, the fisher, and the jobs with a
    yard of style_jobs: the farmer, the gardeners, the forager, the rancher, the lumberjacks, the miner, the digger,
    the fletcher, the tanner, the shepherd and the dyer): a flat house on the street and a walled yard behind it.

    - The house: the flat house shell with a door on the street and a back door to the yard. The job sign stands on
      the porch west of the door, and the house sign east of the door.
    - The work blocks: the work block of the vanilla profession of the job in the west back corner, and a barrel next
      to the door.
    - The home: 2 beds side by side in the east half, with the heads to the east. The path from the door to the back
      door (x = 4) goes along the feet of the beds.
    - The yard: ground(x, z) gives the ground block of each position, and plants(x, z) gives the block above it, or
      None. A low sandstone wall goes around the yard, with a lantern on a pillar at each back corner.

    The caller adds the blocks of the job, then gives the template to shaped.
    """
    t = sun_workshop_shell(rng, SUN_SHED)
    mid = FW // 2
    back = SUN_SHED - 1
    for y, half in ((1, "lower"), (2, "upper")):
        t.set(mid, y, back, "minecraft:spruce_door",
              {"facing": "south", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    t.set(1, 1, back - 1, *work)
    t.set(mid - 1, 1, 2, *BARREL)
    lantern(t, FW - 2, 1, 2)
    home_beds(t, (mid + 1, back - 3), (mid + 1, back - 2), facing="east")
    job_sign(t, mid - 1, 1, 0, job, icon)
    for x in range(FW):
        for z in range(SUN_SHED, CD):
            if x in SUN_YARD_X and z in SUN_YARD_Z:
                t.set(x, 0, z, *block(ground(x, z)))
                plant = plants(x, z)
                if plant:
                    t.set(x, 1, z, *block(plant))
            else:
                t.set(x, 0, z, "minecraft:sandstone")
                pillar = x in (0, FW - 1) and z == CD - 1
                t.set(x, 1, z, "minecraft:cut_sandstone" if pillar else SUN_WALL)
    for x in (0, FW - 1):
        lantern(t, x, 2, CD - 1)
    return t


DECORATED_POT = ("minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})


def sun_apiary():
    """The Sunreach building of the beekeeper: 2 beehives on the ground among the flowers, a path of coarse dirt, and
    pots of honey in the house. A walk in the yard reaches each hive. The yard has no campfire: a campfire burns, and
    the heart takes it as a brazier."""
    rng = random.Random(85)
    mid, row = SUN_WATER
    hives = ((2, row), (FW - 3, row))
    flowers = FLOWERS + ("minecraft:oxeye_daisy", "minecraft:allium")

    def ground(x, z):
        return "minecraft:coarse_dirt" if x == mid else GRASS

    def plants(x, z):
        if (x, z) in hives:
            return "minecraft:beehive", {"facing": "south", "honey_level": str(rng.randint(2, 5))}
        if x == mid or (x + z) % 2:
            return None
        return flowers[rng.randrange(len(flowers))]

    t = sun_yard_workshop(rng, "beekeeper", "minecraft:honeycomb", ground, plants)
    for x in (1, 2):
        t.set(x, 1, 2, *DECORATED_POT)
    return shaped(t)


def sun_fisher_hut():
    """The Sunreach building of the fisher: a house with barrels (the work block of the vanilla fisherman) and a tub,
    and a yard with a pond for the live catch. The pond is a row of waterlogged slabs, with a path of coarse dirt next
    to it: a builder stands on the path to add the water."""
    rng = random.Random(87)
    pond = range(2, FW - 2)
    row = CD - 2

    def ground(x, z):
        if z == row and x in pond:
            return WATER_SLAB
        return "minecraft:coarse_dirt" if z == row - 1 or x == FW // 2 else GRASS

    def plants(x, z):
        return BARREL if (x, z) in ((2, SUN_SHED), (FW - 3, SUN_SHED)) else None

    t = sun_yard_workshop(rng, "fisher", "minecraft:cod", ground, plants, work=BARREL)
    t.set(1, 1, 2, *WATER_CAULDRON)
    # The nets: cobwebs high on the side wall, out of the way.
    for z in (2, 3):
        t.set(1, 3, z, "minecraft:cobweb")
    return shaped(t)


def sun_workshop(rng, job, icon, beds, wide_front=False, wall_lower=None):
    """The house-workshop of a job in a Sunreach flat house of FW x CD blocks (the baker, the pastry cook, the butcher,
    and the jobs with no yard of style_jobs: the sawyers, the mason, the smelters, the blacksmith, the tinker, the
    carpenter, the weaver and the papermaker), with a door on the street or an open shop front. The job sign
    stands on the porch west of the door, and the house sign east of the door. beds gives the foot of each bed of the
    home (see home_beds): the head is 1 block to the back wall. The walls of a flat house have no lower layer of other
    blocks: the workshop does not use wall_lower. The caller adds the blocks of the job, then gives the template to
    shaped."""
    t = sun_workshop_shell(rng, wide_front=wide_front)
    home_beds(t, *beds)
    job_sign(t, FW // 2 - 1, 1, 0, job, icon)
    return t


def sun_oven(t):
    """A brick oven on the back wall of a Sunreach house: a furnace in bricks, with a chimney of bricks through the
    flat roof."""
    x, z = FW // 2, CD - 2
    t.set(x, 1, z, "minecraft:furnace", {"facing": "north", "lit": "false"})
    for dx in (-1, 0, 1):
        t.set(x + dx, 2, z, "minecraft:bricks")
        if dx:
            t.set(x + dx, 1, z, "minecraft:bricks")
    for y in range(3, FH):
        t.set(x + 1, y, z, "minecraft:bricks")


def sun_bakery():
    """The Sunreach building of the baker: a brick oven, a crafting table to knead the dough, barrels of flour, the
    wheat on hay bales, pots of grain and a cake on the counter."""
    rng = random.Random(88)
    t = sun_workshop(rng, "baker", "minecraft:bread", ((1, 3), (FW - 2, 3)))
    sun_oven(t)
    t.set(1, 1, CD - 2, "minecraft:crafting_table")
    for x, z in ((1, CD - 4), (2, CD - 2)):
        t.set(x, 1, z, *BARREL)
    for x, y, z in ((FW - 2, 1, CD - 2), (FW - 2, 1, CD - 3), (FW - 2, 2, CD - 2)):
        t.set(x, y, z, "minecraft:hay_block", LOG_Y)
    for z in (6, 7):
        t.set(FW - 2, 1, z, *DECORATED_POT)
    t.set(FW - 2, 1, 2, *TOP_SLAB)
    t.set(FW - 2, 2, 2, "minecraft:cake", {"bites": "0"})
    lantern(t, 1, 1, 2)
    lantern(t, 1, 1, 7)
    return shaped(t)


def sun_pastry_shop():
    """The Sunreach building of the pastry cook: an open shop front, a counter with cakes, a brick oven, pumpkins and
    barrels of sugar."""
    rng = random.Random(89)
    t = sun_workshop(rng, "pastry_cook", "minecraft:pumpkin_pie", ((1, 5), (FW - 2, 5)), wide_front=True)
    sun_oven(t)
    # The counter behind the open front, with a free passage in the middle.
    for x in (1, 2, FW - 3, FW - 2):
        t.set(x, 1, 3, *TOP_SLAB)
    for x in (2, FW - 3):
        t.set(x, 2, 3, "minecraft:candle_cake", {"lit": "false"})
    for x, y in ((1, 1), (2, 1), (1, 2)):
        t.set(x, y, CD - 2, "minecraft:pumpkin")
    for z in (CD - 2, CD - 3):
        t.set(FW - 2, 1, z, *BARREL)
    lantern(t, 1, 1, 8)
    lantern(t, FW - 2, 1, 8)
    return shaped(t)


def sun_butchery():
    """The Sunreach building of the butcher: 2 smokers (the work block of the vanilla butcher), a block of coal, a
    chopping block, and a rack of chains under the roof. The chains leave 2 blocks of space under them."""
    rng = random.Random(90)
    t = sun_workshop(rng, "butcher", "minecraft:cooked_beef", ((1, 4), (FW - 2, 4)))
    for x in (5, 6):
        t.set(x, 1, CD - 2, "minecraft:smoker", {"facing": "north", "lit": "false"})
    t.set(FW - 2, 1, CD - 2, "minecraft:coal_block")
    for x in (2, 3, 6):
        t.set(x, FLAT_WALL_TOP, 8, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    t.set(2, 1, 3, "minecraft:stripped_oak_log", LOG_Y)
    for z in (CD - 2, CD - 3):
        t.set(1, 1, z, *BARREL)
    t.set(FW - 2, 1, 3, *WATER_CAULDRON)
    lantern(t, 1, 1, 2)
    lantern(t, 1, 1, 7)
    return shaped(t)


# The beds of a Sunreach workshop with no yard of style_jobs (the sawyers, the mason, the smelters, the blacksmith, the
# tinker, the carpenter, the weaver and the papermaker): on the 2 side walls, in the middle of the room. The front of
# the room stays free for the counter and the tools, and the back of the room for the work.
SUN_CRAFT_BEDS = ((1, 5), (FW - 2, 5))

# The Sunreach style of the house-workshops: flat houses of FW x CD blocks (see WorkshopStyle).
SUNREACH = WorkshopStyle(
    width=FW, depth=CD, shed=SUN_SHED, water=SUN_WATER, seed=20, yard_workshop=sun_yard_workshop,
    workshop=sun_workshop, oven=sun_oven, beds=SUN_CRAFT_BEDS,
    sawmill_lantern=(FW - 2, 1, 3), mason_cutter=(2, 3), mason_lantern=(FW - 2, 1, 8),
    smeltery=SmelteryLayout(furnaces=(1, 2), ore_x=FW - 2, coal=(FW - 3, CD - 2), cauldron=(FW - 2, CD - 4),
                            lanterns=((1, 1, 8),)))

# The house-workshops of the Sunreach style, by job: each job of JOBS has a piece of the style.
SUNREACH_JOBS = {
    **style_jobs(SUNREACH),
    "beekeeper": sun_apiary,
    "fisher": sun_fisher_hut,
    "baker": sun_bakery,
    "pastry_cook": sun_pastry_shop,
    "butcher": sun_butchery,
}


# ---------------------------------------------------------------- market

# The market square takes the plot of one house. x goes along the street, z goes away from the street: z = 0 is the
# street side, with the jigsaw connector house_front in the middle. Each layout has:
# - size: (width along the street, depth);
# - post: (x, z) of the market post, in the middle of the free part of the square;
# - stalls: (x, z, facing) of each stall: the corner with the lowest x and z, and the direction of the counter.
# The caravan yard is in the corner at the west of the street side (see caravan_yard).
MARKET_LAYOUTS = {
    # Base, Sunreach and maritime streets: 15 blocks along the street, 9 blocks deep (the depth of a house of 9 x 9).
    "wide": {
        "size": (15, 9),
        "post": (7, 4),
        "stalls": ((1, 5, "east"), (11, 1, "west"), (11, 5, "west")),
    },
    # Valeholm streets: 9 blocks along the street, 15 blocks deep (inside the space of a long house of 9 x 17).
    "deep": {
        "size": (9, 15),
        "post": (4, 7),
        "stalls": ((1, 11, "north"), (5, 11, "north")),
    },
}
MARKET_HEIGHT = 6
# The wool of the awning of each stall, in the order of the stalls of the layout.
AWNINGS = ("red", "yellow", "blue", "lime")
# The direction of a counter: the step (dx, dz) from the middle of the stall to the counter.
COUNTER_SIDES = {"east": (1, 0), "west": (-1, 0), "north": (0, -1)}


def stall(t, x0, z0, facing, color):
    """A stall of 3 x 3 blocks: 4 fence posts at the corners, an awning of wool stripes, a counter of a top slab with a
    lantern on it, and a barrel in the middle of the stall.

    The counter is in the middle of the side of the stall in the direction facing. The barrel is not next to a post:
    a fence connects to a barrel."""
    for x in (x0, x0 + 2):
        for z in (z0, z0 + 2):
            for y in (1, 2):
                t.set(x, y, z, "minecraft:spruce_fence")
    dx, dz = COUNTER_SIDES[facing]
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            # The white stripe goes from the counter to the back of the stall.
            stripe = (x == x0 + 1) if dx == 0 else (z == z0 + 1)
            t.set(x, 3, z, f"minecraft:{'white' if stripe else color}_wool")
    mx, mz = x0 + 1, z0 + 1
    t.set(mx + dx, 1, mz + dz, *TOP_SLAB)
    lantern(t, mx + dx, 2, mz + dz)
    t.set(mx, 1, mz, *BARREL)


def caravan_yard(t):
    """The caravan yard: the corner at the west of the street side (x = 0 to 3, z = 1 to 3), out of the middle of the
    square. The mules come in from the street.

    - A hitching rail of 3 fences on the west edge (x = 0), with a lantern on each end.
    - The place of the mules: x = 1 and 2, z = 1 to 3.
    - A water trough (a water cauldron) and a hay bale at x = 3. They are not next to a fence."""
    for z in (1, 2, 3):
        t.set(0, 1, z, "minecraft:spruce_fence")
    for z in (1, 3):
        lantern(t, 0, 2, z)
    t.set(3, 1, 1, *WATER_CAULDRON)
    t.set(3, 1, 3, "minecraft:hay_block", LOG_Y)


def market(layout):
    """The market square: the place where the workers of the village trade, and where a caravan stops. It takes the
    plot of one house (see MARKET_LAYOUTS).

    - The square is paved: cobblestone with a border of stone bricks. It is open on all the sides, and the side on the
      street (z = 0) is free.
    - The market post stands in the free middle of the square: a log with a lantern on it, on chiseled stone bricks.
      The market sign stands on the street side of the post and faces the street. The heart finds the market by this
      sign: the market post is behind it (tag folkhold_world:market_posts).
    - The stalls (see stall) along the sides of the free middle. The counters face the middle.
    - The free middle: 7 x 7 blocks around the market post, for the workers.
    - The caravan yard in a corner of the street side (see caravan_yard).
    - A lamp post at each corner.
    """
    spec = MARKET_LAYOUTS[layout]
    width, depth = spec["size"]
    rng = random.Random(81)
    t = Template(width, MARKET_HEIGHT, depth)
    t.fill(0, 1, 0, width - 1, MARKET_HEIGHT - 1, depth - 1, "minecraft:air")
    for x in range(width):
        for z in range(depth):
            border = x in (0, width - 1) or z in (0, depth - 1)
            t.set(x, 0, z, "minecraft:stone_bricks" if border else cobble(rng))
    px, pz = spec["post"]
    t.set(px, 0, pz, "minecraft:chiseled_stone_bricks")
    for y in (1, 2, 3):
        t.set(px, y, pz, "minecraft:stripped_spruce_log", LOG_Y)
    lantern(t, px, 4, pz)
    building_sign(t, px, 1, pz - 1, "market")
    for (x0, z0, facing), color in zip(spec["stalls"], AWNINGS):
        stall(t, x0, z0, facing, color)
    caravan_yard(t)
    for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
        lamp_post(t, x, z)
    for y in (1, 2):
        connect_fences(t, y)
    t.jigsaw(width // 2, 1, 0, "north_up", HOUSE_FRONT, "minecraft:empty", "minecraft:empty", "minecraft:air")
    return t


# The job buildings: the piece job_<id> of each job, in the pool village/jobs of each variant. The id is the name of
# the job file.
JOBS = {
    "farmer": farm,
    "gardener": garden,
    "gardener_jungle": jungle_garden,
    "forager": forager_hut,
    "beekeeper": apiary,
    "baker": bakery,
    "pastry_cook": pastry_shop,
    "rancher": ranch,
    "butcher": butchery,
    "fisher": fisher_hut,
    **{job: functools.partial(lumber_yard, job, wood) for job, wood in LUMBERJACKS.items()},
    **{job: functools.partial(sawmill, job, wood) for job, wood in SAWYERS.items()},
    "miner": mine,
    "digger": dig_yard,
    "mason": mason_workshop,
    **{job: functools.partial(smeltery, job) for job in SMELTERS},
    "blacksmith": functools.partial(forge, home=True),
    "tinker": tinker_workshop,
    "fletcher": fletcher_range,
    "carpenter": carpenter_workshop,
    "tanner": tannery,
    "shepherd": sheepfold,
    "weaver": weaving_house,
    "dyer": dye_works,
    "papermaker": paper_mill,
}
# The house-workshops of the styles of the cultures, by culture and job.
STYLE_JOBS = {"valeholm": NORSE_JOBS, "sunreach": SUNREACH_JOBS}
# The cultures whose style has a house-workshop for each job of JOBS. Add a culture here when its style is complete.
COMPLETE_STYLES = ("valeholm", "sunreach")


def check_style_jobs():
    """Stops the script when a style has a piece for a job that is not in JOBS, or when a complete style (see
    COMPLETE_STYLES) has no piece for a job of JOBS."""
    for culture, jobs in STYLE_JOBS.items():
        unknown = sorted(jobs.keys() - JOBS.keys())
        if unknown:
            raise SystemExit(f"The style {culture} has pieces for jobs that are not in JOBS: {unknown}")
        missing = sorted(JOBS.keys() - jobs.keys())
        if culture in COMPLETE_STYLES and missing:
            raise SystemExit(f"Each job needs a house-workshop of the style {culture}. No piece for: {missing}")


check_style_jobs()

PIECES = {
    "plaza": plaza,
    "street": street,
    "street_bank": lambda: street(bank=True),
    "street_jobs": lambda: street(jobs=True),
    "street_end": street_end,
    "tower": tower_piece,
    "gate": gate_piece,
    "house_cottage": cottage,
    "house_barracks": barracks,
    "house_forge": forge,
    "house_storehouse": storehouse,
    "bank": bank,
    "market": functools.partial(market, "wide"),
    **{f"job_{job}": build for job, build in JOBS.items()},
}


# The styles of the cultures: the pieces that a culture changes. The other pieces are the pieces of PIECES. The
# villages of a culture with no style here use PIECES.
STYLES = {
    "valeholm": {
        "street": norse_street,
        "street_bank": lambda: norse_street(bank=True),
        "street_jobs": lambda: norse_street(jobs=True),
        "house_cottage": norse_cottage,
        "house_cottage_side": norse_cottage_side,
        "house_barracks": norse_barracks,
        "house_forge": norse_forge,
        "house_storehouse": norse_storehouse,
        "house_storehouse_side": norse_storehouse_side,
        "market": functools.partial(market, "deep"),
        **{f"job_{job}": build for job, build in NORSE_JOBS.items()},
    },
    "sunreach": {
        "house_cottage": sun_cottage,
        "house_cottage_courtyard": sun_cottage_courtyard,
        "house_barracks": sun_barracks,
        "house_forge": sun_forge,
        "house_storehouse": sun_storehouse,
        **{f"job_{job}": build for job, build in SUNREACH_JOBS.items()},
    },
}
# The pieces of the maritime villages: the village of a site on the coast or on an island, in all the cultures. The
# other pieces are the pieces of PIECES.
MARITIME = {
    "house_fishing_hut": fishing_hut,
    "house_fish_market": fish_market,
    "house_smokehouse": smokehouse,
}
MARITIME_HOUSES = [(3, "house_fishing_hut"), (1, "house_fish_market"), (1, "house_smokehouse"),
                   (2, "house_cottage"), (1, "house_barracks"), (1, "house_storehouse")]
# The blocks of Immersive Weathering in the maritime pieces, when the mod is present (see OptionalReplaceProcessor).
WEATHERED = {
    "minecraft:stone": "immersive_weathering:mossy_stone",
    "minecraft:stone_slab": "immersive_weathering:mossy_stone_slab",
    "minecraft:iron_bars": "immersive_weathering:rusted_iron_bars",
}

# The houses of the pool of the houses of each style, with their weights. The jigsaw tries the houses in a random
# order with these weights: a house that touches another piece goes to the next house, and a plot with no house that
# fits stays free. The styles that are not here use HOUSES.
HOUSES = [(3, "house_cottage"), (2, "house_barracks"), (1, "house_forge"), (2, "house_storehouse")]
STYLE_HOUSES = {
    "valeholm": [(2, "house_cottage"), (2, "house_cottage_side"), (2, "house_barracks"), (1, "house_forge"),
                 (1, "house_storehouse"), (1, "house_storehouse_side")],
    "sunreach": [(3, "house_cottage"), (2, "house_cottage_courtyard"), (2, "house_barracks"), (1, "house_forge"),
                 (2, "house_storehouse")],
}

# ---------------------------------------------------------------- first house-workshops

# The job files and the item values of the economy.
JOB_FILES = os.path.join(ROOT, "main", "resources", "data", "folkhold_world", "folkhold", "jobs")
ITEM_FILE = os.path.join(ROOT, "main", "resources", "data", "folkhold_world", "folkhold", "items.json")
# The jobs of the food family (see the table of the jobs in docs/design.md). One output of each job is food.
FOOD_JOBS = ("farmer", "gardener", "gardener_jungle", "baker", "pastry_cook", "rancher", "butcher", "fisher",
             "beekeeper")
# The culture of the pieces of all the cultures: the culture with no style in STYLES. Its multipliers give the weights
# of the first house-workshops of these pieces. The maritime pieces are for all the cultures: no multiplier.
COMMON_CULTURE = "ashmark"
# The biomes of the vanilla biome tags in the job files (Minecraft 1.21.1).
BIOME_TAGS = {
    "#minecraft:is_taiga": ["minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga",
                            "minecraft:old_growth_spruce_taiga"],
    "#minecraft:is_jungle": ["minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"],
    "#minecraft:is_savanna": ["minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"],
    "#minecraft:is_ocean": ["minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_ocean",
                            "minecraft:deep_lukewarm_ocean", "minecraft:frozen_ocean", "minecraft:ocean",
                            "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean"],
    "#minecraft:is_beach": ["minecraft:beach", "minecraft:snowy_beach"],
    "#minecraft:is_river": ["minecraft:river", "minecraft:frozen_river"],
}


@functools.cache
def job_files():
    """The job files of the economy, by job id (the name of the file)."""
    jobs = {}
    for name in sorted(os.listdir(JOB_FILES)):
        with open(os.path.join(JOB_FILES, name), encoding="utf-8") as f:
            jobs[name.removesuffix(".json")] = json.load(f)
    with open(ITEM_FILE, encoding="utf-8") as f:
        items = json.load(f)
    for job in FOOD_JOBS:
        food = [out["item"] for out in jobs[job]["outputs"] if items.get(out["item"], {}).get("food", 0) > 0]
        assert food, f"The food job {job} makes no food"
    return jobs


def job_biomes(job):
    """The biomes of a job file, with the biomes of its tags, or None for all the biomes."""
    entries = job_files()[job].get("biomes")
    if not entries:
        return None
    return {biome for entry in entries for biome in (BIOME_TAGS[entry] if entry.startswith("#") else [entry])}


def first_jobs(v, food):
    """The first house-workshops of a variant, with their weights: the pieces of the pools jobs_food (food) and
    jobs_start.

    The jigsaw does not know the level of the settlement: a job goes into the pools only with a minimum level of 1. A
    job also needs a profession (a worker of the job trades) and a biome list with all the biomes of the variant: the
    job is valid in each village of the variant. The weight is the weight of the job file times the multiplier of the
    culture of the variant.
    """
    culture = None if v.maritime else v.culture or COMMON_CULTURE
    result = []
    for job in JOBS:
        data = job_files()[job]
        biomes = job_biomes(job)
        if data["min_level"] != 1 or "profession" not in data or (food and job not in FOOD_JOBS):
            continue
        if biomes is not None and not set(v.biomes) <= biomes:
            continue
        result.append((data["weight"] * data.get("cultures", {}).get(culture, 1), f"job_{job}"))
    assert result, f"No first house-workshop in {v.path('')}"
    return result


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

    def __init__(self, name, biomes, blocks, culture=None, maritime=False):
        self.name = name
        self.biomes = biomes
        self.blocks = blocks
        self.culture = culture
        self.maritime = maritime

    @property
    def base(self):
        return not self.blocks

    def of(self, culture):
        """The same variant for the style of a culture."""
        return Variant(self.name, self.biomes, self.blocks, culture)

    def at_sea(self):
        """The same variant for a maritime village."""
        return Variant(self.name, self.biomes, self.blocks, maritime=True)

    @property
    def pieces(self):
        """The pieces of the variant: the pieces of PIECES, with the pieces of its style."""
        return {**PIECES, **(MARITIME if self.maritime else STYLES.get(self.culture, {}))}

    def path(self, name):
        """The path of a file of the village for this variant, with no namespace and no extension. The files of the
        style of a culture are in a folder with the name of the culture, and the files of a maritime village are in the
        folder "maritime"."""
        parts = ["village"]
        if self.culture:
            parts.append(self.culture)
        if self.maritime:
            parts.append("maritime")
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
        """The template with the materials of this variant. The jigsaw connectors use the pools of the variant. The
        fixed positions of the template (see job_material) keep their blocks."""
        out = Template(*t.size)
        prefix = f"{NS}:village/"
        for pos, ((name, props), nbt) in t.blocks.items():
            if nbt is not None and nbt.get("id") == "minecraft:jigsaw" and nbt["pool"].startswith(prefix):
                nbt = dict(nbt, pool=self.id(nbt["pool"][len(prefix):]))
            out.blocks[pos] = ((name, props) if pos in t.fixed else self.replace(name, props), nbt)
        if t.shape_walls:
            connect_walls(out)
        return out


# The variants of the village, with their biomes. The tag of the biomes of the village has the biomes of all the
# variants. In a biome of the tag that no other variant has, the village uses the base variant.
BASE = Variant("taiga", ["minecraft:taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:windswept_hills",
                        "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills", "minecraft:grove",
                        "minecraft:snowy_slopes"], {})
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

    houses = MARITIME_HOUSES if v.maritime else STYLE_HOUSES.get(v.culture, HOUSES)

    return {
        "plaza": pool([(1, house("plaza"))]),
        "houses": pool([(weight, house(piece)) for weight, piece in houses]
                       + [(1, {"element_type": "minecraft:empty_pool_element"})]),
        "homes": pool([(1, house("house_cottage"))]),
        "bank": pool([(1, house("bank"))]),
        "jobs": pool([(1, house(f"job_{job}")) for job in JOBS]),
        "jobs_food": pool([(weight, house(piece)) for weight, piece in first_jobs(v, True)], v.id("jobs_start")),
        "jobs_start": pool([(weight, house(piece)) for weight, piece in first_jobs(v, False)], v.id("houses")),
        "market": pool([(1, house("market"))]),
        "streets_first": pool([(1, street("street"))], v.id("street_ends")),
        "streets_jobs": pool([(1, street("street_jobs"))], v.id("street_ends")),
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
    weathered = [{
        "processor_type": f"{NS}:optional_replace",
        "replacements": [{"input": block, "output": out} for block, out in WEATHERED.items()],
    }] if v.maritime else []
    return {
        "foundation": {"processors": weathered + [{
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
    """The variants of all the cultures, then the variants of the styles of the cultures, then the maritime
    variants."""
    return (list(VARIANTS) + [v.of(culture) for culture in STYLES for v in VARIANTS]
            + [v.at_sea() for v in VARIANTS])


def structure():
    """The village structure: the base variant, then the list of the other variants with their biomes. A variant of
    the style of a culture has the culture. The base variant of a style has no biome: it is the variant of the culture
    in the biomes of no other variant of the culture. The maritime variants are for a site on the coast."""
    base = village_settings(BASE)
    variants = []
    for v in all_variants():
        if v.base and not v.culture and not v.maritime:
            continue
        settings = village_settings(v)
        entry = {"biomes": [] if v.base else v.biomes, "start_pool": settings["start_pool"],
                 "wall": settings["wall"], "homes": settings["homes"]}
        if v.culture:
            entry["culture"] = v.culture
        if v.maritime:
            entry["maritime"] = True
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
        for name, build in v.pieces.items():
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
