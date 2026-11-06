#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-performance.sh -- the performance suite's half that needs no spectra
# (PHASE-15 exit gate item 4; AC-RES-10; Phase 15 unit 8; P15-4).  Real since
# unit 8; it replaces PHASE-01's placeholder.
#
# What it runs: the grading's self-test (no Maven), then
#   bash scripts/perf/run-performance.sh --part no-spectra
# -- application startup to the first window, a large parameter file imported,
# the large PSM and peptide tables opened, filtered, sorted and paged in the
# Results section with the peak GUI heap, a 2 GiB generated file hashed,
# Percolator over the synthetic PIN, Cancel on the stand-in tool, and a project
# of 50 runs reopened -- each repeated, and graded against the thresholds of
# THIS RUNNER'S PROFILE in scripts/perf/performance-data.json.
#
# ON A GITHUB-HOSTED RUNNER THIS STEP IS RED, AND THAT IS THE HONEST STATE.  A
# runner with no recorded profile -- 2 or 4 shared cores, no fixed hardware --
# still runs and reports every measurement, and then exits 4, "NO BASELINE FOR
# THIS RUNNER PROFILE": thresholds measured on one machine mean nothing on
# another, and a step that passed there would be a silent pass.  It turns green
# when a dedicated runner exists and its profile is recorded (docs/developer/
# testing.rst, "Performance": what such a runner needs, and how its thresholds
# are derived).  The one profile recorded today is the shared 64-core host this
# project is developed on, which is NOT the dedicated runner gate item 4 names.
#
# Comet's and Percolator's times over real spectra and Cancel during a real
# Comet search are scripts/ci/nightly-performance-fixture.sh's, its own job,
# waiting on D-006's CI fixture.
#
# COMETGUI_PERFORMANCE_QUICK=1 runs --quick: fewer repeats of every
# measurement, the same sizes and the same thresholds.  The pipeline control
# (scripts/ci/run-pipeline-locally.sh) sets it, so the local transcript costs
# about three minutes rather than about seven; a nightly sets nothing.
#
# Needs the project JDK, Maven and font stack (provisioned by the runner script
# if absent), the network (GitHub's release host for Percolator; Maven's
# repository) and about 3.5 GB of disk in _build/performance/work, emptied
# afterwards.  Exit status: run-performance.sh's -- 0 green, 1 red, 2 misuse,
# 4 no baseline for this runner profile, 7 the self-test failed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-performance.sh: takes no arguments\n' >&2; exit 2; }

declare -a MODE=()
case "${COMETGUI_PERFORMANCE_QUICK:-}" in
    "") ;;
    1) MODE=(--quick) ;;
    *) printf 'nightly-performance.sh: COMETGUI_PERFORMANCE_QUICK is 1 or unset, not %s\n' "${COMETGUI_PERFORMANCE_QUICK}" >&2; exit 2 ;;
esac

bash "${ROOT}/scripts/perf/run-performance.sh" --self-test
exec bash "${ROOT}/scripts/perf/run-performance.sh" --part no-spectra "${MODE[@]}"
