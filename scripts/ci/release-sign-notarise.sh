#!/usr/bin/env bash
#
# release-sign-notarise.sh -- DELIBERATE STUB installed by PHASE-01.
#
# Needs signing identities and notarisation credentials that this project does not have; whether they exist at all is adjacent to D-008.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-16 as the owner.
# Replacing this file with the real check is PHASE-16's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-sign-notarise.sh" \
    --phase  "PHASE-16" \
    --owns   "sign and notarise the native installers where infrastructure permits" \
    --spec   "specification.rst, Release pipeline"
