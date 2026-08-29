#!/usr/bin/env bash
#
# nightly-manifest-verify.sh -- DELIBERATE STUB installed by PHASE-01.
#
# R-TEST-08 is the job that would have caught the PDV 2.6.0 pin going stale. It needs the tool manifest, which PHASE-05 owns; PHASE-15 delivers R-TEST-08 itself.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-manifest-verify.sh" \
    --phase  "PHASE-15" \
    --owns   "verify that every managed tool URL and checksum in the manifest is still reachable and unchanged, and fail loudly when an artefact disappears, changes checksum, or is superseded by a new upstream release (R-TEST-08)" \
    --spec   "specification.rst, R-TEST-08"
