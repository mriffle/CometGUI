#!/usr/bin/env bash
#
# traceability.sh -- the CometGUI traceability gate (R-DOC-03).
#
# R-DOC-03 requires a report mapping R-/AC- identifiers to phases and to test
# names, built by documentation CI, where an identifier with no implementing
# phase -- or an AC- with no test and no human-sign-off mark -- is a build
# failure. `scripts/ci/docs-build.sh` enforces that during the Sphinx build,
# through the builder-inited hook in docs/conf.py. This script is the same
# check without Sphinx: cheap enough to run on every pull request and on every
# edit to docs/traceability-map.toml. The gate itself takes no arguments.
#
# It does three things, and verifies the result of each rather than trusting an
# exit code:
#
#   1. `python -m traceability --check` -- validates the map, writes nothing.
#      Verified by parsing the counts it prints and requiring them to be
#      non-zero and self-consistent.
#   2. `python -m unittest` over the generator's own suite. Verified by
#      requiring "OK" and a non-zero test count in the output -- a suite that
#      collected nothing also exits 0.
#   3. A real generation into _build/traceability/, never into docs/. Verified
#      by counting the rendered R- and AC- rows and requiring them to equal the
#      counts step 1 reported.
#
# The documentation tree is never written to from here; the generated page is
# produced by the documentation build itself.
#
# Usage:
#   bash scripts/ci/traceability.sh
#   bash scripts/ci/traceability.sh --self-test   # the gate, then prove it can fail
#   bash scripts/ci/traceability.sh --help
#
# --self-test adds step 4: eight defects injected into copies of the project
# under _build/traceability/negative/, each of which must be caught with its own
# diagnostic -- a rule no phase delivers, a rule two phases deliver, an AC- with
# no test reference (PHASE-01 exit gate item 5), a named test that does not
# exist, both directions of a human-sign-off mismatch, and an identifier the
# specification does not define or that the map leaves out. It then shows the
# real strict Sphinx build failing on the third of those and passing once it is
# removed, because R-DOC-03 makes an incomplete map a *documentation build*
# failure, not merely a script failure. The working tree is never touched.
#
# Exit status:
#   0  the map validates, the suite passes, and the report renders completely
#   1  the map did not validate -- every problem is printed
#   2  harness misuse or a broken environment (no usable Python, no sources)
#   3  a step exited 0 but produced no or incomplete output
#   4  the generator's unit test suite failed, or --self-test found the gate
#      unfalsifiable: an injected defect that the check did not catch
#
# Needs no network access.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

SCRIPTS_DIR="${PROJECT_ROOT}/scripts"
PACKAGE_DIR="${SCRIPTS_DIR}/traceability"
OUT_DIR="${PROJECT_ROOT}/_build/traceability"
CHECK_LOG="${OUT_DIR}/check.log"
TESTS_LOG="${OUT_DIR}/unittest.log"
RENDER_LOG="${OUT_DIR}/render.log"
RENDERED="${OUT_DIR}/traceability.rst"

die() { printf 'traceability.sh: %s\n' "$1" >&2; exit "${2:-2}"; }

usage() { sed -n '3,52p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

SELF_TEST=0
case "${1:-}" in
    "")            ;;
    --self-test)   SELF_TEST=1 ;;
    -h|--help)     usage; exit 0 ;;
    *)             die "unknown argument: $1 (try --help)" ;;
esac
[ "$#" -le 1 ] || die "too many arguments (try --help)"

[ -d "${PACKAGE_DIR}" ] || die "no generator at ${PACKAGE_DIR}"
[ -f "${PROJECT_ROOT}/docs/traceability-map.toml" ] \
    || die "no mapping file at ${PROJECT_ROOT}/docs/traceability-map.toml"

# The generator is standard library only, so either interpreter works. The
# project virtualenv is preferred because it is the one every other gate uses;
# python3 is a legitimate fallback precisely because there is no dependency to
# install. tomllib requires 3.11.
PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] || continue
    if "${candidate}" -c 'import sys,tomllib; sys.exit(0 if sys.version_info >= (3,11) else 1)' \
        >/dev/null 2>&1; then
        PYTHON="${candidate}"
        break
    fi
