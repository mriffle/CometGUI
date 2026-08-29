#!/usr/bin/env bash
#
# release-e2e-noxml.sh -- DELIBERATE STUB installed by PHASE-01.
#
# Needs the Percolator capability probe (PHASE-05/09) and the packaged end-to-end harness (PHASE-14).
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-14 as the owner.
# Replacing this file with the real check is PHASE-14's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-e2e-noxml.sh" \
    --phase  "PHASE-14" \
    --owns   "run the no-XML compatibility end-to-end scenario against a Percolator build without XML output" \
    --spec   "specification.rst, Release pipeline"
