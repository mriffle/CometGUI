#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-08 workflow engine and Comet adapter gates can fail.
#
#   bash scripts/verify-workflow-gates.sh                  every control
#   bash scripts/verify-workflow-gates.sh --self-test      control H only
#   bash scripts/verify-workflow-gates.sh --only 1b,7,H    named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 08's exit gate is nine claims
# about a real Comet run through the workflow engine -- containment, the merge,
# the decoy blocks, the rerun preview, cancellation, revalidation and
# provenance -- plus the index-compatibility check tier 1 assigned to the
# phase.  Each is checked by tests in cometgui-workflow, cometgui-tools and
# cometgui-params-comet, most of them against the REAL pinned Comet binaries,
# and a green test says nothing about whether it would notice the defect it
# exists to catch.  So this script injects, one at a time, a defect each item
# exists to catch -- into PRODUCTION code of a `git archive HEAD` sandbox --
# proves the injection reached the compiled class, runs the narrowest test
# selection that should catch it, requires it red WITH THAT DEFECT'S OWN
# DIAGNOSTIC (the failing assertion's own words, never a sentence every failure
# shares) and in the named testcase -- and, where a test can show it, requires a
# sibling testcase the defect does not touch to STAY GREEN -- then restores the
# file and requires the clean sandbox green again on the baseline's bytecode.
#
# It is the sibling of scripts/verify-param-ui-gates.sh and
# scripts/verify-install-gates.sh and takes its shape from them: a git-archive
# sandbox, upstream modules built once into a private overlay repository,
# anchors that must match exactly once, a pristine copy per damaged file,
# bytecode digests, graded failures read from surefire's XML, one batched clean
# re-run, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-08-worklog.rst whose sign-off made that injection and
# saw it red; where the record gives the failure text, the control requires the
# same words.  [NEW] marks this script's own (unit 9), held to the same
# standard and labelled so nobody mistakes it for history.
#
# WHAT IT COVERS (PHASE-08 exit gate items; see phases/PHASE-08-workflow-comet.rst)
#
#   0   baseline: the overlay is built from the sandbox, the undamaged sandbox
#       passes every graded selection in one run, and the compiled
#       cometgui-tools, cometgui-params-comet and cometgui-workflow are digested
#   1a  item 1, the -N trap [recorded, unit 4 injection 2]: -N dropped from the
#       per-file Comet command, so the real Comet writes beside its input -- in
#       the input directory proven read-only (CometAdapterRealBinaryTest) --
#       and the argv test names it (CometSearchCommandsTest)
#   1b  item 1, the -N trap [NEW]: every spectrum file on ONE Comet command
#       line, the implementation phases/PHASE-08 warns "will appear to work and
#       will write into the user's data directory": the real read-only-directory
#       search must go red, and the one-input-per-command test name it
#   2   item 2 [NEW]: the -N base put in the project's runs/ directory beside
#       the run instead of inside it, so Comet's outputs land outside the run
#       directory -- red in the real run's before/after snapshot
#       (RealCometRunTest#gate2...)
#   3a  item 3 [recorded, unit 4 injection 1]: the PIN feature columns compared
#       as SETS, so two swapped columns merge; a RENAMED column must still be
#       refused (stays green), on synthetic and on real PINs
#   3b  item 3 [NEW]: the merge writes a header line for EVERY input -- red in
#       the real run's one-header assertion; the real run's gate-1 assertions
#       must stay green
#   4a  item 4 [recorded, unit 3 injection 2]: "no decoys anywhere" judged only
#       for an EMPTY FASTA -- red in the rule test and in the real pre-run
#       block; gate 5's real block must stay green
#   4b  item 4 [NEW]: the FASTA decoy census taken but not added to the pre-run
#       facts, so nothing consults it before Comet launches -- both real decoy
#       blocks go red
#   5   item 5 [NEW]: the double-decoy block applied to decoy_search = 1 only, so
#       decoy_search = 2 over a FASTA of decoys starts Comet -- red in the real
#       gate-5 test and the rule test of decoy_search = 2; gate 4, the engine's
#       own validate step and the decoy_search = 1 rule test stay green
#   6a  item 6 [recorded, unit 1 injection 2]: the merge-pin -> run-percolator
#       edge dropped from the declared graph (RerunPreviewTest scenario (d))
#   6b  item 6 [recorded, unit 1 injection 1]: run-percolator no longer
#       declaring PERCOLATOR_SETTINGS (scenario (b) red, (d) green)
#   6c  item 6 [recorded, unit 6 injection 2]: the COMET_PARAMETERS fingerprint
#       input taken from the Comet binary's digest instead of the parameter
#       file's, so after a REAL run the preview of changed parameters names
#       nothing
#   7   item 7 [recorded, unit 5 injection 1]: RunExecution.cancel no longer
#       calling requestCancellation on running steps -- red in the fake-Comet
#       process-tree test, BOUNDED by that test's own 60 s wait; and then no
#       process this control started may be left alive
#   8a  item 8 [recorded, unit 5 injection 2]: ReuseValidator treating every
#       re-hash as equal -- red in the engine test and in the REAL changed-input
#       test (RealChangedInputTest, both methods)
#   8b  item 8 [NEW]: revalidation served from the hash CACHE instead of a
#       re-read -- red only where the cache holds a stale entry; the plain
#       changed-file test must stay green
#   9a  item 9 [NEW]: the recorded comet.params SHA-256 setting taken from the
#       Comet binary instead of the archived, executed parameter file
#   9b  item 9 [NEW]: every Comet tool record given the FIRST invocation's
#       argument array, while the invocations themselves stay right -- the
#       real run's gate-1 assertions on the launched argvs stay green, gate 9's
#       distinct-argv assertion goes red
#   Iv  index check, VERSION-BLIND [recorded, unit 3 injection 1]: the
#       readable index formats ignore the release (judged `< 4` for every
#       release) -- red in the rule test and in RealIndexTest's ONE test of
#       both releases, the v4 index no longer refused for 2026.03.0
#   Iw  index check, VERSION-BLIND [NEW]: the readable formats are always the
#       FIRST release record's (2026.03.0's), so the same one test goes red on
#       its OTHER half: the v4 index refused for 2026.02.2, which reads it
#   Ih  index check [recorded, unit 6 injection 1]: an existing .idx's header
#       no longer added to the pre-run facts (RealIndexTest)
#   H   the harness itself: an unchanged file, an anchor that matches nothing,
#       an injection that reaches the source but not the bytecode, and a
#       selection that runs zero tests must each be reported as a HARNESS
#       ERROR; a green run graded as a red, and a red without its diagnostic,
#       as a recorded failure -- never as a control that bit
#   C   the clean sandbox passes every selection the dirty runs used, on the
#       baseline's own bytecode
#
# WHAT IT DOES NOT COVER, said plainly:
#
#   * The GUI half of Run (Phase 08 unit 7) -- that is control H7 of
#     scripts/verify-param-ui-gates.sh, and is not repeated here.
#   * The real-binary cancellation test (RealCancellationTest): with
#     cancellation broken the real Comet searches the whole proteome to its end,
#     which is not bounded by a timeout the test owns.  Item 7 is graded on the
#     fake-Comet process-tree test, which is.
#   * Storage (schema versions, the project lock) and unit 7b's path-parameter
#     checks: not exit gate items; their units' sign-offs record injections.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/workflow-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a POM
# are reported loudly.  scratch/ (the pinned Comet binaries, the K562 mzML and
# the proteome, all gitignored, D-006) is symlinked in read-only use: the real
# tests stage copies into their own temporary directories and fail rather than
# skip without it.  Every test JVM's java.io.tmpdir is a directory INSIDE the
# sandbox (through cometgui.surefire.extraArgLine, which none of the three
# modules sets), so every Comet a test stages, and every EngineFake, runs from a
# path that names this sandbox -- which is how a process this script started is
# told from anyone else's.
#
# THREE MODULES, BUILT AGAINST THEIR OWN UPSTREAM.  The injections are in
# cometgui-tools, cometgui-params-comet and cometgui-workflow; the graded tests
# in the same three.  Their upstream modules (read from the sandbox's POMs:
# everything cometgui-workflow depends on, transitively, except those three)
# are built ONCE, from the sandbox, into a PRIVATE overlay repository,
# _build/workflow-gate-m2, whose every other entry is a symlink into
# _build/m2repo.  Every Maven run then builds the three modules alone (`-pl`,
# no -am), each damaged class taken from the reactor -- the overlay holds no
# jar of any of the three, and that is checked.
#
# RED IS READ FROM SUREFIRE'S XML, NOT FROM THE EXIT CODE.  A control may grade
# a test in cometgui-params-comet and one in cometgui-workflow on the same
# injection; a failing upstream module would stop the reactor before the
# downstream one ran.  So every run passes -Dmaven.test.failure.ignore=true, a
# non-zero Maven exit is a HARNESS ERROR (a build that did not run its tests),
# and red means: at least one selected testcase failed or errored in a fresh
# surefire report.  Every failing testcase's message is copied, unescaped, from
# the XML into the run's log, and the expected diagnostic is matched there.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * the anchor must match EXACTLY ONCE, or the run stops as a harness error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled form of the damaged file -- Foo.class
#     and every Foo$*.class -- must DIFFER from the clean baseline;
#   * and every OTHER file under the three modules' target/classes must be
#     byte-identical to the baseline.
#
# EVERY RUN IS PROVED TO HAVE RUN WHAT IT NAMED.  Each selector's class must
# have a fresh surefire report with tests >= 1, every method it names must
# appear in it, and a bare class selector naming a class with @Nested tests is
# refused (surefire drops them without a word); nested tests are named as
# Outer$Inner#method.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip: Phase 01's gates, with their own harnesses; an injection would
# otherwise be stopped by a formatter before a test ran.  Nothing this script
# grades is skipped, no test is excluded, and every run is checked to have
# EXECUTED what it named.
#
# WHAT IT NEEDS.  A built tree: tools/ (JDK, Maven), a populated _build/m2repo,
# and scratch/phase05/artefacts and scratch/fixture.  Offline; writes only under
# _build/ (and the test JVMs' temporary files inside the sandbox).
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the SUMMARY
# line.  Measured on the development machine on 2026-10-07: see
# docs/developer/testing.rst.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, a test that had to stay green went red, a process was
#      left alive, or the clean tree did not pass again
#   2  misuse (unknown option, unknown control)
#   3  the environment is not ready
#   4  HARNESS ERROR: an anchor is gone, an injection did not reach the source
#      or the bytecode, a restoration did not reach the bytecode, a run did not
#      build, or a command ran none of the tests it named.  The run proves
#      nothing and must not be read as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/workflow-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/workflow-gate-pristine"
readonly OVERLAY="${ROOT}/_build/workflow-gate-m2"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/workflow-gate-logs"
# Every test JVM's java.io.tmpdir: inside the sandbox, so a process a test
# started names the sandbox in its path.
readonly TESTTMP="${SANDBOX}/_testtmp"

