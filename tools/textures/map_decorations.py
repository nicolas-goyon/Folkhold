#!/usr/bin/env python3
"""Generate the map decoration icons of the world mod.

Output:
- world/src/main/resources/assets/folkhold_world/textures/map/decorations/village.png

    python3 tools/textures/map_decorations.py

The icon of a map decoration is 8 x 8 pixels, as the icons of the game. The icon of a village waypoint is the flame of
the village heart: a gold rhombus with a light center and a dark outline. The other pixels stay transparent.

Requires Pillow.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "world", "src", "main", "resources", "assets",
                   "folkhold_world", "textures", "map", "decorations", "village.png")

SIZE = 8
OUTLINE = (40, 26, 12, 255)
GOLD = (232, 168, 40, 255)
LIGHT = (255, 236, 150, 255)


def village():
    """A rhombus: the distance from the middle (3.5, 3.5) gives the outline, the gold and the light center."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for x in range(SIZE):
        for y in range(SIZE):
            distance = abs(x - 3.5) + abs(y - 3.5)
            if distance <= 1.0:
                image.putpixel((x, y), LIGHT)
            elif distance <= 3.0:
                image.putpixel((x, y), GOLD)
            elif distance <= 4.0:
                image.putpixel((x, y), OUTLINE)
    return image


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    village().save(OUT)
    print("Wrote", os.path.relpath(OUT))


if __name__ == "__main__":
    main()
