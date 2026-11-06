#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-determinism.sh -- the determinism suite's half that needs no spectra
# (PHASE-15 exit gate item 3; Phase 15 unit 7; P15-4).  Real since unit 7; it
# replaces PHASE-01's placeholder.
#
# Every Percolator build the product offers on this runner is installed from
# its pinned upstream URL into an empty cache by the product's own Tool Manager,
# then run over CometGUI's own committed synthetic PIN through the product's
# command builder: the same run three times (the same seed, each in a fresh
# directory), then with the thread count varied, then -- the control -- with
# another seed.  Every artefact is compared with the first run by the tolerance
# written in scripts/matrix/determinism-data.json and justified in
# docs/developer/testing.rst, "Determinism"; the two must state the same
# tolerance.  Exit 0 only when every repeat and variation agrees within it and
# the control differs where it must; 1 red.
#
# The Comet -> Percolator half needs spectra and runs in its own nightly job,
# scripts/ci/nightly-determinism-fixture.sh, which waits on D-006's CI fixture.
#
# Cost (measured 2026-11-06 on the 64-core reference host, warm Maven
# repository): 29 to 56 s in all, most of it Maven's start and compilation; six runs
# per offered Percolator (twelve on Linux x86-64), each under a second.  Needs
# the project JDK and Maven, and the network to GitHub's release hosts
# (P15-5b); no spectra.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-determinism.sh: takes no arguments\n' >&2; exit 2; }

bash "${ROOT}/scripts/matrix/run-matrix.sh" --self-test
exec bash "${ROOT}/scripts/matrix/run-matrix.sh" --part determinism-synthetic
