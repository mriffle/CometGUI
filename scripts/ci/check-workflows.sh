#!/usr/bin/env bash
#
# check-workflows.sh -- prove the CI definitions and the scripts they invoke
# cannot drift apart.
#
# The check itself is scripts/ci/check-workflows.py; this wrapper exists so
# that every CI step is a uniform `scripts/ci/*.sh` invocation, and so that the
# pull-request pipeline verifies its own definition.
#
# Usage:
#   bash scripts/ci/check-workflows.sh
#   bash scripts/ci/check-workflows.sh --self-test   # the gate, then prove it can fail
#   bash scripts/ci/check-workflows.sh --help
#
# --self-test copies .github/ and scripts/ under _build/, damages the copy
# nineteen ways -- among them renaming a script the workflow names, using an
# action outside a workflow's allowlist, and dropping the `if: always()` that
# makes a FAILING run still upload its evidence -- and requires every damaged
# copy to be rejected and the undamaged one accepted.  The count is a floor,
# not a fixture: scripts/verify-all-gates.sh fails if it ever goes down.
#
# Needs no network access.
#
# Exit status: check-workflows.py's, unchanged --
#   0 they agree   1 they do not   2 misuse   3 unparseable workflow
#   4 --self-test only: a damaged copy was accepted

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

die() { printf 'check-workflows.sh: %s\n' "$1" >&2; exit "${2:-2}"; }

case "${1:-}" in
    ""|--self-test) ;;
    -h|--help) sed -n '3,26p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $1 (try --help)" ;;
esac

PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] && { PYTHON="${candidate}"; break; }
done
[ -n "${PYTHON}" ] || die "no Python 3 (tried .venv/bin/python and python3)"

# Do not litter the source tree with __pycache__ directories.
export PYTHONDONTWRITEBYTECODE=1

exec "${PYTHON}" "${SCRIPT_DIR}/check-workflows.py" --root "${PROJECT_ROOT}" "$@"
