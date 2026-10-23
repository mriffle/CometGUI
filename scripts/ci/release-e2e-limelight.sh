#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# release-e2e-limelight.sh -- DELIBERATE STUB, installed by PHASE-01 and
# re-worded by PHASE-14 unit 9 to say exactly what it waits for.
#
# The test exists: steps 26-27 of the canonical scenario -- the real converter
# installed on first use, its XML validated against the XSD inside the
# converter JAR and counted against the raw table, then uploaded to the D-007
# fake on loopback -- run in both TierACanonicalScenarioTest and
# TierBCanonicalScenarioTest, which scripts/verify-e2e-gates.sh grades on this
# machine (and gate item 5's bypassed final submit goes red there).  As a
# release step it waits for D-006's CI fixture (the scenario needs a real search
# to convert) and for PHASE-16's release artefact, exactly as
# release-e2e-canonical.sh does.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh.  Making it exit 0 to turn a
# pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-e2e-limelight.sh" \
    --phase  "PHASE-16" \
    --installed-by "PHASE-01" \
    --owns   "run the XML-capable full Limelight end-to-end scenario (conversion validated against the converter's XSD, upload to the D-007 fake) against the exact packaged artefact of this release" \
    --spec   "specification.rst, Release pipeline" \
    --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN); the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
    --waits-for "PHASE-16's scripts/ci/release-package.sh, still a stub: the exact artefact this step must test does not exist on the runner" \
    --waits-for "a display on the runner (the project-local Xvfb closure) for the packaged application's window" \
    --ci-needs "the CI fixture fetched by checksum; the release archive installed outside the checkout; Xvfb and the font stack; network to the GitHub release hosts; the D-007 fake on loopback -- never a real Limelight server (a sandbox is the nightly slot's, D-007)" \
    --meanwhile "bash scripts/verify-e2e-gates.sh -- steps 26-27 of TierACanonicalScenarioTest and TierBCanonicalScenarioTest, and control 5b (the final submit bypassed, caught at step 27), on this machine"
