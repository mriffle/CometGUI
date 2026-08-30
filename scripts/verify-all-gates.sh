#!/usr/bin/env bash
#
# CometGUI -- run every falsifiability control the project has, in one command.
#
#   bash scripts/verify-all-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 01 installs eleven quality
# gates and each of them ships with its own demonstration of failure.  This
# script is the aggregate: it runs every one of those demonstrations, maps each
# to the PHASE-01 exit gate item it serves, and exits non-zero if any control
# fails to bite.
#
# IT DELEGATES.  It injects no defect of its own and re-implements no gate.
# Every control below is somebody else's harness -- scripts/verify-*.sh and the
# --self-test modes of scripts/ci/*.sh -- called at its documented entry point.
# Duplicating an injection here would create a second thing to keep in step
# with the gate, which is the drift these scripts exist to prevent.
#
# EXIT CODE 0 PROVES NOTHING, AND THAT APPLIES TO THIS SCRIPT TOO.  A sub-
# harness that exits 0 having run nothing is exactly the failure the project
# warns about, so for every control this script requires three things:
#
#   1. the sub-harness exists and is executable -- checked for EVERY selected
#      control BEFORE any of them runs, and fatal if not.  An aggregator that
#      quietly skips a missing harness is worse than no aggregator, because it
#      converts an absent gate into a green line;
#   2. it exits 0 and its output carries the marker that means "the defect was
#      injected and caught", not merely "the program ended";
#   3. it reports at least as many controls as the floor recorded here.  The
#      floors were measured on 2026-08-29 by running each harness.  A harness
#      may grow -- more controls is fine and the number is printed -- but a run
#      that grades fewer controls than it used to has had controls removed,
#      skipped or silently short-circuited, and that is a FAILURE, not a pass.
#
# COST.  About seven minutes on the 2026-08-29 development machine, almost all
# of it Maven: the coverage/architecture/mutation harness rebuilds a damaged
# copy of the reactor nine times and runs PIT.  It is far too slow to be a
# stage of scripts/build.sh and is deliberately NOT wired into it.  Run it
# before signing off a phase, after touching anything under config/, pom.xml or
# scripts/, and from the nightly pipeline.
#
# NETWORK.  The dependency-scan control needs https://api.osv.dev.  That is
# deliberate and is not an offline mode waiting to be added: an offline
# dependency scan is not a dependency scan.  Everything else runs offline once
# tools/, .venv/ and _build/m2repo exist.
#
# THE WORKING TREE IS NEVER TOUCHED.  Every sub-harness damages a copy under
# _build/.  This script writes only _build/all-gate-logs/.
#
# Exit status:
#   0  every control ran and bit
#   1  at least one control did not bite, or reported fewer controls than its
#      recorded floor
#   2  misuse (unknown option, unknown gate name)
#   3  a sub-harness is missing or not executable -- a control would have been
#      skipped, and a skipped control is never a pass

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly LOGS="${ROOT}/_build/all-gate-logs"

# The controls, in the order they run: cheap first, so a broken tree is
# reported in seconds rather than after the Maven harnesses.  The gate-item
# mapping is in gate_spec below, not in this order.
readonly -a ALL_GATES=(
    license
    workflows
    docs
    traceability
    sbom
    depscan
    pipeline
    quality
    tests
)

PASSED=0
FAILED=0
declare -a FAILURES=()
declare -a ROWS=()
declare -a COVERED=()

# ------------------------------------------------------------- the controls --
#
# gate_spec NAME populates, for one control:
#   GATE_ITEMS   the PHASE-01 exit gate item(s) it serves, for the summary
#   GATE_DEFECT  what is deliberately broken, in one line
#   GATE_SCRIPT  the sub-harness, relative to the repository root
#   GATE_ARGS    its arguments
#   GATE_PROOF   literal strings that MUST appear in the output; their absence
#                means the harness ended without doing its job
#   GATE_FLOOR   the number of controls it reported on 2026-08-29
#   GATE_UNIT    what that number counts, for the summary line
#
# gate_count NAME LOG echoes the number of controls the harness reported, or
# nothing if it reported none in the expected form.

