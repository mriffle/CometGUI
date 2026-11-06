#!/usr/bin/env bash
#
# nightly-manifest-verify.sh -- R-TEST-08, the tool manifest verification job.
#
# Downloads every artefact manifests/tools.json pins (each distinct URL once,
# into a temporary directory, never executed), checks its size, SHA-256 and
# MD5 -- and each pinned archive member's -- against the manifest, and checks
# that every tool repository's newest published GitHub release is one the
# manifest records.  CometGUI redistributes no tool (D-008), so an asset that
# vanishes or changes upstream breaks installation for every new user; this
# job is the only thing that notices first.  The work is done by
# scripts/ci/manifest_verify.py (standard library only); see its docstring and
# docs/developer/testing.rst, "Tool manifest verification".
#
# Usage:
#   bash scripts/ci/nightly-manifest-verify.sh
#   bash scripts/ci/nightly-manifest-verify.sh --manifest PATH [--not-adopted PATH]
#   bash scripts/ci/nightly-manifest-verify.sh --self-test   # loopback only, no upstream
#   bash scripts/ci/nightly-manifest-verify.sh --help
#
# Network (P15-5b): the manifest's download URLs and wherever GitHub redirects
# them, and https://api.github.com/repos/<owner>/<repo>/releases for the
# repositories those URLs name.  Nothing else.  --self-test touches neither.
#
# Exit status (manifest_verify.py's, unchanged):
#   0  VERIFIED          every record and member matches; every newest release recorded
#   1  UPSTREAM CHANGED  (a) a size or digest changed, (b) an artefact is gone
#                        upstream, (c) a new upstream release is unrecorded
#   2  misuse, or the manifest or the not-adopted list refused
#   3  COULD NOT VERIFY  a check could not be completed -- never green
#   7  --self-test only: a case did not behave as required

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

# The project virtualenv where it exists; otherwise the runner's own Python 3
# (the CI job provisions nothing: the verifier is standard library only).
# "python" is tried last for the Windows runner, and only if it is Python 3.
PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)" "$(command -v python || true)"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ] \
        && "${candidate}" -c 'import sys; sys.exit(sys.version_info < (3, 8))' 2>/dev/null; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || { printf 'nightly-manifest-verify.sh: no Python 3.8 or newer\n' >&2; exit 2; }

# -I: isolated (no user site, no PYTHON* variables, no current directory on the
# path); -B: no __pycache__ in the source tree.
exec "${PYTHON}" -I -B "${SCRIPT_DIR}/manifest_verify.py" "$@"
