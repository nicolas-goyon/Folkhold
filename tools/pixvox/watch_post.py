"""Watch post: the marker of an archer post on a tower. The archer stands on top of it.

A hatch of spruce planks in the floor of the platform of a tower: the planks of the floor, an iron frame and an iron
ring in the middle of the top. The ring shows where the archer stands. The sides have the same planks and an iron
band at the top, so the block also looks correct out of a floor.
"""
import numpy as np

from _palette import IRON, SPRUCE, SPRUCE_DARK
from pixvox import Block, Volume, equalize, perlin


def build():
    v = Volume(16)
    colors = np.array(SPRUCE, dtype=np.uint8)
    sides = np.minimum.reduce([v.X, 15 - v.X, v.Z, 15 - v.Z]) == 0
    top, bottom = v.Y == 15, v.Y == 0

    # Planks of 4 voxels with a dark joint: horizontal on the sides, along the X axis on the top and the bottom.
    grain = equalize(perlin(v.shape, (2, 6, 2), seed=41, octaves=2))
    level = np.where(grain > 0.8, 3, np.where(grain < 0.2, 1, 2))
    level = np.where(v.Y % 4 == 3, np.maximum(level, 3), level)
    level = np.where(v.Y % 4 == 0, 0, level)
    v.set(sides, colors[level])
    grain = equalize(perlin(v.shape, (6, 2, 2), seed=43, octaves=2))
    level = np.where(grain > 0.75, 3, np.where(grain < 0.25, 1, 2))
    level = np.where(v.Z % 4 == 3, np.maximum(level, 3), level)
    level = np.where(v.Z % 4 == 0, 0, level)
    v.set((top | bottom) & ~sides, colors[level])

    # The iron frame on the edges of the top, and the iron band at the top of the sides. Rivets at the corners.
    d = np.sort(np.stack([np.minimum(v.X, 15 - v.X), np.minimum(v.Y, 15 - v.Y), np.minimum(v.Z, 15 - v.Z)], -1), -1)
    face_d, edge_d, corner_d = d[..., 0], d[..., 1], d[..., 2]
    frame = top & (np.minimum(np.minimum(v.X, 15 - v.X), np.minimum(v.Z, 15 - v.Z)) == 0)
    band = sides & (v.Y >= 14)
    v.set(frame | band, IRON[2])
    v.set(band & (v.Y == 14), IRON[1])
    v.set((frame | band) & (edge_d == 0) & top, IRON[3])
    rivet = (face_d == 0) & (edge_d == 1) & (corner_d == 1) & (v.Y >= 14)
    v.set(rivet, IRON[4])

    # The iron ring in the middle of the top, with a light upper half and a dark lower half.
    r = np.hypot(v.X + 0.5 - 8, v.Z + 0.5 - 8)
    ring = top & (r >= 2.4) & (r < 4.0)
    v.set(ring, IRON[2])
    v.set(ring & (v.Z < 8), IRON[3])
    v.set(ring & (r >= 3.4) & (v.Z >= 8), IRON[1])
    v.set(top & (r < 2.4), SPRUCE_DARK[1])
    return Block("watch_post", v, seamless=False)
