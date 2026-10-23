#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# release-e2e-canonical.sh -- DELIBERATE STUB, installed by PHASE-01 and
# re-worded by PHASE-14 unit 9 to say exactly what it waits for.
#
# The test exists: TierBCanonicalScenarioTest (PHASE-14 unit 7) runs all thirty
# steps of the canonical scenario against an installed packaged archive through
# the test-only loopback bridge, and scripts/verify-e2e-gates.sh grades it on
# this machine.  It cannot be this release step yet, for two reasons that are
# not this phase's to remove:
#
#   * D-006's CI half is OPEN.  The scenario searches the D-006 LOCAL fixture
#     (two K562 mzML files and the UniProt human proteome, fetched by checksum
#     into gitignored scratch/), which the owner chose for local testing only.
#     No CI job may depend on it, and nothing may present it as the CI fixture.
#   * "that exact artefact" is the one PHASE-16's release-package.sh will build;
#     it is still a stub.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh.  Making it exit 0 to turn a
# pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "release-e2e-canonical.sh" \
    --phase  "PHASE-16" \
    --installed-by "PHASE-01" \
    --owns   "run the Tier B canonical end-to-end scenario (TierBCanonicalScenarioTest) against the exact packaged artefact of this release" \
    --spec   "specification.rst, Release pipeline" \
    --waits-for "D-006's CI fixture set, which the owner has deliberately not chosen yet (DECISIONS.rst, D-006: that half is OPEN); the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
    --waits-for "PHASE-16's scripts/ci/release-package.sh, still a stub: the exact artefact this step must test does not exist on the runner" \
    --waits-for "a display on the runner (the project-local Xvfb closure) for the packaged application's window" \
    --ci-needs "the CI fixture fetched by checksum; the release archive installed outside the checkout; Xvfb and the font stack; network to the GitHub release hosts the tools' pinned URLs use; the D-007 fake on loopback -- never a real Limelight server" \
    --meanwhile "bash scripts/verify-e2e-gates.sh -- builds the archive, installs it and runs TierBCanonicalScenarioTest (gate item 2) on the local fixture, on this machine" \
    --meanwhile "by hand: bash scripts/package/build-app-image.sh, then LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierBCanonicalScenarioTest -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true"
