#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-version-matrix.sh -- the version matrix's spectra-free half, for real
# (PHASE-15 exit gate item 1; Phase 15 unit 6; replaces the PHASE-01 stub).
#
# What it runs, on a Linux runner (2-4 cores is enough; nothing here is
# parallel):
#   1. the matrix's grading self-test (scripts/matrix/matrix.py --self-test:
#      synthetic census and rows, no Maven, no network), then
#   2. bash scripts/matrix/run-matrix.sh --part tools: every build the product
#      offers on this host installed from its pinned upstream URL into an empty
#      cache by the product's own Tool Manager and probes; Comet -p/-q against
#      the committed fixtures; Percolator over CometGUI's own synthetic PIN
#      against the reviewed goldens (R-TEST-07); PDV and the Limelight
#      converter installed and identified; and every other combination the
#      manifest and the product name recorded by name as UNAVAILABLE (with its
#      decision) or UNVERIFIED (with what a runner of that platform would run).
# The (Comet, Percolator) pair runs need spectra and are NOT run here: they
# are scripts/ci/nightly-large-dataset.sh's, its own job, waiting on D-006.
# In this job's report they are UNVERIFIED, "not exercised by this
# invocation", and that is not a failure of this job: it was not asked to.
#
# It needs the project toolchain (JDK and Maven, scripts/ci/toolchain.sh), which
# nightly.yml's version-matrix job provisions first, and the network: GitHub's
# release hosts for the artefacts (P15-5b) and Maven's repository for the
# build's dependencies.  It never depends on the D-006 local fixture.
#
# Report: _build/version-matrix/matrix-report.{json,txt}.
#
# Exit status: 0 the matrix is green for its part; 1 red (a RUN-FAILED row or a
# derivation problem, each named in the report); 2 misuse or a broken
# environment; 7 the grading self-test failed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-version-matrix.sh: takes no arguments\n' >&2; exit 2; }

bash "${ROOT}/scripts/matrix/run-matrix.sh" --self-test
exec bash "${ROOT}/scripts/matrix/run-matrix.sh" --part tools
