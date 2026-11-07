#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# verify-hardening-gates.sh -- prove the six PHASE-15 exit gate items fail on
# the defects they exist to catch (Phase 15 unit 10).
#
#   bash scripts/verify-hardening-gates.sh              every control, on HEAD
#   bash scripts/verify-hardening-gates.sh --worktree   every control, on the
#                                                       working tree's tracked
#                                                       files as they are on disk
#   bash scripts/verify-hardening-gates.sh --list       the controls, and stop
#   bash scripts/verify-hardening-gates.sh -h|--help
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 15's exit gate is six claims,
# phases/PHASE-15-hardening.rst, quoted so the grading below can be read
# against them:
#
#   1. Every version/platform combination the product offers has a passing
#      matrix run, and every combination it does not offer is explicitly
#      recorded as unavailable rather than untested.
#   2. The mutation gate passes at >= 80% in the critical packages with none of
#      the forbidden survivors.
#   3. Determinism repeats on a pinned fixture agree within the documented
#      tolerance, and the tolerance is justified in writing.
#   4. Performance thresholds pass on the dedicated runner and are recorded as
#      a baseline.
#   5. The manifest verification job demonstrably fails when a checksum is
#      altered.
#   6. No release-critical test is quarantined.
#
# WHAT EACH CONTROL DOES.  Every injection is one recorded in
# handoffs/PHASE-15-worklog.rst or docs/developer/testing.rst unless marked
# NEW; each runs in a sandbox, never the working tree.
#
#   S   the sandboxes: `git archive HEAD` (or, with --worktree, the tracked
#       files as they are on disk) extracted twice -- _build/hardening-gate-
#       sandbox, where Maven runs, with tools/, scratch/, .venv/ and
#       _build/m2repo linked in read-only as every harness does; and
#       _build/hardening-gate-tree, a plain copy with no links, for the
#       script-only controls.
#   6   item 6, on the plain copy.  6a: scripts/ci/flakiness-check.sh on the
#       real tree -- OK, the register empty: no release-critical test is
#       quarantined.  6b (NEW): a register entry naming
#       TierACanonicalScenarioTest, complete in all seven fields -- RED
#       [RELEASE-CRITICAL] naming it.  The check's own falsifiability is the
#       flakiness control (flakiness-check.sh --self-test, 40 cases); this
#       control does not repeat it.
#   K   the census (NEW), on the plain copy: every run step of every workflow
#       in .github/workflows/, read through check-workflows.py --list-steps,
#       classified exactly as run-pipeline-locally.sh classifies it (a script
#       referencing stub-lib.sh is a stub, everything else real); every stub
#       run in an empty environment must exit 70; every stub owned or
#       installed by PHASE-15 must say it waits for D-006 or a runner
#       (stub-lib.sh --waits-for).  No hand list: a step added, removed or
#       made a stub moves the census.  Ka: --waits-for removed from
#       nightly-large-dataset.sh -- red naming it.  Kb: nightly-determinism.sh
#       made a PHASE-15 stub with no --waits-for -- red naming it.
#   1a  item 1, clean: scripts/matrix/run-matrix.sh --part tools --offline --
#       every offered Linux build installed from upstream into an empty cache
#       and exercised; GREEN.  Its compiled classes are the bytecode baseline.
#   1c  item 1, the matrix's reader [recorded, unit 6]: matrix.py's plain-JSON
#       manifest reader skipping Percolator 3.06.5 for Windows, graded by
#       `matrix.py grade` over 1a's report (no Maven) -- red at "only in the
#       product [('percolator', '3.06.5', 'windows-x86-64')]"; the same
#       grading with the reader restored, GREEN.
#   H   the harness on itself, each reported as a HARNESS FAILURE and never as
#       a control that bit: an injection whose anchor is missing (H1); one that
#       leaves the file unchanged (H2); a green graded as red (H3, 1c's clean
#       grading); a red without its diagnostic (H4, 1c's red graded on 1b's
#       words); an injection that reached the source but not the compiled
#       class (H5, 1b's injection with no build after it).
#   1b  item 1 [recorded, unit 6]: PercolatorCommands requesting -X from a
#       build without XML_OUTPUT -- both managed Linux Percolator rows
#       RUN-FAILED at "the same build without XML_OUTPUT in its probed set: -X
#       left out, and said so", the Comet rows still RUN-PASSED.
#   3a  item 3, clean: run-matrix.sh --part determinism-synthetic --offline
#       (no spectra) -- GREEN, and every compiled class identical to the
#       baseline (1b's and H5's restorations reached the bytecode).
#   3b  item 3 [recorded, unit 7]: PercolatorSettings.valueText giving the
#       seed from the clock -- RED at "artefact synthetic-psms DIFFERS".
#   3c  item 3, the written tolerance: determinism.py check-doc on the real
#       data and testing.rst -- OK; (NEW) the data removing one of the pepXML's
#       fields under a name the document does not state -- red at "fields
#       removed differ -- only in the document [...], only in the data [...]".
#   2   item 2 (NEW, cheap): scripts/build.sh --gates-pit-root over a shadow
#       of the sandbox holding a SYNTHETIC cometgui-domain mutations.xml
#       written here (every package mutated; no PIT run).  Clean: every
#       mutation KILLED -- OK.  Defect: one package's mutations all SURVIVED
#       while the module stays above 80% -- red, the package named BELOW.
#       The real-PIT proof of the same grading (a 0% package inside a module
#       above 80%, a real TIMED_OUT, an empty target, an unmutated package) is
#       the tests control's control M (scripts/verify-test-gates.sh), which
#       this control does not duplicate.
#   4a  item 4 [recorded, unit 8]: StreamingHashService digesting every chunk
#       twice more -- run-performance.sh --quick --only
#       hash-throughput,cancel-fake-tool: RED hash-throughput "under its
#       threshold", cancel-fake-tool PASS beside it.
#   4b  item 4 [recorded, unit 8]: ProcessTree.destroyAll sending no polite
#       stop -- RED cancel-fake-tool "over its threshold", hash-throughput PASS
#       beside it.
#   4c  item 4, clean: both GREEN, every compiled class identical to the
#       baseline; then the same report graded as a GitHub-hosted runner
#       (--profile github-ubuntu-latest) -- NO BASELINE FOR THIS RUNNER
#       PROFILE, exit 4: never green.
#   C   the restorations: every damaged file passes sha256sum -c against its
#       pristine digest; the shared repository's org/cometgui jars, the
#       working tree's git status and its cometgui-*/target are unchanged.
#
#   Item 5 is DELEGATED to the manifest control (scripts/ci/nightly-manifest-
#   verify.sh --self-test: a checksum altered -> RED (a)); item 2's real-PIT
#   half to the tests control's M; item 6's check-of-the-check to the
#   flakiness control.  None is repeated here.
#
#   Each Maven injection: the anchor must match EXACTLY ONCE and be gone
#   after; the file must differ from its pristine copy; after the run and
#   BEFORE grading, the damaged class's compiled form must DIFFER from the
#   baseline and every other compiled class be IDENTICAL to it; the red must
#   carry its own words -- a red for another reason is a HARNESS FAILURE,
#   never a pass; the file is then restored and sha256sum -c proves it.  A
#   script or data injection is proved by its digest and its anchor.
#
# LEAN.  Seven Maven runs, each `-pl cometgui-app -am test` restricted to the
# opt-in classes the suite names: about 11 minutes on the 64-core development
# host (see docs/developer/testing.rst, "The hardening harness").  Spectra are
# never needed: the version matrix runs its tools part, determinism its
# synthetic half, performance two no-spectra measurements in quick mode.
#
# WHERE IT WORKS.  Never in the working tree, and never in its module target/
# directories (C proves it).  It writes _build/hardening-gate-{sandbox,tree,
# pristine,logs} and the tests' own temporary directories under /tmp.
# _build/m2repo is read offline, never written (digested before and after).
#
# NETWORK -- REQUIRED.  The tools part installs every offered Linux build from
# the URL manifests/tools.json pins (github.com, redirecting to
# *.githubusercontent.com); the preflight asks each for its headers.  Nothing
# else is contacted.  No fixture is needed: D-006's CI half is OPEN, and the
# local fixture is not read.
#
# EXIT STATUS
#   0  every control bit
#   1  a control failed: a gate did not bite, bit for another reason, a
#      sibling that had to stay green went red, or a clean run was not green
#   2  misuse
#   3  the environment is not ready (toolchain, virtualenv, repository, network)
#   4  HARNESS FAILURE before grading: an anchor missing, an injection that
#      changed nothing or reached no compiled class, a sandbox that could not
#      be built.  The run proves nothing and is not a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/hardening-gate-sandbox"
readonly TREE="${ROOT}/_build/hardening-gate-tree"
readonly PRISTINE="${ROOT}/_build/hardening-gate-pristine"
readonly LOGS="${ROOT}/_build/hardening-gate-logs"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly PYTHON="${ROOT}/.venv/bin/python"

