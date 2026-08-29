#!/usr/bin/env bash
#
# release-no-test-bridge.sh -- DELIBERATE STUB installed by PHASE-01.
#
# The bridge this check exists to detect is introduced by PHASE-14, which also delivers R-TEST-06.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-14 as the owner.
# Replacing this file with the real check is PHASE-14's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-no-test-bridge.sh" \
    --phase  "PHASE-14" \
    --owns   "prove that no test-only loopback bridge is present in the published artefact (R-TEST-06)" \
    --spec   "specification.rst, R-TEST-06"
