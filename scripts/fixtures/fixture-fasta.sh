#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# fixture-fasta.sh -- select, build and verify the fixture FASTA (PHASE-16,
# "The CI fixture", steps 3, 4 and 6; Phase 16 unit 2).  A thin wrapper: it
# finds a Python 3 and hands over to scripts/fixtures/fixture_fasta.py, whose
# header states the two selection rules.
#
# Usage:
#   bash scripts/fixtures/fixture-fasta.sh describe
#       # no source needed: the committed FASTA against the committed list
#   bash scripts/fixtures/fixture-fasta.sh verify --source UP000005640_9606.fasta
#       # rebuild from the source and the list; compare byte for byte
#   bash scripts/fixtures/fixture-fasta.sh build --source FASTA --out FILE
#   bash scripts/fixtures/fixture-fasta.sh identify --report DIR --source FASTA \
#       --background N --out LIST
#       # the list again, from an acceptance report made over the source
#
# The source is UP000005640_9606.fasta of UniProt release 2026_02, pinned by
# SHA-256 in fixture_fasta.py; any other bytes are refused.
#
# Exit status: 0 done and verified; 1 a check failed; 2 misuse or refused.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

case "${1:-}" in
    -h|--help|"") sed -n '7,26p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; [ -n "${1:-}" ] && exit 0 || exit 2 ;;
esac

PYTHON=""
for candidate in "${ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'fixture-fasta.sh: no Python 3.9 or newer\n' >&2; exit 2; }

exec "${PYTHON}" -B "${SCRIPT_DIR}/fixture_fasta.py" "$@"