# The production files the Maven controls damage.
readonly PERCOLATOR_COMMANDS="cometgui-tools/src/main/java/org/cometgui/tools/percolator/PercolatorCommands.java"
readonly PERCOLATOR_SETTINGS="cometgui-params-percolator/src/main/java/org/cometgui/params/percolator/PercolatorSettings.java"
readonly HASH_SERVICE="cometgui-provenance/src/main/java/org/cometgui/provenance/hashing/StreamingHashService.java"
readonly PROCESS_TREE="cometgui-process/src/main/java/org/cometgui/tools/process/ProcessTree.java"
# The scripts and data the other controls damage.
readonly MATRIX_PY="scripts/matrix/matrix.py"
readonly DETERMINISM_DATA="scripts/matrix/determinism-data.json"
readonly QUARANTINE="scripts/ci/quarantine.toml"
readonly LARGE_DATASET="scripts/ci/nightly-large-dataset.sh"
readonly NIGHTLY_DETERMINISM="scripts/ci/nightly-determinism.sh"

# The one release-critical test 6b registers, and why it is one (the
# flakiness check computes it; this is only the test's name).
readonly CRITICAL_TEST="org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest"

# Every Maven-driven run is bounded: a hung run is a harness failure, never a
# control left running.
readonly RUN_LIMIT=1500

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a RESTORED=()
SOURCE_MODE="head"

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the six PHASE-15 exit gate items fail on the defects
they exist to catch: the version matrix (item 1), the per-package mutation
grading (item 2, cheaply; its real-PIT proof is the tests control's M), the
determinism suite and its written tolerance (item 3), two performance
thresholds and the no-baseline verdict (item 4), the quarantine register
(item 6), and a census of every Phase-15-owned nightly and release step.  Item
5 is the manifest control's.

Usage:
  bash scripts/${SCRIPT_NAME}              every control, on git archive HEAD
  bash scripts/${SCRIPT_NAME} --worktree   every control, on the working tree's
                                           tracked files as they are on disk
                                           (for a sign-off's own injections;
                                           said loudly in the output)
  bash scripts/${SCRIPT_NAME} --list       the controls, and stop
  bash scripts/${SCRIPT_NAME} -h|--help

NEEDS NETWORK to the URLs manifests/tools.json pins for Linux x86-64
(github.com, redirecting to *.githubusercontent.com); tools/ (JDK, Maven,
font stack), .venv/ and _build/m2repo, all read only.  No spectra: no
fixture is read.  About 11 minutes; seven Maven runs.

Writes only _build/hardening-gate-{sandbox,tree,pristine,logs} and the tests'
temporary directories.  Run through scripts/verify-all-gates.sh (as
hardening), or alone.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a HARNESS FAILURE before grading.
USAGE
}

list_controls() {
    sed -n '/^# WHAT EACH CONTROL DOES/,/^# LEAN\./p' -- "${BASH_SOURCE[0]}" \
        | sed '$d' | sed 's/^# \{0,1\}//'
}

die() {
    printf '\nFATAL: %s\n' "$1" >&2
    exit "${2:-1}"
}

harness_error() {
    printf '\nHARNESS FAILURE: %s\n' "$1" >&2
    printf 'The run proves nothing and must not be read as a pass.\n' >&2
    exit 4
}

CONTROL_ID=""
CONTROL_STARTED=0

begin_control() {
    CONTROL_ID="$1"
    shift
    CONTROL_STARTED="$(date +%s)"
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' CONTROL %s  %s\n' "${CONTROL_ID}" "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

end_control() {
    local elapsed=$(( $(date +%s) - CONTROL_STARTED ))
    TIMINGS+=("$(printf '%-3s %5ds' "${CONTROL_ID}" "${elapsed}")")
    printf '   (control %s took %ds)\n' "${CONTROL_ID}" "${elapsed}"
}

# Inside control H a provoked grading may print a pass or a fail; neither is
# counted -- the SUMMARY count is what verify-all-gates.sh holds to a floor.
DELIBERATE=0
record_pass() {
    if [ "${DELIBERATE}" -eq 1 ]; then
        printf '   (inside control H, not counted: %s)\n' "$*"
        return
    fi
    PASSED=$((PASSED + 1))
    printf '   PASS  %s\n' "$*"
}

record_fail() {
    FAILED=$((FAILED + 1))
    FAILURES+=("$*")
    if [ "${DELIBERATE}" -eq 1 ]; then
        printf '   (refused, as control H requires: %s)\n' "$*"
    else
        printf '   FAIL  %s\n' "$*"
    fi
}

rel() {
    printf '%s' "${1#"${ROOT}/"}"
}

# base_of NAME -- the directory a damaged file lives in: sandbox or tree.
base_of() {
    case "$1" in
        sandbox) printf '%s' "${SANDBOX}" ;;
        tree)    printf '%s' "${TREE}" ;;
        *)       harness_error "no such base '$1'" ;;
    esac
}

# ------------------------------------------------------------- injections --
#
# inject LABEL BASE FILE OLD NEW -- replace OLD by NEW in BASE/FILE, which
# must hold OLD exactly once; NEW must differ from OLD.  A first injection
# into a file keeps its pristine copy and digest.  After it, the file must
# differ from the pristine copy and OLD must be gone (unless NEW contains it).
inject() {
    local label="$1" base="$2" file="$3" old="$4" new="$5" dir rc=0
    dir="$(base_of "${base}")"
    [ -f "${dir}/${file}" ] || harness_error "(${label}) ${base}/${file} does not exist."
    mkdir -p -- "${PRISTINE}/${base}/$(dirname -- "${file}")"
    if [ ! -f "${PRISTINE}/${base}/${file}" ]; then
        cp -p -- "${dir}/${file}" "${PRISTINE}/${base}/${file}"
        ( cd -- "${dir}" && sha256sum -- "${file}" ) >>"${PRISTINE}/${base}.sha256"
    fi
    INJ_FILE="${dir}/${file}" INJ_OLD="${old}" INJ_NEW="${new}" "${PYTHON}" -I - <<'PYTHON' || rc=$?
import os
import sys
path, old, new = os.environ["INJ_FILE"], os.environ["INJ_OLD"], os.environ["INJ_NEW"]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
count = text.count(old)
if count != 1:
    print(f"   the anchor occurs {count} time(s), not once")
    sys.exit(3)
if new == old:
    sys.exit(5)
text = text.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text)
if old not in new and old in text:
    sys.exit(6)
PYTHON
    case "${rc}" in
        0) ;;
        3) harness_error "(${label}) the injection's anchor is not in ${base}/${file} exactly once: the file has moved on, and the injection would test nothing. Re-establish the anchor; never skip the control." ;;
        5) harness_error "(${label}) the injection would leave ${base}/${file} unchanged (its replacement equals its anchor): an unchanged file proves nothing." ;;
        6) harness_error "(${label}) the anchor is still in ${base}/${file} after the injection." ;;
        *) harness_error "(${label}) the injection into ${base}/${file} could not be applied (exit ${rc})." ;;
    esac
    if cmp -s -- "${dir}/${file}" "${PRISTINE}/${base}/${file}"; then
        harness_error "(${label}) ${base}/${file} is byte-identical to its pristine copy after the injection."
    fi
    printf '   injected %s/%s (%s); it differs from its pristine copy\n' "${base}" "${file}" "${label}"
}

