"""Chest of the village bank: dark oak planks in an iron frame, with a gold lock on each side.

The frame is part of the volume: each voxel of an edge has one color on its two faces. The top of the frame is light
and its bottom is dark, so the light of the frame is the same on all the faces. The block has no front, so each side
has a lock.
"""
import numpy as np

from _palette import DARK_OAK, GOLD, IRON
from pixvox import Block, Volume, equalize, perlin


def planks(v, mask, seed):
    """Horizontal planks of 3 voxels with a dark joint, on the side faces in the mask."""
    grain = equalize(perlin(v.shape, (2, 6, 2), seed=seed, octaves=2))
    level = np.full(v.shape, 2)
    level[grain > 0.8] = 3
    level[grain < 0.2] = 1
    level = np.where(v.Y % 4 == 3, 3, level)
    level = np.where(v.Y % 4 == 1, np.minimum(level, 1), level)
    level = np.where(v.Y % 4 == 0, 0, level)
    v.set(mask, np.array(DARK_OAK, dtype=np.uint8)[level])


def build():
    v = Volume(16)
    sides = np.minimum.reduce([v.X, 15 - v.X, v.Z, 15 - v.Z]) == 0
    planks(v, sides, seed=31)
    # The lid and the bottom: planks along the X axis.
    ends = (v.Y == 15) | (v.Y == 0)
    grain = equalize(perlin(v.shape, (6, 2, 2), seed=33, octaves=2))
    level = np.where(grain > 0.75, 3, np.where(grain < 0.25, 1, 2))
    level = np.where(v.Z % 4 == 0, 0, level)
    v.set(ends & ~sides, np.array(DARK_OAK, dtype=np.uint8)[level])

    # Distance of each voxel to the nearest face, edge and corner.
    d = np.sort(np.stack([np.minimum(v.X, 15 - v.X), np.minimum(v.Y, 15 - v.Y), np.minimum(v.Z, 15 - v.Z)], -1), -1)
    face_d, edge_d, corner_d = d[..., 0], d[..., 1], d[..., 2]
    top, bottom = v.Y == 15, v.Y == 0
    frame = (face_d == 0) & (edge_d < 2)
    v.set(frame, IRON[2])
    v.set(frame & (edge_d == 0), IRON[3])
    v.set(frame & (edge_d == 0) & top, IRON[4])
    v.set(frame & bottom, IRON[1])
    v.set(frame & (edge_d == 1) & ~top & ~bottom & (v.Y == 14), IRON[1])
    corner = (face_d == 0) & (corner_d < 3)
    v.set(corner, IRON[3])
    v.set(corner & (edge_d == 0) & top, IRON[4])
    rivet = (face_d == 0) & (edge_d == 1) & (corner_d == 1)
    v.set(rivet, IRON[4])

    # Two iron straps across the lid.
    straps = top & ((v.X == 5) | (v.X == 10))
    v.set(straps, IRON[3])

    # The gold lock in the middle of each side: a plate of 4 x 5 voxels with a dark keyhole.
    along = np.where((v.Z == 0) | (v.Z == 15), v.X, v.Z)
    plate = sides & (edge_d >= 2) & (along >= 6) & (along <= 9) & (v.Y >= 6) & (v.Y <= 10)
    v.set(plate, GOLD[2])
    v.set(plate & (v.Y == 10), GOLD[3])
    v.set(plate & (v.Y == 6), GOLD[1])
    keyhole = plate & ((along == 7) | (along == 8))
    v.set(keyhole & (v.Y == 8), DARK_OAK[0])
    v.set(keyhole & (v.Y == 7), GOLD[0])
    return Block("bank_chest", v, seamless=False)
