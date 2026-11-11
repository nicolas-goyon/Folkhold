"""Tests of tools/textures/png_file.py. Run: python3 -m unittest discover -s scripts -p "test_*.py"

Requires Pillow and PixVox (tools/pixvox/requirements.txt).
"""
import os
import sys
import tempfile
import unittest

from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools", "textures"))
import png_file  # noqa: E402


def sample():
    image = Image.new("RGBA", (8, 4), (0, 0, 0, 0))
    for x in range(8):
        image.putpixel((x, 1), (10 * x, 200, 255 - x, 255))
    return image


class WritePngTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.folder.name, "icon.png")

    def tearDown(self):
        self.folder.cleanup()

    def read(self):
        with open(self.path, "rb") as f:
            return f.read()

    def test_new_file_has_the_pixels(self):
        self.assertTrue(png_file.write_png(self.path, sample()))
        with Image.open(self.path) as image:
            self.assertEqual(image.mode, "RGBA")
            self.assertEqual(image.tobytes(), sample().tobytes())

    def test_bytes_do_not_depend_on_the_file_on_disk(self):
        png_file.write_png(self.path, sample())
        first = self.read()
        os.remove(self.path)
        png_file.write_png(self.path, sample())
        self.assertEqual(self.read(), first)

    def test_same_pixels_with_other_bytes_are_not_written(self):
        # Other PNG bytes for the same pixels: Pillow with another compression level.
        sample().save(self.path, compress_level=1)
        before = self.read()
        self.assertFalse(png_file.write_png(self.path, sample()))
        self.assertEqual(self.read(), before)

    def test_changed_pixel_is_written(self):
        sample().save(self.path)
        changed = sample()
        changed.putpixel((0, 0), (255, 0, 0, 255))
        self.assertTrue(png_file.write_png(self.path, changed))
        with Image.open(self.path) as image:
            self.assertEqual(image.tobytes(), changed.tobytes())

    def test_changed_mode_is_written(self):
        sample().convert("RGB").save(self.path)
        self.assertTrue(png_file.write_png(self.path, sample()))
        with Image.open(self.path) as image:
            self.assertEqual(image.mode, "RGBA")


if __name__ == "__main__":
    unittest.main()