readonly TOOLS="cometgui-tools"
readonly PARAMS="cometgui-params-comet"
readonly WORKFLOW="cometgui-workflow"
readonly -a MODULES=("${TOOLS}" "${PARAMS}" "${WORKFLOW}")

# The production files the controls damage.
readonly TC="${TOOLS}/src/main/java/org/cometgui/tools/comet"
readonly SEARCH_COMMANDS="${TC}/CometSearchCommands.java"
readonly PIN_MERGER="${TC}/PinMerger.java"
readonly PV="${PARAMS}/src/main/java/org/cometgui/params/comet/validation"
readonly DECOY_RULE="${PV}/FastaDecoyRule.java"
readonly INDEX_RULE="${PV}/IndexCompatibilityRule.java"
readonly WF="${WORKFLOW}/src/main/java/org/cometgui/workflow"
readonly STEP_GRAPH="${WF}/state/StepGraph.java"
readonly ENGINE_STEP="${WF}/state/EngineStep.java"
readonly RUN_EXECUTION="${WF}/engine/RunExecution.java"
readonly REUSE_VALIDATOR="${WF}/engine/ReuseValidator.java"
readonly STEP_CONTEXT="${WF}/engine/StepContext.java"
readonly PRE_RUN_CHECKS="${WF}/steps/PreRunChecks.java"
readonly RUN_INPUTS="${WF}/steps/RunInputs.java"
readonly COMET_WORKFLOW="${WF}/steps/CometWorkflow.java"

# The graded test classes.
readonly T_ARGV="CometSearchCommandsTest"
readonly T_ADAPTER="CometAdapterRealBinaryTest"
readonly T_MERGER="PinMergerTest"
readonly T_RUN="RealCometRunTest"
readonly T_DECOY_RULES="FastaDecoyRulesTest"
readonly T_DECOY="RealDecoyBlockTest"
readonly T_PREVIEW="RerunPreviewTest"
readonly T_CANCEL="CancellationTest"
readonly T_RETRY="RetryRevalidationTest"
readonly T_CHANGED="RealChangedInputTest"
readonly T_INDEX_RULES="IndexCompatibilityRulesTest"
readonly T_INDEX="RealIndexTest"

readonly M_CANCEL="cancellingARunningStepKillsItsToolAndTheToolsChildAndLeavesAWholeRecord"
readonly M_CHANGED="aRecordedInputThatChangedRefusesReuseNamingTheFileAndOffersToRunItsProducer"
readonly M_STALE="theReHashIsNotServedFromTheCacheEvenWhenTheCacheHoldsAStaleEntry"
readonly M_V4="aVersion4IndexIsRefusedForTheNewerReleaseAndAcceptedForTheOlder"

# Phase 01's gates have their own harnesses; see the header.  The last two are
# this script's own: red is read from the XML (see the header), and every test
# JVM's temporary directory is inside the sandbox.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run.
readonly -a ALL_CONTROLS=(1a 1b 2 3a 3b 4a 4b 5 6a 6b 6c 7 8a 8b 9a 9b Iv Iw Ih H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the nine PHASE-08 exit gate items, and the index
compatibility check, fail on the defects they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/ (JDK, Maven), a populated _build/m2repo, and
scratch/phase05/artefacts and scratch/fixture (the pinned Comet binaries and
the D-006 inputs).  It runs Maven offline, damages only a git-archive sandbox
under _build/, and writes only under _build/.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing, or a run that ran nothing).
USAGE
}

die() {
    printf '\nFATAL: %s\n' "$1" >&2
    exit "${2:-1}"
}

harness_error() {
    printf '\nHARNESS ERROR: %s\n' "$1" >&2
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
    TIMINGS+=("$(printf '%-4s %5ds' "${CONTROL_ID}" "${elapsed}")")
    printf '   (control %s took %ds)\n' "${CONTROL_ID}" "${elapsed}"
}

# While control H provokes a failure on purpose, it is printed as REFUSED, and
# any pass the provoked grading prints is NOT counted: the SUMMARY count is
# what verify-all-gates.sh holds to a floor.
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

is_module() {
    local wanted="$1" module
    for module in "${MODULES[@]}"; do
        [ "${module}" = "${wanted}" ] && return 0
    done
    return 1
}

# ---------------------------------------------------------- upstream modules --
#
# upstream_modules -- every cometgui module cometgui-workflow depends on,
# transitively, read from the sandbox's own POMs, except the three every run
# rebuilds.  An upstream module that itself depends on one of the three would
# carry an undamaged copy of it into the overlay: a harness error, not a quiet
# hole in every control.
upstream_modules() {
    python3 - "${SANDBOX}" "${WORKFLOW}" "${MODULES[@]}" <<'PYTHON'
import re
import sys
from pathlib import Path

root, top, rebuilt = Path(sys.argv[1]), sys.argv[2], set(sys.argv[3:])
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    deps[module] = sorted(set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text)) - {module})
for wanted in rebuilt:
    if wanted not in deps:
        sys.stderr.write("no module %r in the sandbox\n" % wanted)
        raise SystemExit(1)
seen, todo = set(), list(deps[top])
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
if not rebuilt - {top} <= seen:
    sys.stderr.write("%s does not depend on every rebuilt module\n" % top)
    raise SystemExit(1)
seen -= rebuilt
for module in sorted(seen):
    carried = rebuilt & set(deps.get(module, []))
    if carried:
        sys.stderr.write("%s depends on %s, so the overlay would carry an undamaged copy\n" % (module, sorted(carried)))
        raise SystemExit(1)
print(",".join(sorted(seen)))
PYTHON
}

COMETGUI_REPO_DIGEST=""
cometgui_repo_digest() {
    ( cd -- "${M2REPO}/org/cometgui" && find . -type f -name '*.jar' -print0 | sort -z \
        | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1 )
}

