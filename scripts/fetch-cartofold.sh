#!/usr/bin/env bash
# Download the Cartofold jar of the GitHub Release into a local folder, for a build with -PcartofoldDir=<dir>.
# Use it when the HEAD request of Gradle to the release URL fails (for example behind a proxy). The script downloads
# with GET and checks the sha256 of the jar against modpack/mods/cartofold.pw.toml.
#
# Usage: scripts/fetch-cartofold.sh [dir]   (default dir: build/cartofold)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
dir="$(realpath -m "${1:-$root/build/cartofold}")"

version="$(sed -n 's/^cartofold_version=//p' "$root/gradle.properties")"
if [ -z "$version" ]; then
    echo "No cartofold_version in gradle.properties." >&2
    exit 1
fi
expected="$(sed -n 's/^hash = "\([0-9a-f]\{64\}\)"$/\1/p' "$root/modpack/mods/cartofold.pw.toml")"
if [ -z "$expected" ]; then
    echo "No sha256 hash in modpack/mods/cartofold.pw.toml." >&2
    exit 1
fi

url="https://github.com/nicolas-goyon/Cartofold/releases/download/v${version}/cartofold-${version}.jar"
jar="$dir/v${version}/cartofold-${version}.jar"
mkdir -p "$(dirname "$jar")"
curl -fsSL -o "$jar.part" "$url"
actual="$(sha256sum "$jar.part" | cut -d' ' -f1)"
if [ "$actual" != "$expected" ]; then
    rm -f "$jar.part"
    echo "The sha256 of $url is $actual." >&2
    echo "modpack/mods/cartofold.pw.toml gives $expected. Make sure that cartofold_version and the modpack agree." >&2
    exit 1
fi
mv "$jar.part" "$jar"
echo "Cartofold $version is in $jar."
echo "Build with: ./gradlew build -PcartofoldDir=$dir"
