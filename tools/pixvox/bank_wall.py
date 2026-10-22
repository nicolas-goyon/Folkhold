"""Wall of the village bank: courses of deepslate stones with dark joints, and iron rivets.

The joints stay away from the vertical edges, so a wall of many blocks shows the same courses with no seam.
"""
import numpy as np

from _palette import DEEPSLATE, IRON
from _patterns import masonry
from pixvox import Block, Volume


def build():
    v = Volume(16)
    joints = masonry(v, DEEPSLATE, course=4, length=8, offsets=(2, 6), seed=3)
    v.noise(DEEPSLATE, cells=4, octaves=2, seed=8, mask=~joints, weights=[0, 1, 3, 2, 0])
    # A rivet on the middle of some stones: one bright voxel with its shadow below it.
    rivet = (~joints) & (v.Y % 8 == 2) & ((v.X + v.Z) % 8 == 6)
    shadow = np.roll(rivet, -1, axis=1) & ~joints
    v.set(shadow, DEEPSLATE[0])
    v.set(rivet, IRON[4])
    return Block("bank_wall", v)
