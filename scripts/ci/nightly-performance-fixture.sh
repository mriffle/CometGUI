#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-performance-fixture.sh -- the performance suite's fixture part:
# Comet's execution time (informational) and Percolator's over a real dataset,
# and Cancel during a real Comet search, through the product's run port
# (PHASE-15 exit gate item 4; Phase 15 unit 8; P15-4).
#
# THE FIXTURE IS ONE NAMED INPUT: COMETGUI_MATRIX_FIXTURE, a directory of
# *.mzML and one *.fasta -- the version matrix's.  There is no default, and on a
# runner there is no fixture: D-006's CI half is OPEN, and the local fixture
# under scratch/fixture is NOT the CI fixture.
#
# NOT CONFIGURED (COMETGUI_MATRIX_FIXTURE empty -- on every runner today): this
# step says so, names what it waits for and what the job would need, and exits
# 70 through scripts/ci/stub-lib.sh, in its own job, never green.
#
# CONFIGURED: it runs  bash scripts/perf/run-performance.sh --part fixture  and
# exits with its status -- which on a runner with no recorded profile is 4, "no
# baseline for this runner profile", for the same reason as the no-spectra
# job's (scripts/ci/nightly-performance.sh).
#
# As for nightly-large-dataset.sh: this file references stub-lib.sh, so the
# pipeline control classifies it as a stub and expects 70; whoever wires the
# D-006 fixture into the job moves the not-configured branch out in the same
# change.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-performance-fixture.sh: takes no arguments; the fixture is COMETGUI_MATRIX_FIXTURE\n' >&2; exit 2; }

if [ -z "${COMETGUI_MATRIX_FIXTURE:-}" ]; then
    exec bash "${SCRIPT_DIR}/stub-lib.sh" \
        --script "nightly-performance-fixture.sh" \
        --phase  "PHASE-15" \
        --installed-by "PHASE-15" \
        --owns   "measure Comet's (informational) and Percolator's execution time over a real dataset and Cancel during a real Comet search, against this runner's profile (scripts/perf/run-performance.sh --part fixture)" \
        --spec   "specification.rst, Performance and resource tests; PHASE-15 exit gate item 4" \
        --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN). COMETGUI_MATRIX_FIXTURE is empty, so nothing was searched; the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
        --waits-for "a dedicated performance runner with a recorded profile in scripts/perf/performance-data.json; on any other runner the configured step exits 4, no baseline for this runner profile" \
        --ci-needs "COMETGUI_MATRIX_FIXTURE naming a directory of *.mzML and one *.fasta, fetched by checksum before this step, under a licence the owner has accepted for CI use, from a host the owner names; about two minutes measured on the 64-core reference host" \
        --meanwhile "COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/perf/run-performance.sh --part fixture -- on this machine" \
        --completed-by "the owner decides D-006's CI half and the fixture is fetched into the job (configuration: the configured branch of this script is already written)"
fi

exec bash "${ROOT}/scripts/perf/run-performance.sh" --part fixture
