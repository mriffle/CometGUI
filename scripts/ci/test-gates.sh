#!/usr/bin/env bash
#
# test-gates.sh -- the JaCoCo coverage gate, the ArchUnit rules and the PIT mutation gate.
#
# The specification's "JaCoCo coverage gate", "ArchUnit tests" and "PIT
# mutation tests for critical packages" items.  PIT is genuinely run here; the
# other two ran inside mvn verify and this stage proves they were not vacuous.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only gates`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only gates "$@"
