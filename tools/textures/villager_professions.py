#!/usr/bin/env python3
"""Generate the villager and zombie villager profession textures of the world mod.

Output:
- world/src/main/resources/assets/folkhold_world/textures/entity/villager/profession/*.png
- world/src/main/resources/assets/folkhold_world/textures/entity/zombie_villager/profession/*.png

    python3 tools/textures/villager_professions.py

A profession texture is an overlay on the villager type texture (64 x 64). It uses the texture areas of the vanilla
villager model. The zombie villager model uses the same areas, with its arms in place of the crossed arms. The areas
that a profession does not paint stay transparent:

- head: x 0..31, y 0..17; hat: x 32..63, y 0..17; nose: x 24..31, y 0..5
- body: x 16..43, y 20..37 (front face: x 22..29, y 26..37)
- legs: x 0..15, y 22..37
- robe (jacket): x 0..27, y 38..63 (front face: x 6..13, y 44..63)
- crossed arms: x 40..63, y 22..49
- hat brim: x 30..61, y 47..62. A pixel in this area shows a brim around the head.

The zombie villager texture of a profession is the villager texture with darker colors, a green tint, and holes in
the clothes of the body and of the robe.

Requires Pillow.
"""
import json
import os

from PIL import Image

TEXTURES = os.path.join(os.path.dirname(__file__), "..", "..", "world", "src", "main", "resources", "assets",
                        "folkhold_world", "textures", "entity")
OUT = os.path.join(TEXTURES, "villager", "profession")
OUT_ZOMBIE = os.path.join(TEXTURES, "zombie_villager", "profession")

SIZE = 64
BODY = (16, 20, 44, 38)
BODY_FRONT_X = 22
ROBE = (0, 38, 28, 64)
ROBE_FRONT_X = 6
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


def inhabitant():
    """Brown tunic with a dot pattern and a dark belt."""
    brown = (122, 86, 52, 255)
    dot = (96, 66, 40, 255)
    belt = (60, 40, 24, 255)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    fill(image, BODY, brown)
    x1, y1, x2, y2 = ROBE
    fill(image, ROBE, brown)
    for y in range(y1 + 1, y2, 3):
        row = (y - y1 - 1) // 3
        for x in range(x1, x2):
            if (x - x1 + row) % 3 == 1:
                image.putpixel((x, y), dot)
    for x in range(x1, x2):
        image.putpixel((x, 45), belt)
    return image


def lumberjack():
    """Red and black plaid shirt, brown trousers part of the robe, a leather belt."""
    red = (168, 40, 36, 255)
    black = (40, 30, 30, 255)
    dark_red = (110, 28, 26, 255)
    leather = (92, 60, 32, 255)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def plaid(x, y):
        stripe_x = (x // 2) % 2 == 0
        stripe_y = (y // 2) % 2 == 0
        if stripe_x and stripe_y:
            return black
        if stripe_x or stripe_y:
            return dark_red
        return red

    x1, y1, x2, y2 = BODY
    for x in range(x1, x2):
        for y in range(y1, y2):
            image.putpixel((x, y), plaid(x, y))
    x1, y1, x2, y2 = ROBE
    for x in range(x1, x2):
        for y in range(y1, y2):
            image.putpixel((x, y), plaid(x, y) if y < 52 else leather)
    for x in range(x1, x2):
        image.putpixel((x, 47), leather)
    return image


def baker():
    """White apron on the front, flour-white cap on the head."""
    white = (236, 232, 222, 255)
    shade = (206, 200, 188, 255)
    cream = (214, 186, 140, 255)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    fill(image, BODY, cream)
    # Apron: the front faces of the body and of the robe.
    for x in range(BODY_FRONT_X, BODY_FRONT_X + 8):
        for y in range(28, BODY[3]):
            image.putpixel((x, y), white)
    for x in range(ROBE_FRONT_X, ROBE_FRONT_X + 8):
        for y in range(44, ROBE[3]):
            image.putpixel((x, y), white if (x + y) % 9 else shade)
    # Cap: the top face of the hat and the two top rows of its four sides.
    fill(image, (40, 0, 48, 8), white)
    fill(image, (32, 8, 64, 10), white)
    for x in range(32, 64, 4):
        image.putpixel((x, 9), shade)
    return image


PROFESSIONS = {
    "merchant": merchant,
    "inhabitant": inhabitant,
    "lumberjack": lumberjack,
    "baker": baker,
}

# The hat of a profession: "partial" hides the hat of the villager type (biome) under the profession hat.
HATS = {
    "baker": "partial",
}


def inside(area, x, y):
    x1, y1, x2, y2 = area
    return x1 <= x < x2 and y1 <= y < y2


def zombie(image):
    """The clothes of a zombie villager: darker colors with a green tint, dirt marks, and holes in the body and in
    the robe."""
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for x in range(SIZE):
        for y in range(SIZE):
            r, g, b, a = image.getpixel((x, y))
            if a == 0:
                continue
            clothes = inside(BODY, x, y) or inside(ROBE, x, y)
            if clothes and (x * 5 + y * 3) % 17 == 0:
                continue
            r, g, b = int(r * 0.62) + 20, int(g * 0.68) + 28, int(b * 0.55) + 14
            if clothes and (x * 11 + y * 7) % 13 == 0:
                r, g, b = int(r * 0.75), int(g * 0.75), int(b * 0.75)
            out.putpixel((x, y), (min(r, 255), min(g, 255), min(b, 255), a))
    return out


def write(folder, name, image):
    path = os.path.join(folder, name + ".png")
    image.save(path)
    meta = path + ".mcmeta"
    if name in HATS:
        with open(meta, "w", encoding="utf-8") as file:
            json.dump({"villager": {"hat": HATS[name]}}, file, indent=2)
            file.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)
    print("wrote", os.path.normpath(path))


def check(name, image):
    x1, y1, x2, y2 = HAT_BRIM
    for x in range(x1, x2):
        for y in range(y1, y2):
            if image.getpixel((x, y))[3] != 0:
                raise ValueError(f"{name}: the pixel {(x, y)} is in the hat brim area")


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(OUT_ZOMBIE, exist_ok=True)
    for name, build in PROFESSIONS.items():
        image = build()
        check(name, image)
        write(OUT, name, image)
        write(OUT_ZOMBIE, name, zombie(image))


if __name__ == "__main__":
    main()
