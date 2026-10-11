#!/usr/bin/env python3
"""Write an empty structure file (1 air block) for GameTest templates.

Usage: python3 tools/make_empty_structure.py <output.nbt> [data_version]
Default data version: 3955 (Minecraft 1.21.1).
"""
import gzip
import struct
import sys

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def int_tag(n, v):
    return bytes([TAG_INT]) + name(n) + struct.pack(">i", v)


def string_tag(n, v):
    return bytes([TAG_STRING]) + name(n) + name(v)


def int_list(n, values):
    return bytes([TAG_LIST]) + name(n) + bytes([TAG_INT]) + struct.pack(">i", len(values)) + b"".join(
        struct.pack(">i", v) for v in values)


def compound_list(n, payloads):
    elem = TAG_COMPOUND if payloads else TAG_END
    return bytes([TAG_LIST]) + name(n) + bytes([elem]) + struct.pack(">i", len(payloads)) + b"".join(
        p + bytes([TAG_END]) for p in payloads)


def main():
    out = sys.argv[1]
    data_version = int(sys.argv[2]) if len(sys.argv) > 2 else 3955
    root = (
        int_tag("DataVersion", data_version)
        + int_list("size", [1, 1, 1])
        + compound_list("palette", [string_tag("Name", "minecraft:air")])
        + compound_list("blocks", [int_list("pos", [0, 0, 0]) + int_tag("state", 0)])
        + compound_list("entities", [])
    )
    data = bytes([TAG_COMPOUND]) + name("") + root + bytes([TAG_END])
    with gzip.open(out, "wb") as f:
        f.write(data)


if __name__ == "__main__":
    main()
