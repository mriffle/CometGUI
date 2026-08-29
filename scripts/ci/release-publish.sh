#!/usr/bin/env bash
#
# release-publish.sh -- DELIBERATE STUB installed by PHASE-01.
#
# This project has NO GIT REMOTE and none may be created (D-008, open). This stub is also the guard: it refuses rather than pushing anywhere, and it must keep refusing until the owner decides where CometGUI is published.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-16 as the owner.
# Replacing this file with the real check is PHASE-16's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-publish.sh" \
    --phase  "PHASE-16" \
    --owns   "publish the release, only if every preceding gate passed -- and it must publish nothing until D-008 says where CometGUI is published" \
    --spec   "specification.rst, Release pipeline; DECISIONS.rst D-008"
