#!/usr/bin/env bash
#
# release-checksums.sh -- DELIBERATE STUB installed by PHASE-01.
#
# There are no release artefacts to checksum until release-package.sh is real.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-16 as the owner.
# Replacing this file with the real check is PHASE-16's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-checksums.sh" \
    --phase  "PHASE-16" \
    --owns   "compute and publish the release checksums for every produced artefact" \
    --spec   "specification.rst, Release pipeline"