gate_spec() {
    GATE_ITEMS=""; GATE_DEFECT=""; GATE_SCRIPT=""; GATE_ARGS=()
    GATE_PROOF=(); GATE_FLOOR=0; GATE_UNIT="control(s)"
    case "$1" in
        license)
            GATE_ITEMS="D-001"
            GATE_DEFECT="five damaged copies of LICENSE -- truncated, altered title, CRLF-expanded, a wrong git blob, and absent"
            GATE_SCRIPT="scripts/verify-license.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("SELF-TEST PASSED" "real LICENSE accepted")
            GATE_FLOOR=5
            GATE_UNIT="damaged copies rejected"
            ;;
        workflows)
            GATE_ITEMS="6"
            GATE_DEFECT="nineteen damaged copies of .github/ -- a renamed step script, a dropped required step, continue-on-error, a trailing || true, a git push added to release.yml, an unknown workflow naming a missing script, an action outside a workflow allowlist, an action pinned to a tag, and the if: always() dropped from the transcript upload"
            GATE_SCRIPT="scripts/ci/check-workflows.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("self-test OK" "the undamaged one accepted")
            GATE_FLOOR=9
            GATE_UNIT="damaged copies rejected"
            ;;
        docs)
            GATE_ITEMS="2"
            GATE_DEFECT="a broken :ref: cross-reference appended to a copy of docs/index.rst"
            GATE_SCRIPT="scripts/ci/docs-build.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "self-test OK -- fails on the broken cross-reference, passes without it."
                "docs-build.sh: PASSED."
            )
            GATE_FLOOR=1
            GATE_UNIT="injected cross-reference"
            ;;
        traceability)
            GATE_ITEMS="5"
            GATE_DEFECT="eight defects in copies of the map and the phase documents -- among them an AC- given no test reference, which must also fail the strict Sphinx build"
            GATE_SCRIPT="scripts/ci/traceability.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "selftest: OK"
                "self-test OK -- the gate fails on every injected defect"
                "traceability.sh: PASSED."
            )
            GATE_FLOOR=8
            GATE_UNIT="injected defects caught"
            ;;
        sbom)
            GATE_ITEMS="6"
            GATE_DEFECT="eight damaged SBOMs -- empty components array, no components key, SPDX in a CycloneDX field, JUnit dropped, reactor modules only, a mangled purl, a missing file, zero bytes"
            GATE_SCRIPT="scripts/ci/sbom.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("damaged SBOM(s) rejected, the real one accepted." "sbom.sh: PASSED.")
            GATE_FLOOR=8
            GATE_UNIT="damaged SBOMs rejected"
            ;;
        depscan)
            GATE_ITEMS="6"
            GATE_DEFECT="a known-vulnerable fixture, three kinds of unreachable endpoint, an endpoint that answers 200 with an all-clear lie, five kinds of bad allowlist, and an empty SBOM"
            GATE_SCRIPT="scripts/ci/dependency-scan.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("dependency-scan.sh: self-test OK" "control-real-scan")
            GATE_FLOOR=16
            GATE_UNIT="cases"
            ;;
        pipeline)
            GATE_ITEMS="6"
            GATE_DEFECT="none injected: every nightly and release step whose work belongs to a later phase is a stub, and each MUST exit 70 rather than 0 -- the silent pass this phase was told not to ship"
            GATE_SCRIPT="scripts/ci/run-pipeline-locally.sh"
            GATE_ARGS=(nightly release)
            GATE_PROOF=("0 unexpected." "OK -- every executed step behaved as its classification requires.")
            GATE_FLOOR=24
            GATE_UNIT="steps executed and classified"
            ;;
        quality)
            GATE_ITEMS="1, 6"
            GATE_DEFECT="misformatted source, an MIT header on a GPL-3.0 file, a package-info.java with no header at all, string comparison by reference, a brace-less conditional, a guaranteed null dereference"
            GATE_SCRIPT="scripts/verify-quality-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=("Every gate rejected its defect and accepted the clean tree.")
            GATE_FLOOR=20
            GATE_UNIT="controls"
            ;;
        tests)
            GATE_ITEMS="3, 4"
            GATE_DEFECT="a JavaFX import in the domain, a ProcessBuilder outside the process service, a truncated ArchUnit import, an untested class in a gated package, an untested view-model, a weakened test suite, a module whose coverage was never measured"
            GATE_SCRIPT="scripts/verify-test-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=("Every gate rejected its defect and accepted the clean tree.")
            GATE_FLOOR=32
            GATE_UNIT="assertions"
            ;;
        *)
            return 1
            ;;
    esac
    return 0
}

