#!/usr/bin/env bash
# Shows the important lines of build logs as GitHub Actions error annotations.
# The annotations appear on the summary page of the run and in the pull request.
# GitHub shows a maximum of 10 error annotations for each step.
#
# The script reads each log from its end: the failure is at the end of a log, and the start of a log has the
# noise of the startup. It looks for these markers, the most specific first:
#   - a failed GameTest ("<name> failed at <position>! <message>"), one annotation for each test;
#   - a failed JUnit test or a compile error;
#   - the Gradle summary ("What went wrong").
# With no marker in any log, the last lines of the last log are the annotation.
#
# Usage: scripts/ci-annotate-errors.sh <log-file>...
set -uo pipefail

max_annotations=10
tail_lines=40
count=0

encode() {
    # Encode the characters that the annotation syntax uses: %, CR and LF.
    sed -e 's/%/%25/g' -e 's/\r/%0D/g' | awk 'BEGIN { ORS = "%0A" } { print }'
}

annotate() {
    local title="$1"
    local text="$2"
    if [ "$count" -lt "$max_annotations" ] && [ -n "$text" ]; then
        echo "::error title=${title}::$(printf '%s\n' "$text" | encode)"
        count=$((count + 1))
    fi
}

last_log=""
for log in "$@"; do
    [ -f "$log" ] || continue
    name=$(basename "$log")
    last_log="$log"

    # Failed GameTests: the name of the test and its message.
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        annotate "${name}: GameTest" "$(printf '%s' "$line" | sed -E 's/^.*LogTestReporter\]: //')"
    done < <(grep -E 'LogTestReporter\]: .* failed at ' "$log" | tail -n "$max_annotations")

    # Failed JUnit tests and compile errors.
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        annotate "${name}: test or compile" "$line"
    done < <(grep -E '^[^ ]+ > [^ ]+.* FAILED$|: error: ' "$log" | tail -n 5)

    # The Gradle summary at the end of the log.
    summary=$(awk '/What went wrong/ { found = 1 } found { print }' "$log" | head -n 20)
    if [ -n "$summary" ]; then
        annotate "${name}: Gradle" "$summary"
    fi
done

if [ "$count" -eq 0 ] && [ -n "$last_log" ]; then
    annotate "$(basename "$last_log"): last ${tail_lines} lines" "$(tail -n "$tail_lines" "$last_log")"
fi

if [ "$count" -eq 0 ]; then
    echo "::error title=Build failed::No log to read. Open the log of the failed step."
fi
