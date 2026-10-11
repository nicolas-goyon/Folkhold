#!/usr/bin/env bash
# Add the base mods of the Folkhold modpack from Modrinth.
# Run this script one time, in the dev container, from the repository root:
#   scripts/dev.sh bash modpack/add-base-mods.sh
# Then check the result (git diff), test the pack and commit the files in modpack/.
set -euo pipefail
cd "$(dirname "$0")"

add() {
    echo "==> $1"
    packwiz modrinth add --project-id "$1" -y
}

# Performance
add sodium
add lithium
add modernfix
add ferrite-core
add immediatelyfast
add entityculling

# Nature
add dynamictrees
add serene-seasons
add 4dEFIhvD                    # Smart Feeding Troughs (confirm, see issue #5)

# Weather
add weather-storms-tornadoes
add expanded-weather2-dynamics  # optional

# Terrain and aging
add 74oZhrEe                    # The Roads More Travelled (beta)
add immersive-weather-renewed   # beta, unofficial port
add moonlight                   # required by Immersive Weathering: Renewed, not declared on Modrinth

# Navigation
add small-ships                 # beta

# Comfort and development
add jei
add jade
add spark

# Pin the beta and unofficial mods. 'packwiz update --all' does not change pinned mods.
pin_project() {
    local file
    file=$(grep -l "mod-id = \"$1\"" mods/*.pw.toml 2>/dev/null | head -n 1 || true)
    if [ -z "$file" ] && [ -f "mods/$1.pw.toml" ]; then
        file="mods/$1.pw.toml"
    fi
    if [ -z "$file" ]; then
        echo "WARNING: no metafile found for '$1'. Pin it by hand: packwiz pin <name>" >&2
        return 0
    fi
    echo "==> pin $(basename "$file" .pw.toml)"
    packwiz pin "$(basename "$file" .pw.toml)"
}

pin_project 74oZhrEe
pin_project immersive-weather-renewed
pin_project small-ships
pin_project serene-seasons

packwiz refresh
echo
echo "Done. Mods in the pack:"
packwiz list