build_overlay() {
    rm -rf -- "${OVERLAY}"
    mkdir -p -- "${OVERLAY}/org/cometgui"
    local entry
    for entry in "${M2REPO}"/*; do
        [ "$(basename -- "${entry}")" = "org" ] && continue
        ln -s -- "${entry}" "${OVERLAY}/"
    done
    for entry in "${M2REPO}"/org/*; do
        [ "$(basename -- "${entry}")" = "cometgui" ] && continue
        ln -s -- "${entry}" "${OVERLAY}/org/"
    done
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"

    local upstream log="${LOGS}/upstream-install.log" rc=0 marker
    upstream="$(upstream_modules)" \
        || harness_error "the upstream modules of ${WORKFLOW} cannot be read from the sandbox's POMs, or one of them depends on a module every run rebuilds."
    [ -n "${upstream}" ] || harness_error "${WORKFLOW} has no upstream module at all; the POM read is wrong."
    marker="${LOGS}/upstream-install.marker"
    touch -- "${marker}"
    printf '   upstream of %s: %s\n' "${MODULES[*]}" "${upstream}"
    printf '   mvn -o -pl %s -am install -DskipTests (into %s)\n' "${upstream}" "$(rel "${OVERLAY}")"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${upstream}" -am install -DskipTests ) >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    [ "${rc}" -eq 0 ] \
        || harness_error "the sandbox's upstream modules did not build and install into the overlay (exit ${rc}, log: $(rel "${log}"))."
    local module jar
    for module in ${upstream//,/ }; do
        jar="${OVERLAY}/org/cometgui/${module}/0.1.0-SNAPSHOT/${module}-0.1.0-SNAPSHOT.jar"
        [ -s "${jar}" ] && [ "${jar}" -nt "${marker}" ] && [ ! -L "${jar}" ] \
            || harness_error "the overlay has no freshly built ${module} jar at $(rel "${jar}"); the three modules would be built against an older one."
    done
    for module in "${MODULES[@]}"; do
        [ ! -e "${OVERLAY}/org/cometgui/${module}" ] \
            || harness_error "the overlay holds ${module}, so a run could take an undamaged ${module} from it instead of the reactor."
    done
    [ ! -L "${OVERLAY}/org/cometgui" ] \
        || harness_error "the overlay's org/cometgui is a symlink into the shared repository."
    record_pass "upstream ${upstream} built from the sandbox and installed into the private overlay only"
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree -- "sha256  <module>/<path>" for every file under the three
# modules' target/classes: compiled classes AND the resources copied there.
class_tree() {
    local module dir
    for module in "${MODULES[@]}"; do
        dir="${SANDBOX}/${module}/target/classes"
        [ -d "${dir}" ] || continue
        ( cd -- "${dir}" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum ) \
            | sed "s#  \./#  ${module}/#"
    done
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"

record_baseline_tree() {
    class_tree >"${BASELINE_TREE}"
    local module count summary=""
    for module in "${MODULES[@]}"; do
        count="$(grep -c "  ${module}/.*\.class\$" "${BASELINE_TREE}" || true)"
        [ "${count}" -gt 0 ] \
            || harness_error "the baseline run compiled no class in ${module}, so no clean bytecode can be compared against."
        summary="${summary}${summary:+, }${count} ${module}"
    done
    record_pass "baseline: the three compiled modules are digested (${summary} classes; $(grep -vc '\.class$' "${BASELINE_TREE}") resources)"
}

compiled_prefix() {
    local source="$1"
    case "${source}" in
        */src/main/java/*.java)
            local module="${source%%/*}" within="${source#*/src/main/java/}"
            printf '%s/%s' "${module}" "${within%.java}"
            ;;
        *) harness_error "no compiled form is known for ${source}" ;;
    esac
}

is_compiled_form_of() {
    local prefix="$1" path="$2"
    [ "${path}" = "${prefix}.class" ] && return 0
    case "${path}" in
        "${prefix}\$"*.class) return 0 ;;
    esac
    return 1
}

# compare_tree LOG [INJECTED SOURCE] -- the compiled modules after a run,
# against the baseline.  With an injected source: its compiled form MUST
# differ, and everything else MUST be identical.  Without one: everything must
# be identical.  Anything else is a harness error.
compare_tree() {
    local log="$1" injected="${2:-}"
    [ -s "${BASELINE_TREE}" ] || harness_error "no baseline digest of the compiled modules was recorded."
    local now prefix=""
    now="$(class_tree)"
    [ -n "${injected}" ] && prefix="$(compiled_prefix "${injected}")"

    local -A before=() after=() all=()
    local digest path
    while read -r digest path; do
        before["${path}"]="${digest}"
    done <"${BASELINE_TREE}"
    while read -r digest path; do
        [ -n "${path}" ] && after["${path}"]="${digest}"
    done <<<"${now}"
    for path in "${!before[@]}"; do all["${path}"]=1; done
    for path in "${!after[@]}"; do all["${path}"]=1; done

    local changed=0 count=0 others=0
    local -a stray=()
    for path in "${!all[@]}"; do
        if [ -n "${prefix}" ] && is_compiled_form_of "${prefix}" "${path}"; then
            count=$((count + 1))
            [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ] && changed=$((changed + 1))
            continue
        fi
        if [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ]; then
            stray+=("${path}")
        else
            others=$((others + 1))
        fi
    done

    if [ "${#stray[@]}" -gt 0 ]; then
        harness_error "$(rel "${log}"): ${#stray[@]} file(s) under target/classes differ from the clean baseline although no control damaged them: ${stray[*]:0:5}. A previous restoration did not reach the bytecode, or the build is not reproducible, so this run's result cannot be attributed."
    fi
    if [ -n "${prefix}" ]; then
        [ "${count}" -gt 0 ] \
            || harness_error "$(rel "${log}"): target/classes has no compiled form of ${injected} at all."
        [ "${changed}" -gt 0 ] \
            || harness_error "$(rel "${log}"): the compiled form of ${injected} is BYTE-IDENTICAL to the clean baseline after the dirty run. The injection reached the source and not the bytecode, so the run tested the clean code and its result is not evidence of anything."
        printf '   bytecode: %d of %d compiled file(s) of the damaged source changed; %d other file(s) identical to the baseline\n' \
            "${changed}" "${count}" "${others}"
    else
        printf '   bytecode: all %d compiled file(s) identical to the clean baseline\n' "${others}"
    fi
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG SELECTORS -- the one test command shape in this script: the three
# modules alone, against the overlay, test failures not stopping the reactor.
# A non-zero exit therefore means the build itself failed.  The exit status and
# every failing testcase's message (from the XML, unescaped) are written INTO
# the log.
run_mvn() {
    local log="$1" selectors="$2" rc=0 module
    for module in "${MODULES[@]}"; do
        rm -rf -- "${SANDBOX}/${module}/target/surefire-reports"
    done
    mkdir -p -- "${TESTTMP}"
    local reactor
    reactor="$(IFS=,; printf '%s' "${MODULES[*]}")"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${reactor}" test \
            -Dmaven.test.failure.ignore=true \
            "-Dcometgui.surefire.extraArgLine=-Djava.io.tmpdir=${TESTTMP}" \
            -Dtest="${selectors}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    append_failures "${log}"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s test -Dmaven.test.failure.ignore=true -Dtest=%s' \
        "$(IFS=,; printf '%s' "${MODULES[*]}")" "$1"
}

# append_failures LOG -- every failed or errored testcase in the fresh reports,
# with its message and the first lines of its trace, unescaped, appended to the
# log under a marker.  The grading reads these lines; the console's trimmed
# summary is kept above them.
append_failures() {
    local log="$1"
    python3 - "${SANDBOX}" "${MODULES[@]}" >>"${log}" <<'PYTHON'
import glob
import sys
import xml.etree.ElementTree as ET

root, modules = sys.argv[1], sys.argv[2:]
print("\n=== FAILED TESTCASES FROM SUREFIRE XML ===")
count = 0
for module in modules:
    for path in sorted(glob.glob("%s/%s/target/surefire-reports/TEST-*.xml" % (root, module))):
        suite = ET.parse(path).getroot()
        for case in suite.iter("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is None:
                    continue
                count += 1
                print("FAILED %s#%s [%s] %s" % (suite.get("name"), case.get("name"), kind, node.get("type", "")))
                message = node.get("message") or ""
                for line in message.splitlines()[:40]:
                    print("  message: %s" % line)
                for line in (node.text or "").splitlines()[:25]:
                    print("  trace: %s" % line)
print("=== %d FAILED TESTCASE(S) ===" % count)
PYTHON
}

# failed_count LOG -- the number of failed testcases append_failures counted.
failed_count() {
    sed -n 's/^=== \([0-9][0-9]*\) FAILED TESTCASE(S) ===$/\1/p' "$1" | tail -1
}

selector_class() {
    local selector="${1%%#*}"
    printf '%s' "${selector%%\$*}"
}

# test_source_of CLASS -- the one test source of that name in the three modules.
test_source_of() {
    local class="$1" found
    found="$(cd -- "${SANDBOX}" && find "${TOOLS}/src/test/java" "${PARAMS}/src/test/java" \
        "${WORKFLOW}/src/test/java" -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found}"
}

report_of() {
    local class="$1" module
    for module in "${MODULES[@]}"; do
        find "${SANDBOX}/${module}/target/surefire-reports" -maxdepth 1 \
            -name "TEST-*.${class}.xml" 2>/dev/null || true
    done | head -1
}

# testcase_outcome CLASS METHOD -- passed, failed, or absent, from the XML
# report of the class (nested classes' tests are in the outer class's report).
# A parameterised method passes only if every one of its invocations does.
testcase_outcome() {
    local class="$1" method="$2" report
    report="$(report_of "${class}")"
    python3 - "${report}" "${method}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

report, method = sys.argv[1:3]
outcome = "absent"
if report:
    for case in ET.parse(report).getroot().iter("testcase"):
        name = case.get("name", "")
        if name == method or (method and (name.startswith(method + "(")
                or name.startswith(method + "[") or name.startswith(method + "{"))):
            failed = case.find("failure") is not None or case.find("error") is not None
            if failed:
                outcome = "failed"
            elif outcome == "absent":
                outcome = "passed"
print(outcome)
PYTHON
}

# verify_classes_ran LOG SELECTORS -- every class a selector names produced a
# report with at least one test, a bare class selector names no class with
# @Nested tests, and every method named ran.  Otherwise a harness error.
verify_classes_ran() {
    local log="$1" selectors="$2"
    local -a wanted=() methods=()
    local selector class report count source method outcome
    IFS=',' read -r -a wanted <<<"${selectors}"
    for selector in "${wanted[@]}"; do
        class="$(selector_class "${selector}")"
        source="$(test_source_of "${class}")"
        report="$(report_of "${class}")"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${selector} produced no surefire report for $(rel "${log}"). The command named a test that surefire did not execute, so this run tested nothing."
        fi
        count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" | head -1)"
        [ -n "${count}" ] \
            || harness_error "the surefire report for ${class} carries no tests= count; this harness cannot read it and would pass vacuously."
        [ "${count}" -ge 1 ] \
            || harness_error "${selector} executed ${count} tests in $(rel "${log}"). A selection that runs no test cannot prove or disprove a gate."
        if [ "${selector}" = "${selector%%#*}" ]; then
            if grep -q '@Nested' "${SANDBOX}/${source}"; then
                harness_error "the selector ${selector} names a class with @Nested tests, and surefire drops those from a class selector in a list without a word. Name the method (Outer\$Inner#method)."
            fi
            continue
        fi
        IFS='+' read -r -a methods <<<"${selector#*#}"
        for method in "${methods[@]}"; do
            outcome="$(testcase_outcome "${class}" "${method}")"
            [ "${outcome}" != "absent" ] \
                || harness_error "${class}#${method} did not run in $(rel "${log}"); the selector ${selector} selects less than it names. A selection that runs zero of the tests it names cannot prove or disprove a gate."
        done
    done
}

assert_testcase() {
    local label="$1" want="$2" class="$3" method="$4" got
    got="$(testcase_outcome "${class}" "${method}")"
    [ "${got}" != "absent" ] \
        || harness_error "(${label}) ${class}.${method} did not run, so its outcome cannot be read."
    if [ "${got}" = "${want}" ]; then
        record_pass "${label}: ${class}.${method} ${got}"
    else
        record_fail "${label}: ${class}.${method} ${got}, and this control requires it ${want}"
    fi
}

# assert_class_setup_failed LABEL CLASS -- the class's @BeforeAll failed: surefire
# then reports one testcase with an EMPTY name for the class, and no method.
# The real-binary tests run their search in @BeforeAll, so a defect that breaks
# the search is red there.
assert_class_setup_failed() {
    local label="$1" class="$2" got
    got="$(testcase_outcome "${class}" "")"
    if [ "${got}" = "failed" ]; then
        record_pass "${label}: ${class}'s class-level setup failed"
    else
        record_fail "${label}: ${class}'s class-level setup did not fail (${got}), and this control requires it to"
    fi
}

# THREE WAYS TO STATE AN EXPECTED DIAGNOSTIC: a literal where the text carries
# nothing that varies, a regular expression where it does -- and then the
# expression must still name the thing -- and, for an assertEquals of two SETS
# of engine steps, "set": JUnit prints a Set.of in an order that changes from
# one JVM to the next, so the words are compared as sets.  The expectation is
# "PREFIX::MISSING::EXTRA": a failure message beginning with PREFIX then
# "expected: <[...]> but was: <[...]>", whose expected set minus its actual set
# is exactly MISSING and whose actual set minus its expected set is exactly
# EXTRA (comma-separated step names; either may be empty).
set_match() {
    local log="$1" expected="$2"
    python3 - "${log}" "${expected}" <<'PYTHON'
import re
import sys

log, spec = sys.argv[1:3]
prefix, missing, extra = spec.split("::")
want_missing = {name for name in missing.split(",") if name}
want_extra = {name for name in extra.split(",") if name}
pattern = re.compile(re.escape(prefix) + r"expected: <\[([A-Z_, ]*)\]> but was: <\[([A-Z_, ]*)\]>")


def names(text):
    return {name.strip() for name in text.split(",") if name.strip()}


with open(log, encoding="utf-8", errors="replace") as handle:
    for line in handle:
        found = pattern.search(line)
        if found is None:
            continue
        expected, actual = names(found.group(1)), names(found.group(2))
        if expected - actual == want_missing and actual - expected == want_extra:
            print(line.strip())
            raise SystemExit(0)
raise SystemExit(1)
PYTHON
}

log_has() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -qF -- "${expected}" "${log}" ;;
        regex) grep -qE -- "${expected}" "${log}" ;;
        set) set_match "${log}" "${expected}" >/dev/null ;;
        *) harness_error "unknown match mode '${mode}'" ;;
    esac
}

