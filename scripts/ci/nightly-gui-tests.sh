#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-gui-tests.sh -- DELIBERATE STUB, installed by PHASE-01 and re-worded
# by PHASE-14 unit 9 to say exactly what it waits for.
#
# The suites exist: the headless GUI tests run in every scripts/build.sh, and
# PHASE-14 built the end-to-end tier -- Tier A in-process, Tier B against the
# installed packaged archive -- graded by scripts/verify-e2e-gates.sh on this
# machine.  What they cannot run on a nightly runner yet is their data: every
# end-to-end scenario searches the D-006 LOCAL fixture, and D-006's CI half is
# OPEN -- no CI job may depend on the local fixture, and nothing may present it
# as the CI fixture.  The owning phase follows nightly.yml's job name.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh.  Making it exit 0 to turn a
# pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-gui-tests.sh" \
    --phase  "PHASE-15" \
    --installed-by "PHASE-01" \
    --owns   "run the headless and native GUI end-to-end suites (Tier A and Tier B) every night" \
    --spec   "specification.rst, Nightly pipeline" \
    --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN); the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
    --waits-for "a display on the runner (the project-local Xvfb closure) and, for Tier B, a packaged archive built on it (scripts/package/build-app-image.sh)" \
    --ci-needs "the CI fixture fetched by checksum; the X11 closure and font stack; network to the GitHub release hosts the tools' pinned URLs use; the D-007 fake on loopback" \
    --meanwhile "bash scripts/verify-e2e-gates.sh -- both tiers, both scenarios, the failure-path suite and gate item 5's injections, on the local fixture, on this machine"
