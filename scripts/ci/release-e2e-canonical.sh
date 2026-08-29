#!/usr/bin/env bash
#
# release-e2e-canonical.sh -- DELIBERATE STUB installed by PHASE-01.
#
# The Tier B harness and the canonical scenario are PHASE-14 deliverables.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-14 as the owner.
# Replacing this file with the real check is PHASE-14's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-e2e-canonical.sh" \
    --phase  "PHASE-14" \
    --owns   "run the Tier B canonical end-to-end scenario against the exact packaged artefact" \
    --spec   "specification.rst, Release pipeline"