# restore BASE FILE -- the pristine copy back, with a fresh mtime so the next
# build recompiles it, proved by sha256sum -c against the pristine digest.
restore() {
    local base="$1" file="$2" dir check
    dir="$(base_of "${base}")"
    [ -f "${PRISTINE}/${base}/${file}" ] || harness_error "no pristine copy of ${base}/${file} to restore from."
    cp -- "${PRISTINE}/${base}/${file}" "${dir}/${file}"
    touch -- "${dir}/${file}"
    check="$( cd -- "${dir}" && grep -F "  ${file}" "${PRISTINE}/${base}.sha256" | tail -1 | sha256sum -c - 2>&1 )" \
        || harness_error "could not restore ${base}/${file}: sha256sum -c says ${check}"
    RESTORED+=("${base} ${file}")
    printf '   restored %s/%s -- sha256sum -c: %s\n' "${base}" "${file}" "${check}"
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree -- "sha256  <module>/target/classes/<path>" for every compiled
# production class in the sandbox.  Resources are left out: every injection
# here is code, and cometgui-app's filtered build identity carries a time.
class_tree() {
    ( cd -- "${SANDBOX}" && find cometgui-*/target/classes -type f -name '*.class' -print0 2>/dev/null \
        | LC_ALL=C sort -z | xargs -0 -r sha256sum )
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"

# compare_tree LABEL LOG [INJECTED SOURCE] -- with an injected source, at
# least one of its compiled forms (the class or an inner class) must DIFFER
# from the baseline, and every other compiled class must be IDENTICAL; without
# one, every class must be identical.  Anything else is a harness failure:
# the run did not test what this script says it tested.
compare_tree() {
    local label="$1" log="$2" injected="${3:-}" prefix="" now rc=0 out
    [ -s "${BASELINE_TREE}" ] || harness_error "(${label}) no baseline digest of the compiled classes was recorded."
    if [ -n "${injected}" ]; then
        case "${injected}" in
            */src/main/java/*.java)
                prefix="${injected%%/*}/target/classes/${injected#*/src/main/java/}"
                prefix="${prefix%.java}"
                ;;
            *) harness_error "(${label}) no compiled form is known for ${injected}" ;;
        esac
    fi
    now="${LOGS}/${label}-classes.sha256"
    class_tree >"${now}"
    out="$("${PYTHON}" -I - "${BASELINE_TREE}" "${now}" "${prefix}" <<'PYTHON'
import sys
def read(path):
    table = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            digest, name = line.rstrip("\n").split("  ", 1)
            table[name] = digest
    return table
base, now, prefix = read(sys.argv[1]), read(sys.argv[2]), sys.argv[3]
def ours(name):
    return bool(prefix) and (name == prefix + ".class" or name.startswith(prefix + "$"))
changed = sorted(n for n in set(base) | set(now) if base.get(n) != now.get(n))
mine = [n for n in changed if ours(n)]
others = [n for n in changed if not ours(n)]
print(f"{len(now)} compiled classes; {len(mine)} compiled form(s) of the damaged class differ; "
      f"{len(others)} other class(es) differ")
for name in others[:10]:
    print(f"   other: {name} ({'absent before' if name not in base else 'absent now' if name not in now else 'differs'})")
if others:
    sys.exit(2)
if prefix and not mine:
    sys.exit(1)
sys.exit(0)
PYTHON
)" || rc=$?
    printf '   bytecode: %s\n' "${out%%$'\n'*}"
    case "${rc}" in
        0) ;;
        1)
            local hint=""
            grep -qE 'COMPILATION ERROR|Compilation failure|spotless|checkstyle' "${log}" 2>/dev/null \
                && hint=" The log carries a compiler, Spotless or Checkstyle refusal, so the damaged source never compiled."
            harness_error "(${label}) the injection reached ${injected} but not its compiled class: every compiled form is identical to the clean baseline, so whatever this run graded, it was not the defect.${hint} See $(rel "${log}")." ;;
        2) printf '%s\n' "${out}" | sed -n '2,$p'
           harness_error "(${label}) compiled classes OTHER than the damaged one differ from the baseline: the run did not test the one defect it names. See $(rel "${now}")." ;;
        *) harness_error "(${label}) the compiled classes could not be compared (exit ${rc})." ;;
    esac
}

# -------------------------------------------------------------- grading --
#
# A needle is a literal string, or "re:" and an extended regular expression
# matched line by line.
log_has() {
    local log="$1" needle="$2"
    case "${needle}" in
        re:*) grep -qE -- "${needle#re:}" "${log}" ;;
        *)    grep -qF -- "${needle}" "${log}" ;;
    esac
}

# grade_red LABEL RC WANT LOG NEEDLE... -- the run with the defect in it must
# have exited WANT (non-zero) and its output must carry every needle.
grade_red() {
    local label="$1" rc="$2" want="$3" log="$4" needle
    shift 4
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached what runs (log: $(rel "${log}"))"
        return 1
    fi
    if [ "${rc}" -ne "${want}" ]; then
        record_fail "${label}: HARNESS FAILURE -- red with exit ${rc}, not the ${want} its verdict means; a red for another reason is not this gate biting (log: $(rel "${log}"))"
        return 1
    fi
    for needle in "$@"; do
        if ! log_has "${log}" "${needle}"; then
            record_fail "${label}: HARNESS FAILURE -- red, but without its expected diagnostic '${needle}'; a red for another reason is not this gate biting (log: $(rel "${log}"))"
            return 1
        fi
    done
    record_pass "${label}: rejected (exit ${rc}) with its own words"
    case "$1" in
        re:*) printf '         %s\n' "$(grep -m1 -E -- "${1#re:}" "${log}" | sed 's/^ *//' | cut -c1-220 || true)" ;;
        *)    printf '         %s\n' "$(grep -m1 -F -- "$1" "${log}" | sed 's/^ *//' | cut -c1-220 || true)" ;;
    esac
    return 0
}

# grade_green LABEL RC LOG NEEDLE... -- the clean counterpart: exit 0 and
# every needle.
grade_green() {
    local label="$1" rc="$2" log="$3" needle
    shift 3
    if [ "${rc}" -ne 0 ]; then
        record_fail "${label}: the clean run is not green (exit ${rc}); nothing graded against it can be attributed to its defect (log: $(rel "${log}"))"
        return 1
    fi
    for needle in "$@"; do
        if ! log_has "${log}" "${needle}"; then
            record_fail "${label}: the clean run exited 0 without '${needle}' (log: $(rel "${log}"))"
            return 1
        fi
    done
    record_pass "${label}: green with its own words"
    return 0
}

# run_in BASE LOG COMMAND... -- run a command in a sandbox, bounded, its
# output to LOG.  Sets RUN_RC.
RUN_RC=0
run_in() {
    local base="$1" log="$2" dir started
    shift 2
    dir="$(base_of "${base}")"
    RUN_RC=0
    started="$(date +%s)"
    ( cd -- "${dir}" && timeout --kill-after=30 "${RUN_LIMIT}" "$@" ) >"${log}" 2>&1 || RUN_RC=$?
    [ "${RUN_RC}" -ne 124 ] && [ "${RUN_RC}" -ne 137 ] \
        || harness_error "$(rel "${log}"): the run did not end within ${RUN_LIMIT} s and was killed."
    printf '   ran   %s  -> exit %d in %ds; log %s\n' "$*" "${RUN_RC}" "$(( $(date +%s) - started ))" "$(rel "${log}")"
}

