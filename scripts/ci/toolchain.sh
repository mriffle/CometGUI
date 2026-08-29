#!/usr/bin/env bash
#
# toolchain.sh -- provision the project-local JDK and Maven, and prove their versions.
#
# Nothing is installed on the host and no setup-java action is used: the
# toolchain comes from scripts/feasibility/install-toolchain.sh by pinned
# SHA-256, exactly as it does on a developer machine.  A CI runner that
# resolved its JDK from an action would not be running the same build.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only toolchain`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only toolchain "$@"
