#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-determinism-fixture.sh -- the determinism suite's fixture half: the
# whole Comet -> Percolator run through the product's run port over a real
# dataset, repeated with the same inputs, parameters and seed, then with
# Comet's and Percolator's thread counts varied, then the seed (the control),
# for every (Comet, Percolator) pair the product offers on this host, compared
# artefact by artefact with the documented tolerance (PHASE-15 exit gate item
# 3; Phase 15 unit 7; P15-4).
#
# THE FIXTURE IS ONE NAMED INPUT: COMETGUI_MATRIX_FIXTURE, a directory of
# *.mzML and one *.fasta -- the version matrix's.  There is no default, and on a
# runner there is no fixture: D-006's CI half is OPEN, and the local fixture
# under scratch/fixture is NOT the CI fixture (it must never be committed,
# fetched here or presented as one).
#
# NOT CONFIGURED (COMETGUI_MATRIX_FIXTURE empty -- on every runner today): this
# step says so, names what it waits for and what the job would need, and exits
# 70 through scripts/ci/stub-lib.sh, in its own job, never green.
#
# CONFIGURED: it runs  bash scripts/matrix/run-matrix.sh --part determinism
# (both halves) and exits with its status: 0 green, 1 red.
#
# As for nightly-large-dataset.sh: this file references stub-lib.sh, so the
# pipeline control classifies it as a stub and expects 70; whoever wires the
# D-006 fixture into the job moves the not-configured branch out in the same
# change.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-determinism-fixture.sh: takes no arguments; the fixture is COMETGUI_MATRIX_FIXTURE\n' >&2; exit 2; }

if [ -z "${COMETGUI_MATRIX_FIXTURE:-}" ]; then
    exec bash "${SCRIPT_DIR}/stub-lib.sh" \
        --script "nightly-determinism-fixture.sh" \
        --phase  "PHASE-15" \
        --installed-by "PHASE-15" \
        --owns   "repeat the whole Comet -> Percolator run over a real dataset with the same inputs, parameters and seed, vary the thread counts and the seed, and compare every artefact with the documented tolerance (scripts/matrix/run-matrix.sh --part determinism)" \
        --spec   "specification.rst, Version matrix tests (repeated-run determinism on a pinned fixture); PHASE-15 exit gate item 3" \
        --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN). COMETGUI_MATRIX_FIXTURE is empty, so nothing was searched; the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
        --ci-needs "COMETGUI_MATRIX_FIXTURE naming a directory of *.mzML and one *.fasta, fetched by checksum before this step, under a licence the owner has accepted for CI use, from a host the owner names; about 300 MB of scratch for the runs' directories; six minutes measured on the 64-core reference host, not yet on a 4-core runner" \
        --meanwhile "COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh --part determinism -- every pair on this machine" \
        --completed-by "the owner decides D-006's CI half and the fixture is fetched into the job (configuration: the configured branch of this script is already written)"
fi

exec bash "${ROOT}/scripts/matrix/run-matrix.sh" --part determinism