# ------------------------------------------------------------- the sandbox --
cometgui_repo_digest() {
    if [ -d "${M2REPO}/org/cometgui" ]; then
        ( cd -- "${M2REPO}/org/cometgui" && find . -type f -name '*.jar' -print0 | LC_ALL=C sort -z \
            | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1 )
    else
        printf 'absent'
    fi
}

worktree_targets_digest() {
    ( cd -- "${ROOT}" && find cometgui-*/target -type f -printf '%p %s %T@\n' 2>/dev/null \
        | LC_ALL=C sort | sha256sum | cut -d' ' -f1 )
}

extract_into() {
    local dir="$1"
    rm -rf -- "${dir}"
    mkdir -p -- "${dir}"
    if [ "${SOURCE_MODE}" = "worktree" ]; then
        ( cd -- "${ROOT}" && git ls-files -z | tar --null -T - -cf - ) | tar -x -C "${dir}" \
            || harness_error "the working tree's tracked files could not be copied into $(rel "${dir}")."
    else
        ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${dir}" \
            || harness_error "git archive HEAD could not be extracted into $(rel "${dir}")."
    fi
}

preflight() {
    local -a missing=()
    [ -f "${ROOT}/tools/env.sh" ] || missing+=("tools/env.sh (bash scripts/ci/toolchain.sh)")
    [ -d "${ROOT}/tools/fontstack-bookworm-20260829" ] || missing+=("the font stack (bash scripts/ci/fontstack.sh)")
    [ -x "${PYTHON}" ] || missing+=(".venv/bin/python (bash scripts/ci/python-env.sh)")
    [ -d "${M2REPO}/org" ] || missing+=("_build/m2repo (bash scripts/build.sh)")
    command -v git >/dev/null || missing+=("git")
    command -v curl >/dev/null || missing+=("curl")
    command -v timeout >/dev/null || missing+=("timeout")
    if [ "${#missing[@]}" -gt 0 ]; then
        printf '  missing: %s\n' "${missing[@]}" >&2
        die "the environment is not ready; a control that cannot run is never a pass." 3
    fi
    # The tools part installs every offered Linux x86-64 build from its pinned
    # URL; each must answer.
    local url bad=0 urls
    urls="$("${PYTHON}" -I -c '
import json, sys
doc = json.load(open(sys.argv[1], encoding="utf-8"))
for a in doc["artefacts"]:
    if a["os"] == "linux" and a["arch"] == "x86-64":
        print(a["url"])
' "${ROOT}/manifests/tools.json" | LC_ALL=C sort -u)"
    [ -n "${urls}" ] || die "manifests/tools.json names no Linux x86-64 artefact to install" 3
    # A reachability check, not a grading: a transport failure or an answer
    # other than 200 is asked again, at most three times in all, and every
    # attempt that failed is printed with what curl saw (GitHub answers a burst
    # of requests from one host with 429 now and then).
    local attempt answer
    while read -r url; do
        answer=""
        for attempt in 1 2 3; do
            answer="$(curl -sSIL --max-time 60 -o /dev/null -w '%{http_code}' -- "${url}" 2>&1)" || true
            [ "${answer##*$'\n'}" = "200" ] && break
            printf '  network  attempt %d of 3 for %s: %s\n' "${attempt}" "${url}" "${answer//$'\n'/ }" >&2
            [ "${attempt}" -lt 3 ] && sleep 5
        done
        if [ "${answer##*$'\n'}" = "200" ]; then
            printf '  network  ok   %s\n' "${url}"
        else
            printf '  network  FAIL %s\n' "${url}" >&2
            bad=$((bad + 1))
        fi
    done <<<"${urls}"
    [ "${bad}" -eq 0 ] || die "${bad} pinned artefact URL(s) did not answer; the tools part would fail for the wrong reason." 3
}