log_first_match() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -F -- "${expected}" "${log}" | head -1 ;;
        regex) grep -E -- "${expected}" "${log}" | head -1 ;;
        set) set_match "${log}" "${expected}" || true ;;
    esac
}

# grade_red MODE LABEL FAILED-COUNT LOG EXPECTED -- the grading half of a dirty
# run, separate from running it so that control H can grade a run it knows is
# wrong and require the grader to refuse it.  "Red" is the number of failed
# testcases in the fresh XML (see the header), never the exit code.
grade_red() {
    local mode="$1" label="$2" failed="$3" log="$4" expected="$5"
    if [ "${failed}" -eq 0 ]; then
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: $(rel "${log}"))"
        return
    fi
    if ! log_has "${log}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: $(rel "${log}"))"
        printf '         first failure: %s\n' \
            "$(grep -m1 -A1 '^FAILED ' "${log}" | tr '\n' ' ' | cut -c1-240 || true)"
        return
    fi
    record_pass "${label}: rejected, ${failed} testcase(s) red"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
}

assert_log_contains() { graded_log fixed "$@"; }
assert_log_matches() { graded_log regex "$@"; }

graded_log() {
    local mode="$1" label="$2" log="$3" expected="$4"
    if log_has "${log}" "${expected}" "${mode}"; then
        record_pass "${label}"
        printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
    else
        record_fail "${label}: nothing in $(rel "${log}") matches '${expected}'"
    fi
}

# ------------------------------------------------------------- processes --
#
# live_sandbox_processes -- "pid args" of every live (not zombie) process whose
# command line names this sandbox: a Comet a test staged under the sandbox's
# temporary directory, an EngineFake run from the sandbox's classes, a test JVM.
# Nothing else on the machine can name it, so these are processes this script
# started.
live_sandbox_processes() {
    ps -eo pid=,stat=,args= 2>/dev/null | awk -v box="${SANDBOX}" -v self="$$" '
        $2 !~ /^Z/ && index($0, box) > 0 && $1 != self && $0 !~ /awk -v box=/ {
            pid = $1; $1 = ""; $2 = ""; sub(/^  */, ""); print pid " " $0
        }' || true
}

# assert_no_processes_left LABEL -- after a run: nothing the run started is
# alive.  Anything that is gets killed (it is ours: see above) and the control
# FAILS -- a process that outlives its test is a defect, even when the test is
# red on purpose.
assert_no_processes_left() {
    local label="$1" left pid
    sleep 1
    left="$(live_sandbox_processes)"
    if [ -z "${left}" ]; then
        record_pass "${label}: no Comet, EngineFake or test JVM started from the sandbox is alive (ps)"
        return
    fi
    record_fail "${label}: process(es) started from the sandbox outlived the run: $(printf '%s' "${left}" | cut -c1-160 | tr '\n' ';')"
    for pid in $(printf '%s\n' "${left}" | awk '{print $1}'); do
        kill -KILL "${pid}" 2>/dev/null || true
    done
}

# dirty_run LABEL SOURCE SELECTORS LOG -- runs the narrow check on the damaged
# sandbox and proves what it ran.  Leaves the failed-testcase count in
# DIRTY_FAILED.
DIRTY_FAILED=0
dirty_run() {
    local label="$1" source="$2" selectors="$3" log="$4" rc=0
    DIRTY_FAILED=0
    run_mvn "${log}" "${selectors}" || rc=$?
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "(${label}) the damaged sandbox does not compile -- see $(rel "${log}"). A red that never ran a test is not a result."
    fi
    [ "${rc}" -eq 0 ] \
        || harness_error "(${label}) Maven exited ${rc} although test failures do not stop it here (-Dmaven.test.failure.ignore): the build itself failed -- see $(rel "${log}")."
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException: org\.cometgui' "${log}"; then
        harness_error "(${label}) $(rel "${log}") carries NoClassDefFoundError/ClassNotFoundException for project code: the red is the harness's, not the gate's."
    fi
    compare_tree "${log}" "${source}"
    verify_classes_ran "${log}" "${selectors}"
    DIRTY_FAILED="$(failed_count "${log}")"
    [ -n "${DIRTY_FAILED}" ] || harness_error "(${label}) $(rel "${log}") carries no failed-testcase count."
    USED_SELECTORS+=("${selectors}")
}

