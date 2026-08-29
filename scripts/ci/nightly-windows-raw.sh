#!/usr/bin/env bash
#
# nightly-windows-raw.sh -- DELIBERATE STUB installed by PHASE-01.
#
# Needs a Windows runner and a real RAW fixture. Neither exists here: this environment has no non-Linux machine and D-006 (fixture data) is open.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-windows-raw.sh" \
    --phase  "PHASE-15" \
    --owns   "run the Windows Thermo RAW search smoke test on a Windows runner" \
    --spec   "specification.rst, Nightly pipeline"
