#!/usr/bin/env bash
#
# nightly-performance.sh -- DELIBERATE STUB installed by PHASE-01.
#
# There is nothing to measure until the workflow engine exists.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-performance.sh" \
    --phase  "PHASE-15" \
    --owns   "collect performance metrics and fail against the specification's thresholds on a dedicated runner" \
    --spec   "specification.rst, Nightly pipeline"
