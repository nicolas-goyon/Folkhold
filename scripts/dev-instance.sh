#!/usr/bin/env bash
# Make a Prism Launcher instance (zip) that updates itself from a packwiz pack on a web server.
# Before each launch, the instance runs packwiz-installer-bootstrap with the URL of the pack. packwiz-installer then
# installs the files of the pack and removes the files of the earlier version of the pack.
# The instance gets the versions of Minecraft and NeoForge from modpack/pack.toml. packwiz-installer does not change
# these versions after the import.
#
# Usage: scripts/dev-instance.sh <output.zip> <URL of pack.toml> [instance name]
set -euo pipefail

# packwiz-installer-bootstrap gets the latest packwiz-installer at each launch.
BOOTSTRAP_VERSION="v0.0.3"
BOOTSTRAP_SHA256="a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c"

if [ $# -lt 2 ]; then
    echo "Usage: $0 <output.zip> <URL of pack.toml> [instance name]" >&2
    exit 2
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(realpath -m "$1")"
url="$2"
name="${3:-Folkhold (dev)}"

minecraft="$(sed -n 's/^minecraft = "\(.*\)"$/\1/p' "$root/modpack/pack.toml")"
neoforge="$(sed -n 's/^neoforge = "\(.*\)"$/\1/p' "$root/modpack/pack.toml")"
if [ -z "$minecraft" ] || [ -z "$neoforge" ]; then
    echo "modpack/pack.toml has no version of Minecraft or of NeoForge." >&2
    exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/.minecraft"

bootstrap="$work/.minecraft/packwiz-installer-bootstrap.jar"
curl --fail --silent --show-error --location --retry 3 --output "$bootstrap" \
    "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/${BOOTSTRAP_VERSION}/packwiz-installer-bootstrap.jar"
echo "${BOOTSTRAP_SHA256}  ${bootstrap}" | sha256sum --check --quiet

# The pre-launch command runs in the game folder of the instance (.minecraft), next to the bootstrap jar.
cat > "$work/instance.cfg" <<EOF
InstanceType=OneSix
name=${name}
OverrideCommands=true
PreLaunchCommand="\$INST_JAVA" -jar packwiz-installer-bootstrap.jar ${url}
EOF

cat > "$work/mmc-pack.json" <<EOF
{
    "components": [
        {
            "important": true,
            "uid": "net.minecraft",
            "version": "${minecraft}"
        },
        {
            "uid": "net.neoforged",
            "version": "${neoforge}"
        }
    ],
    "formatVersion": 1
}
EOF

mkdir -p "$(dirname "$out")"
rm -f "$out"
(cd "$work" && python3 -m zipfile -c "$out" instance.cfg mmc-pack.json .minecraft)
echo "Instance written to $out"
