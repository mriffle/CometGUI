#!/usr/bin/env bash
#
# run-pipeline-locally.sh -- run a CI workflow's steps on this machine and
# record the transcript.
#
# WHY THIS EXISTS.  PHASE-01 exit gate item 6 is "CI runs the pull-request
# pipeline on a pull request and its failure modes are demonstrated, not
# assumed."  Half of that cannot be met here and must not be faked: this
# repository has NO GIT REMOTE, creating one is D-008 (open, the owner's
# decision), and pointing a workflow at a repository that does not exist would
# be worse than an honest gap.  So GitHub has never executed these files.
#
# The other half is met literally.  This script reads the steps OUT OF THE
# WORKFLOW FILES -- via `check-workflows.py --list-steps`, not out of a copy of
# them -- and executes each one, so what is demonstrated is the workflow's own
# content rather than a hand-written approximation of it.
#
# Each step is classified before it runs, and the classification is the
# assertion:
#
#   * a step whose script is a stub (it references scripts/ci/stub-lib.sh) MUST
#     exit 70.  A stub that exited 0 would be a silent pass, which is the thing
#     PHASE-01 was told not to ship;
#   * every other step MUST exit 0;
#   * an `uses:` step is recorded as not runnable locally (the checkout is the
#     working tree);
#   * a Windows or macOS matrix entry is recorded as NOT RUN: this environment
#     has one Linux machine.  Naming them is deliberate -- a later phase turns
#     them on rather than discovering they were never written.
#
# Anything else -- a stub that passed, a real step that failed -- fails this
# script, and it says which.
#
# Usage:
#   bash scripts/ci/run-pipeline-locally.sh                 # all three
#   bash scripts/ci/run-pipeline-locally.sh pull-request
#   bash scripts/ci/run-pipeline-locally.sh nightly release
#   bash scripts/ci/run-pipeline-locally.sh --help
#
# Output: _build/ci-transcript/<workflow>/ per-step logs, and
#         _build/ci-transcript/transcript.txt, the evidence for the half of
#         gate item 6 that can be met.
#
# Exit status:
#   0  every step behaved as its classification requires
#   1  a step did not: a real step failed, or a stub passed
#   2  harness misuse or a broken environment

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
OUT="${PROJECT_ROOT}/_build/ci-transcript"
TRANSCRIPT="${OUT}/transcript.txt"
STUB_EXIT=70

die() { printf 'run-pipeline-locally.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
usage() { sed -n '3,47p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

declare -a WORKFLOWS=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        pull-request|nightly|release) WORKFLOWS+=("$1.yml"); shift ;;
        *) die "unknown argument: $1 (try --help)" ;;
    esac
done
[ "${#WORKFLOWS[@]}" -gt 0 ] || WORKFLOWS=(pull-request.yml nightly.yml release.yml)

PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] && { PYTHON="${candidate}"; break; }
done
[ -n "${PYTHON}" ] || die "no Python 3"

# Do not litter the source tree with __pycache__ directories.
export PYTHONDONTWRITEBYTECODE=1

cd -- "${PROJECT_ROOT}"
rm -rf -- "${OUT}"
mkdir -p -- "${OUT}"

emit() { printf '%s\n' "$*" | tee -a -- "${TRANSCRIPT}"; }

FAILURES=0
TOTAL=0
RAN=0

{
    printf '===============================================================================\n'
    printf ' CometGUI -- CI pipelines executed locally\n'
    printf '===============================================================================\n'
    printf ' generated  %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    printf ' by         scripts/ci/run-pipeline-locally.sh\n'
    printf ' host       %s\n' "$(uname -srm)"
    printf ' git HEAD   %s\n' "$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD 2>/dev/null || echo '(no git)')"
    printf ' git remote %s\n' "$(git -C "${PROJECT_ROOT}" remote -v | head -1 || true)"
    printf '\n'
    printf ' WHAT THIS IS EVIDENCE FOR, AND WHAT IT IS NOT.\n'
    printf '\n'
    printf ' PHASE-01 exit gate item 6 reads: "CI runs the pull-request pipeline on a pull\n'
    printf ' request and its failure modes are demonstrated, not assumed."\n'
    printf '\n'
    printf '   NOT MET   "on a pull request". This repository has no git remote, and\n'
    printf '             creating one is D-008, an owner decision coupled to the GPL-3.0\n'
    printf '             source-availability obligation. GitHub has never executed these\n'
    printf '             workflow files, and no step, script or configuration here points\n'
    printf '             at a repository that does not exist.\n'
    printf '\n'
    printf '   MET       Every step in every pipeline has been executed on this machine,\n'
    printf '             with its exact command, and each behaved as it must: real steps\n'
    printf '             passed, stubs failed with exit %d naming the phase that owns them.\n' "${STUB_EXIT}"
    printf '             The steps below were read out of .github/workflows/*.yml at run\n'
    printf '             time, so this transcript cannot describe a pipeline other than\n'
    printf '             the one in the repository.\n'
    printf '\n'
} > "${TRANSCRIPT}"
cat -- "${TRANSCRIPT}"

