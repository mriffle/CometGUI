#!/usr/bin/env bash
#
# artefacts.sh -- prove the build produced what it claims to have produced.
#
# Exit code 0 proves nothing.  This checks that every module jar exists and
# holds class files, that the test reports exist and are clean, and that the
# run did not write to ~/.m2.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only artefacts`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only artefacts "$@"