control_S() {
    begin_control "S" "the sandboxes"
    rm -rf -- "${PRISTINE}" "${LOGS}"
    mkdir -p -- "${PRISTINE}" "${LOGS}"
    extract_into "${SANDBOX}"
    extract_into "${TREE}"
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch" 2>/dev/null || true
    [ -e "${ROOT}/scratch" ] || rm -f -- "${SANDBOX}/scratch"
    ln -s -- "${ROOT}/.venv" "${SANDBOX}/.venv"
    mkdir -p -- "${SANDBOX}/_build"
    ln -s -- "${M2REPO}" "${SANDBOX}/_build/m2repo"
    local needed
    for needed in tools/env.sh .venv/bin/python _build/m2repo/org manifests/tools.json pom.xml \
        "${PERCOLATOR_COMMANDS}" "${PERCOLATOR_SETTINGS}" "${HASH_SERVICE}" "${PROCESS_TREE}"; do
        [ -e "${SANDBOX}/${needed}" ] || harness_error "the sandbox does not resolve ${needed}."
    done
    [ ! -e "${TREE}/tools" ] && [ ! -e "${TREE}/.venv" ] \
        || harness_error "the plain copy holds tools/ or .venv/; it must be the tracked files alone."
    local head what
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    if [ "${SOURCE_MODE}" = "worktree" ]; then
        what="the working tree's tracked files AS THEY ARE ON DISK (--worktree; HEAD is ${head})"
    else
        what="git archive ${head}"
    fi
    record_pass "sandboxes: ${what} in $(rel "${SANDBOX}") (tools/, scratch/, .venv/, _build/m2repo linked) and $(rel "${TREE}") (no links)"
    local dirty
    dirty="$(cd -- "${ROOT}" && git status --porcelain --untracked-files=no | head -20)"
    if [ -n "${dirty}" ] && [ "${SOURCE_MODE}" = "head" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes. This run proves the gates\n'
        printf '        of HEAD (%s), not of the working tree (--worktree grades those):\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
    end_control
}

# ------------------------------------------------------------- the controls --

control_6() {
    begin_control "6" "item 6: no release-critical test is quarantined (the real check, on the plain copy)"
    local log="${LOGS}/6a-flakiness.log"
    run_in tree "${log}" "${PYTHON}" -I -B scripts/ci/flakiness_check.py --root "${TREE}"
    grade_green "6a the real tree: the register holds no release-critical test" "${RUN_RC}" "${log}" \
        "flakiness-check: OK -- no retry, no unregistered quarantine, no release-critical test quarantined" \
        "quarantine register scripts/ci/quarantine.toml: 0 entries" || true

    # 6b (NEW): a complete entry -- all seven fields, a real diagnostics file
    # -- naming a release-critical test.
    mkdir -p -- "${TREE}/scripts/ci/quarantine"
    printf '[ERROR] Tests run: 30, Failures: 1 -- written by %s control 6b\n' "${SCRIPT_NAME}" \
        >"${TREE}/scripts/ci/quarantine/hardening-gate-6b.log"
    inject "6b a release-critical test registered" tree "${QUARANTINE}" "schema = 1" "schema = 1

[[quarantine]]
test = \"${CRITICAL_TEST}\"
issue = \"handoffs/PHASE-15-worklog.rst\"
owner = \"the Phase 15 orchestrator\"
diagnostics = \"scripts/ci/quarantine/hardening-gate-6b.log\"
reason = \"fails one run in twenty while the packaged window opens\"
fix_plan = \"make the step wait on the window's observable state\"
since = 2026-11-01"
    log="${LOGS}/6b-flakiness.log"
    run_in tree "${log}" "${PYTHON}" -I -B scripts/ci/flakiness_check.py --root "${TREE}"
    grade_red "6b a release-critical test in the register" "${RUN_RC}" 1 "${log}" \
        "RED [RELEASE-CRITICAL] ${QUARANTINE}: ${CRITICAL_TEST} is release-critical (" \
        "flakiness-check: FAILED" || true
    restore tree "${QUARANTINE}"
    rm -f -- "${TREE}/scripts/ci/quarantine/hardening-gate-6b.log"
    rmdir -- "${TREE}/scripts/ci/quarantine" 2>/dev/null || true
    printf '   the check'"'"'s own 40 cases are the flakiness control'"'"'s; not repeated here\n'
    end_control
}

# census LOG -- every run step of every workflow, classified as
# run-pipeline-locally.sh classifies it, every stub run, every PHASE-15 stub
# required to wait on D-006 or a runner.  Sets RUN_RC (0 clean, 1 red).
census() {
    local log="$1"
    RUN_RC=0
    ( cd -- "${TREE}" && timeout --kill-after=10 300 "${PYTHON}" -I - "${TREE}" <<'PYTHON' ) >"${log}" 2>&1 || RUN_RC=$?
import os
import re
import subprocess
import sys
from pathlib import Path

tree = Path(sys.argv[1])
workflows = sorted(p.name for p in (tree / ".github" / "workflows").iterdir() if p.suffix in (".yml", ".yaml"))
scripts = {}
for workflow in workflows:
    rows = subprocess.run([sys.executable, "-I", str(tree / "scripts/ci/check-workflows.py"), "--root", str(tree),
                           "--list-steps", workflow], capture_output=True, text=True, check=True).stdout
    for row in rows.splitlines():
        job, runner, name, kind, value = row.split("\t")
        if kind != "run":
            continue
        script = value.split()[1]
        scripts.setdefault(script, []).append(f"{workflow}:{job}")
if not scripts:
    print("census: HARNESS FAILURE -- no run step was read from any workflow")
    sys.exit(4)
clean = {"PATH": os.environ["PATH"], "HOME": os.environ.get("HOME", "/nonexistent"), "LC_ALL": "C.UTF-8"}
problems, real, stubs, ours = [], [], [], []
for script in sorted(scripts):
    path = tree / script
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    where = ", ".join(sorted(set(scripts[script])))
    if "stub-lib.sh" not in text:
        real.append(script)
        print(f"  REAL   {script:46s} {where}")
        continue
    run = subprocess.run(["bash", str(path)], cwd=tree, env=clean, capture_output=True, text=True, timeout=120)
    out = run.stdout + run.stderr
    owner = re.search(r"^ *owned by +(PHASE-\d\d)", out, re.M)
    installed = re.search(r"deliberate stub installed by (PHASE-\d\d)", out)
    owner = owner.group(1) if owner else "?"
    installed = installed.group(1) if installed else "?"
    waits, current = [], None
    for line in out.splitlines():
        labelled = re.match(r"^   (\S.{10}) (.*)$", line)
        blank = re.match(r"^ {15}(\S.*)$", line)
        if labelled:
            current = labelled.group(1).strip()
            if current == "waits for":
                waits.append(labelled.group(2))
        elif blank and current == "waits for":
            waits.append(blank.group(1))
        elif not line.strip():
            current = None
    stubs.append(script)
    phase15 = "PHASE-15" in (owner, installed)
    if phase15:
        ours.append(script)
    print(f"  STUB   {script:46s} {where}; exit {run.returncode}; owned by {owner}, installed by {installed}; "
          f"{len(waits)} waits-for")
    for item in waits:
        print(f"           waits for: {item[:150]}")
    if run.returncode != 70:
        problems.append(f"{script} references stub-lib.sh, so run-pipeline-locally.sh classifies it a stub "
                        f"and requires exit 70, but in an empty environment it exited {run.returncode}")
    if owner == "?":
        problems.append(f"{script} is a stub that names no owning phase")
    if phase15 and not any(re.search(r"D-006|runner", item, re.I) for item in waits):
        problems.append(f"{script} is a PHASE-15 stub (owned by {owner}, installed by {installed}) that does not "
                        "say it waits for D-006 or a runner: Phase 15 may leave a step a stub only when it "
                        "waits on one of those (stub-lib.sh --waits-for)")
print(f"census: {len(scripts)} step script(s) in {len(workflows)} workflow(s): {len(real)} real, "
      f"{len(stubs)} stub(s), {len(ours)} of them PHASE-15's")
if not real or not ours:
    print("census: HARNESS FAILURE -- the census found no real step or no PHASE-15 stub; it read nothing it "
          "could grade")
    sys.exit(4)
for problem in problems:
    print(f"census: RED -- {problem}")
if problems:
    print(f"census: RED -- {len(problems)} problem(s)")
    sys.exit(1)
print("census: OK -- every stub exits 70 and every PHASE-15 stub waits for D-006 or a runner")
PYTHON
    [ "${RUN_RC}" -ne 4 ] || harness_error "$(rel "${log}"): $(grep -m1 'HARNESS FAILURE' "${log}")"
    printf '   ran   the census -> exit %d; log %s\n' "${RUN_RC}" "$(rel "${log}")"
}

control_K() {
    begin_control "K" "the census: every Phase-15-owned nightly and release step real, or a stub waiting on D-006 or a runner"
    local log="${LOGS}/K-census.log"
    census "${log}"
    grade_green "K  the census of every workflow's steps" "${RUN_RC}" "${log}" \
        "census: OK -- every stub exits 70 and every PHASE-15 stub waits for D-006 or a runner" \
        "STUB   ${LARGE_DATASET}" "REAL   ${NIGHTLY_DETERMINISM}" || true
    sed -n 's/^census: \([0-9].*\)$/         \1/p' "${log}"

    inject "Ka --waits-for removed from a PHASE-15 stub" tree "${LARGE_DATASET}" \
        "        --waits-for \"D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN). COMETGUI_MATRIX_FIXTURE is empty, so nothing was searched; the local fixture under scratch/fixture is NOT the CI fixture and no CI job may use it\" \\
" ""
    log="${LOGS}/Ka-census.log"
    census "${log}"
    grade_red "Ka a PHASE-15 stub that waits for nothing" "${RUN_RC}" 1 "${log}" \
        "census: RED -- ${LARGE_DATASET} is a PHASE-15 stub" "does not say it waits for D-006 or a runner" || true
    restore tree "${LARGE_DATASET}"

    inject "Kb a real Phase-15 step made a stub" tree "${NIGHTLY_DETERMINISM}" \
        "[ \"\$#\" -eq 0 ] || { printf 'nightly-determinism.sh: takes no arguments\\n' >&2; exit 2; }" \
        "[ \"\$#\" -eq 0 ] || { printf 'nightly-determinism.sh: takes no arguments\\n' >&2; exit 2; }
exec bash \"\${SCRIPT_DIR}/stub-lib.sh\" --script nightly-determinism.sh --phase PHASE-15 --owns \"determinism comparisons\" --spec \"specification.rst, Nightly pipeline\""
    log="${LOGS}/Kb-census.log"
    census "${log}"
    grade_red "Kb a PHASE-15 stub left in place of a real step" "${RUN_RC}" 1 "${log}" \
        "STUB   ${NIGHTLY_DETERMINISM}" "census: RED -- ${NIGHTLY_DETERMINISM} is a PHASE-15 stub" || true
    restore tree "${NIGHTLY_DETERMINISM}"
    end_control
}

MATRIX_CLEAN=""
control_1a() {
    begin_control "1a" "item 1, clean: the version matrix's tools part (every offered Linux build installed and exercised)"
    MATRIX_CLEAN="${SANDBOX}/_build/hg-matrix-clean"
    local log="${LOGS}/1a-matrix.log"
    run_in sandbox "${log}" bash scripts/matrix/run-matrix.sh --part tools --offline --out "${MATRIX_CLEAN}"
    grade_green "1a the matrix over every offered build" "${RUN_RC}" "${log}" \
        "VERDICT  GREEN -- every combination this invocation is responsible for RUN-PASSED" \
        "re:^ RUN-PASSED +offer:percolator:3\.07\.1:linux-x86-64 " \
        "re:^ RUN-PASSED +offer:percolator:3\.06\.5:linux-x86-64 " \
        "re:^ RUN-PASSED +offer:comet:2026\.03\.0:linux-x86-64 " \
        || die "the clean matrix is not green, so no injection can be graded against it; see $(rel "${log}")." 1
    sed -n 's/^ SUMMARY  /         /p' "${log}"
    class_tree >"${BASELINE_TREE}"
    local count
    count="$(wc -l <"${BASELINE_TREE}")"
    [ "${count}" -gt 1000 ] || harness_error "the clean run compiled only ${count} classes; no baseline worth comparing against."
    record_pass "baseline bytecode: ${count} compiled production classes digested after 1a"
    end_control
}

GRADE_CLEAN_LOG=""
GRADE_RED_LOG=""
control_1c() {
    begin_control "1c" "item 1: the matrix's own manifest reader skipping a row (graded over 1a's report, no Maven)"
    GRADE_CLEAN_LOG="${LOGS}/1c-grade-clean.log"
    run_in sandbox "${GRADE_CLEAN_LOG}" "${PYTHON}" -B "${MATRIX_PY}" grade --report "${MATRIX_CLEAN}" --part tools \
        --out "${SANDBOX}/_build/hg-grade-clean"
    grade_green "1c the grading of 1a's report" "${RUN_RC}" "${GRADE_CLEAN_LOG}" \
        "VERDICT  GREEN" " PROBLEMS: none" || true
    inject "1c the reader skips Percolator 3.06.5 for Windows" sandbox "${MATRIX_PY}" \
        '        for artefact in document["artefacts"]
    ]' \
        '        for artefact in document["artefacts"]
        if (artefact["tool"], artefact["version"], artefact["os"]) != ("percolator", "3.06.5", "windows")
    ]'
    GRADE_RED_LOG="${LOGS}/1c-grade-red.log"
    run_in sandbox "${GRADE_RED_LOG}" "${PYTHON}" -B "${MATRIX_PY}" grade --report "${MATRIX_CLEAN}" --part tools \
        --out "${SANDBOX}/_build/hg-grade-red"
    grade_red "1c a manifest row the matrix ignored" "${RUN_RC}" 1 "${GRADE_RED_LOG}" \
        "only in the product [('percolator', '3.06.5', 'windows-x86-64')]" "VERDICT  RED" || true
    restore sandbox "${MATRIX_PY}"
    end_control
}