# ------------------------------------------------- injection and its guards --

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -p -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
}

# replace_once LABEL FILE OLD NEW -- the anchor must match EXACTLY ONCE.
replace_once() {
    local label="$1" file="$2" old="$3" new="$4" rc=0
    python3 - "${SANDBOX}/${file}" "${old}" "${new}" "${label}" <<'PYTHON' || rc=$?
import sys

path, old, new, label = sys.argv[1:5]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
found = text.count(old)
if found != 1:
    sys.stderr.write(
        "the anchor for %r occurs %d time(s) in %s, expected exactly once.\n"
        "  anchor: %r\n" % (label, found, path, old)
    )
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text.replace(old, new))
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) the injection anchor is gone from ${file}. The source moved under this control, which would inject nothing and report green."
    fi
}

assert_modified() {
    local label="$1" file="$2"
    [ -e "${SANDBOX}/${file}" ] || harness_error "(${label}) ${file} is missing from the sandbox."
    [ -e "${PRISTINE}/${file}" ] || harness_error "(${label}) no pristine copy of ${file} was taken, so nothing can be compared."
    if cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}"; then
        harness_error "(${label}) ${file} is byte-identical to the pristine copy. The defect was not injected and the control would have tested nothing."
    fi
    printf '   injected %s (%s changed line(s) against the pristine copy)\n' "${file}" \
        "$(diff "${PRISTINE}/${file}" "${SANDBOX}/${file}" | grep -c '^[<>]' || true)"
}

restore_pristine() {
    local file="$1"
    [ -e "${PRISTINE}/${file}" ] || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}" || harness_error "could not restore ${file} in the sandbox."
    touch -- "${SANDBOX}/${file}"
    RESTORED+=("${file}")
    printf '   restored %s (byte-identical to the pristine copy, and touched)\n' "${file}"
}

# inject_and_run LABEL SOURCE SELECTORS MODE EXPECTED OLD NEW -- damage one
# production file once, run the selection that grades it, require the red with
# its own words.  The caller then makes further assertions and restores.
DIRTY_LOG=""
inject_and_run() {
    local label="$1" source="$2" selectors="$3" mode="$4" expected="$5" old="$6" new="$7"
    save_pristine "${source}"
    replace_once "${label}" "${source}" "${old}" "${new}"
    assert_modified "${label}" "${source}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${selectors}")"
    dirty_run "${label}" "${source}" "${selectors}" "${DIRTY_LOG}"
    grade_red "${mode}" "${label}" "${DIRTY_FAILED}" "${DIRTY_LOG}" "${expected}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"
    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."
    # scratch/ is gitignored (D-006): the pinned binaries and the real inputs.
    # The real tests read it through the repository root they find above their
    # working directory, which is the sandbox.  They copy what they use.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    [ -d "${SANDBOX}/scratch/phase05/artefacts" ] && [ -d "${SANDBOX}/scratch/fixture" ] \
        || harness_error "the sandbox's scratch/ symlink does not resolve to the pinned binaries and the D-006 inputs. Every real-binary control would fail for the wrong reason."
    local head dirty
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: $(rel "${SANDBOX}") (git archive ${head})"
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' scripts pom.xml 'cometgui-*/pom.xml' | head -20)"
    if [ -n "${dirty}" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes this harness reads.\n'
        printf '        This run proves the gates of HEAD (%s), not of the working tree:\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
}

# ------------------------------------------------------------- the selectors --
#
# One place for every selector, so the baseline and the final clean run select
# exactly what the dirty runs select.
readonly SEL_1="${T_ARGV}#parameterFileCommands+oneInputPerCommand,${T_ADAPTER}"
readonly SEL_2="${T_RUN}"
readonly SEL_3A="${T_MERGER}#swappedColumns+renamedColumn,${T_ADAPTER}#swappedRealColumns+renamedRealColumn"
readonly SEL_4="${T_DECOY_RULES}#noDecoysAnywhere+doubleDecoysConcatenated+doubleDecoysSeparate,${T_DECOY}"
readonly SEL_6="${T_PREVIEW}\$StageReruns#cometParametersRerunEverything+percolatorParametersRerunPercolatorAndDownstream"
readonly SEL_7="${T_CANCEL}#${M_CANCEL}"
readonly SEL_8A="${T_RETRY}#${M_CHANGED}+${M_STALE},${T_CHANGED}"
readonly SEL_8B="${T_RETRY}#${M_CHANGED}+${M_STALE}"
readonly SEL_I="${T_INDEX_RULES}#formatByRelease,${T_INDEX}#${M_V4}"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 a selection naming a method that does not exist.
readonly SEL_H="${SEL_8B}"
readonly SEL_H4="${T_RETRY}#noSuchTestMethodInThisClass"

control_selectors() {
    case "$1" in
        1a|1b) printf '%s' "${SEL_1}" ;;
        2|3b|6c|9a|9b) printf '%s' "${SEL_2}" ;;
        3a) printf '%s' "${SEL_3A}" ;;
        4a|4b|5) printf '%s' "${SEL_4}" ;;
        6a|6b) printf '%s' "${SEL_6}" ;;
        7) printf '%s' "${SEL_7}" ;;
        8a) printf '%s' "${SEL_8A}" ;;
        8b) printf '%s' "${SEL_8B}" ;;
        Iv|Iw|Ih) printf '%s' "${SEL_I}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

BASELINE_SELECTORS=""
plan_baseline() {
    local id
    local -a selectors=()
    for id in "$@"; do
        selectors+=("$(control_selectors "${id}")")
    done
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the overlay is built and the undamaged sandbox passes every graded selection"
    build_overlay
    local log="${LOGS}/0-baseline.log" rc=0 failed
    printf '   %s\n' "$(gate_command "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${BASELINE_SELECTORS}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the graded selections (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, 0 failed testcases, every graded selection executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree
    assert_no_processes_left "baseline"
    end_control
}

# ------------------------------------------------------------- the controls --

control_1a() {
    begin_control "1a" "item 1 [recorded, unit 4]: -N dropped from the per-file Comet command"
    # Without -N, Comet writes <input>.pep.xml and <input>.pin BESIDE its input
    # -- into the directory the real test proved read-only, so the real search
    # in the test's @BeforeAll is refused by the file system.  The argv test
    # names the missing element.
    inject_and_run "-N dropped" "${SEARCH_COMMANDS}" "${SEL_1}" regex \
        'Error - cannot write to file "[^"]*/read-only inputs/k562_3\.pep\.xml"' \
        '        argv.add(OUTPUT_BASE_OPTION + base);
' \
        ''
    assert_class_setup_failed "the real search from the read-only directory, in its @BeforeAll" "${T_ADAPTER}"
    assert_testcase "the argv test names the command without -N" failed "${T_ARGV}" parameterFileCommands
    assert_log_contains "in the argv test's own words: -N into the run expected, the input alone found" \
        "${DIRTY_LOG}" 'outputs/comet/K562 3, /data/K562 3.mzML]> but was: <[/tools/comet 2026.03.0/bin/comet, -P/projects/p one/runs/20261006T120000Z-run-0001/parameters/comet.params, /data/K562 3.mzML]>'
    assert_no_processes_left "1a"
    restore_pristine "${SEARCH_COMMANDS}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: every spectrum file on ONE Comet command line"
    # The implementation the phase document warns about: one invocation, every
    # input on it, -N kept.  Comet ignores -N when given several inputs and
    # writes beside each input -- into the read-only directory.  It must go red
    # in the real search AND in the one-input-per-command test.
    inject_and_run "every input on one command line" "${SEARCH_COMMANDS}" "${SEL_1}" regex \
        'Error - cannot write to file "[^"]*/read-only inputs/k562_3\.pep\.xml"' \
        '        for (OutputBase input : inputs) {
            commands.add(command(input));
        }' \
        '        ToolCommand single = command(inputs.get(0));
        List<String> every = new ArrayList<>(single.argv());
        for (OutputBase other : inputs.subList(1, inputs.size())) {
            every.add(other.input().toString());
        }
        commands.add(new ToolCommand(every, single.workingDirectory(), single.environment()));'
    assert_class_setup_failed "the real search from the read-only directory, in its @BeforeAll" "${T_ADAPTER}"
    assert_log_matches "the refused command carried -N into the run AND both inputs: Comet ignored -N" \
        "${DIRTY_LOG}" '"-N[^"]*/outputs/comet/k562_3", "[^"]*/read-only inputs/k562_3\.mzML", "[^"]*/read-only inputs/k562_4\.mzML"'
    assert_testcase "one input per command: the test that says so went red" failed "${T_ARGV}" oneInputPerCommand
    assert_log_contains "in that test's own words" "${DIRTY_LOG}" \
        'expected: <[/data/K562 3.mzML]> but was: <[/data/K562 3.mzML, /data/fractions/k562_4.MZXML]>'
    assert_no_processes_left "1b"
    restore_pristine "${SEARCH_COMMANDS}"
    end_control
}

