#!/usr/bin/env bash
#
# maven-verify.sh -- compile, format, static analysis, fast JUnit tests, coverage and ArchUnit.
#
# One Maven invocation covers six of the specification's pull-request items,
# because the POM binds them into the ordinary lifecycle rather than bolting
# them on: Spotless and Checkstyle at validate, the compiler and Surefire and
# the JaCoCo agent and the ArchUnit tests as usual, SpotBugs and the JaCoCo
# check executions at verify.  Splitting them into separate `mvn` runs would
# recompile the reactor several times and would still be the same checks.
# The evidence that each one really ran -- rather than being skipped -- is
# scripts/ci/format-evidence.sh and scripts/ci/test-gates.sh, which follow.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only build`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only build "$@"
