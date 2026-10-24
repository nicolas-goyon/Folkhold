"""Heart of a village: a block of carved stone around a warm core that glows.

A frame of light stone surrounds a dark panel on each face. A round window in the panel shows the core, with a gold
ring around it. The core glows in the dark (the block also gives light 7 in the game). The bottom has no window.
"""
import numpy as np

from _palette import EMBER, GOLD, STONE
from pixvox import Block, Ramp, Volume

FRAME = Ramp([STONE[2], STONE[3], STONE[4]])
PANEL = Ramp([STONE[0], STONE[1], STONE[2]])


def build():
    v = Volume(16)
    x, y, z = v.X + 0.5, v.Y + 0.5, v.Z + 0.5
    d = np.sort(np.stack([np.minimum(v.X, 15 - v.X), np.minimum(v.Y, 15 - v.Y), np.minimum(v.Z, 15 - v.Z)], -1), -1)
    face_d, edge_d = d[..., 0], d[..., 1]
    skin = face_d == 0

    # The frame: 2 voxels along each edge, with the grain of the stone. Its outer edges catch the light, except at the
    # bottom.
    frame = skin & (edge_d < 2)
    v.noise(FRAME, cells=4, octaves=2, seed=41, mask=frame, weights=[2, 4, 1])
    v.set(frame & (edge_d == 0) & (v.Y != 0), STONE[4])
    v.set(frame & (v.Y == 0), STONE[1])
    panel = skin & (edge_d >= 2)
    v.noise(PANEL, cells=4, octaves=2, seed=43, mask=panel, weights=[1, 4, 2])
    v.set(panel & (edge_d == 2), STONE[0])

    # The round window of each face except the bottom, with a gold ring around it.
    radius = np.where((v.Z == 0) | (v.Z == 15), np.hypot(x - 8, y - 8),
                      np.where((v.X == 0) | (v.X == 15), np.hypot(z - 8, y - 8), np.hypot(x - 8, z - 8)))
    windowed = skin & (v.Y != 0) & (edge_d >= 2)
    ring = windowed & (radius >= 3.4) & (radius < 4.4)
    core = windowed & (radius < 3.4)
    v.set(ring, GOLD[2])
    v.set(ring & (v.Y > 8), GOLD[3])
    v.set(ring & (v.Y < 7) & (v.Y != 15), GOLD[1])
    v.set(core, EMBER[2])
    v.set(core & (radius < 2.4), EMBER[3])
    v.set(core & (radius < 1.2), EMBER[4])
    return Block("village_heart", v, seamless=False, emissive=core, emission=12)
