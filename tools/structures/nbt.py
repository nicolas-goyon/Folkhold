"""Small writer for Minecraft structure files (.nbt). Python standard library only.

A Template holds blocks by position. Positions without a block are structure void: the game keeps the
blocks of the world there.
"""
import gzip
import os
import struct
import zlib

TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10

# Minecraft 1.21.1
DATA_VERSION = 3955


class Int(int):
    """An NBT int (the default for Python int values is also int)."""


class Byte(int):
    """An NBT byte."""


def _name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(value):
    """Returns (tag type, payload bytes) for a Python value."""
    if isinstance(value, Byte):
        return TAG_BYTE, struct.pack(">b", value)
    if isinstance(value, bool):
        return TAG_BYTE, struct.pack(">b", 1 if value else 0)
    if isinstance(value, int):
        return TAG_INT, struct.pack(">i", value)
    if isinstance(value, str):
        return TAG_STRING, _name(value)
    if isinstance(value, dict):
        out = b""
        for key, item in value.items():
            tag, data = _payload(item)
            out += bytes([tag]) + _name(key) + data
        return TAG_COMPOUND, out + bytes([TAG_END])
    if isinstance(value, list):
        if not value:
            return TAG_LIST, bytes([TAG_END]) + struct.pack(">i", 0)
        tags = [_payload(item) for item in value]
        element = tags[0][0]
        return TAG_LIST, bytes([element]) + struct.pack(">i", len(tags)) + b"".join(data for _, data in tags)
    raise TypeError(f"Unsupported NBT value: {value!r}")


# The gzip header of each written file: magic, deflate, no flags, mtime 0, maximum compression, OS unknown.
GZIP_HEADER = b"\x1f\x8b\x08\x00\x00\x00\x00\x00\x02\xff"
COMPRESS_LEVEL = 9


def encode_nbt(root):
    """Returns the uncompressed bytes of a structure file for the root compound."""
    tag, data = _payload(root)
    assert tag == TAG_COMPOUND
    return bytes([TAG_COMPOUND]) + _name("") + data


def gzip_bytes(raw):
    """Returns the gzip file for raw. The header is fixed and the compression level is fixed.

    The deflate bytes can be different with a different zlib (zlib or zlib-ng) for the same raw bytes.
    """
    deflate = zlib.compressobj(COMPRESS_LEVEL, zlib.DEFLATED, -zlib.MAX_WBITS)
    body = deflate.compress(raw) + deflate.flush()
    return GZIP_HEADER + body + struct.pack("<II", zlib.crc32(raw), len(raw) & 0xFFFFFFFF)


def read_nbt_bytes(path):
    """Returns the uncompressed bytes of the file at path, or None when the file is missing or is not gzip."""
    try:
        with gzip.open(path, "rb") as f:
            return f.read()
    except (OSError, EOFError, zlib.error):
        return None


def write_nbt(path, root):
    """Writes the structure file. Returns True when the file is written.

    When the file exists and its uncompressed content is equal to the new content, the function does not write the
    file. Thus the output does not depend on the zlib of the machine, and Git sees a change only when the content
    changes.
    """
    raw = encode_nbt(root)
    if os.path.exists(path) and read_nbt_bytes(path) == raw:
        return False
    with open(path, "wb") as out:
        out.write(gzip_bytes(raw))
    return True


class Template:
    def __init__(self, size_x, size_y, size_z):
        self.size = (size_x, size_y, size_z)
        self.blocks = {}

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, name, props=None, nbt=None):
        if not self.inside(x, y, z):
            raise ValueError(f"Position {(x, y, z)} is outside the template {self.size}")
        key = (name, tuple(sorted((props or {}).items())))
        self.blocks[(x, y, z)] = (key, nbt)

    def get(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[0][0] if entry else None

    def fill(self, x1, y1, z1, x2, y2, z2, name, props=None):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, props)

    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state):
        """A jigsaw block. orientation: for example "north_up" (the block faces north)."""
        self.set(x, y, z, "minecraft:jigsaw", {"orientation": orientation}, {
            "id": "minecraft:jigsaw",
            "name": name,
            "target": target,
            "pool": pool,
            "final_state": final_state,
            "joint": "rollable",
            "placement_priority": 0,
            "selection_priority": 0,
        })

    def write(self, path):
        palette_index = {}
        palette = []
        blocks = []
        for (x, y, z), (key, nbt) in sorted(self.blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            if key not in palette_index:
                palette_index[key] = len(palette)
                name, props = key
                entry = {"Name": name}
                if props:
                    entry["Properties"] = {k: str(v) for k, v in props}
                palette.append(entry)
            block = {"pos": [x, y, z], "state": palette_index[key]}
            if nbt is not None:
                block["nbt"] = nbt
            blocks.append(block)
        write_nbt(path, {
            "DataVersion": DATA_VERSION,
            "size": list(self.size),
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        })
