#!/usr/bin/env bash
# Export the modpack as a .mrpack file. The file contains the Folkhold mod jars (as overrides).
# Build the mods first (./gradlew build). Needs packwiz (available in the dev container).
#
# Usage: scripts/export-modpack.sh <output.mrpack> [pack-version]
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output.mrpack> [pack-version]" >&2
    exit 2
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(realpath -m "$1")"
pack_version="${2:-}"

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
