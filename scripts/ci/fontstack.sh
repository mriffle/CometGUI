#!/usr/bin/env bash
#
# fontstack.sh -- provision the project-local font stack the JavaFX tests need.
#
# NOT optional, and not a nicety.  PHASE-00 established that the pinned JDK
# ships no Monocle and that a Scene containing any Control dies with
# "fontFactory is null" without freetype, fontconfig, pango and real font
# files.  A GitHub runner hits this, so it is a named step rather than
# something left to chance.
#
# There is no second implementation of this check: this script runs
# `scripts/build.sh --only fontstack`, which is the same stage function the one
# documented build command runs.  That is what stops the pull-request pipeline
# and the local gate from drifting into different things.
#
# Exit status: whatever scripts/build.sh exits -- 0 only if the stage passed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd -- "${PROJECT_ROOT}"
exec bash scripts/build.sh --only fontstack "$@"
