#!/usr/bin/env python3
"""Write a structure file (.nbt) for GameTest templates.

The structure is a box of air. An optional floor fills the bottom layer with one block.

Usage:
    python3 tools/make_test_structure.py <output.nbt> [--size X Y Z] [--floor BLOCK] [--data-version N]

Examples:
    python3 tools/make_test_structure.py empty.nbt
    python3 tools/make_test_structure.py arena.nbt --size 16 4 16 --floor minecraft:smooth_stone

Default size: 1 1 1. Default data version: 3955 (Minecraft 1.21.1).
"""
import argparse
import gzip
import struct

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
    parser = argparse.ArgumentParser(description="Write a GameTest structure file.")
    parser.add_argument("output")
    parser.add_argument("--size", nargs=3, type=int, default=[1, 1, 1], metavar=("X", "Y", "Z"))
    parser.add_argument("--floor", default=None, help="Block id for the bottom layer, for example minecraft:stone")
    parser.add_argument("--data-version", type=int, default=3955)
    args = parser.parse_args()

    sx, sy, sz = args.size
    palette = [string_tag("Name", "minecraft:air")]
    blocks = []
    if args.floor:
        palette.append(string_tag("Name", args.floor))
        for x in range(sx):
            for z in range(sz):
                blocks.append(int_list("pos", [x, 0, z]) + int_tag("state", 1))
    first_air_layer = 1 if args.floor else 0
    for x in range(sx):
        for y in range(first_air_layer, sy):
            for z in range(sz):
                blocks.append(int_list("pos", [x, y, z]) + int_tag("state", 0))

    root = (
        int_tag("DataVersion", args.data_version)
        + int_list("size", [sx, sy, sz])
        + compound_list("palette", palette)
        + compound_list("blocks", blocks)
        + compound_list("entities", [])
    )
    data = bytes([TAG_COMPOUND]) + name("") + root + bytes([TAG_END])
    with gzip.open(args.output, "wb") as f:
        f.write(data)


if __name__ == "__main__":
    main()
