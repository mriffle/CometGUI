#!/usr/bin/env bash
#
# windows-percolator-verify.sh -- run the Windows Percolator checklist.
#
# PHASE-00 residue, work unit U3.  This is a THIN WRAPPER, on purpose, and
# there is no logic in it beyond finding a Python interpreter.
#
# WHY.  Every check in this project must be a single `bash scripts/...`
# invocation that a person can run locally, and scripts/ci/check-workflows.py
# enforces exactly that -- so the entry point has to be a .sh.  But on a GitHub
# `windows-latest` runner `shell: bash` is Git Bash, an MSYS environment that
# rewrites arguments that look like POSIX paths, has its own quoting rules and
# its own idea of what a path is.  No logic may depend on Git Bash behaving
# like bash, so all of it lives in scripts/ci/windows_percolator_verify.py,
# which launches every process with an argument array rather than a shell
# string.  This file finds a Python and execs that.
#
# The script path handed to Python is RELATIVE, and this wrapper cd's to the
# project root first.  That is deliberate: MSYS rewrites arguments that begin
# with "/" into Windows paths on the way to a native program, and a relative
# path is left alone.  The flags this script takes contain no "/" either.
#
# FINDING PYTHON.  There is no .venv on a Windows runner and this script must
# not want one.  In Git Bash `python3` frequently does not exist while `python`
# does, and on a Windows image `python` may be a Microsoft Store app-execution
# alias that exits without running anything -- so each candidate is TESTED by
# running it, not merely located with `command -v`.  `py -3`, the Windows
# launcher, is the last resort.
#
# WHAT IT DOES.  The seven steps of the checklist in
# docs/feasibility/windows-artefact.rst ("Recommended: the checklist for a
# Windows machine"), plus a section 8 that exercises the portable noxml ZIP
# the product actually ships after D-002 option C.  Everything is written under
# _build/windows-verify/ (gitignored); the transcript is both written there and
# printed to stdout, so the job log carries it even if an artifact upload does
# not happen.  Nothing is installed, no pip, no apt, no third-party module.
#
# THIS IS NOT A STUB.  It deliberately does not use the project's stub helper
# under scripts/ci/, which marks a step as unimplemented and forces exit 70.
# That matters mechanically as well as morally: run-pipeline-locally.sh
# classifies a step as a stub by grepping the script for that helper's file
# name, so even NAMING it in a comment here would make a real check be
# reported as unimplemented.  Hence the circumlocution.
#
# Usage:
#   bash scripts/ci/windows-percolator-verify.sh                # Windows only
#   bash scripts/ci/windows-percolator-verify.sh --check-only   # anywhere
#   bash scripts/ci/windows-percolator-verify.sh --self-test    # anywhere
#   bash scripts/ci/windows-percolator-verify.sh --help
#
# Needs network access (about 2.1 MB: the 1.8 MB installer and the 0.3 MB
# portable ZIP), except for --self-test, which needs none.
#
# Exit status -- passed through from the Python driver unchanged:
#   0  PASS: every checklist assertion held on Windows, each naming the value
#      it observed.  Also a clean --check-only (which is NOT a pass: no Windows
#      binary was executed) and a clean --self-test.
#   1  NEGATIVE: the binary ran and the evidence contradicts an inference this
#      project relies on.  A real finding, meant to be loud.
#   2  INCONCLUSIVE: the binary did not run far enough for the test to mean
#      anything.
#   3  HARNESS FAILURE: download, checksum, extraction, Python or the PIN
#      generator failed.  Nothing was learned about the binary.
#   4  REFUSED: not a Windows host and --check-only was not given.
#   5  MISUSE: bad arguments, or no usable Python 3 was found.
#   6  SELF-TEST FAILED: a damaged case was accepted, or a control rejected.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
DRIVER="scripts/ci/windows_percolator_verify.py"
EXIT_MISUSE=5

die() { printf 'windows-percolator-verify.sh: %s\n' "$1" >&2; exit "${2:-${EXIT_MISUSE}}"; }

cd -- "${PROJECT_ROOT}"
[ -f "${DRIVER}" ] || die "no driver at ${PROJECT_ROOT}/${DRIVER}"

# Test the candidate by RUNNING it: `command -v python` succeeds for the
# Microsoft Store stub, which then does nothing useful.
usable_python() {
    "$@" -c 'import sys; raise SystemExit(0 if sys.version_info[:2] >= (3, 8) else 1)' \
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
    "no usable Python 3.8+ found: tried python3, python and 'py -3'. Install a
Python 3.8 or newer and make sure it is on PATH. NOT by adding a setup-python
action: this project's CI contract forbids setup actions, and
scripts/ci/check-workflows.py permits only the pinned checkout and
artifact-upload actions. Nothing is installed by this script."

printf 'windows-percolator-verify.sh: using %s (%s)\n' \
    "${PYTHON[*]}" "$("${PYTHON[@]}" -c 'import sys; print(sys.version.split()[0])')"
printf 'windows-percolator-verify.sh: + %s %s%s\n\n' \
    "${PYTHON[*]}" "${DRIVER}" "${*:+ $*}"

exec "${PYTHON[@]}" "${DRIVER}" "$@"
