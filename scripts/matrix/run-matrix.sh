#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# run-matrix.sh -- the version matrix's one entry point (PHASE-15 exit gate
# item 1; Phase 15 unit 6).
#
# Installs every build the product offers on this host from its pinned upstream
# URL into an empty tool cache, through the product's own Tool Manager and
# probes, exercises each (Comet -p/-q against the committed fixtures,
# Percolator over CometGUI's own synthetic PIN), runs the whole Comet ->
# Percolator workflow for every (Comet, Percolator) pair over the named
# fixture, and grades every combination the manifest and the product name --
# RUN-PASSED, RUN-FAILED, UNAVAILABLE or UNVERIFIED -- into
# _build/version-matrix/matrix-report.{json,txt}.  The work is
# scripts/matrix/matrix.py's; see its docstring and docs/developer/testing.rst,
# "Version matrix".
#
# THE DETERMINISM SUITE (PHASE-15 exit gate item 3; Phase 15 unit 7) is this
# runner's --part determinism (both halves) or --part determinism-synthetic (no
# spectra): the same pinned run repeated, then varied where nothing should
# change, compared artefact by artefact with the documented tolerance by
# scripts/matrix/determinism.py into _build/determinism/; see its docstring and
# docs/developer/testing.rst, "Determinism".
#
# OPT-IN (P15-5): nothing here runs in a plain `mvn verify` or scripts/build.sh.
#
# THE FIXTURE IS ONE NAMED INPUT, WITH NO DEFAULT (P15-4):
#   COMETGUI_MATRIX_FIXTURE=DIR   a directory of *.mzML and one *.fasta
# Without it the pair part is REFUSED (exit 3, never green).  D-006's CI fixture
# is OPEN; locally:  COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh
#
# Usage:
#   bash scripts/matrix/run-matrix.sh [--part tools|pairs|all] [--out DIR]
#                                     [--fixture DIR] [--offline] [--no-local-309]
#   bash scripts/matrix/run-matrix.sh --part determinism|determinism-synthetic
#                                     [--out DIR] [--fixture DIR] [--offline]
#   bash scripts/matrix/run-matrix.sh --self-test   # both gradings, no Maven, no network
#   bash scripts/matrix/run-matrix.sh --help
#
# Network (P15-5b): the manifest's download URLs and the GitHub storage hosts
# they redirect to; Maven's repository unless --offline.
#
# Exit status (matrix.py's): 0 green; 1 red; 2 misuse; 3 the fixture part
# refused (no fixture named); 7 --self-test failed.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

case "${1:-}" in
    -h|--help) sed -n '3,47p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

PYTHON=""
for candidate in "${ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'run-matrix.sh: no Python 3.9 or newer\n' >&2; exit 2; }

if [ "${1:-}" = "--self-test" ]; then
    "${PYTHON}" -B "${SCRIPT_DIR}/matrix.py" --self-test
    exec "${PYTHON}" -B "${SCRIPT_DIR}/determinism.py" --self-test
fi

GRADER="matrix.py"
for argument in "$@"; do
    case "${argument}" in
        determinism|determinism-synthetic) GRADER="determinism.py" ;;
    esac
done

if [ ! -f "${ROOT}/tools/env.sh" ]; then
    bash "${ROOT}/scripts/ci/toolchain.sh" || { printf 'run-matrix.sh: the project toolchain could not be provisioned\n' >&2; exit 2; }
fi
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
command -v mvn >/dev/null 2>&1 || { printf 'run-matrix.sh: no mvn after sourcing tools/env.sh\n' >&2; exit 2; }

cd -- "${ROOT}"
exec "${PYTHON}" -B "${SCRIPT_DIR}/${GRADER}" run "$@"