# The -X injection, shared by 1b and H5.
inject_x() {
    inject "$1" sandbox "${PERCOLATOR_COMMANDS}" \
        "            if (accepts(artefact.option())) {" \
        "            if (accepts(artefact.option()) || artefact == PercolatorArtefact.POUT_XML) {"
}

control_H() {
    begin_control "H" "the harness on itself: each must be a HARNESS FAILURE, never a control that bit"
    local out rc

    # Each provocation runs in a subshell, so nothing it counts is counted.
    h_expect() {
        local id="$1" what="$2" words="$3"
        if [ "${rc}" -ne 0 ] && grep -qF -- "${words}" <<<"${out}"; then
            record_pass "${id} ${what}: reported as a HARNESS FAILURE (exit ${rc})"
        else
            record_fail "${id} ${what}: the harness did NOT report it as a HARNESS FAILURE (exit ${rc}); it would have graded a control that tested nothing"
            printf '%s\n' "${out}" | tail -5 | sed 's/^/         | /'
        fi
    }

    rc=0; out="$( (DELIBERATE=1; inject "H1" sandbox "${PERCOLATOR_COMMANDS}" "an anchor this file does not hold" "x") 2>&1 )" || rc=$?
    h_expect "H1" "an injection whose anchor is missing" "HARNESS FAILURE: (H1) the injection's anchor is not in"
    cmp -s -- "${SANDBOX}/${PERCOLATOR_COMMANDS}" "${PRISTINE}/sandbox/${PERCOLATOR_COMMANDS}" \
        || harness_error "H1 changed ${PERCOLATOR_COMMANDS} although its anchor was missing."

    rc=0; out="$( (DELIBERATE=1; inject "H2" sandbox "${PERCOLATOR_COMMANDS}" \
        "            if (accepts(artefact.option())) {" "            if (accepts(artefact.option())) {") 2>&1 )" || rc=$?
    h_expect "H2" "an injection that leaves the file unchanged" "HARNESS FAILURE: (H2) the injection would leave"

    rc=0; out="$( (DELIBERATE=1; grade_red "H3" 0 1 "${GRADE_CLEAN_LOG}" "VERDICT  GREEN" || exit 1) 2>&1 )" || rc=$?
    h_expect "H3" "a green graded as red" "HARNESS FAILURE -- the check PASSED with the defect present"

    rc=0; out="$( (DELIBERATE=1; grade_red "H4" 1 1 "${GRADE_RED_LOG}" \
        "the same build without XML_OUTPUT in its probed set: -X left out, and said so" || exit 1) 2>&1 )" || rc=$?
    h_expect "H4" "a red without its expected diagnostic" "HARNESS FAILURE -- red, but without its expected diagnostic"

    inject_x "H5 the -X injection, with no build after it"
    rc=0; out="$( (DELIBERATE=1; compare_tree "H5" "${LOGS}/H5-no-run.log" "${PERCOLATOR_COMMANDS}") 2>&1 )" || rc=$?
    h_expect "H5" "an injection that reached the source but not the compiled class" \
        "HARNESS FAILURE: (H5) the injection reached ${PERCOLATOR_COMMANDS} but not its compiled class"
    restore sandbox "${PERCOLATOR_COMMANDS}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1: PercolatorCommands requesting -X from a build without XML_OUTPUT"
    inject_x "1b -X whatever the build's capabilities"
    local log="${LOGS}/1b-matrix.log"
    run_in sandbox "${log}" bash scripts/matrix/run-matrix.sh --part tools --offline --out "${SANDBOX}/_build/hg-matrix-x"
    compare_tree "1b" "${SANDBOX}/_build/hg-matrix-x/maven.log" "${PERCOLATOR_COMMANDS}"
    record_pass "1b the damaged class reached the bytecode, and nothing else changed"
    grade_red "1b -X requested from a build that cannot write XML" "${RUN_RC}" 1 "${log}" \
        "the same build without XML_OUTPUT in its probed set: -X left out, and said so" \
        "re:^ RUN-FAILED +offer:percolator:3\.07\.1:linux-x86-64 +the same build without XML_OUTPUT" \
        "re:^ RUN-FAILED +offer:percolator:3\.06\.5:linux-x86-64 +the same build without XML_OUTPUT" \
        "re:^ RUN-PASSED +offer:comet:2026\.03\.0:linux-x86-64 " \
        "VERDICT  RED" || true
    restore sandbox "${PERCOLATOR_COMMANDS}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3, clean: the determinism suite's synthetic half (no spectra)"
    local out="${SANDBOX}/_build/hg-determinism-clean" log="${LOGS}/3a-determinism.log"
    run_in sandbox "${log}" bash scripts/matrix/run-matrix.sh --part determinism-synthetic --offline --out "${out}"
    compare_tree "3a" "${out}/maven.log"
    record_pass "3a every compiled class identical to the baseline: 1b's and H5's restorations reached the bytecode"
    grade_green "3a repeats and variations over the synthetic PIN" "${RUN_RC}" "${log}" \
        "VERDICT  GREEN -- every repeat and variation agrees within the documented tolerance; every control differs" \
        " PROBLEMS: none" || true
    end_control
}

control_3b() {
    begin_control "3b" "item 3: PercolatorSettings.valueText giving the seed from the clock"
    inject "3b the seed from the clock" sandbox "${PERCOLATOR_SETTINGS}" \
        "            case RANDOM_SEED -> Integer.toString(randomSeed);" \
        "            case RANDOM_SEED -> Long.toString(1 + Math.floorMod(System.nanoTime(), 20000L));"
    local out="${SANDBOX}/_build/hg-determinism-seed" log="${LOGS}/3b-determinism.log"
    run_in sandbox "${log}" bash scripts/matrix/run-matrix.sh --part determinism-synthetic --offline --out "${out}"
    compare_tree "3b" "${out}/maven.log" "${PERCOLATOR_SETTINGS}"
    record_pass "3b the damaged class reached the bytecode, and nothing else changed"
    grade_red "3b a repeat that is not a repeat" "${RUN_RC}" 1 "${log}" \
        "re:repeat-2 \(repeat\): artefact synthetic-psms DIFFERS -- out/psms\.tsv" \
        "VERDICT  RED" || true
    restore sandbox "${PERCOLATOR_SETTINGS}"
    end_control
}

control_3c() {
    begin_control "3c" "item 3: the written tolerance and the data agree (determinism.py check-doc)"
    local log="${LOGS}/3c-check-doc.log"
    run_in sandbox "${log}" "${PYTHON}" -B scripts/matrix/determinism.py check-doc
    grade_green "3c testing.rst's Determinism section and the data" "${RUN_RC}" "${log}" \
        "determinism: OK -- the document and the data state the same tolerance" || true
    # NEW: the data removing a field of the pepXML's comparison under a name
    # the written tolerance does not state -- a tolerance widened in the data
    # alone.  (Making the data's N 4 is refused before check-doc reads it: the
    # data's own validation, exit 2, a red for another reason.)
    inject "3c a field the document does not state" sandbox "${DETERMINISM_DATA}" \
        '"name": "msms_pipeline_analysis date",' '"name": "msms_pipeline_analysis date and time",'
    log="${LOGS}/3c-check-doc-red.log"
    run_in sandbox "${log}" "${PYTHON}" -B scripts/matrix/determinism.py check-doc
    grade_red "3c a tolerance the document does not state" "${RUN_RC}" 1 "${log}" \
        "determinism: the document and the data state different tolerances (red):" \
        "fixture:comet-pepxml: fields removed differ -- only in the document ['msms_pipeline_analysis date'], only in the data ['msms_pipeline_analysis date and time']" || true
    restore sandbox "${DETERMINISM_DATA}"
    end_control
}

# write_pit_report SHADOW DEFECT -- a synthetic PIT mutations.xml for
# cometgui-domain over the sandbox's compiled classes: four mutations per
# top-level class, all KILLED; with DEFECT=1 every mutation of the smallest
# package SURVIVED, and the module must stay at or above 80%.  Prints
# "<package> <its mutations> <module detected>/<module total>".
write_pit_report() {
    "${PYTHON}" -I - "${SANDBOX}/cometgui-domain/target/classes" "$1/cometgui-domain/target/pit-reports/mutations.xml" "$2" <<'PYTHON'
import collections
import sys
from pathlib import Path
from xml.sax.saxutils import escape

classes, out, defect = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3] == "1"
outer = sorted({str(p.relative_to(classes))[:-6].split("$")[0].replace("/", ".")
                for p in classes.rglob("*.class") if p.name != "package-info.class"})
outer = [name for name in outer if name.startswith("org.cometgui.domain.")]
packages = collections.defaultdict(list)
for name in outer:
    packages[name.rsplit(".", 1)[0]].append(name)
if len(packages) < 2:
    sys.exit(4)
chosen = min(packages, key=lambda p: (len(packages[p]), p))
lines, total, detected = ["<?xml version='1.0' encoding='UTF-8'?>", "<mutations>"], 0, 0
for name in outer:
    package = name.rsplit(".", 1)[0]
    status = "SURVIVED" if defect and package == chosen else "KILLED"
    for line in range(1, 5):
        total += 1
        detected += status == "KILLED"
        lines.append(f"<mutation detected='{str(status == 'KILLED').lower()}' status='{status}' numberOfTestsRun='1'>"
                     f"<sourceFile>{escape(name.rsplit('.', 1)[1])}.java</sourceFile>"
                     f"<mutatedClass>{escape(name)}</mutatedClass><mutatedMethod>synthetic</mutatedMethod>"
                     f"<methodDescription>()V</methodDescription><lineNumber>{line}</lineNumber>"
                     f"<mutator>org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator</mutator>"
                     f"<description>written by verify-hardening-gates.sh control 2</description></mutation>")
lines.append("</mutations>")
if defect and detected * 100 < 80 * total:
    sys.exit(5)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"{chosen} {4 * len(packages[chosen])} {detected}/{total} {len(packages)}")
PYTHON
}

