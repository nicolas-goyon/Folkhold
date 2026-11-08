"""Write the PNG files of the texture generators.

`write_png` does not write a file when the decoded pixels of the file on disk are equal to the new pixels. When it
writes, it uses `pixvox.png.save_png`: the bytes are the same on all machines (they do not depend on the version of
Pillow or of zlib). Thus the byte comparison of the workflow "Generated files" does not depend on the machine, and it
fails when the pixels of a texture change.

Requires Pillow and PixVox (tools/pixvox/requirements.txt).
"""
import os

from PIL import Image
from pixvox.png import save_png


def same_pixels(path, image):
    """Return True when the PNG file at `path` exists and has the same mode, size and pixels as `image`."""
    if not os.path.exists(path):
        return False
    try:
        with Image.open(path) as old:
            old.load()
            return old.mode == image.mode and old.size == image.size and old.tobytes() == image.tobytes()
    except OSError:
        return False


def write_png(path, image):
    """Write `image` to `path`. Return False and do not write when the file has the same pixels."""
    if same_pixels(path, image):
        return False
    save_png(path, image)
    return True
