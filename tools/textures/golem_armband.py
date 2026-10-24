#!/usr/bin/env python3
"""Generate the armband texture of the faction golem of the world mod.

Output:
- world/src/main/resources/assets/folkhold_world/textures/entity/faction_golem/armband.png

    python3 tools/textures/golem_armband.py

The armband is a box of 4 x 3 x 6 pixels around the left arm of the iron golem model (see GolemArmbandLayer). The
texture is 32 x 16 pixels. It uses the areas of a box with these sizes at the texture offset (0, 0):

- top and bottom faces: x 6..13, y 0..5
- side faces: x 0..19, y 6..8. The outer face of the left arm is x 10..15.

The texture is gray: the color of the faction tints it. White gives the exact color of the faction, and a darker gray
gives a darker shade. The other pixels stay transparent.

Requires Pillow.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "world", "src", "main", "resources", "assets",
                   "folkhold_world", "textures", "entity", "faction_golem", "armband.png")

WIDTH = 32
HEIGHT = 16
CAPS = (6, 0, 14, 6)
SIDES = (0, 6, 20, 9)
OUTER_FACE = (10, 16)


def gray(value):
    return (value, value, value, 255)


def armband():
    """A cloth band: a woven middle row, darker edges, and a small dark mark on the outer face."""
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    x1, y1, x2, y2 = CAPS
    for x in range(x1, x2):
        for y in range(y1, y2):
            image.putpixel((x, y), gray(205))
    x1, y1, x2, y2 = SIDES
    for x in range(x1, x2):
        image.putpixel((x, y1), gray(190))
        image.putpixel((x, y1 + 1), gray(255 if x % 2 == 0 else 238))
        image.putpixel((x, y2 - 1), gray(190))
    middle = (OUTER_FACE[0] + OUTER_FACE[1]) // 2
    for x in (middle - 1, middle):
        image.putpixel((x, y1 + 1), gray(140))
    return image


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    armband().save(OUT)
    print("Wrote", os.path.relpath(OUT))


if __name__ == "__main__":
    main()
