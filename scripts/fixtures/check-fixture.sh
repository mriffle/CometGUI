#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# check-fixture.sh -- the fixture acceptance check (PHASE-16, "The CI fixture",
# step 5; exit gate item 8; Phase 16 unit 2).
#
# Takes a fixture directory -- the two K562 mzML files (D-006) and one FASTA --
# and searches the pair against that FASTA through the product's own workflow,
# for every Comet and Percolator pair the product offers on this host, in the
# version matrix's configuration and with one result per spectrum.  Exits 0
# only if, for every pair: Percolator trained and wrote its results; PSMs and
# peptides at 1% each clear a floor; decoy PSMs clear a floor; PSMs fall on
# both sides of the 1% line; and the product's Limelight conversion is valid
# against the converter's own schema.  Each criterion is reported by name with
# its value and its floor.  The work is scripts/fixtures/fixture_acceptance.py's;
# the floors, each with its measurement and reasoning, are
# scripts/fixtures/fixture-acceptance.json; docs/developer/test_fixtures.rst
# explains both.
#
# It reads no golden: a FASTA that has just been made has a new fixture digest
# and no reviewed golden yet (R-TEST-07).
#
# OPT-IN: nothing here runs in a plain `mvn verify` or scripts/build.sh.
#
# Usage:
#   bash scripts/fixtures/check-fixture.sh DIR [--out DIR] [--offline] [--require-committed]
#   bash scripts/fixtures/check-fixture.sh --grade REPORT --fixture DIR
#   bash scripts/fixtures/check-fixture.sh --self-test DIR      # every control; searches
#   bash scripts/fixtures/check-fixture.sh --self-test-grading  # no Maven, no network
#   bash scripts/fixtures/check-fixture.sh --help
#
# --self-test DIR cuts the OVER-FILTERED FASTA (the identified proteins only)
# from the FASTA in DIR with the committed accession list, runs the whole check
# on it and requires it RED on decoy-psms and percolator-trained with one
# result per spectrum, and on nothing else.
#
# Network: the pinned upstream URLs of every offered Comet and Percolator and
# of the Limelight converter (GitHub), as the version matrix; Maven's
# repository unless --offline.  The runs are serial on any machine.
#
# Exit status (fixture_acceptance.py's): 0 green; 1 red; 2 misuse; 3 REFUSED
# (no fixture named, or a file missing -- nothing was searched, never green);
# 7 a self-test control did not behave as required.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

case "${1:-}" in
    -h|--help) sed -n '7,45p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

PYTHON=""
for candidate in "${ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'check-fixture.sh: no Python 3.9 or newer\n' >&2; exit 2; }

if [ "${1:-}" = "--self-test-grading" ]; then
    cd -- "${ROOT}"
    exec "${PYTHON}" -B "${SCRIPT_DIR}/fixture_acceptance.py" "$@"
fi

if [ ! -f "${ROOT}/tools/env.sh" ]; then
    bash "${ROOT}/scripts/ci/toolchain.sh" || { printf 'check-fixture.sh: the project toolchain could not be provisioned\n' >&2; exit 2; }
fi
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
command -v mvn >/dev/null 2>&1 || { printf 'check-fixture.sh: no mvn after sourcing tools/env.sh\n' >&2; exit 2; }

# Every argument that is not an option is a directory -- the fixture, or the
# value of --out, --grade, --fixture or --self-test -- and one named relative
# to where the caller stands must still be that directory after the cd below.
ARGS=()
for argument in "$@"; do
    case "${argument}" in
        -*|/*) ARGS+=("${argument}") ;;
        *) ARGS+=("${PWD}/${argument}") ;;
    esac
done

cd -- "${ROOT}"
exec "${PYTHON}" -B "${SCRIPT_DIR}/fixture_acceptance.py" ${ARGS[@]+"${ARGS[@]}"}