gate_count() {
    local name="$1" log="$2"
    case "${name}" in
        license)
            sed -n 's/.*SELF-TEST PASSED -- \([0-9][0-9]*\) negative controls rejected.*/\1/p' -- "${log}" | head -1 ;;
        workflows)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\) damaged copies rejected.*/\1/p' -- "${log}" | head -1 ;;
        docs)
            grep -cF -- 'self-test OK -- fails on the broken cross-reference' "${log}" || true ;;
        traceability)
            sed -n 's/^selftest: OK -- \([0-9][0-9]*\) case(s).*/\1/p' -- "${log}" | head -1 ;;
        sbom)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\) damaged SBOM(s) rejected.*/\1/p' -- "${log}" | head -1 ;;
        depscan)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\)\/[0-9][0-9]* cases.*/\1/p' -- "${log}" | head -1 ;;
        pipeline)
            sed -n 's/.*workflow(s); \([0-9][0-9]*\) executed on this machine; 0 unexpected.*/\1/p' -- "${log}" | head -1 ;;
        quality)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        tests)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) assertion(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
    esac
}

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- run every falsifiability control the project has and prove
that every PHASE-01 quality gate still fails on the defect it exists to catch.

Usage:
  bash scripts/${SCRIPT_NAME}                 run every control
  bash scripts/${SCRIPT_NAME} --only NAME     run one control (repeatable, or
                                              comma-separated)
  bash scripts/${SCRIPT_NAME} --list          what the controls are, what each
                                              injects, and the command that
                                              proves it
  bash scripts/${SCRIPT_NAME} --help

