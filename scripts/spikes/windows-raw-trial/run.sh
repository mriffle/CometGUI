#!/usr/bin/env bash
#
# run.sh -- run the Windows RAW trial (PHASE-16, work unit 1).
#
# A THIN WRAPPER, on purpose: it finds a Python 3 and hands over to trial.py,
# whose header says what the trial is, how to run it and what it prints.
# There is no logic here because on a GitHub `windows-latest` runner
# `shell: bash` is Git Bash, an MSYS environment that rewrites arguments that
# look like POSIX paths; nothing may depend on it behaving like bash.  The
# same arrangement, for the same reason, as
# scripts/ci/windows-percolator-verify.sh.
#
# The script path handed to Python is RELATIVE and this wrapper cd's to the
# project root first: MSYS rewrites an argument that begins with "/" on its
# way to a native program, and leaves a relative path alone.
#
# FINDING PYTHON.  A runner has no .venv and this must not want one.  In Git
# Bash `python3` often does not exist while `python` does, and `python` may be
# a Microsoft Store alias that exits without running anything -- so each
# candidate is TESTED by running it.  `py -3` is the last resort.  Nothing is
# installed: no setup action, no pip.
#
# Python runs with -I (isolated): the trial reads downloaded files, and an
# isolated interpreter takes no module from the current directory, the
# script's directory or the environment.  trial.py imports the standard
# library only.
#
# Usage:
#   bash scripts/spikes/windows-raw-trial/run.sh                 # Windows only
#   bash scripts/spikes/windows-raw-trial/run.sh --check-only    # anywhere
#   bash scripts/spikes/windows-raw-trial/run.sh --check-only --work-dir DIR
#
# Exit status -- trial.py's, unchanged:
#   0  every Comet release read ph050213_18.raw and Percolator gave counts at
#      q < 0.01 for each; or a clean --check-only, which executes nothing
#   1  a Comet release could not read ph050213_18.raw
#   2  read, but Percolator did not train or found nothing at q < 0.01
#   3  no verdict: a download failed its pin, or the harness failed
#   4  refused: not Windows, and --check-only was not given
#   5  misuse, or no usable Python 3.9+ was found

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
DRIVER="scripts/spikes/windows-raw-trial/trial.py"
EXIT_MISUSE=5

die() { printf 'run.sh: %s\n' "$1" >&2; exit "${2:-${EXIT_MISUSE}}"; }

cd -- "${PROJECT_ROOT}"
[ -f "${DRIVER}" ] || die "no driver at ${PROJECT_ROOT}/${DRIVER}"

# Test the candidate by RUNNING it: `command -v python` succeeds for the
# Microsoft Store alias, which then does nothing useful.
usable_python() {
    "$@" -I -c 'import sys; raise SystemExit(0 if sys.version_info[:2] >= (3, 9) else 1)' \
        >/dev/null 2>&1
}

PYTHON=()
for candidate in python3 python; do
    if command -v -- "${candidate}" >/dev/null 2>&1 && usable_python "${candidate}"; then
        PYTHON=("${candidate}")
        break
    fi
done
if [ "${#PYTHON[@]}" -eq 0 ] && command -v -- py >/dev/null 2>&1 && usable_python py -3; then
    PYTHON=(py -3)
fi
[ "${#PYTHON[@]}" -gt 0 ] || die \
    "no usable Python 3.9+ found: tried python3, python and 'py -3'. Nothing is
installed by this script, and this project's workflows may not add a setup
action (scripts/ci/check-workflows.py)."

printf 'run.sh: using %s (%s)\n' \
    "${PYTHON[*]}" "$("${PYTHON[@]}" -I -c 'import sys; print(sys.version.split()[0])')"
printf 'run.sh: + %s -I %s%s\n\n' "${PYTHON[*]}" "${DRIVER}" "${*:+ $*}"

exec "${PYTHON[@]}" -I "${DRIVER}" "$@"