control_2() {
    begin_control "2" "item 2 [NEW]: the -N base outside the run directory, beside the run in runs/"
    # Comet's outputs land in <project>/runs/, which is writable, so the search
    # SUCCEEDS -- only the before/after snapshot of the whole tree can see that
    # something was written outside the run directory.
    inject_and_run "outputs written outside the run directory" "${SEARCH_COMMANDS}" "${SEL_2}" fixed \
        'expected: <[project/runs]> but was: <[project/runs, project/runs/k562_3.pep.xml' \
        '        Path outputBase = run.cometOutputBase(input.base());' \
        '        Path outputBase = run.root().resolveSibling(input.base());'
    assert_testcase "gate 2's snapshot is the method that failed" failed \
        "${T_RUN}" gate2NothingWrittenOutsideTheRunDirectory
    assert_no_processes_left "2"
    restore_pristine "${SEARCH_COMMANDS}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3 [recorded, unit 4]: PIN feature columns compared as sets"
    inject_and_run "feature columns compared as sets" "${PIN_MERGER}" "${SEL_3A}" regex \
        'Expected (org\.cometgui\.tools\.comet\.)?CometOutputException to be thrown, but nothing was thrown' \
        '        if (expected.equals(actual)) {' \
        '        if (new java.util.HashSet<>(expected).equals(new java.util.HashSet<>(actual))) {'
    assert_testcase "two swapped synthetic columns merged" failed "${T_MERGER}" swappedColumns
    assert_testcase "two swapped columns of a REAL PIN merged" failed "${T_ADAPTER}" swappedRealColumns
    assert_testcase "a renamed synthetic column, which differs as a set too, is still refused" passed \
        "${T_MERGER}" renamedColumn
    assert_testcase "a renamed column of a real PIN is still refused" passed "${T_ADAPTER}" renamedRealColumn
    restore_pristine "${PIN_MERGER}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [NEW]: the merge writes a header for every input"
    inject_and_run "a header written per input" "${PIN_MERGER}" "${SEL_2}" fixed \
        'expected: <1> but was: <2>' \
        '                            requireSameFeatures(first, reference, input, reader.header());' \
        '                            requireSameFeatures(first, reference, input, reader.header());
                            writeLine(out, String.join("\t", reader.header().columns()));'
    assert_testcase "gate 3's merged-PIN method is the one that failed" failed \
        "${T_RUN}" gate3MergedPinHasOneHeaderAndTheSummedRows
    assert_testcase "the real run itself, gate 1, stays green: nothing checks the merged header but gate 3" passed \
        "${T_RUN}" gate1TwoFilesFromAReadOnlyDirectory
    assert_no_processes_left "3b"
    restore_pristine "${PIN_MERGER}"
    end_control
}

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 3]: \"no decoys anywhere\" judged only for an empty FASTA"
    inject_and_run "no-decoys rule only for an empty FASTA" "${DECOY_RULE}" "${SEL_4}" fixed \
        'expected exactly one finding: ValidationReport[findings=[]]' \
        '        if (source.get() == DecoySource.FASTA_CONTAINS_DECOYS && !census.hasDecoys()) {' \
        '        if (source.get() == DecoySource.FASTA_CONTAINS_DECOYS && census.records() == 0) {'
    assert_testcase "the rule test of gate 4 failed" failed "${T_DECOY_RULES}" noDecoysAnywhere
    assert_testcase "the REAL gate-4 block failed: the run was not blocked before Comet" failed \
        "${T_DECOY}" gate4NoDecoysAnywhereBlocksBeforeCometStarts
    assert_log_contains "in the real test's own words" "${DIRTY_LOG}" \
        'the decoy configuration did not block the run'
    assert_testcase "gate 5's real block, which the defect does not touch, stays green" passed \
        "${T_DECOY}" gate5DoubleDecoysBlockBeforeCometStarts
    assert_no_processes_left "4a"
    restore_pristine "${DECOY_RULE}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [NEW]: the FASTA decoy census taken but not added to the pre-run facts"
    # The scan still runs; its result is dropped, so the validator judges the
    # decoy configuration with no census -- and nothing blocks before launch.
    inject_and_run "the census not consulted before launch" "${PRE_RUN_CHECKS}" "${SEL_4}" fixed \
        'the decoy configuration did not block the run' \
        '                facts = census(model, file, facts, problems);' \
        '                census(model, file, facts, problems);'
    assert_testcase "gate 4's real block failed" failed \
        "${T_DECOY}" gate4NoDecoysAnywhereBlocksBeforeCometStarts
    assert_testcase "gate 5's real block failed" failed "${T_DECOY}" gate5DoubleDecoysBlockBeforeCometStarts
    assert_testcase "the rule tests, which are fed a census directly, stay green" passed \
        "${T_DECOY_RULES}" noDecoysAnywhere
    assert_no_processes_left "4b"
    restore_pristine "${PRE_RUN_CHECKS}"
    end_control
}

control_5() {
    begin_control "5" "item 5 [NEW]: the double-decoy block applied only to decoy_search = 1"
    inject_and_run "double decoys blocked for decoy_search = 1 only" "${DECOY_RULE}" "${SEL_4}" fixed \
        'the decoy configuration did not block the run' \
        '        } else if (source.get() != DecoySource.FASTA_CONTAINS_DECOYS && census.hasDecoys()) {' \
        '        } else if (source.get() == DecoySource.COMET_INTERNAL_CONCATENATED && census.hasDecoys()) {'
    assert_testcase "the REAL gate-5 test failed (its decoy_search = 2 half)" failed \
        "${T_DECOY}" gate5DoubleDecoysBlockBeforeCometStarts
    assert_testcase "the rule test of decoy_search = 2 failed" failed "${T_DECOY_RULES}" doubleDecoysSeparate
    assert_testcase "the rule test of decoy_search = 1 stays green" passed \
        "${T_DECOY_RULES}" doubleDecoysConcatenated
    assert_testcase "the engine's own validate step (decoy_search = 1) stays green" passed \
        "${T_DECOY}" theRunsOwnValidateStepBlocksBeforeComet
    assert_testcase "gate 4's real block stays green" passed \
        "${T_DECOY}" gate4NoDecoysAnywhereBlocksBeforeCometStarts
    assert_no_processes_left "5"
    restore_pristine "${DECOY_RULE}"
    end_control
}

control_6a() {
    begin_control "6a" "item 6 [recorded, unit 1]: the merge-pin -> run-percolator edge dropped"
    # Without the edge, a change to the Comet parameters no longer reaches
    # Percolator: the steps missing from the preview are EXACTLY run-percolator
    # and everything downstream of it that is not also downstream of merge-pin.
    inject_and_run "a DAG edge dropped" "${STEP_GRAPH}" "${SEL_6}" set \
        're-executed ==> ::RUN_PERCOLATOR,PARSE_PERCOLATOR,FINALISE_RESULTS,LAUNCH_PDV,CONVERT_LIMELIGHT,UPLOAD_LIMELIGHT::' \
        '                            requires(EngineStep.MERGE_PIN, EngineStep.RUN_PERCOLATOR),
' \
        ''
    assert_testcase "scenario (d), changed Comet parameters, is the one that failed" failed \
        "${T_PREVIEW}" cometParametersRerunEverything
    restore_pristine "${STEP_GRAPH}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 1]: run-percolator no longer declaring PERCOLATOR_SETTINGS"
    inject_and_run "Percolator settings not an input of run-percolator" "${ENGINE_STEP}" "${SEL_6}" set \
        're-executed ==> ::RUN_PERCOLATOR,PARSE_PERCOLATOR,FINALISE_RESULTS,FINALISE_PROVENANCE,LAUNCH_PDV,CONVERT_LIMELIGHT,UPLOAD_LIMELIGHT,APPEND_DOWNSTREAM_PROVENANCE::' \
        '            List.of(InputKind.PERCOLATOR_SETTINGS, InputKind.PERCOLATOR_TOOL)),' \
        '            List.of(InputKind.PERCOLATOR_TOOL)),'
    assert_testcase "scenario (b), changed Percolator parameters, failed" failed \
        "${T_PREVIEW}" percolatorParametersRerunPercolatorAndDownstream
    assert_testcase "scenario (d), changed Comet parameters, stays green" passed \
        "${T_PREVIEW}" cometParametersRerunEverything
    restore_pristine "${ENGINE_STEP}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 6]: the Comet-parameter fingerprint taken from the binary's digest"
    inject_and_run "COMET_PARAMETERS fingerprinted from the Comet binary" "${RUN_INPUTS}" "${SEL_2}" set \
        'AssertionFailedError: ::SERIALISE_COMET_PARAMS,RUN_COMET,VALIDATE_COMET_OUTPUTS,MERGE_PIN,FINALISE_PROVENANCE::' \
        '        values.put(InputKind.COMET_PARAMETERS, new InputValue.Bytes(parametersSha256));' \
        '        values.put(InputKind.COMET_PARAMETERS, new InputValue.Bytes(cometSha256));'
    assert_testcase "gate 6 after the REAL run is the method that failed" failed \
        "${T_RUN}" gate6RerunPreviewAfterARealRun
    assert_testcase "the real run itself, gate 1, stays green" passed \
        "${T_RUN}" gate1TwoFilesFromAReadOnlyDirectory
    assert_no_processes_left "6c"
    restore_pristine "${RUN_INPUTS}"
    end_control
}

