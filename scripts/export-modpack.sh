#!/usr/bin/env bash
# Export the modpack as a .mrpack file. The file contains the Folkhold mod jars (as overrides).
# Build the mods first (./gradlew build). Needs packwiz (available in the dev container).
#
# With a pack folder, the script also writes the packwiz pack with the Folkhold jars to this folder: pack.toml,
# index.toml and the files of the index. On a web server, packwiz-installer installs and updates an instance from
# this pack (see scripts/dev-instance.sh). The folder must be empty or must not exist.
#
# Usage: scripts/export-modpack.sh <output.mrpack> [pack-version] [pack-folder]
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output.mrpack> [pack-version] [pack-folder]" >&2
    exit 2
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(realpath -m "$1")"
pack_version="${2:-}"
pack_dir=""
if [ -n "${3:-}" ]; then
    pack_dir="$(realpath -m "$3")"
    if [ -e "$pack_dir" ] && [ -n "$(ls -A "$pack_dir")" ]; then
        echo "The pack folder $pack_dir is not empty." >&2
        exit 1
    fi
fi

if [ ! -f "$root/modpack/pack.toml" ]; then
    echo "No modpack/pack.toml. Nothing to export." >&2
    exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -r "$root/modpack/." "$work/"
mkdir -p "$work/mods"

found=0
for jar in "$root"/{core,blocks,world,balance,events}/build/libs/folkhold_*.jar; do
    [ -e "$jar" ] || continue
    case "$jar" in
        *-sources.jar) ;;
        *) cp "$jar" "$work/mods/"; found=1 ;;
    esac
done
if [ "$found" -eq 0 ]; then
    echo "No mod jar found. Run ./gradlew build first." >&2
    exit 1
fi

cd "$work"
if [ -n "$pack_version" ]; then
    sed -i "s/^version = .*/version = \"${pack_version}\"/" pack.toml
fi
packwiz refresh
packwiz modrinth export --output "$out"
echo "Exported $out"

if [ -n "$pack_dir" ]; then
    mkdir -p "$pack_dir"
    cp pack.toml index.toml "$pack_dir/"
    sed -n 's/^file = "\(.*\)"$/\1/p' index.toml | while IFS= read -r file; do
        mkdir -p "$pack_dir/$(dirname "$file")"
        cp "$file" "$pack_dir/$file"
    done
    echo "Pack written to $pack_dir"
fi
