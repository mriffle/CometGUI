#!/usr/bin/env bash
#
# check-failure-paths.sh -- Phase 14 exit-gate item 4: "Every failure-path
# scenario in the specification has a passing test."
#
# Reads scripts/e2e/failure-paths.toml, the map of the specification's
# "Failure-path scenarios" to the tests that exercise each, and checks:
#
#   (a) the map's scenarios are exactly the specification's -- the paragraph is
#       parsed out of specification.rst on every run, so a scenario missing,
#       added, reworded or moved in the map is a failure;
#   (b) every mapped test exists: its class file under */src/test/java, any
#       nested class, and the method, declared with a JUnit test annotation and
#       not @Disabled;
#   (c) with --reports, every mapped test in --scope ran and passed, counted from
#       the surefire reports given (a skip, a failure, a missing report and a
#       run of zero are each a failure).
#
# A scenario marked "partial" in the map fails the check unless
# --allow-partial is given, which reports it instead.
#
# Usage:
#   bash scripts/e2e/check-failure-paths.sh [--allow-partial]
#                                         (a) and (b) on the real tree
#   bash scripts/e2e/check-failure-paths.sh --reports DIR... [--scope S] [--allow-partial]
#                                         (a), (b) and (c); S is ordinary
#                                         (default), e2e or all
#   bash scripts/e2e/check-failure-paths.sh --map FILE --spec FILE ...
#                                         check other copies
#   bash scripts/e2e/check-failure-paths.sh --self-test
#                                         damage copies of the map, the
#                                         specification and synthetic reports
#                                         under _build/e2e-failure-paths-selftest,
#                                         require each rejected with its own
#                                         diagnostic, then check the real tree
#   bash scripts/e2e/check-failure-paths.sh --help
#
# Exit status:
#   0  every check passed (the self-test: every control behaved and the real
#      tree passed (a) and (b))
#   1  a check failed, each finding named (the self-test: a control was not
#      injected, not rejected, or rejected with the wrong diagnostic -- a
#      harness failure, never a pass)
#   2  misuse: bad arguments, no python, an unreadable map or specification

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECKER="${ROOT}/scripts/e2e/failure_paths.py"
SELFTEST_DIR="${ROOT}/_build/e2e-failure-paths-selftest"

usage() {
    sed -n '3,46p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

misuse() {
    printf 'check-failure-paths: %s\n' "$1" >&2
    exit 2
}

PYTHON="${ROOT}/.venv/bin/python"
[[ -x "${PYTHON}" ]] || PYTHON="$(command -v python3 || true)"
[[ -n "${PYTHON}" && -x "${PYTHON}" ]] || misuse "no python (.venv/bin/python or python3)"
"${PYTHON}" -c 'import tomllib' 2>/dev/null || misuse "${PYTHON} has no tomllib (Python 3.11 or newer is required)"

MAP="${ROOT}/scripts/e2e/failure-paths.toml"
SPEC="${ROOT}/specification.rst"
SCOPE="ordinary"
SELF_TEST=0
ALLOW_PARTIAL=()
REPORTS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --help|-h) usage; exit 0 ;;
        --self-test) SELF_TEST=1; shift ;;
        --allow-partial) ALLOW_PARTIAL=(--allow-partial); shift ;;
        --map) [[ $# -ge 2 ]] || misuse "--map needs a file"; MAP="$2"; shift 2 ;;
        --spec) [[ $# -ge 2 ]] || misuse "--spec needs a file"; SPEC="$2"; shift 2 ;;
        --scope) [[ $# -ge 2 ]] || misuse "--scope needs ordinary, e2e or all"; SCOPE="$2"; shift 2 ;;
        --reports)
            shift
            [[ $# -ge 1 && "$1" != --* ]] || misuse "--reports needs at least one directory"
            while [[ $# -gt 0 && "$1" != --* ]]; do REPORTS+=("$1"); shift; done
            ;;
        *) misuse "unknown argument: $1 (see --help)" ;;
    esac
done

[[ -f "${MAP}" ]] || misuse "no map at ${MAP}"
[[ -f "${SPEC}" ]] || misuse "no specification at ${SPEC}"
case "${SCOPE}" in ordinary|e2e|all) ;; *) misuse "--scope must be ordinary, e2e or all, not ${SCOPE}" ;; esac

if [[ "${SELF_TEST}" -eq 1 ]]; then
    exec "${PYTHON}" "${CHECKER}" --spec "${SPEC}" --map "${MAP}" --source-root "${ROOT}" \
        --self-test "${SELFTEST_DIR}"
fi

ARGS=(--spec "${SPEC}" --map "${MAP}" --source-root "${ROOT}" --scope "${SCOPE}")
if [[ ${#REPORTS[@]} -gt 0 ]]; then
    ARGS+=(--reports "${REPORTS[@]}")
fi
exec "${PYTHON}" "${CHECKER}" "${ARGS[@]}" "${ALLOW_PARTIAL[@]+"${ALLOW_PARTIAL[@]}"}"