WHAT IT DOES.  It runs each gate's own harness -- scripts/verify-*.sh and the
--self-test modes of scripts/ci/*.sh -- and requires each to exit 0, to print
the marker that means the injected defect was caught, and to grade at least as
many controls as it did when this script was written.  It injects nothing
itself.  A sub-harness that is missing or not executable is a fatal error
before anything runs (exit 3): a skipped control must never be counted as a
pass.

WHAT IT COSTS.  About seven minutes, almost all of it Maven.  Elapsed time is
printed per control.  This is why it is not a stage of scripts/build.sh.

WHEN TO RUN IT.
  * Before signing off a phase -- it is the evidence that the phase's gates
    still bite, which "the build is green" is not.
  * After changing anything under config/, pom.xml, scripts/ or
    .github/workflows/.
  * In the nightly pipeline, where seven minutes is free.

WHAT IT NEEDS.  A built tree (run bash scripts/build.sh first) for tools/,
.venv/ and _build/m2repo; the project-local font stack; and network access to
https://api.osv.dev for the dependency-scan control.

WHAT IT DOES NOT COVER.  Exit gate item 1 -- "a clean checkout builds and tests
green with one documented command" -- is bash scripts/build.sh, and is not
repeated here; the quality control proves only the half of item 1 that consists
of gates failing the build.  The "on a pull request" half of exit gate item 6
needs GitHub to run the pipeline, and GitHub has never run anything in this
repository -- 0 workflow runs as of 2026-08-30, on a remote that has existed
since D-008 was decided that day.  The pipeline control proves every step on
this machine instead, and says so.
USAGE
}

# die MESSAGE [EXIT CODE].  Only $1 is the message: $* would print the exit
# code as part of it.
die() {
    printf '\n%s: %s\n' "${SCRIPT_NAME}" "$1" >&2
    exit "${2:-2}"
}

banner() {
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' %s\n' "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

list_gates() {
    local name
    printf '\n%s -- %d falsifiability control(s)\n\n' "${SCRIPT_NAME}" "${#ALL_GATES[@]}"
    printf '  %-13s %-9s %s\n' "NAME" "GATE ITEM" "COMMAND"
    printf '  %-13s %-9s %s\n' "----" "---------" "-------"
    for name in "${ALL_GATES[@]}"; do
        gate_spec "${name}"
        printf '  %-13s %-9s bash %s%s\n' \
            "${name}" "${GATE_ITEMS}" "${GATE_SCRIPT}" \
            "$([ "${#GATE_ARGS[@]}" -gt 0 ] && printf ' %s' "${GATE_ARGS[*]}")"
        printf '  %-13s %-9s injects: %s\n\n' "" "" "${GATE_DEFECT}"
    done
    printf '  Gate items: 1 one documented build command; 2 strict documentation build;\n'
    printf '  3 ArchUnit layering; 4 coverage; 5 traceability; 6 CI pipelines.\n'
    printf '  D-001 is the GPL-3.0 licence obligation, a phase deliverable rather than a\n'
    printf '  numbered gate item.  See phases/PHASE-01-build-skeleton.rst.\n\n'
}

# preflight SELECTED...  -- every sub-harness must be there and executable
# before any of them runs.  This is the single most important property of this
# script, so it is checked for all of them and reported in full rather than
# failing at the first one.
preflight() {
    local name missing=0 path
    printf '\n=== Preflight: every sub-harness must exist and be executable ===\n'
    printf 'A missing harness is a FAILURE, never a skipped control.\n\n'
    for name in "$@"; do
        gate_spec "${name}"
        path="${ROOT}/${GATE_SCRIPT}"
        if [ ! -f "${path}" ]; then
            printf '  MISSING        %-13s %s\n' "${name}" "${GATE_SCRIPT}"
            missing=$((missing + 1))
        elif [ ! -x "${path}" ]; then
            printf '  NOT EXECUTABLE %-13s %s (mode %s)\n' \
                "${name}" "${GATE_SCRIPT}" "$(stat -c %a -- "${path}")"
            missing=$((missing + 1))
        elif [ ! -s "${path}" ]; then
            printf '  EMPTY          %-13s %s\n' "${name}" "${GATE_SCRIPT}"
            missing=$((missing + 1))
        else
            printf '  ok             %-13s %s\n' "${name}" "${GATE_SCRIPT}"
        fi
    done
    if [ "${missing}" -ne 0 ]; then
        printf '\n'
        printf '%s: %d sub-harness(es) cannot be run.\n' "${SCRIPT_NAME}" "${missing}" >&2
        printf '%s: REFUSING TO CONTINUE. Running the rest would report a green\n' "${SCRIPT_NAME}" >&2
        printf '%s: summary for a set of gates that were never proved to bite,\n' "${SCRIPT_NAME}" >&2
        printf '%s: which is the one thing this script exists to prevent.\n' "${SCRIPT_NAME}" >&2
        exit 3
    fi
    printf '\n  %d/%d sub-harness(es) present and executable.\n' "$#" "$#"
}

# run_gate NAME -- run one control and grade it.
run_gate() {
    local name="$1"
    gate_spec "${name}"

    local log="${LOGS}/${name}.log"
    local rel="${log#"${ROOT}/"}"
    local cmd="bash ${GATE_SCRIPT}"
    [ "${#GATE_ARGS[@]}" -gt 0 ] && cmd="${cmd} ${GATE_ARGS[*]}"

    banner "CONTROL ${name}  --  PHASE-01 exit gate item ${GATE_ITEMS}"
    printf '  injects  %s\n' "${GATE_DEFECT}"
    printf '  proved by %s\n' "${cmd}"
    printf '  log       %s\n\n' "${rel}"

    local started rc=0
    started="$(date +%s)"
    if [ "${#GATE_ARGS[@]}" -gt 0 ]; then
        bash "${ROOT}/${GATE_SCRIPT}" "${GATE_ARGS[@]}" >"${log}" 2>&1 || rc=$?
    else
        bash "${ROOT}/${GATE_SCRIPT}" >"${log}" 2>&1 || rc=$?
    fi
    local elapsed=$(( $(date +%s) - started ))

    # --- grade it -----------------------------------------------------------
    local verdict="PASS" why="" count=""

    if [ ! -s "${log}" ]; then
        verdict="FAIL"
        why="the harness wrote no output at all (exit ${rc}); it cannot have run a control"
    elif [ "${rc}" -ne 0 ]; then
        verdict="FAIL"
        why="exited ${rc}; a gate did not bite, or the harness could not run. See ${rel}"
    else
        local marker
        for marker in "${GATE_PROOF[@]}"; do
            if ! grep -qF -- "${marker}" "${log}"; then
                verdict="FAIL"
                why="exited 0 but never printed '${marker}' -- it ended without proving anything. See ${rel}"
                break
            fi
        done
    fi

    if [ "${verdict}" = "PASS" ]; then
        count="$(gate_count "${name}" "${log}" || true)"
        if [ -z "${count}" ]; then
            verdict="FAIL"
            why="exited 0 with its marker but reported no control count; this script cannot tell how much it graded. See ${rel}"
        elif [ "${count}" -lt "${GATE_FLOOR}" ]; then
            verdict="FAIL"
            why="graded ${count} ${GATE_UNIT}, fewer than the recorded floor of ${GATE_FLOOR}; controls have been removed or skipped. See ${rel}"
        fi
    fi

    # --- record it ----------------------------------------------------------
    if [ "${verdict}" = "PASS" ]; then
        PASSED=$((PASSED + 1))
        printf '  PASS  %s: %s %s in %ds\n' "${name}" "${count}" "${GATE_UNIT}" "${elapsed}"
        [ "${count}" -gt "${GATE_FLOOR}" ] \
            && printf '        (the floor recorded here is %d; the harness has grown)\n' "${GATE_FLOOR}"
        COVERED+=("${GATE_ITEMS}")
    else
        FAILED=$((FAILED + 1))
        FAILURES+=("${name}: ${why}")
        printf '  FAIL  %s: %s\n' "${name}" "${why}"
        printf '        last lines of %s:\n' "${rel}"
        tail -5 -- "${log}" 2>/dev/null | sed 's/^/          /' || true
        count="${count:-0}"
    fi

    ROWS+=("$(printf '  %-4s  %-13s %-9s %-6s %5ds  %s' \
        "${verdict}" "${name}" "${GATE_ITEMS}" "${count:-?}" "${elapsed}" "${cmd}")")
    ROWS+=("$(printf '        injected: %s' "${GATE_DEFECT}")")
}

# -------------------------------------------------------------------- main --
main() {
    local -a selected=()
    local only=""

    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --list)    list_gates; exit 0 ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs a gate name (try --list)"
                only="${only}${only:+,}$2"
                shift 2
                ;;
            *) usage >&2; die "unknown option: $1" ;;
        esac
    done

    if [ -n "${only}" ]; then
        local want found
        local -a wanted=()
        IFS=',' read -r -a wanted <<< "${only}"
        for want in "${wanted[@]}"; do
            found=0
            for name in "${ALL_GATES[@]}"; do
                [ "${name}" = "${want}" ] && found=1
            done
            [ "${found}" -eq 1 ] || die "--only: no such gate '${want}'. Names: ${ALL_GATES[*]}"
        done
        # Run in ALL_GATES order, once each, whatever order they were given in.
        for name in "${ALL_GATES[@]}"; do
            for want in "${wanted[@]}"; do
                if [ "${name}" = "${want}" ]; then
                    selected+=("${name}")
                    break
                fi
            done
        done
    else
        selected=("${ALL_GATES[@]}")
    fi

    cd -- "${ROOT}"
    mkdir -p -- "${LOGS}"

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-01 gate must be seen to fail on its own defect\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  controls     %d of %d\n' "${#selected[@]}" "${#ALL_GATES[@]}"
    printf '  logs         %s\n' "${LOGS#"${ROOT}/"}"
    printf '  expect this to take several minutes; see --help\n'

    preflight "${selected[@]}"

    local started
    started="$(date +%s)"
    local name
    for name in "${selected[@]}"; do
        run_gate "${name}"
    done
    local total=$(( $(date +%s) - started ))

    # --- the summary --------------------------------------------------------
    printf '\n===============================================================================\n'
    printf ' SUMMARY\n'
    printf '===============================================================================\n'
    printf '  %-4s  %-13s %-9s %-6s %6s  %s\n' \
        "" "GATE" "ITEM" "GRADED" "TIME" "COMMAND THAT PROVES IT"
    printf '%s\n' "${ROWS[@]}"

    # Which exit gate items this run covered, deduplicated and sorted.
    local items
    items="$(printf '%s\n' "${COVERED[@]}" | tr ',' '\n' | tr -d ' ' \
        | grep -E '^[0-9]+$' | sort -un | paste -sd, - || true)"
    printf '\n  PHASE-01 exit gate items covered by the controls that passed: %s\n' \
        "${items:-none}"
    printf '  Item 1 is covered only in part: these controls prove that the gates which\n'
    printf '  FAIL the build still bite. That a clean checkout BUILDS green is\n'
    printf '  bash scripts/build.sh, and is not repeated here.\n'
    printf '  The "on a pull request" half of item 6 needs GitHub to run the pipeline,\n'
    printf '  and GitHub has run nothing in this repository yet; the pipeline control\n'
    printf '  proves every step on this machine instead.\n'

    printf '\n  %d control(s) passed, %d failed, in %d seconds (%dm%02ds).\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"

    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    printf '\n  Every gate was seen to reject its defect and accept the clean tree.\n\n'
}

main "$@"
