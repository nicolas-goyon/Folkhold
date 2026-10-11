#!/usr/bin/env sh
# Run a command in the Folkhold dev container. With no command, open a shell.
# Examples:
#   scripts/dev.sh
#   scripts/dev.sh ./gradlew build
set -e
cd "$(dirname "$0")/.."
exec docker compose run --rm dev "$@"
