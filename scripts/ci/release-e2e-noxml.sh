#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# release-e2e-noxml.sh -- DELIBERATE STUB, installed by PHASE-01 and re-worded
# by PHASE-14 unit 9 to say exactly what it waits for.
#
# The test exists: TierBNoXmlScenarioTest (PHASE-14 unit 7) -- Percolator 3.09
# registered as a local binary, the Limelight explanation before any
# conversion, the compatible rerun installing 3.07.1 only on the action, and
# "Comet was not rerun" proved from disk -- against an installed packaged
# archive, graded by scripts/verify-e2e-gates.sh on this machine.  As a release
# step it waits for D-006's CI fixture and PHASE-16's release artefact, and
# for one more thing: Percolator 3.09 has no Linux x86-64 managed artefact, so
# the scenario registers the binary from the upstream .rpm, staged by hand
# under gitignored scratch/percolator/3.09.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh.  Making it exit 0 to turn a
# pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-e2e-noxml.sh" \
    --phase  "PHASE-16" \
    --installed-by "PHASE-01" \
    --owns   "run the no-XML compatibility end-to-end scenario (TierBNoXmlScenarioTest: a Percolator build without XML output, the compatible rerun, Comet not rerun) against the exact packaged artefact of this release" \
    --spec   "specification.rst, Release pipeline" \
    --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN); the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
    --waits-for "PHASE-16's scripts/ci/release-package.sh, still a stub: the exact artefact this step must test does not exist on the runner" \
    --waits-for "a display on the runner (the project-local Xvfb closure) for the packaged application's window" \
    --ci-needs "the CI fixture fetched by checksum; the release archive installed outside the checkout; Xvfb and the font stack; network to the GitHub release hosts; a Percolator 3.09 binary for the runner, fetched from the upstream release by a pinned URL and SHA-256 (there is no Linux managed build of 3.09; the local one is staged under scratch/percolator/3.09)" \
    --meanwhile "bash scripts/verify-e2e-gates.sh -- TierBNoXmlScenarioTest (gate item 3), and control 3i (the rerun touching Comet's outputs, caught on disk), on this machine"
