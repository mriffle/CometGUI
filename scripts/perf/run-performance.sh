#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# run-performance.sh -- the performance suite's one entry point (PHASE-15 exit
# gate item 4; AC-RES-10; Phase 15 unit 8).
#
# Measures, through the product's own code paths, each of the specification's
# nine: application startup to the first usable window; importing a large
# Comet parameter file; opening, filtering, sorting and paging large PSM and
# peptide tables in the Results section; hashing a multi-gigabyte file; Comet's
# (informational) and Percolator's execution time; the peak GUI heap for the
# large tables; cancellation latency; and project reopen time -- each repeated
# to a median and a range, graded against the thresholds of this runner's
# PROFILE in scripts/perf/performance-data.json, into
# _build/performance/performance-report.{txt,json}.  The work is
# scripts/perf/performance.py's; see its docstring and
# docs/developer/testing.rst, "Performance".
#
# OPT-IN (P15-5): nothing here runs in a plain `mvn verify` or scripts/build.sh.
#
# RUNNER PROFILES: thresholds exist only for a recorded profile.  On a host no
# profile matches (every GitHub-hosted runner today) every measurement still
# runs and is reported, and the verdict is NO BASELINE FOR THIS RUNNER PROFILE,
# exit 4 -- never green.  The one recorded profile is a SHARED host, not the
# dedicated runner gate item 4 names.
#
# THE FIXTURE IS ONE NAMED INPUT, WITH NO DEFAULT (P15-4): COMETGUI_MATRIX_FIXTURE.
# Comet's and Percolator's times over spectra and Comet's cancellation need it;
# without it they are REFUSED (exit 3, never green).  D-006's CI fixture is OPEN.
#
# Usage:
#   bash scripts/perf/run-performance.sh [--part all|no-spectra|fixture] [--quick]
#                                        [--only ID,...] [--profile NAME] [--fixture DIR]
#                                        [--out DIR] [--work DIR] [--offline]
#   bash scripts/perf/run-performance.sh --self-test   # the grading, no Maven
#   bash scripts/perf/run-performance.sh --check-doc   # testing.rst against the data
#   bash scripts/perf/run-performance.sh --help
#
# Disk: the work directory (default _build/performance/work) holds up to about
# 3.5 GB while it runs -- a 2 GiB generated file, six copies of the 205 MB large
# results tables -- and is emptied at the end.  Network: the Percolator (and,
# for the fixture part, Comet) artefact's pinned upstream URL; Maven's
# repository unless --offline.
#
# Exit status (performance.py's): 0 green; 1 red; 2 misuse; 3 the fixture part
# refused; 4 no baseline for this runner profile; 5 check-doc red; 7 --self-test
# failed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

case "${1:-}" in
    -h|--help) sed -n '3,52p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

PYTHON=""
for candidate in "${ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'run-performance.sh: no Python 3.9 or newer\n' >&2; exit 2; }

case "${1:-}" in
    --self-test) exec "${PYTHON}" -B "${SCRIPT_DIR}/performance.py" --self-test ;;
    --check-doc) exec "${PYTHON}" -B "${SCRIPT_DIR}/performance.py" check-doc ;;
esac

if [ ! -f "${ROOT}/tools/env.sh" ]; then
    bash "${ROOT}/scripts/ci/toolchain.sh" || { printf 'run-performance.sh: the project toolchain could not be provisioned\n' >&2; exit 2; }
fi
if [ ! -d "${ROOT}/tools/fontstack-bookworm-20260829" ]; then
    bash "${ROOT}/scripts/ci/fontstack.sh" || { printf 'run-performance.sh: the font stack the headless GUI needs could not be provisioned\n' >&2; exit 2; }
fi
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
command -v mvn >/dev/null 2>&1 || { printf 'run-performance.sh: no mvn after sourcing tools/env.sh\n' >&2; exit 2; }

cd -- "${ROOT}"
exec "${PYTHON}" -B "${SCRIPT_DIR}/performance.py" run "$@"
