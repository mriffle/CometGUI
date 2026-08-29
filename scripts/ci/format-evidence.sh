#!/usr/bin/env bash
#
# format-evidence.sh -- evidence that Spotless, Checkstyle and SpotBugs actually inspected the code.
#
# The specification's "formatting and style check" and "static analysis" items.
# All three tools exit 0 when they are misconfigured to look at nothing, so
# this stage reads their reports and compares what they inspected against the
# source and class files that exist.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only format`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only format "$@"