control_7() {
    begin_control "7" "item 7 [recorded, unit 5]: cancel no longer reaching the running steps"
    # Bounded: the test waits at most 60 s (DEATH_BOUND_SECONDS) for the fake
    # Comet's child to die, then its finally block destroys both; the fake's
    # own watchdog is the second bound.  Then nothing this run started may be
    # alive.
    inject_and_run "cancellation not delivered to running steps" "${RUN_EXECUTION}" "${SEL_7}" fixed \
        'java.util.concurrent.TimeoutException' \
        '                running.getValue().requestCancellation();
' \
        ''
    assert_testcase "the process-tree cancellation test is the one that failed" failed "${T_CANCEL}" "${M_CANCEL}"
    assert_log_matches "it failed at the wait for the killed child, inside its own bound" \
        "${DIRTY_LOG}" 'CancellationTest\.java:[0-9]+\)'
    assert_no_processes_left "7: after the uncancelled fake Comet and its child"
    restore_pristine "${RUN_EXECUTION}"
    end_control
}

control_8a() {
    begin_control "8a" "item 8 [recorded, unit 5]: every re-hash treated as equal to its record"
    inject_and_run "a changed file treated as unchanged" "${REUSE_VALIDATOR}" "${SEL_8A}" fixed \
        'expected: <false> but was: <true>' \
        '                if (now.sha256().equals(was)) {' \
        '                if (now.sha256().equals(now.sha256())) {'
    assert_testcase "the engine's changed-input test failed" failed "${T_RETRY}" "${M_CHANGED}"
    assert_testcase "the REAL changed-spectrum test failed" failed "${T_CHANGED}" gate8ChangedSpectrumFileRefusesReuse
    assert_testcase "the REAL changed-FASTA test failed" failed "${T_CHANGED}" gate8ChangedFastaRefusesReuse
    assert_no_processes_left "8a"
    restore_pristine "${REUSE_VALIDATOR}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [NEW]: revalidation served from the hash cache"
    # Through the cache a changed file is still seen -- its attributes changed
    # -- unless the cache holds a stale entry the attributes do not betray.
    # Only the test that plants one can tell; the plain changed-file test must
    # stay green, which is the measurement that it could not.
    inject_and_run "revalidation served from the cache" "${REUSE_VALIDATOR}" "${SEL_8B}" fixed \
        'revalidation asked the cache for nothing it could serve' \
        '                FileHashes now = hashes.rehash(file.path());' \
        '                FileHashes now = hashes.hash(file.path());'
    assert_testcase "the stale-cache test failed" failed "${T_RETRY}" "${M_STALE}"
    assert_testcase "the plain changed-input test stays green" passed "${T_RETRY}" "${M_CHANGED}"
    restore_pristine "${REUSE_VALIDATOR}"
    end_control
}

control_9a() {
    begin_control "9a" "item 9 [NEW]: the recorded comet.params hash taken from the Comet binary"
    inject_and_run "the archived parameter hash is not the executed file's" "${COMET_WORKFLOW}" "${SEL_2}" fixed \
        'but was: <ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed>' \
        '        settings.put(PARAMS_SHA256_SETTING, written.hashes().sha256());' \
        '        settings.put(PARAMS_SHA256_SETTING, hashes.hash(comet.executable()).sha256());'
    assert_testcase "gate 9 is the method that failed" failed "${T_RUN}" gate9ArgvPerFileAndArchivedParamsHash
    assert_testcase "the real run itself, gate 1, stays green" passed "${T_RUN}" gate1TwoFilesFromAReadOnlyDirectory
    assert_no_processes_left "9a"
    restore_pristine "${COMET_WORKFLOW}"
    end_control
}

control_9b() {
    begin_control "9b" "item 9 [NEW]: every tool record given the first invocation's argument array"
    # The invocations launched are right -- gate 1 asserts the launched argvs
    # and must stay green -- but provenance records ONE argv for both files.
    inject_and_run "two invocations recorded with one argv" "${STEP_CONTEXT}" "${SEL_2}" fixed \
        'the argvs differ only in -N and the input ==> expected: <[2, 3]> but was: <[]>' \
        '                records[done.index()] = record(invocation, done.outcome(), results[done.index()]);' \
        '                records[done.index()] =
                        record(
                                new Invocation(
                                        invocation.stageId(),
                                        invocation.tool(),
                                        list.get(0).command()),
                                done.outcome(),
                                results[done.index()]);'
    assert_testcase "gate 9 is the method that failed" failed "${T_RUN}" gate9ArgvPerFileAndArchivedParamsHash
    assert_testcase "gate 1, which asserts the LAUNCHED argvs, stays green" passed \
        "${T_RUN}" gate1TwoFilesFromAReadOnlyDirectory
    assert_no_processes_left "9b"
    restore_pristine "${STEP_CONTEXT}"
    end_control
}

control_Iv() {
    begin_control "Iv" "index check, VERSION-BLIND [recorded, unit 3]: the readable formats ignore the release"
    inject_and_run "index format judged < 4 for every release" "${INDEX_RULE}" "${SEL_I}" fixed \
        'expected: <INDEX_FORMAT_UNREADABLE> but was: <INDEX_OPTION_UNRECORDED>' \
        '        if (!formats.reads(index.formatVersion())) {' \
        '        if (index.formatVersion() < 4) {'
    assert_testcase "the rule test of the format by release failed" failed "${T_INDEX_RULES}" formatByRelease
    assert_testcase "the REAL one-test-both-releases index test failed" failed "${T_INDEX}" "${M_V4}"
    assert_log_matches "on its 2026.03.0 half: the v4 index was not refused" "${DIRTY_LOG}" \
        'Expected (org\.cometgui\.workflow\.steps\.)?RunBlockedException to be thrown, but nothing was thrown'
    assert_no_processes_left "Iv"
    restore_pristine "${INDEX_RULE}"
    end_control
}

control_Iw() {
    begin_control "Iw" "index check, VERSION-BLIND [NEW]: the first release record's formats for every release"
    inject_and_run "2026.03.0's readable formats for every release" "${INDEX_RULE}" "${SEL_I}" fixed \
        'and Comet 2026.02.2 reads index format v5' \
        '                model.metadata().version(model.version()).orElseThrow().indexFormats();' \
        '                model.metadata().versions().get(0).indexFormats();'
    assert_testcase "the rule test of the format by release failed" failed "${T_INDEX_RULES}" formatByRelease
    assert_testcase "the REAL one-test-both-releases index test failed, on its 2026.02.2 half" failed \
        "${T_INDEX}" "${M_V4}"
    assert_log_lacks "its 2026.03.0 half held: the v4 index was still refused for 2026.03.0" "${DIRTY_LOG}" \
        'RunBlockedException to be thrown, but nothing was thrown'
    assert_no_processes_left "Iw"
    restore_pristine "${INDEX_RULE}"
    end_control
}

assert_log_lacks() {
    local label="$1" log="$2" unexpected="$3"
    if grep -qF -- "${unexpected}" "${log}"; then
        record_fail "${label}: $(rel "${log}") contains '${unexpected}'"
    else
        record_pass "${label}"
    fi
}

control_Ih() {
    begin_control "Ih" "index check [recorded, unit 6]: an existing .idx's header not added to the pre-run facts"
    inject_and_run "the .idx header left out of the pre-run facts" "${PRE_RUN_CHECKS}" "${SEL_I}" regex \
        'Expected (org\.cometgui\.workflow\.steps\.)?RunBlockedException to be thrown, but nothing was thrown' \
        '        PreRunFacts withIndex = facts.withIndex(description);' \
        '        PreRunFacts withIndex = facts;'
    assert_testcase "the REAL index test failed" failed "${T_INDEX}" "${M_V4}"
    assert_testcase "the rule test, fed the header directly, stays green" passed "${T_INDEX_RULES}" formatByRelease
    assert_no_processes_left "Ih"
    restore_pristine "${PRE_RUN_CHECKS}"
    end_control
}

# ---------------------------------------------------------------- control H --

