#!/usr/bin/env bash
#
# nightly-version-matrix.sh -- DELIBERATE STUB installed by PHASE-01.
#
# The version matrix needs the tool registry (PHASE-05) and the capability probe, neither of which exists yet.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-version-matrix.sh" \
    --phase  "PHASE-15" \
    --owns   "run the broader Comet and Percolator version matrix, restricted to combinations the tool manifest provides and whose loadability probe passes" \
    --spec   "specification.rst, Nightly pipeline"