done
[ -n "${PYTHON}" ] || die "no Python 3.11+ with tomllib (tried .venv/bin/python and python3)"

mkdir -p -- "${OUT_DIR}"
rm -f -- "${CHECK_LOG}" "${TESTS_LOG}" "${RENDER_LOG}" "${RENDERED}"

export PYTHONPATH="${SCRIPTS_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
export PYTHONDONTWRITEBYTECODE=1
cd -- "${PROJECT_ROOT}"

printf 'traceability.sh: interpreter %s (%s)\n' \
    "${PYTHON}" "$("${PYTHON}" -c 'import platform; print(platform.python_version())')"
printf 'traceability.sh: project root %s\n\n' "${PROJECT_ROOT}"

# run_tee LOGFILE COMMAND...  -- runs COMMAND, tees combined output to LOGFILE,
# and returns COMMAND's own exit status. Not the pipeline's: `cmd | tee` exits
# with tee's status, so a naive `cmd | tee || fail` reports a failing generator
# as a pass. That is exactly the "exit code 0 proves nothing" trap, one level
# down. Every call site puts this in a condition context, which is what keeps
# `set -e` from killing the script on an expected failure.
run_tee() {
    local log="$1"; shift
    local status
    "$@" 2>&1 | tee -- "${log}"
    status="${PIPESTATUS[0]}"
    return "${status}"
}

# ---------------------------------------------------------------------------
# 1. Validate the map. Writes nothing.
# ---------------------------------------------------------------------------

printf '=== 1/3  python -m traceability --check ===\n'
check_status=0
run_tee "${CHECK_LOG}" "${PYTHON}" -m traceability --check --root "${PROJECT_ROOT}" \
    || check_status=$?
if [ "${check_status}" -ne 0 ]; then
    printf '\ntraceability.sh: FAILED -- the traceability map did not validate.\n' >&2
    printf 'traceability.sh: R-DOC-03 makes this a documentation build failure too;\n' >&2
    printf 'traceability.sh: fix docs/traceability-map.toml or the phase document it\n' >&2
    printf 'traceability.sh: disagrees with. Full output at %s\n' "${CHECK_LOG}" >&2
    if [ "${check_status}" -eq 2 ]; then
        exit 2
    fi
    exit 1
fi

grep -q 'map is complete -- no problems found' -- "${CHECK_LOG}" \
    || die "--check exited 0 without reporting a complete map; see ${CHECK_LOG}" 3

RULES="$(sed -n 's/^traceability: \([0-9]\{1,\}\) R- rules.*/\1/p' -- "${CHECK_LOG}" | head -n 1)"
CRITERIA="$(sed -n 's/^traceability: [0-9]\{1,\} R- rules, \([0-9]\{1,\}\) AC- criteria.*/\1/p' \
    -- "${CHECK_LOG}" | head -n 1)"
[ -n "${RULES}" ] && [ "${RULES}" -gt 0 ] \
    || die "--check reported no R- rules; a report over an empty specification is not a pass" 3
[ -n "${CRITERIA}" ] && [ "${CRITERIA}" -gt 0 ] \
    || die "--check reported no AC- criteria; refusing to call that a pass" 3

# ---------------------------------------------------------------------------
# 2. The generator's own unit tests.
# ---------------------------------------------------------------------------

printf '\n=== 2/3  python -m unittest (the generator'\''s own suite) ===\n'
tests_status=0
run_tee "${TESTS_LOG}" "${PYTHON}" -m unittest discover \
    --start-directory "${PACKAGE_DIR}/tests" \
    --top-level-directory "${SCRIPTS_DIR}" \
    --verbose || tests_status=$?
if [ "${tests_status}" -ne 0 ]; then
    printf '\ntraceability.sh: FAILED -- the traceability generator'\''s unit tests did not pass.\n' >&2
    printf 'traceability.sh: full output at %s\n' "${TESTS_LOG}" >&2
    exit 4
fi

# unittest exits 0 for an empty suite as happily as for a passing one.
RAN="$(sed -n 's/^Ran \([0-9]\{1,\}\) tests\{0,1\} in .*/\1/p' -- "${TESTS_LOG}" | tail -n 1)"
[ -n "${RAN}" ] && [ "${RAN}" -gt 0 ] \
    || die "the unit test run collected no tests; an empty suite exits 0 too" 3
