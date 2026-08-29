#!/usr/bin/env bash
#
# python-env.sh -- provision the project virtualenv for the documentation toolchain.
#
# .venv is created from requirements-dev.txt, pinned closure and all.  Nothing
# is installed on the host.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only python`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only python "$@"