expect_harness_error() {
    local label="$1" why="$2"
    shift 2
    local out rc=0
    out="$( ( "$@" ) 2>&1 )" || rc=$?
    if [ "${rc}" -eq 4 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR' \
        && printf '%s' "${out}" | grep -qF -- "${why}"; then
        record_pass "${label}"
        printf '         %s\n' "$(printf '%s\n' "${out}" | grep 'HARNESS ERROR' | head -1 | cut -c1-200)"
    else
        record_fail "${label}: the harness did NOT refuse (exit ${rc}); it must stop with a HARNESS ERROR naming '${why}'. Output: $(printf '%s' "${out}" | tail -3 | tr '\n' ' ' | cut -c1-300)"
    fi
}

expect_recorded_failure() {
    local label="$1" must="$2"
    shift 2
    local before="${FAILED}"
    DELIBERATE=1
    "$@"
    DELIBERATE=0
    if [ "${FAILED}" -eq $(( before + 1 )) ] && printf '%s' "${FAILURES[-1]}" | grep -qF -- "${must}"; then
        FAILED="${before}"
        unset 'FAILURES[-1]'
        record_pass "${label}"
    else
        record_fail "${label}: HARNESS FAILURE -- it did not record exactly one failure saying '${must}'. Every other control in this script is unreliable."
    fi
}

readonly H_FILE="${REUSE_VALIDATOR}"
readonly H_ANCHOR='                if (now.sha256().equals(was)) {'

h_same_text() {
    replace_once "H1" "${H_FILE}" "${H_ANCHOR}" "${H_ANCHOR}"
    assert_modified "H1" "${H_FILE}"
}

h_no_anchor() {
    replace_once "H2" "${H_FILE}" 'this text is in no source file' 'nor is this'
}

h_source_not_bytecode() {
    replace_once "H3" "${H_FILE}" "${H_ANCHOR}" \
        "${H_ANCHOR} // H3: the source changed and the bytecode cannot"
    assert_modified "H3" "${H_FILE}"
    dirty_run "H3" "${H_FILE}" "${SEL_H}" "${LOGS}/H3-comment-only.log"
}

h_zero_tests() {
    local log="${LOGS}/H4-zero-tests.log" rc=0
    run_mvn "${log}" "${SEL_H4}" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "H4's Maven run itself failed (exit ${rc}); it must build and select nothing."
    verify_classes_ran "${log}" "${SEL_H4}"
}

control_H() {
    begin_control "H" "the harness itself: an injection that did not land, or a run that ran nothing, is never a pass"
    save_pristine "${H_FILE}"

    expect_harness_error "H1 an injection whose replacement equals its anchor is refused" \
        "byte-identical to the pristine copy" h_same_text
    expect_harness_error "H2 an anchor that matches nothing is refused" \
        "the injection anchor is gone" h_no_anchor
    cmp -s "${SANDBOX}/${H_FILE}" "${PRISTINE}/${H_FILE}" \
        || harness_error "control H's refused injections changed ${H_FILE} after all."

    # A comment on the anchor's own line moves no line number, so javac writes
    # the same class: assert_modified accepts it -- the source did change --
    # and the bytecode check must refuse it.  A real Maven run.
    expect_harness_error "H3 an injection that changed the source but not the bytecode is refused" \
        "BYTE-IDENTICAL to the clean baseline after the dirty run" h_source_not_bytecode
    restore_pristine "${H_FILE}"

    # A selection naming a method that does not exist: surefire, told not to
    # fail on it, runs zero tests and Maven exits 0.  A real Maven run.
    expect_harness_error "H4 a selection that runs zero of the tests it names is refused" \
        "this run tested nothing" h_zero_tests

    # H3's run tested the clean code; graded as a red, the grader must record a
    # failure rather than a pass.
    local log="${LOGS}/H3-comment-only.log" failed
    [ -s "${log}" ] || harness_error "H3 left no log, so H5 has nothing to grade."
    failed="$(failed_count "${log}")"
    [ "${failed}" = "0" ] \
        || harness_error "H3's run of the clean code did not pass (${failed:-unknown} failed); H5 needs a green run to grade."
    expect_recorded_failure "H5 a control run with NO defect injected is recorded as a failure, not a pass" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red fixed "H5 (deliberately graded with no defect injected)" "${failed}" "${log}" \
        'expected: <false> but was: <true>'

    log="${LOGS}/H6-wrong-reason.log"
    printf 'FAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red fixed "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <false> but was: <true>'

    # H7: the process check itself.  A copy of sleep(1) run from the sandbox's
    # temporary directory stands for a Comet a test left behind; the check must
    # record a failure naming it, and kill it.
    mkdir -p -- "${TESTTMP}"
    local sleeper="${TESTTMP}/left-behind-by-H7"
    cp -- "$(command -v sleep)" "${sleeper}"
    "${sleeper}" 300 &
    local orphan=$!
    disown "${orphan}"
    expect_recorded_failure "H7 a process started from the sandbox and left alive is recorded as a failure" \
        "outlived the run" assert_no_processes_left "H7 (deliberately left a process alive)"
    sleep 1
    if kill -0 "${orphan}" 2>/dev/null; then
        kill -KILL "${orphan}" 2>/dev/null || true
        record_fail "H7: the process check recorded the left-behind process but did not kill it (pid ${orphan})"
    else
        record_pass "H7 and it was killed: pid ${orphan} is gone"
    fi
    rm -f -- "${sleeper}"

    USED_SELECTORS+=("${SEL_H}")
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1a) control_1a ;; 1b) control_1b ;; 2) control_2 ;;
        3a) control_3a ;; 3b) control_3b ;;
        4a) control_4a ;; 4b) control_4b ;; 5) control_5 ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;;
        7) control_7 ;; 8a) control_8a ;; 8b) control_8b ;;
        9a) control_9a ;; 9b) control_9b ;;
        Iv) control_Iv ;; Iw) control_Iw ;; Ih) control_Ih ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log="${LOGS}/C-clean.log" rc=0 selectors failed
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    printf '   %s\n' "$(gate_command "${selectors}")"
    run_mvn "${log}" "${selectors}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
    else
        verify_classes_ran "${log}" "${selectors}"
        record_pass "the clean sandbox passes again: exit 0, 0 failed testcases, every selection the dirty runs used executed ($(printf '%s\n' "${selectors}" | tr ',' '\n' | grep -c .) selectors)"
    fi
    compare_tree "${log}"
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | sort -u); do
        record_pass "restored compiled form of ${file##*/} is byte-identical to the clean baseline"
    done
    assert_no_processes_left "final clean run"
    end_control
}

main() {
    local self_test_only=0 only=""
    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --self-test) self_test_only=1; shift ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs control ids" 2
                only="${only}${only:+,}$2"
                shift 2
                ;;
            *) usage >&2; die "unknown option: $1" 2 ;;
        esac
    done

    cd -- "${ROOT}"
    command -v git >/dev/null || die "git is not on PATH; the sandbox is a git archive." 3
    command -v python3 >/dev/null || die "python3 is not on PATH." 3
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run bash scripts/build.sh first." 3
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    [ -d "${M2REPO}/org/cometgui" ] || die "$(rel "${M2REPO}") is not populated; run bash scripts/build.sh first." 3
    [ -d "${ROOT}/scratch/phase05/artefacts" ] && [ -d "${ROOT}/scratch/fixture" ] \
        || die "scratch/phase05/artefacts or scratch/fixture is missing. The real-binary gate tests read the pinned Comet binaries and the D-006 inputs from there and FAIL rather than skip without them; refill them as docs/developer/testing.rst describes." 3

    local -a selected=()
    if [ "${self_test_only}" -eq 1 ]; then
        selected=(H)
    elif [ -n "${only}" ]; then
        IFS=',' read -r -a selected <<<"${only}"
    else
        selected=("${ALL_CONTROLS[@]}")
    fi
    local id
    for id in "${selected[@]}"; do
        control_selectors "${id}" >/dev/null
    done

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256 "${LOGS}"/*.marker

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-08 gate item must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  logs         %s\n' "$(rel "${LOGS}")"
    printf '  controls     %s\n' "${selected[*]}"

    local started
    started="$(date +%s)"
    plan_baseline "${selected[@]}"
    build_sandbox
    control_baseline || true
    if [ "${FAILED}" -eq 0 ]; then
        for id in "${selected[@]}"; do
            run_control "${id}"
        done
        final_clean_run
    fi
    [ "$(cometgui_repo_digest)" = "${COMETGUI_REPO_DIGEST}" ] \
        || harness_error "the shared repository's org/cometgui jars changed during this run; this script must never write them."

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' per-control wall clock:\n'
    printf '   %s\n' "${TIMINGS[@]}"
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds (%dm%02ds)\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} workflow-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an unchanged file, a missing anchor, an\n'
        printf '  injection that reached the source but not the bytecode and a selection that\n'
        printf '  ran zero tests as a HARNESS ERROR, and a run with no defect and a red for\n'
        printf '  the wrong reason as a FAILURE -- never as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-08 exit gate items 1 to 9 and the index-compatibility check were proved\n'
    printf '  here, each by a production defect in cometgui-tools, cometgui-params-comet or\n'
    printf '  cometgui-workflow in a git-archive sandbox, proved in the bytecode and graded\n'
    printf '  on the failing assertion'"'"'s own words in the named testcase -- most of them\n'
    printf '  against the real pinned Comet binaries.\n'
    printf '  Two index controls were version-blind (Iv and Iw), each red in the one real\n'
    printf '  test of both releases, on opposite halves.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
