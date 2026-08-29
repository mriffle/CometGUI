#!/usr/bin/env bash
#
# nightly-determinism.sh -- DELIBERATE STUB installed by PHASE-01.
#
# Determinism can only be compared once there is a real search to run.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-determinism.sh" \
    --phase  "PHASE-15" \
    --owns   "run the same search twice and compare the outputs for determinism" \
    --spec   "specification.rst, Nightly pipeline"
