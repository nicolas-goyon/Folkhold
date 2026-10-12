#!/usr/bin/env python3
"""Generate the villager profession textures of the world mod.

Output: world/src/main/resources/assets/folkhold_world/textures/entity/villager/profession/*.png

    python3 tools/textures/villager_professions.py

A profession texture is an overlay on the villager type texture (64 x 64). It uses the texture areas of the vanilla
villager model. The areas that a profession does not paint stay transparent:

- head: x 0..31, y 0..17; hat: x 32..63, y 0..17; nose: x 24..31, y 0..5
- body: x 16..43, y 20..37 (front face: x 22..29, y 26..37)
- legs: x 0..15, y 22..37
- robe (jacket): x 0..27, y 38..63 (front face: x 6..13, y 44..63)
- crossed arms: x 40..63, y 22..49
- hat brim: x 30..61, y 47..62. A pixel in this area shows a brim around the head.

Requires Pillow.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "world", "src", "main", "resources", "assets",
                   "folkhold_world", "textures", "entity", "villager", "profession")

SIZE = 64
BODY = (16, 20, 44, 38)
BODY_FRONT_X = 22
ROBE = (0, 38, 28, 64)
HAT_BRIM = (30, 47, 62, 63)


def fill(image, area, color):
    x1, y1, x2, y2 = area
    for x in range(x1, x2):
        for y in range(y1, y2):
            image.putpixel((x, y), color)


def merchant():
    """Green robe with dark diagonal stripes, a gold belt and a gold line on the front of the body."""
    green = (46, 94, 62, 255)
    dark = (30, 64, 42, 255)
    gold = (201, 162, 39, 255)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    fill(image, BODY, green)
    for y in range(26, BODY[3]):
        image.putpixel((BODY_FRONT_X + 3, y), gold)
    x1, y1, x2, y2 = ROBE
    for x in range(x1, x2):
        for y in range(y1, y2):
            image.putpixel((x, y), dark if (x + y) % 7 == 5 else green)
    for x in range(x1, x2):
        image.putpixel((x, 46), gold)
    return image


PROFESSIONS = {
    "merchant": merchant,
}


def check(name, image):
    x1, y1, x2, y2 = HAT_BRIM
    for x in range(x1, x2):
        for y in range(y1, y2):
            if image.getpixel((x, y))[3] != 0:
                raise ValueError(f"{name}: the pixel {(x, y)} is in the hat brim area")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in PROFESSIONS.items():
        image = build()
        check(name, image)
        path = os.path.join(OUT, name + ".png")
        image.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