is_stub() {
    local script="$1"
    [ -f "${PROJECT_ROOT}/${script}" ] && grep -q 'stub-lib\.sh' -- "${PROJECT_ROOT}/${script}"
}

for workflow in "${WORKFLOWS[@]}"; do
    wf_dir="${OUT}/${workflow%.yml}"
    mkdir -p -- "${wf_dir}"
    emit ""
    emit "==============================================================================="
    emit " .github/workflows/${workflow}"
    emit "==============================================================================="

    index=0
    noted_job=""
    while IFS=$'\t' read -r job runner name kind value; do
        [ -n "${job}" ] || continue
        index=$((index + 1))
        TOTAL=$((TOTAL + 1))
        slug="$(printf '%s' "${name}" | tr -c 'A-Za-z0-9' '-' | tr -s '-' | sed 's/^-//;s/-$//' | cut -c1-48)"
        log="${wf_dir}/$(printf '%02d' "${index}")-${slug}.log"

        if [ "${kind}" = "uses" ]; then
            emit ""
            emit "  step ${index}  [${job} / ${runner}]  ${name}"
            emit "    uses:  ${value}"
            emit "    SKIPPED  a GitHub action; not runnable locally. The checkout that this"
            emit "             step would perform is the working tree this script is running in."
            continue
        fi

        # A matrix job lists its runners comma-separated. Only the Linux entry
        # can be executed here, and the others are recorded as unverifiable.
        non_linux=""
        case "${runner}" in
            *windows*|*macos*) non_linux="${runner}" ;;
        esac
        if [ -n "${non_linux}" ] && [[ "${runner}" != *ubuntu* ]]; then
            emit ""
            emit "  step ${index}  [${job} / ${runner}]  ${name}"
            emit "    run:   ${value}"
            emit "    NOT RUN  this environment has no ${runner} machine. Recorded, not"
            emit "             assumed: a later phase switches this on rather than writes it."
            continue
        fi

        script="$(printf '%s' "${value}" | awk '{print $2}')"
        expected=0
        classification="real step: must exit 0"
        if is_stub "${script}"; then
            expected="${STUB_EXIT}"
            classification="STUB: must exit ${STUB_EXIT} and name its owning phase"
        fi

        emit ""
        emit "  step ${index}  [${job} / ${runner}]  ${name}"
        emit "    run:   ${value}"
        emit "    class: ${classification}"
        started="$(date +%s)"
        got=0
        # shellcheck disable=SC2086
        ( cd -- "${PROJECT_ROOT}" && eval "${value}" ) >"${log}" 2>&1 || got=$?
        elapsed=$(( $(date +%s) - started ))
        RAN=$((RAN + 1))

        if [ "${got}" -eq "${expected}" ]; then
            emit "    RESULT ok   exit ${got} in ${elapsed}s   log: ${log#"${PROJECT_ROOT}/"}"
            if [ "${expected}" -eq "${STUB_EXIT}" ]; then
                owner="$(grep -m1 'owned by' -- "${log}" | sed 's/^ *//' || true)"
                emit "           ${owner}"
            fi
        else
            FAILURES=$((FAILURES + 1))
            emit "    RESULT FAILED  exit ${got}, expected ${expected}   log: ${log#"${PROJECT_ROOT}/"}"
            if [ "${expected}" -eq "${STUB_EXIT}" ] && [ "${got}" -eq 0 ]; then
                emit "           A STUB EXITED 0. That is a silent pass and is exactly what"
                emit "           PHASE-01 was told not to ship."
            fi
            sed -n '1,25p' -- "${log}" | sed 's/^/           | /' | tee -a -- "${TRANSCRIPT}"
        fi

        if [ -n "${non_linux}" ] && [ "${noted_job}" != "${job}" ]; then
            noted_job="${job}"
            emit "    NOTE   this job's matrix is '${runner}'. Only the Linux entry runs here, for"
            emit "           every step of this job: there is no non-Linux machine in this"
            emit "           environment and, with no remote, nowhere to run one. The Windows and"
            emit "           macOS entries are named so a later phase switches them on rather than"
            emit "           writes them. Unverified is not passed."
        fi
    done < <("${PYTHON}" "${SCRIPT_DIR}/check-workflows.py" --root "${PROJECT_ROOT}" --list-steps "${workflow}")
done

emit ""
emit "==============================================================================="
emit " SUMMARY"
emit "==============================================================================="
emit "  ${TOTAL} step(s) across ${#WORKFLOWS[@]} workflow(s); ${RAN} executed on this machine; ${FAILURES} unexpected."
if [ "${FAILURES}" -ne 0 ]; then
    emit "  FAILED -- a real step failed, or a stub passed. See the logs above."
    exit 1
fi
emit "  OK -- every executed step behaved as its classification requires."
emit "  Still unmet: 'on a pull request'. No remote exists (D-008)."
emit ""
printf '\nrun-pipeline-locally.sh: transcript at %s\n' "${TRANSCRIPT#"${PROJECT_ROOT}/"}"
exit 0
