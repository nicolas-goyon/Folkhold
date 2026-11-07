"""Tests of tools/structures/nbt.py. Run: python3 -m unittest discover -s scripts -p "test_*.py" """
import gzip
import os
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools", "structures"))
import nbt  # noqa: E402

ROOT = {"DataVersion": nbt.DATA_VERSION, "size": [1, 1, 1], "palette": [{"Name": "minecraft:stone"}],
        "blocks": [{"pos": [0, 0, 0], "state": 0}], "entities": []}


class WriteNbtTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.folder.name, "piece.nbt")

    def tearDown(self):
        self.folder.cleanup()

    def test_new_file_has_the_content_and_a_fixed_header(self):
        self.assertTrue(nbt.write_nbt(self.path, ROOT))
        with open(self.path, "rb") as f:
            data = f.read()
        self.assertEqual(data[:10], nbt.GZIP_HEADER)
        self.assertEqual(gzip.decompress(data), nbt.encode_nbt(ROOT))

    def test_same_content_with_other_gzip_bytes_is_not_written(self):
        # Other gzip bytes for the same content: another compression level, a file name and an mtime in the header.
        with gzip.GzipFile(self.path, "wb", compresslevel=1, mtime=123456) as out:
            out.write(nbt.encode_nbt(ROOT))
        with open(self.path, "rb") as f:
            before = f.read()
        self.assertFalse(nbt.write_nbt(self.path, ROOT))
        with open(self.path, "rb") as f:
            self.assertEqual(f.read(), before)

    def test_changed_content_is_written(self):
        nbt.write_nbt(self.path, ROOT)
        changed = dict(ROOT, palette=[{"Name": "minecraft:dirt"}])
        self.assertTrue(nbt.write_nbt(self.path, changed))
        self.assertEqual(nbt.read_nbt_bytes(self.path), nbt.encode_nbt(changed))

    def test_file_that_is_not_gzip_is_written(self):
        with open(self.path, "wb") as f:
            f.write(b"not gzip")
        self.assertTrue(nbt.write_nbt(self.path, ROOT))
        self.assertEqual(nbt.read_nbt_bytes(self.path), nbt.encode_nbt(ROOT))

    def test_gzip_bytes_is_deterministic(self):
        raw = nbt.encode_nbt(ROOT)
        self.assertEqual(nbt.gzip_bytes(raw), nbt.gzip_bytes(raw))
        self.assertEqual(zlib.decompress(nbt.gzip_bytes(raw), 16 + zlib.MAX_WBITS), raw)


if __name__ == "__main__":
    unittest.main()
