#!/usr/bin/env bash
# Show the important lines of build logs as GitHub Actions error annotations.
# The annotations appear on the summary page of the run and in the pull request.
# GitHub shows a maximum of 10 error annotations for each step.
#
# Usage: scripts/ci-annotate-errors.sh <log-file>...
set -uo pipefail

pattern='error:|FAILED|What went wrong|Exception|Caused by|/ERROR\]|/FATAL\]|failed|Could not|Unresolved|cannot find symbol|Test.*FAIL'
lines_per_annotation=40
max_annotations=10
count=0

encode() {
    # Encode the characters that the annotation syntax uses: %, CR and LF.
    sed -e 's/%/%25/g' -e 's/\r/%0D/g' | awk 'BEGIN { ORS = "%0A" } { print }'
}

for log in "$@"; do
    [ -f "$log" ] || continue
    extract="$(grep -E -A 4 "$pattern" "$log" | grep -v -E '^\s*at (java|jdk|org\.gradle|worker\.org\.gradle)' | head -n 400)"
    [ -n "$extract" ] || continue
    total=$(printf '%s\n' "$extract" | wc -l)
    start=1
    while [ "$start" -le "$total" ] && [ "$count" -lt "$max_annotations" ]; do
        chunk="$(printf '%s\n' "$extract" | sed -n "${start},$((start + lines_per_annotation - 1))p" | encode)"
        echo "::error title=$(basename "$log") (lines ${start}+)::${chunk}"
        start=$((start + lines_per_annotation))
        count=$((count + 1))
    done
done

if [ "$count" -eq 0 ]; then
    echo "::error title=Build failed::No known error pattern in the logs. Open the log of the failed step."
fi