grep -qx 'OK' -- "${TESTS_LOG}" \
    || die "the unit test run did not end in OK; see ${TESTS_LOG}" 3

# ---------------------------------------------------------------------------
# 3. Render the report for real, outside the documentation tree, and count it.
# ---------------------------------------------------------------------------

printf '\n=== 3/3  render the report into %s ===\n' "${OUT_DIR}"
render_status=0
run_tee "${RENDER_LOG}" "${PYTHON}" -m traceability --root "${PROJECT_ROOT}" \
    --out "${RENDERED}" || render_status=$?
[ "${render_status}" -eq 0 ] || die "the generator failed while rendering; see ${RENDER_LOG}" 1

[ -s "${RENDERED}" ] || die "the generator exited 0 but wrote no report to ${RENDERED}" 3

RENDERED_RULES="$(grep -c '^   \* - ``R-' -- "${RENDERED}" || true)"
RENDERED_CRITERIA="$(grep -c '^   \* - ``AC-' -- "${RENDERED}" || true)"
[ "${RENDERED_RULES}" -eq "${RULES}" ] \
    || die "the report has ${RENDERED_RULES} R- row(s) but the specification defines ${RULES}" 3
[ "${RENDERED_CRITERIA}" -eq "${CRITERIA}" ] \
    || die "the report has ${RENDERED_CRITERIA} AC- row(s) but the specification defines ${CRITERIA}" 3
for marker in '.. _dev-traceability:' 'Requirement rules' 'Acceptance criteria' \
              'This page is generated'; do
    grep -qF -- "${marker}" "${RENDERED}" \
        || die "the rendered report is missing the section marker '${marker}'" 3
done

# ---------------------------------------------------------------------------
# 4. --self-test -- prove the gate can fail (CONTRIBUTING.rst, gate conventions)
# ---------------------------------------------------------------------------

NEGATIVE_DIR="${OUT_DIR}/negative"
GATE_CASE="criterion-with-no-test-reference"

if [ "${SELF_TEST}" -eq 1 ]; then
    SPHINX_BUILD="${PROJECT_ROOT}/.venv/bin/sphinx-build"
    [ -x "${SPHINX_BUILD}" ] || die "--self-test needs ${SPHINX_BUILD}; create the
