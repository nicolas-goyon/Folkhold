"""Patterns shared by the Folkhold blocks.

The patterns use the voxels of the faces of a block of 16 x 16 x 16 voxels. A voxel of an edge belongs to two faces:
the patterns place the joints away from the edges, so the two faces agree on the color of each edge voxel.
"""
import numpy as np


def masonry(v, ramp, *, course=4, length=8, offsets=(2, 6), seed=0, variation=1):
    """Paints courses of stones on the faces of a full block: dark joints, a light top edge on each stone.

    Args:
        v: the volume, 16 voxels on each side
        ramp: the colors, from the dark joints to the light edges (5 colors)
        course: the height of a course, in voxels
        length: the length of a stone, in voxels
        offsets: the position of the first vertical joint of the even and of the odd courses. A value from 1 to
            length - 2 keeps the joints away from the vertical edges of the block.
        seed: the seed of the tone of each stone
        variation: the number of steps of the ramp between the darkest and the lightest stone

    Returns:
        the mask of the joints
    """
    x, y, z = v.X, v.Y, v.Z
    north_south = (z == 0) | (z == 15)
    west_east = (x == 0) | (x == 15)
    sides = north_south | west_east
    row = y // course
    offset = np.where(row % 2 == 0, offsets[0], offsets[1])
    along = np.where(north_south, x, z)
    vertical = sides & ((along - offset) % length == 0)
    horizontal = sides & (y % course == 0)
    # The joints of the top and of the bottom: the joints of the top and the bottom courses go on across the face.
    top_offset = offsets[(15 // course) % 2]
    bottom_offset = offsets[0]
    top = (y == 15) & (((x - top_offset) % length == 0) | ((z - top_offset) % length == 0))
    bottom = (y == 0) & (((x - bottom_offset) % length == 0) | ((z - bottom_offset) % length == 0))
    joints = vertical | horizontal | top | bottom

    # The tone of each stone: a number from its course and its position along the face.
    stone = (row * 31 + ((along - offset) // length) * 7 + np.where(north_south, 3, 11) + seed) % 5
    stone = np.where(y == 15, ((x - top_offset) // length) * 5 + ((z - top_offset) // length) * 13 + seed, stone)
    stone = np.where(y == 0, ((x - bottom_offset) // length) * 5 + ((z - bottom_offset) // length) * 13 + seed,
                     stone)
    tone = 2 + (stone % (variation + 1)) - variation // 2
    light_edge = sides & (y % course == course - 1) & (y != 15)
    level = np.clip(tone + np.where(light_edge, 1, 0), 1, len(ramp) - 1)
    colors = np.array([ramp[i] for i in range(len(ramp))], dtype=np.uint8)
    v.set(None, colors[level])
    v.set(joints, ramp[0])
    return joints
