#!/usr/bin/env bash
#
# release-package.sh -- DELIBERATE STUB installed by PHASE-01.
#
# jpackage was proved feasible in PHASE-00, but there is no application to package and release packaging is PHASE-16's deliverable.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-16 as the owner.
# Replacing this file with the real check is PHASE-16's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-package.sh" \
    --phase  "PHASE-16" \
    --owns   "build the native packaged application with its bundled runtime and produce the installer or archive for this platform" \
    --spec   "specification.rst, Release pipeline"
