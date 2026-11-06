#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-large-dataset.sh -- the version matrix's fixture half: the whole
# Comet -> Percolator workflow for every (Comet, Percolator) pair the product
# offers on this host, over a real dataset, against version-keyed goldens
# (PHASE-15 exit gate item 1, R-TEST-07, AC-TST-10; Phase 15 unit 6; P15-4).
#
# THE FIXTURE IS ONE NAMED INPUT: COMETGUI_MATRIX_FIXTURE, a directory of
# *.mzML and one *.fasta.  There is no default, and on a runner there is no
# fixture: D-006's CI half -- which spectra and FASTA CI may use, under what
# licence, fetched from where -- is OPEN, and the local fixture under
# scratch/fixture is NOT the CI fixture (it must never be committed, fetched
# here or presented as one).
#
# NOT CONFIGURED (COMETGUI_MATRIX_FIXTURE empty -- on every runner today): this
# step says so, names what it waits for and what the job would need, and exits
# 70 through scripts/ci/stub-lib.sh, in its own job.  Exit 70 is the one
# non-zero status both pipeline rules accept (run-pipeline-locally.sh requires
# a stub to exit 70; check-workflows.py tells a stub by its reference to
# stub-lib.sh); a step that exited 0 having searched nothing would be the silent
# pass this project does not ship.
#
# CONFIGURED (COMETGUI_MATRIX_FIXTURE names a directory): it runs
#   bash scripts/matrix/run-matrix.sh --part pairs
# and exits with its status: 0 every pair RUN-PASSED against its reviewed
# golden for that fixture's digest; 1 red.  A D-006 CI fixture has a different
# digest from the local one, so its first run is red until goldens for it are
# added WITH their review records (R-TEST-07) -- by a person, not by this job.
#
# A note for the pipeline control: this file references stub-lib.sh, so
# run-pipeline-locally.sh always classifies it as a stub and expects 70.  Run
# with COMETGUI_MATRIX_FIXTURE exported, the step is real and the local control
# reports it as unexpected -- correctly: when D-006 is decided, whoever wires the
# fixture into the job moves the not-configured branch out (or re-classifies the
# step) in the same change.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-large-dataset.sh: takes no arguments; the fixture is COMETGUI_MATRIX_FIXTURE\n' >&2; exit 2; }

if [ -z "${COMETGUI_MATRIX_FIXTURE:-}" ]; then
    exec bash "${SCRIPT_DIR}/stub-lib.sh" \
        --script "nightly-large-dataset.sh" \
        --phase  "PHASE-15" \
        --installed-by "PHASE-01" \
        --owns   "run the whole Comet -> Percolator workflow for every (Comet, Percolator) pair the product offers, over a real dataset, against version-keyed goldens with R-TEST-07's review rule (scripts/matrix/run-matrix.sh --part pairs)" \
        --spec   "specification.rst, Version matrix tests and Scientific regression fixtures and oracles; PHASE-15 exit gate item 1" \
        --waits-for "D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN). COMETGUI_MATRIX_FIXTURE is empty, so nothing was searched; the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it" \
        --ci-needs "COMETGUI_MATRIX_FIXTURE naming a directory of *.mzML and one *.fasta, fetched by checksum before this step (a few to a few tens of MB: the local fixture's two mzML are 20 MB and its FASTA 14 MB, and each pair run takes about 15 s on 4 threads), under a licence the owner has accepted for CI use, from a host the owner names; then goldens for that fixture's digest under cometgui-app/src/test/resources/goldens/ with their review records" \
        --meanwhile "COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh -- every pair on this machine, against the local fixture's reviewed goldens" \
        --completed-by "the owner decides D-006's CI half and the fixture is fetched into the job (configuration: the configured branch of this script is already written)"
fi

exec bash "${ROOT}/scripts/matrix/run-matrix.sh" --part pairs