control_2() {
    begin_control "2" "item 2, cheaply: build.sh's per-package mutation grading over a synthetic report (the real-PIT proof is the tests control's M)"
    local shadow="${SANDBOX}/_build/hg-pit-shadow" module facts rc
    rm -rf -- "${shadow}"
    mkdir -p -- "${shadow}"
    ln -s -- "${SANDBOX}/pom.xml" "${shadow}/pom.xml"
    for module in "${SANDBOX}"/cometgui-*/; do
        module="$(basename -- "${module}")"
        [ -f "${SANDBOX}/${module}/pom.xml" ] || continue
        mkdir -p -- "${shadow}/${module}/target"
        ln -s -- "${SANDBOX}/${module}/pom.xml" "${shadow}/${module}/pom.xml"
        ln -s -- "${SANDBOX}/${module}/src" "${shadow}/${module}/src"
        [ -d "${SANDBOX}/${module}/target/classes" ] \
            && ln -s -- "${SANDBOX}/${module}/target/classes" "${shadow}/${module}/target/classes"
    done
    rc=0; facts="$(write_pit_report "${shadow}" 0)" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "(2) the synthetic report could not be written (exit ${rc}): the sandbox's cometgui-domain classes were not found."
    local log="${LOGS}/2-pit-clean.log"
    run_in sandbox "${log}" bash scripts/build.sh --gates-pit-root "${shadow}" --gates-pit-modules cometgui-domain
    grade_green "2  every domain package mutated and every mutation killed" "${RUN_RC}" "${log}" \
        "OK: the gates stage's mutation grading passed over" \
        "re:^   [0-9]+ package\(s\) graded, [0-9]+ at or above 80%, 0 below$" || true

    rc=0; facts="$(write_pit_report "${shadow}" 1)" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "(2) the synthetic defect could not be written so that the module stays at or above 80% (exit ${rc})."
    local package mutations module_score
    read -r package mutations module_score _ <<<"${facts}"
    printf '   synthetic defect: %s, %s mutation(s), every one SURVIVED; module %s detected (>= 80%%)\n' \
        "${package}" "${mutations}" "${module_score}"
    grep -c "status='SURVIVED'" "${shadow}/cometgui-domain/target/pit-reports/mutations.xml" | grep -qx "${mutations}" \
        || harness_error "(2) the synthetic report does not hold the ${mutations} SURVIVED mutation(s) it was written with."
    log="${LOGS}/2-pit-below.log"
    run_in sandbox "${log}" bash scripts/build.sh --gates-pit-root "${shadow}" --gates-pit-modules cometgui-domain
    grade_red "2  a package below 80% inside a module above it" "${RUN_RC}" 1 "${log}" \
        "re:^   BELOW +${package//./\\.} " \
        "${package} is under the R-TEST-02 threshold of 80%; its undetected mutants:" \
        "re:^   module +cometgui-domain .* not graded$" \
        "mutation-gate check(s) failed." || true
    end_control
}

PERF_ONLY="hash-throughput,cancel-fake-tool"

