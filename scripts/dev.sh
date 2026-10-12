#!/usr/bin/env sh
# Run a command in the Folkhold dev container. With no command, open a shell.
# Examples:
#   scripts/dev.sh
#   scripts/dev.sh ./gradlew build
set -e
cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is not installed (the docker command is not available)." >&2
    echo "Prerequisites: see docs/getting-started.md (Docker, Git)." >&2
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "The Docker engine does not run. Start it, then run the command again:" >&2
    echo "  Linux: sudo systemctl start docker" >&2
    echo "  macOS: open -a Docker" >&2
    exit 1
fi

exec docker compose run --rm dev "$@"
