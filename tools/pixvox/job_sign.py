"""Job sign: the sign stand in front of the building of a job.

The stand is the stand of the building signs (see building_signs.py): two spruce posts on feet, a cap on each post, a
cross bar and a plaque that hangs from two iron hooks. The plaque has a raised frame on its two faces and an empty
field of 8 x 8 pixels in the middle. The model has no icon: the block entity renderer of the game (JobSignRenderer)
shows the icon item of the job on the field, on the two faces. One block serves all the jobs.

The model faces north: the front of the plaque is the north face. The field is flat at z = 7 on the front and at
z = 9 on the back, from x = 4 to 12 and from y = 8 to 16. build() makes the model job_sign for export.py.
"""
import numpy as np

from _palette import IRON
from building_signs import OUTLINE, paint
from pixvox import Model, Volume, ramp

# The plaque: a light field of painted wood in a spruce frame with a gold trim.
FIELD = ramp("#d9c597", n=3, spread=0.10, hue_shift=6)
TRIM = ramp("#c89a3e", n=3, spread=0.20, hue_shift=8)


def plaque(v):
    """Paints the plaque: the board (z 7 and 8) and the raised frame of each face (z 6 at the front, z 9 at the back).

    A pixel (u, row) of the plaque is at y = 19 - row. It is at x = 15 - u on the front (north face) and at x = u on
    the back (south face). The plaque has the pixels 2 to 13: an outline, a trim ring and the field (4 to 11).
    """
    x, y, z = v.X, v.Y, v.Z
    row = 19 - y
    for board_z, relief_z, u in ((7, 6, 15 - x), (8, 9, x)):
        inside = (u >= 2) & (u <= 13) & (row >= 2) & (row <= 13)
        # The distance to the edge of the plaque: 0 is the outline, 1 is the trim ring, 2 and more is the field.
        ring = np.minimum.reduce([u - 2, 13 - u, row - 2, 13 - row])
        board = inside & (z == board_z)
        relief = inside & (z == relief_z) & (ring <= 1)
        layer = board | relief
        v.set(layer & (ring == 0), OUTLINE)
        # The trim ring has a bevel: light on the top and on the left, dark on the bottom and on the right.
        trim_ring = layer & (ring == 1)
        v.set(trim_ring, TRIM[1])
        v.set(trim_ring & (((row == 3) & (u < 12)) | ((u == 3) & (row < 12))), TRIM[2])
        v.set(trim_ring & (((row == 12) & (u > 3)) | ((u == 12) & (row > 3))), TRIM[0])
        field = board & (ring >= 2)
        v.set(field, FIELD[1])
        v.noise(FIELD, cells=8, octaves=1, seed=7, mask=field, weights=[1, 12, 1])


def sign_volume():
    """The volume of the job sign."""
    v = Volume(16, 24, 16)
    seed = 627
    feet = v.m_box(0, 0, 5, 2, 0, 10) | v.m_box(13, 0, 5, 15, 0, 10)
    posts = v.m_box(0, 1, 7, 1, 21, 8) | v.m_box(14, 1, 7, 15, 21, 8)
    caps = v.m_box(0, 22, 6, 1, 23, 9) | v.m_box(14, 22, 6, 15, 23, 9)
    bar = v.m_box(2, 20, 7, 13, 21, 8)
    hooks = v.m_box(4, 18, 7, 4, 19, 8) | v.m_box(11, 18, 7, 11, 19, 8)
    plaque(v)
    # All the parts are solid before the paint: a top under an other part does not catch the light.
    v.set(feet | posts | caps | bar | hooks, IRON[1])
    paint(v, feet | posts, "spruce_log", seed=seed)
    paint(v, caps, "stripped_spruce", seed=seed + 1)
    paint(v, bar, "spruce_planks", seed=seed + 2, along="x")
    # The hooks: an iron ring under the bar.
    v.set(hooks, IRON[1])
    v.set(hooks & (v.Y == 18), IRON[3])
    return v


def build():
    return Model("job_sign", sign_volume(), inventory_scale=0.8, inventory_center=True)
