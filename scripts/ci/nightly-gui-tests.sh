#!/usr/bin/env bash
#
# nightly-gui-tests.sh -- DELIBERATE STUB installed by PHASE-01.
#
# PHASE-01 proved a headless JavaFX Scene can be built at all; the GUI end-to-end harness itself is PHASE-14.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-14 as the owner.
# Replacing this file with the real check is PHASE-14's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-gui-tests.sh" \
    --phase  "PHASE-14" \
    --owns   "run the headless and native GUI end-to-end suites (Tier A and Tier B)" \
    --spec   "specification.rst, Nightly pipeline"
