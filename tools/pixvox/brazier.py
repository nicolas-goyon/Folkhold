"""Brazier of a village: a stone bowl of embers on a square pedestal. The model is 22 pixels high.

The parts follow the shape of the block (BrazierBlock): a plinth, a fluted column, a collar and a bowl with an iron
band under its rim. A brazier that does not burn holds dark charcoal and ash. A lit brazier has embers that pulse and
glow, and four planes of flames.
"""
from _palette import IRON, STONE
from pixvox import EMBER, FIRE, Model, Volume, fire, pulse, ramp

CHAR = ramp("#2f2926", n=4, spread=0.16, hue_shift=6)
ASH = ramp("#7d7772", n=3, spread=0.14, hue_shift=6)
LIGHT = (-0.5, 1.0, -0.35)
FRAMES = 16
# The top of the embers, in model units.
EMBERS_TOP = 20


def brazier_volume():
    """The volume of the brazier, and the mask of the top of the embers."""
    v = Volume(16, 22, 16)
    x, y, z = v.X, v.Y, v.Z
    plinth = v.m_box(2, 0, 2, 13, 2, 13)
    column = v.m_box(5, 3, 5, 10, 11, 10)
    collar = v.m_box(3, 12, 3, 12, 13, 12)
    corners = ((x == 1) | (x == 14)) & ((z == 1) | (z == 14))
    bowl = v.m_box(1, 14, 1, 14, 21, 14) & ~corners & ~v.m_box(3, 16, 3, 12, 21, 12)
    heap = v.m_ellipsoid((8, 17.0, 8), (5.6, 3.6, 5.6)) & v.m_box(3, 16, 3, 12, 19, 12)
    stone = plinth | column | collar | bowl

    v.set(stone, STONE[2])
    v.shade(stone, STONE, light=LIGHT, contrast=1.1, bias=0.05, noise=0.12, seed=7, ao=0.5)
    # Flutes on the column: a dark groove in the middle of each face.
    flutes = column & (((x == 7) | (x == 8)) & ((z == 5) | (z == 10)) | ((z == 7) | (z == 8)) & ((x == 5) | (x == 10)))
    v.set(flutes & (y > 3) & (y < 11), STONE[1])
    # The top edges of the plinth, the collar and the rim catch the light.
    v.set(plinth & (y == 2) & (v.m_exposed("up")), STONE[4])
    v.set(collar & (y == 13), STONE[4])
    v.set(bowl & (y == 21), STONE[4])
    # The iron band under the rim, with rivets.
    band = bowl & ((y == 18) | (y == 19)) & ~v.m_box(3, 14, 3, 12, 21, 12)
    v.set(band, IRON[2])
    v.set(band & (y == 19), IRON[3])
    v.set(band & (y == 18) & (((x + z) % 4) == 0), IRON[4])

    v.set(heap, CHAR[1])
    v.shade(heap, CHAR, light=LIGHT, contrast=0.9, noise=0.35, seed=4)
    top = heap & v.m_exposed("up")
    ash = top & v.m_noise(0.78, cells=8, seed=13, mask=top)
    v.set(ash, ASH[1])
    embers = top & ~ash & v.m_noise(0.45, cells=8, seed=11, mask=top)
    return v, embers


def build():
    v, embers = brazier_volume()
    unlit = Model("brazier", v, inventory_scale=0.8, inventory_center=True)

    frames = v.frames(FRAMES)
    pulse(frames, embers, EMBER, seed=5, speed=1, amplitude=0.55, variation=0.5, bias=0.08)
    lit = Model("brazier_lit", frames, frametime=2, emissive=embers, emission=12, inventory_scale=0.8,
                inventory_center=True)
    big = fire(16, frames=FRAMES, seed=1, palette=FIRE)
    small = fire(16, frames=FRAMES, seed=7, palette=FIRE)
    # The bottom of the flames is in the embers. The planes are narrow enough to stay in the bowl.
    bottom = EMBERS_TOP - 3
    lit.add_cross(big, center=(8, 8), y=bottom, width=12, height=15, planes=2, emission=15)
    lit.add_cross(small, center=(8, 8), y=bottom, width=10, height=13, planes=2, angle=0, emission=15)
    return [unlit, lit]