control_4a() {
    begin_control "4a" "item 4: StreamingHashService digesting every chunk twice more (hash-throughput)"
    inject "4a two wasted SHA-256 passes per chunk" sandbox "${HASH_SERVICE}" \
        "                sha256.update(buffer, 0, read);
            }" \
        "                sha256.update(buffer, 0, read);
                newDigest(SHA_256_ALGORITHM).update(buffer, 0, read);
                newDigest(SHA_256_ALGORITHM).update(buffer, 0, read);
            }"
    local out="${SANDBOX}/_build/hg-perf-hash" log="${LOGS}/4a-performance.log"
    run_in sandbox "${log}" bash scripts/perf/run-performance.sh --part no-spectra --quick --only "${PERF_ONLY}" \
        --offline --out "${out}"
    compare_tree "4a" "${out}/maven.log" "${HASH_SERVICE}"
    record_pass "4a the damaged class reached the bytecode, and nothing else changed (3b's restoration with it)"
    grade_red "4a hashing slower than its threshold" "${RUN_RC}" 1 "${log}" \
        "re:^RED hash-throughput -- .* is under its threshold " "RED hash-throughput -- " \
        "re:^PASS +cancel-fake-tool " "performance: RED" || true
    restore sandbox "${HASH_SERVICE}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4: ProcessTree.destroyAll sending no polite stop (cancel-fake-tool)"
    inject "4b no polite stop" sandbox "${PROCESS_TREE}" \
        "        for (ProcessHandle handle : ordered) {
            handle.destroy();" \
        "        for (ProcessHandle handle : ordered) {
            handle.isAlive();"
    local out="${SANDBOX}/_build/hg-perf-cancel" log="${LOGS}/4b-performance.log"
    run_in sandbox "${log}" bash scripts/perf/run-performance.sh --part no-spectra --quick --only "${PERF_ONLY}" \
        --offline --out "${out}"
    compare_tree "4b" "${out}/maven.log" "${PROCESS_TREE}"
    record_pass "4b the damaged class reached the bytecode, and nothing else changed (4a's restoration with it)"
    grade_red "4b cancellation slower than its threshold" "${RUN_RC}" 1 "${log}" \
        "re:^RED cancel-fake-tool -- .* is over its threshold " "RED cancel-fake-tool -- " \
        "re:^PASS +hash-throughput " "performance: RED" || true
    restore sandbox "${PROCESS_TREE}"
    end_control
}

control_4c() {
    begin_control "4c" "item 4, clean, and the no-baseline verdict of a runner with no profile"
    local out="${SANDBOX}/_build/hg-perf-clean" log="${LOGS}/4c-performance.log"
    run_in sandbox "${log}" bash scripts/perf/run-performance.sh --part no-spectra --quick --only "${PERF_ONLY}" \
        --offline --out "${out}"
    compare_tree "4c" "${out}/maven.log"
    record_pass "4c every compiled class identical to the baseline: 4b's restoration reached the bytecode"
    grade_green "4c both measurements within their thresholds" "${RUN_RC}" "${log}" \
        "performance: GREEN -- 2 PASS, 0 RED" "re:^PASS +hash-throughput " "re:^PASS +cancel-fake-tool " || true
    local regrade="${SANDBOX}/_build/hg-perf-github"
    rm -rf -- "${regrade}"
    cp -r -- "${out}" "${regrade}"
    log="${LOGS}/4c-no-baseline.log"
    run_in sandbox "${log}" "${PYTHON}" -B scripts/perf/performance.py grade --report "${regrade}" \
        --profile github-ubuntu-latest
    grade_red "4c the same records on a runner with no recorded profile" "${RUN_RC}" 4 "${log}" \
        "performance: NO BASELINE FOR THIS RUNNER PROFILE -- 0 PASS, 0 RED" \
        "no runner profile is named 'github-ubuntu-latest'" \
        "re:^NO-BASELINE +hash-throughput " || true
    end_control
}

control_C() {
    begin_control "C" "every restoration proved, and nothing shared or in the working tree changed"
    local entry base file bad=0 check
    for entry in "${RESTORED[@]}"; do
        base="${entry%% *}"
        file="${entry#* }"
        check="$( cd -- "$(base_of "${base}")" && grep -F "  ${file}" "${PRISTINE}/${base}.sha256" | tail -1 | sha256sum -c - 2>&1 )" \
            || { printf '   NOT RESTORED %s/%s: %s\n' "${base}" "${file}" "${check}"; bad=$((bad + 1)); }
    done
    if [ "${bad}" -eq 0 ] && [ "${#RESTORED[@]}" -ge 10 ]; then
        record_pass "every damaged file (${#RESTORED[@]} restorations) passes sha256sum -c against its pristine digest"
    else
        record_fail "C: ${bad} damaged file(s) not restored, or only ${#RESTORED[@]} restorations recorded"
    fi
    if [ "$(cometgui_repo_digest)" = "${REPO_DIGEST}" ]; then
        record_pass "the shared repository's org/cometgui jars are unchanged"
    else
        record_fail "C: _build/m2repo/org/cometgui changed during the run; this harness must only read it"
    fi
    local status_now targets_now
    status_now="$(cd -- "${ROOT}" && git status --porcelain)"
    targets_now="$(worktree_targets_digest)"
    if [ "${status_now}" = "${GIT_STATUS}" ] && [ "${targets_now}" = "${TARGETS_DIGEST}" ]; then
        record_pass "the working tree is untouched: git status and every file under its cometgui-*/target unchanged"
    else
        [ "${status_now}" = "${GIT_STATUS}" ] \
            || printf '   git status before:\n%s\n   and now:\n%s\n' "${GIT_STATUS:-   (clean)}" "${status_now:-   (clean)}"
        [ "${targets_now}" = "${TARGETS_DIGEST}" ] || printf '   the working tree'"'"'s cometgui-*/target changed\n'
        record_fail "C: the working tree changed during the run (see above). If nobody else wrote to it, this harness did; if someone did, this run cannot say it did not"
    fi
    end_control
}

# -------------------------------------------------------------------- main --
main() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --list) list_controls; exit 0 ;;
            --worktree) SOURCE_MODE="worktree"; shift ;;
            *) usage >&2; die "unknown option: $1" 2 ;;
        esac
    done

    export LC_ALL=C.UTF-8 LANG=C.UTF-8
    export PYTHONDONTWRITEBYTECODE=1
    local started
    started="$(date +%s)"
    printf '===============================================================================\n'
    printf ' %s -- PHASE-15 exit gate items 1 to 6 must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  source       %s\n' "$([ "${SOURCE_MODE}" = worktree ] && echo 'the working tree (--worktree)' || echo 'git archive HEAD')"
    printf '  host         %s cores, load %s\n' "$(nproc)" "$(cut -d' ' -f1-3 /proc/loadavg)"
    preflight
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null 2>&1 || die "no mvn after sourcing tools/env.sh" 3

    REPO_DIGEST="$(cometgui_repo_digest)"
    GIT_STATUS="$(cd -- "${ROOT}" && git status --porcelain)"
    TARGETS_DIGEST="$(worktree_targets_digest)"

    control_S
    control_6
    control_K
    control_1a
    control_1c
    control_H
    control_1b
    control_3a
    control_3b
    control_3c
    control_2
    control_4a
    control_4b
    control_4c
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' DELEGATED, not repeated here\n'
    printf -- '-------------------------------------------------------------------------------\n'
    printf '   item 5  the manifest control: scripts/ci/nightly-manifest-verify.sh --self-test\n'
    printf '           (a checksum altered -> RED (a), against a loopback server)\n'
    printf '   item 2  the tests control'"'"'s control M: real PIT runs over cometgui-domain\n'
    printf '   item 6  the flakiness control: scripts/ci/flakiness-check.sh --self-test\n'
    control_C

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' SUMMARY\n'
    printf '===============================================================================\n'
    printf '  control  time\n'
    printf '  %s\n' "${TIMINGS[@]}"
    printf '\n  total %ds (%dm%02ds)\n' "${total}" "$((total / 60))" "$((total % 60))"
    printf '\nSUMMARY: %d control(s) passed, %d failed\n' "${PASSED}" "${FAILED}"
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  FAILED: %s\n' "${FAILURES[@]}"
        exit 1
    fi
    printf '\nEvery gate rejected its defect and accepted the clean tree.\n'
    printf 'PHASE-15 exit gate items 1, 2, 3, 4 and 6 were proved here; item 5 is the manifest control'"'"'s,\n'
    printf 'item 2'"'"'s real-PIT half the tests control'"'"'s M.\n'
    printf 'The harness reports a missing anchor, an unchanged file, a green graded as red, a red without\n'
    printf 'its diagnostic and an injection that reached the source but not the bytecode as a HARNESS FAILURE.\n'
}

main "$@"
