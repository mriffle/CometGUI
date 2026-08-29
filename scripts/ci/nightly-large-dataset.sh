#!/usr/bin/env bash
#
# nightly-large-dataset.sh -- DELIBERATE STUB installed by PHASE-01.
#
# There is no workflow engine, no real dataset fixture and no oracle to compare against yet.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-large-dataset.sh" \
    --phase  "PHASE-15" \
    --owns   "run the larger real-data scientific regression with tolerant oracles and version-keyed goldens" \
    --spec   "specification.rst, Nightly pipeline"
