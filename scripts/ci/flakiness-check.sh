#!/usr/bin/env bash
#
# flakiness-check.sh -- the flakiness policy, enforced (specification.rst,
# "Flakiness policy"; PHASE-15 exit gate item 6: "No release-critical test is
# quarantined").
#
# Red on any unconditional retry (surefire/failsafe rerunFailingTestsCount, an
# ignored test failure, a JUnit retry extension, a harness loop that re-runs a
# test runner until it passes), on any test-disabling construct that is neither
# a recognised legitimate condition nor covered by an entry of the quarantine
# register scripts/ci/quarantine.toml, on a register entry missing its issue,
# owner, captured diagnostics, reason, fix plan or date, and on any entry that
# names a release-critical test.  The work is done by
# scripts/ci/flakiness_check.py (standard library only, Python 3.11); the
# policy in words is docs/developer/testing.rst, "Flakiness policy".
#
# Usage:
#   bash scripts/ci/flakiness-check.sh                      check the tree
#   bash scripts/ci/flakiness-check.sh --list-release-critical
#   bash scripts/ci/flakiness-check.sh --self-test          every forbidden shape, injected into copies
#
# Exit status: 0 the tree keeps the policy; 1 it does not; 2 misuse, or a
# scan that found nothing to scan; 7 --self-test only: a case misbehaved.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

# The project virtualenv where it exists; otherwise the runner's own Python 3
# (the check is standard library only, and tomllib needs 3.11).
PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 11))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'flakiness-check.sh: no Python 3.11 or newer\n' >&2; exit 2; }

# -I: isolated; -B: no __pycache__ in the source tree.
exec "${PYTHON}" -I -B "${SCRIPT_DIR}/flakiness_check.py" --root "${PROJECT_ROOT}" "$@"
