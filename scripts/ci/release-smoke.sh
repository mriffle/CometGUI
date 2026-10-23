#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# release-smoke.sh -- DELIBERATE STUB, installed by PHASE-01 and re-worded by
# PHASE-14 unit 9 to say exactly what it waits for.
#
# The check itself exists: scripts/package/smoke-installed.sh (PHASE-14 unit 5)
# proves an installed archive starts with no JDK on PATH, its bundled runtime
# has bin/java and JavaFX, it shows a CometGUI window and writes nothing into
# its own install tree.  What this release step cannot have yet is the thing
# to run it on: the artefact of THIS release, which PHASE-16's
# scripts/ci/release-package.sh will produce and which is still a stub.  Running
# the smoke test here against an archive built some other way would present a
# different artefact as the release one.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh.  Making it exit 0 to turn a
# pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-smoke.sh" \
    --phase  "PHASE-14" \
    --installed-by "PHASE-01" \
    --owns   "run the clean-home packaged smoke test (scripts/package/smoke-installed.sh) against the exact installer output of this release" \
    --spec   "specification.rst, Release pipeline" \
    --waits-for "PHASE-16's scripts/ci/release-package.sh, still a stub: no release artefact exists on the runner for this step to install" \
    --waits-for "a display on the runner: the smoke test requires a real CometGUI window, on the project-local Xvfb closure (tools/x11-bookworm-20260829), which nothing provisions on a hosted runner yet" \
    --ci-needs "the archive release-package.sh wrote, installed by scripts/package/install-archive.sh outside the checkout; the X11 closure and font stack; the Limelight converter JAR fetched by its pinned URL and SHA-256 (smoke-installed.sh --converter-jar); no spectrum or FASTA data" \
    --meanwhile "bash scripts/package/build-app-image.sh, then bash scripts/package/install-archive.sh ARCHIVE DIR and bash scripts/package/smoke-installed.sh DIR -- the same check against a locally built archive (PHASE-14 unit 5)" \
    --meanwhile "bash scripts/verify-e2e-gates.sh builds that archive afresh and drives it end to end (Tier B)"
