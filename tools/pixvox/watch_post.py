"""Watch post: the marker of an archer post on a tower. The archer stands on top of it.

A post of stripped spruce with two iron bands. The top shows the end of the post with a painted target, so a builder
sees where the archer stands.
"""
import numpy as np

from _palette import IRON, PAINT_RED, PAINT_WHITE, SPRUCE
from pixvox import Block, Volume, perlin


def build():
    v = Volume(16)
    # Grain along the post: noise stretched on the Y axis.
    v.noise(SPRUCE, cells=(6, 1, 6), octaves=2, seed=21, weights=[1, 3, 4, 2, 0])
    sides = np.minimum.reduce([v.X, 15 - v.X, v.Z, 15 - v.Z]) == 0
    # Two iron bands, each with a lit top row and a shadow row under it.
    for y in (3, 11):
        band = sides & ((v.Y == y) | (v.Y == y + 1))
        v.set(band, IRON[2])
        v.set(band & (v.Y == y + 1), IRON[3])
        v.set(sides & (v.Y == y - 1), SPRUCE[0])
        rivets = band & (v.Y == y) & ((v.X + v.Z) % 5 == 2)
        v.set(rivets, IRON[4])
    # The two ends show the rings of the wood. The top also has a target of red and white paint.
    top = v.Y == 15
    wobble = perlin(v.shape, (3, 2, 3), seed=4, octaves=2)
    r = np.hypot(v.X + 0.5 - 8, v.Z + 0.5 - 8) + 0.6 * wobble
    rings = np.where(np.floor(r / 1.5) % 2 == 0, 2, 3)
    ends = (v.Y == 15) | (v.Y == 0)
    v.set(ends & ~sides, np.array(SPRUCE, dtype=np.uint8)[rings])
    v.set(top, np.array(SPRUCE, dtype=np.uint8)[rings])
    v.set(top & (r < 6.2), PAINT_WHITE[2])
    v.set(top & (r < 4.6), PAINT_RED[2])
    v.set(top & (r < 3.0), PAINT_WHITE[2])
    v.set(top & (r < 1.5), PAINT_RED[1])
    v.set(top & (r >= 6.2) & (r < 6.9), SPRUCE[1])
    return Block("watch_post", v, seamless=False)