project virtualenv first (nothing may be installed on the host):
    python3 -m venv ${PROJECT_ROOT}/.venv
    ${PROJECT_ROOT}/.venv/bin/pip install -r ${PROJECT_ROOT}/docs/requirements.txt"

    printf '\n=== 4/5  injected defects: each must be caught, and only by itself ===\n'
    self_status=0
    run_tee "${OUT_DIR}/selftest.log" "${PYTHON}" -m traceability.selftest \
        --root "${PROJECT_ROOT}" --work "${NEGATIVE_DIR}" || self_status=$?
    if [ "${self_status}" -ne 0 ]; then
        printf '\ntraceability.sh: SELF-TEST FAILED -- see %s\n' "${OUT_DIR}/selftest.log" >&2
        exit 4
    fi
    grep -q '^selftest: OK' -- "${OUT_DIR}/selftest.log" \
        || die "the self-test exited 0 without reporting OK" 3

    # The same defect, through the real documentation build. R-DOC-03 says an
    # AC- with no test and no human-sign-off mark "is a documentation build
    # failure" -- so showing the script exiting non-zero is not enough.
    printf '\n=== 5/5  the same defect must fail the strict Sphinx build ===\n'
    CASE_DIR="${NEGATIVE_DIR}/${GATE_CASE}"
    NEG_LOG="${OUT_DIR}/sphinx-broken.log"
    POS_LOG="${OUT_DIR}/sphinx-clean.log"

    "${PYTHON}" -m traceability.selftest --root "${PROJECT_ROOT}" \
        --work "${NEGATIVE_DIR}" --case "${GATE_CASE}" --keep > /dev/null \
        || die "could not re-create the ${GATE_CASE} copy" 4
    # A fresh clone has no generated page either; remove the copied one so the
    # only thing that can fail this build is the map.
    rm -f -- "${CASE_DIR}/docs/developer/traceability.rst"
    rm -rf -- "${CASE_DIR}/docs/_build"

    printf 'traceability.sh: running the R-DOC-05 command line over the injured copy:\n'
    printf '    %s -n -W -b html %s/docs %s/docs/_build/html\n\n' \
        "${SPHINX_BUILD}" "${CASE_DIR}" "${CASE_DIR}"
    neg_status=0
    run_tee "${NEG_LOG}" "${SPHINX_BUILD}" -n -W -b html \
        "${CASE_DIR}/docs" "${CASE_DIR}/docs/_build/html" || neg_status=$?
    if [ "${neg_status}" -eq 0 ]; then
        printf '\ntraceability.sh: SELF-TEST FAILED -- an AC- with no test reference did\n' >&2
        printf 'traceability.sh: NOT fail the documentation build. R-DOC-03 is not enforced.\n' >&2
        exit 4
    fi
    for needed in 'AC-NO-EVIDENCE' 'no test reference and no human-sign-off mark'; do
        grep -qF -- "${needed}" "${NEG_LOG}" \
            || die "the build failed, but not on the injected defect (no '${needed}' in ${NEG_LOG})" 4
    done
    printf '\ntraceability.sh: the documentation build failed as required (exit %s):\n' "${neg_status}"
    grep -F -- 'no test reference and no human-sign-off mark' "${NEG_LOG}" | sed 's/^/    /'

    printf '\n--- removing the injection and rebuilding the same tree ---\n'
    "${PYTHON}" -m traceability.selftest --root "${PROJECT_ROOT}" \
        --work "${NEGATIVE_DIR}" --case "${GATE_CASE}" > /dev/null \
        || die "could not restore the ${GATE_CASE} copy" 4
    rm -f -- "${CASE_DIR}/docs/developer/traceability.rst"
    rm -rf -- "${CASE_DIR}/docs/_build"
    pos_status=0
    run_tee "${POS_LOG}" "${SPHINX_BUILD}" -n -W -b html \
        "${CASE_DIR}/docs" "${CASE_DIR}/docs/_build/html" || pos_status=$?
    [ "${pos_status}" -eq 0 ] \
        || die "the copy does not build clean once the defect is removed; see ${POS_LOG}" 1
    [ -s "${CASE_DIR}/docs/developer/traceability.rst" ] \
        || die "the clean build exited 0 but generated no traceability page" 3
    [ -s "${CASE_DIR}/docs/_build/html/developer/traceability.html" ] \
        || die "the clean build exited 0 but published no traceability HTML" 3
    printf '\ntraceability.sh: self-test OK -- the gate fails on every injected defect\n'
    printf 'traceability.sh: and the documentation build passes once they are removed.\n'
fi

printf '\ntraceability.sh: PASSED.\n'
printf 'traceability.sh: %s R- rules, %s AC- criteria, all mapped and verified\n' \
    "${RULES}" "${CRITERIA}"
sed -n 's/^traceability: criteria by evidence -- /traceability.sh: criteria by evidence: /p' \
    -- "${CHECK_LOG}"
sed -n 's/^traceability: evidence names /traceability.sh: evidence names /p' -- "${CHECK_LOG}"
printf 'traceability.sh: unit tests    %s passed\n' "${RAN}"
printf 'traceability.sh: rendered      %s (%s R- rows, %s AC- rows)\n' \
    "${RENDERED}" "${RENDERED_RULES}" "${RENDERED_CRITERIA}"
printf 'traceability.sh: logs          %s, %s, %s\n' "${CHECK_LOG}" "${TESTS_LOG}" "${RENDER_LOG}"
if [ "${SELF_TEST}" -eq 1 ]; then
    printf 'traceability.sh: self-test    8 injected defects, all caught; strict Sphinx\n'
    printf 'traceability.sh:              build fails on gate item 5 and passes without it\n'
    printf 'traceability.sh: copies       %s\n' "${NEGATIVE_DIR}"
fi
printf 'traceability.sh: the published page is generated by the documentation build\n'
printf 'traceability.sh: (docs/conf.py -> builder-inited), not by this script.\n'
exit 0
