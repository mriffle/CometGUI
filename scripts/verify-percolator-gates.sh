#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-09 Percolator adapter and version-capability
# gates can fail.
#
#   bash scripts/verify-percolator-gates.sh                every control
#   bash scripts/verify-percolator-gates.sh --self-test    control H only
#   bash scripts/verify-percolator-gates.sh --only 2a,5,H  named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 09's exit gate is nine claims
# about Percolator -- a real run whose artefacts parse, XML only with the
# capability and the need, a default resolved from observed capability, the
# skip reason, the zero-decoy PIN refused before launch, the compatible-version
# rerun, the seed of every run, the weights split count, and raw outputs left
# byte-identical and read-only.  Each is checked by tests in
# cometgui-params-percolator, cometgui-results, cometgui-tools,
# cometgui-workflow, cometgui-ui and cometgui-app, several of them against the
# REAL Percolator 3.07.1 and 3.09 binaries on a merged PIN the real Comet
# wrote, and a green test says nothing about whether it would notice the defect
# it exists to catch.  So this script injects, one at a time, a defect each item
# exists to catch -- into PRODUCTION code of a `git archive HEAD` sandbox --
# proves the injection reached the compiled class, runs the narrowest test
# selection that should catch it, requires it red WITH THAT DEFECT'S OWN
# DIAGNOSTIC in the named testcase -- and, where a test can show it, requires a
# sibling testcase the defect does not touch to STAY GREEN -- then restores the
# file and requires the clean sandbox green again on the baseline's bytecode.
#
# It is the sibling of scripts/verify-workflow-gates.sh and takes its shape from
# it: a git-archive sandbox, anchors that must match exactly once, a pristine
# copy per damaged file, bytecode digests, graded failures read from surefire's
# XML, one batched clean re-run, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-09-worklog.rst whose sign-off made that injection and
# saw it red.  [NEW] marks this script's own (unit 9): each is a defect the
# unit-9 brief names for its gate item, or a VERSION-BLIND rule (P9-2), held to
# the same standard and labelled so nobody mistakes it for history.
#
# WHAT IT COVERS (PHASE-09 exit gate items; see phases/PHASE-09-percolator.rst)
#
#   0   baseline: the undamaged sandbox passes every graded selection in one
#       run, and every compiled module is digested
#   P   probe [recorded, unit 1]: the target/decoy judgement of a probe table
#       disabled, so a capability is granted without its observable -- a PSM
#       table with a decoy in it still earns PSM_TSV_OUTPUT
#   1a  item 1 [NEW]: the table reader silently drops the first data row --
#       red against the real 3.07.1/3.06.5/3.09 tables and in the real run
#       (RealPercolatorRunTest's independently counted rows)
#   1b  item 1 [NEW]: run-percolator no longer checks that an artefact the
#       command asked for EXISTS (an empty one is still refused)
#   2a  item 2 [recorded, unit 3]: -X whenever the build is capable, needed or
#       not -- red in the RECORDED argv of the real 3.07.1 run with Limelight
#       off; the real 3.09 runs stay green
#   2b  item 2 [NEW]: -X whenever a stage needs it, capable or not -- the real
#       3.09 chosen with Limelight on is handed -X and fails
#   3v  item 3, VERSION-BLIND [recorded, unit 2]: a stage available iff some
#       candidate's version is below 3.09 -- red only in the inferred-claim
#       tests; the real 3.07.1/3.09 pair stays green
#   3w  item 3, VERSION-BLIND [NEW]: the default chosen by "satisfies Limelight
#       iff version < 3.09" -- red only in the future-version and
#       inferred-claim tests; the real pair stays green
#   3u  item 3, VERSION-BLIND [NEW]: the Percolator section's Limelight status
#       says "can run" whenever a build older than 3.09 is known -- red in the
#       view-model's and the GUI's inferred-claim tests; the real pair green
#   4   item 4 [NEW]: the skip reason no longer names the missing capability --
#       red in the resolver, the view-model, the GUI and the real run's
#       provenance.json
#   5   item 5 [recorded, unit 5]: the pre-launch PIN check bypassed -- the
#       REAL zero-decoy PIN reaches the real Percolator
#   6a  item 6 [NEW]: the derived run's plan no longer provides run-comet, so
#       the real rerun's record no longer names the Comet stage it reuses
#   6b  item 6 [recorded, unit 6]: the "every Comet result step must be reused"
#       refusal disabled
#   6c  item 6 [recorded, unit 6]: the "nothing changed" refusal disabled
#   7   item 7 [NEW]: the effective seed dropped from provenance.json of every
#       run that did not succeed -- the real failed runs go red, the real
#       successful runs (gate 7's own test) stay green
#   8a  item 8 [NEW]: the weights reader reads at most three splits -- the
#       constructed two- and four-split files go red, the real three-split
#       files stay green
#   8b  item 8 [recorded, unit 4]: the same-features-in-every-split check
#       disabled
#   9a  item 9 [recorded, unit 5]: raw outputs never made read-only
#   9b  item 9 [NEW]: parsing writes to the raw file it parsed (the weights
#       reader writes back what it read, with CRLF line ends)
#   H   the harness itself: an unchanged file, an anchor that matches nothing,
#       an injection that reaches the source but not the bytecode, and a
#       selection that runs zero tests must each be reported as a HARNESS
#       ERROR; a green run graded as a red, and a red without its diagnostic,
#       as a recorded failure -- never as a control that bit
#   C   the clean sandbox passes every selection the dirty runs used, on the
#       baseline's own bytecode
#
# VERSION-BLIND, SAID PLAINLY.  On the real Linux pair (3.07.1 with XML_OUTPUT,
# 3.09 without) a rule keyed on "version < 3.09" and a rule keyed on observed
# XML_OUTPUT select the same build and give the same availability, so NO test
# over the real binaries can tell them apart.  Controls 3v, 3w and 3u require
# the real-pair tests to stay GREEN under the version rule -- that is the
# measurement -- and are caught only by the future-version (3.10) and
# inferred-claim (macOS-like 3.07.1) tests.
#
# WHAT IT DOES NOT COVER, said plainly:
#
#   * Windows and macOS: nothing here executes a non-Linux Percolator.
#   * Export (gate item 9's "or export"): there is no export until Phase 10.
#   * Skipping the source merged PIN's re-hash in RerunSource ALONE: the copy
#     made into the derived run is re-hashed against the same record and
#     refused with the same sentence, so that single-site defect is masked by
#     design; item 6 is graded on the plan and on the two recorded refusals.
#   * The probe's version-blind control (unit 1's DECOY_OUTPUT granted only from
#     3.07) and the real probe test PercolatorRealBinaryTest: they cost a 36 s
#     real-binary class in the baseline, every dirty run and the clean run;
#     unit 1's sign-off recorded that injection red, and control P grades the
#     probe on its fake-binary tests.
#   * Live processes: the real Percolator and Comet runs here are synchronous
#     and bounded; this script does not move the test JVMs' temporary
#     directory (cometgui-ui and cometgui-app set cometgui.surefire.extraArgLine
#     themselves for headless JavaFX), so it cannot tell its own processes from
#     anyone else's and does not try.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/percolator-gate-sandbox and damages that.  `git archive HEAD` is
# the COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a
# POM are reported loudly.  scratch/ (the pinned Comet and Percolator binaries,
# the 3.09 wrapper and its Boost libraries, the K562 mzML and the proteome --
# all gitignored) is symlinked in read-only use, and tools/ (the font stack the
# headless JavaFX tests need) likewise.
#
# ONE CLOSED MODULE SET PER CONTROL.  Each control names the top module of the
# narrowest set that holds both its damaged file and its graded tests, and
# every run is `mvn -o -pl TOP -am test`: the damaged module and everything
# downstream of it up to TOP are compiled from the sandbox in the same reactor,
# so a test never sees an undamaged copy from a repository.  Nothing is ever
# installed: the shared _build/m2repo is only read, and its org/cometgui jars
# are digested before and after.
#
# RED IS READ FROM SUREFIRE'S XML, NOT FROM THE EXIT CODE.  Every run passes
# -Dmaven.test.failure.ignore=true, so a non-zero Maven exit is a HARNESS ERROR
# (a build that did not run its tests), and red means: at least one selected
# testcase failed or errored in a fresh surefire report.  Every failing
# testcase's message is copied, unescaped, from the XML into the run's log, and
# the expected diagnostic is matched there.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * the anchor must match EXACTLY ONCE, or the run stops as a harness error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled form of the damaged file -- Foo.class
#     and every Foo$*.class -- must DIFFER from the clean baseline;
#   * and every OTHER file under target/classes of the run's modules must be
#     byte-identical to the baseline -- with ONE line of ONE file removed
#     first: cometgui-app's build-identity.properties is Maven-filtered and
#     carries ${maven.build.timestamp}, rewritten on every run, so its digest
#     is taken without the `cometgui.buildTimestamp=` line, which is required
#     to be there (as scripts/verify-param-ui-gates.sh does).
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
# WHAT IT NEEDS.  A built tree: tools/ (JDK, Maven, the font stack), a
# populated _build/m2repo, and scratch/phase05/artefacts, scratch/fixture and
# scratch/percolator/3.09.  Offline; writes only under _build/.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the SUMMARY
# line.  See docs/developer/testing.rst for the figure measured when it shipped.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, a test that had to stay green went red, or the clean
#      tree did not pass again
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
readonly SANDBOX="${ROOT}/_build/percolator-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/percolator-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/percolator-gate-logs"

# The real-binary fixtures, gitignored; the tests fail rather than skip without
# them.  How to refill each is in docs/developer/testing.rst and in the tests'
# own failure messages.
readonly -a SCRATCH_NEEDED=(
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip"
    "scratch/phase05/artefacts/v2026.02.2__comet.linux.exe"
    "scratch/percolator/3.09/run-percolator-3.09.sh"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML"
    "scratch/fixture/UP000005640_9606.fasta"
)
readonly FONTSTACK="tools/fontstack-bookworm-20260829/root"
# The one normalised file (see the header).
readonly IDENTITY="cometgui-app/org/cometgui/app/config/build-identity.properties"
readonly IDENTITY_LINE='^cometgui\.buildTimestamp='

# The module sets (the top of each `-pl TOP -am`).
readonly PPERC="cometgui-params-percolator"
readonly RESULTS="cometgui-results"
readonly TOOLS="cometgui-tools"
readonly WORKFLOW="cometgui-workflow"
readonly APP="cometgui-app"

# The production files the controls damage.
readonly PR="${PPERC}/src/main/java/org/cometgui/params/percolator/resolution"
readonly RESOLVER="${PR}/PercolatorResolver.java"
readonly MESSAGES="${PR}/ResolutionMessages.java"
readonly RP="${RESULTS}/src/main/java/org/cometgui/results/parser"
readonly TABLE_READER="${RP}/ResultTableReader.java"
readonly WEIGHTS_READER="${RP}/WeightsReader.java"
readonly TP="${TOOLS}/src/main/java/org/cometgui/tools/percolator"
readonly PROBE_ARTEFACTS="${TP}/ProbeArtefacts.java"
readonly COMMANDS="${TP}/PercolatorCommands.java"
readonly WF="${WORKFLOW}/src/main/java/org/cometgui/workflow"
readonly STEPS="${WF}/steps/PercolatorSteps.java"
readonly RERUN="${WF}/steps/PercolatorRerun.java"
readonly RUN_EXECUTION="${WF}/engine/RunExecution.java"
readonly VIEW_MODEL="cometgui-ui/src/main/java/org/cometgui/ui/viewmodel/percolator/PercolatorViewModel.java"

# The graded test classes.
readonly T_RESOLVER="PercolatorResolverTest"
readonly T_CHANGE="ResolutionChangeTest"
readonly T_OUTPUT="RealPercolatorOutputTest"
readonly T_WEIGHTS="WeightsReaderTest"
readonly T_FILTERING="RealOutputFilteringTest"
readonly T_PROBE="PercolatorCapabilityProbeTest"
readonly T_COMMANDS="PercolatorCommandsTest"
readonly T_RUN="RealPercolatorRunTest"
readonly T_STEP="PercolatorStepTest"
readonly T_RERUN="PercolatorRerunTest"
readonly T_REAL_RERUN="RealPercolatorRerunTest"
readonly T_VM="PercolatorViewModelTest"
readonly T_SECTION="PercolatorSectionUiTest"
readonly T_UNAVAILABLE="PercolatorUnavailableUiTest"

# Phase 01's gates have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run: the cheap module sets first.
readonly -a ALL_CONTROLS=(3v 3w 8a 8b 9b P 1a 1b 2a 2b 5 6a 6b 6c 7 9a 3u 4 H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the nine PHASE-09 exit gate items, and the capability
probe, fail on the defects they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/ (JDK, Maven, the font stack), a populated
_build/m2repo, and scratch/phase05/artefacts, scratch/fixture and
scratch/percolator/3.09 (the pinned binaries, the 3.09 wrapper and the D-006
inputs).  It runs Maven offline, damages only a git-archive sandbox under
_build/, and writes only under _build/.

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

# ------------------------------------------------------------- module sets --
#
# reactor_of TOP -- TOP and every cometgui module it depends on, transitively,
# read from the sandbox's own POMs: exactly what `mvn -pl TOP -am` builds.
# Comma-separated, sorted.
declare -A REACTORS=()
reactor_of() {
    local top="$1"
    if [ -z "${REACTORS[${top}]:-}" ]; then
        REACTORS[${top}]="$(python3 - "${SANDBOX}" "${top}" <<'PYTHON'
import re
import sys
from pathlib import Path

root, top = Path(sys.argv[1]), sys.argv[2]
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    deps[module] = sorted(set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text)) - {module})
if top not in deps:
    sys.stderr.write("no module %r in the sandbox\n" % top)
    raise SystemExit(1)
seen, todo = set(), [top]
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
missing = sorted(seen - set(deps))
if missing:
    sys.stderr.write("%s depends on modules the sandbox does not hold: %s\n" % (top, missing))
    raise SystemExit(1)
print(",".join(sorted(seen)))
PYTHON
)" || harness_error "the module set of ${top} cannot be read from the sandbox's POMs."
    fi
    printf '%s' "${REACTORS[${top}]}"
}

COMETGUI_REPO_DIGEST=""
cometgui_repo_digest() {
    ( cd -- "${M2REPO}/org/cometgui" && find . -type f -name '*.jar' -print0 | sort -z \
        | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1 )
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree MODULES -- "sha256  <module>/<path>" for every file under the
# named modules' target/classes: compiled classes AND the resources copied
# there; build-identity.properties digested without its timestamp line, which
# must be there (NO-TIMESTAMP-LINE otherwise, a harness error).
class_tree() {
    local modules="$1" module dir digest path file
    for module in ${modules//,/ }; do
        dir="${SANDBOX}/${module}/target/classes"
        [ -d "${dir}" ] || continue
        ( cd -- "${dir}" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum ) \
            | sed "s#  \./#  ${module}/#"
    done | while read -r digest path; do
        if [ "${path}" = "${IDENTITY}" ]; then
            file="${SANDBOX}/cometgui-app/target/classes/${IDENTITY#cometgui-app/}"
            grep -qE -- "${IDENTITY_LINE}" "${file}" \
                || { printf 'NO-TIMESTAMP-LINE  %s\n' "${path}"; continue; }
            digest="$(grep -vE -- "${IDENTITY_LINE}" "${file}" | sha256sum | cut -d' ' -f1)"
        fi
        printf '%s  %s\n' "${digest}" "${path}"
    done
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"

record_baseline_tree() {
    local modules="$1" module count summary=""
    class_tree "${modules}" >"${BASELINE_TREE}"
    for module in ${modules//,/ }; do
        count="$(grep -c "  ${module}/.*\.class\$" "${BASELINE_TREE}" || true)"
        [ "${count}" -gt 0 ] \
            || harness_error "the baseline run compiled no class in ${module}, so no clean bytecode can be compared against."
        summary="${summary}${summary:+, }${count} ${module#cometgui-}"
    done
    grep -q "  ${IDENTITY}\$" "${BASELINE_TREE}" \
        || harness_error "the baseline's cometgui-app/target/classes holds no build-identity.properties; the one normalised file is not where this script says it is."
    if grep -q '^NO-TIMESTAMP-LINE' "${BASELINE_TREE}"; then
        harness_error "build-identity.properties has no cometgui.buildTimestamp line, so this script's one normalisation is stale. Re-establish it; do not delete the check."
    fi
    record_pass "baseline: every compiled module is digested (${summary} classes; $(grep -vc '\.class$' "${BASELINE_TREE}") resources)"
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

# compare_tree LOG MODULES [INJECTED SOURCE] -- the compiled modules a run
# built, against the baseline.  With an injected source: its compiled form MUST
# differ, and everything else MUST be identical.  Without one: everything must
# be identical.  Anything else is a harness error.  Only the run's own modules
# are compared: a module outside its reactor was neither built nor used by it.
compare_tree() {
    local log="$1" modules="$2" injected="${3:-}"
    [ -s "${BASELINE_TREE}" ] || harness_error "no baseline digest of the compiled modules was recorded."
    local now prefix="" module
    now="$(class_tree "${modules}")"
    if printf '%s\n' "${now}" | grep -q '^NO-TIMESTAMP-LINE'; then
        harness_error "$(rel "${log}"): build-identity.properties lost its timestamp line during the run."
    fi
    [ -n "${injected}" ] && prefix="$(compiled_prefix "${injected}")"
    if [ -n "${prefix}" ]; then
        case ",${modules}," in
            *",${prefix%%/*},"*) ;;
            *) harness_error "$(rel "${log}"): ${injected} is not in the run's module set ${modules}, so the run cannot have compiled it." ;;
        esac
    fi

    local -A before=() after=() all=()
    local digest path
    while read -r digest path; do
        module="${path%%/*}"
        case ",${modules}," in
            *",${module},"*) before["${path}"]="${digest}" ;;
        esac
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
        printf '   bytecode: %d of %d compiled file(s) of the damaged source changed; %d other file(s) in %s identical to the baseline\n' \
            "${changed}" "${count}" "${others}" "${modules}"
    else
        printf '   bytecode: all %d compiled file(s) in %s identical to the clean baseline\n' "${others}" "${modules}"
    fi
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG TOP SELECTORS -- the one test command shape in this script: TOP
# and its upstream modules in one reactor, nothing installed, test failures
# not stopping the reactor.  A non-zero exit therefore means the build itself
# failed.  The exit status and every failing testcase's message (from the XML,
# unescaped) are written INTO the log.
RUN_MODULES=""
run_mvn() {
    local log="$1" top="$2" selectors="$3" rc=0 module
    RUN_MODULES="$(reactor_of "${top}")"
    for module in ${RUN_MODULES//,/ }; do
        rm -rf -- "${SANDBOX}/${module}/target/surefire-reports"
    done
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" \
            -pl "${top}" -am test \
            -Dmaven.test.failure.ignore=true \
            -Dtest="${selectors}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    append_failures "${log}" "${RUN_MODULES}"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s -am test -Dmaven.test.failure.ignore=true -Dtest=%s' "$1" "$2"
}

# append_failures LOG MODULES -- every failed or errored testcase in the fresh
# reports, with its message and the first lines of its trace, unescaped,
# appended to the log under a marker.  The grading reads these lines.
append_failures() {
    local log="$1" modules="$2"
    # shellcheck disable=SC2086
    python3 - "${SANDBOX}" ${modules//,/ } >>"${log}" <<'PYTHON'
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

# test_source_of CLASS MODULES -- the one test source of that name in the
# run's modules.
test_source_of() {
    local class="$1" modules="$2" found module
    local -a dirs=()
    for module in ${modules//,/ }; do
        [ -d "${SANDBOX}/${module}/src/test/java" ] && dirs+=("${module}/src/test/java")
    done
    found="$(cd -- "${SANDBOX}" && find "${dirs[@]}" -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox's ${modules}. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found}"
}

report_of() {
    local class="$1" module
    for module in ${RUN_MODULES//,/ }; do
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
        source="$(test_source_of "${class}" "${RUN_MODULES}")"
        report="$(report_of "${class}")"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${selector} produced no surefire report for $(rel "${log}"). The command named a test that surefire did not execute, so this run tested nothing."
        fi
        count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" | head -1)"
        [ -n "${count}" ] \
            || harness_error "the surefire report for ${class} carries no tests= count; this harness cannot read it and would pass vacuously."
        [ "${count}" -ge 1 ] \
            || harness_error "${selector} executed ${count} tests in $(rel "${log}"). A selection that runs no test cannot prove or disprove a gate, so this run tested nothing."
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
                || harness_error "${class}#${method} did not run in $(rel "${log}"); the selector ${selector} selects less than it names. A selection that runs zero of the tests it names cannot prove or disprove a gate, so this run tested nothing."
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

log_has() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -qF -- "${expected}" "${log}" ;;
        regex) grep -qE -- "${expected}" "${log}" ;;
        *) harness_error "unknown match mode '${mode}'" ;;
    esac
}

log_first_match() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -F -- "${expected}" "${log}" | head -1 ;;
        regex) grep -E -- "${expected}" "${log}" | head -1 ;;
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

# dirty_run LABEL SOURCE TOP SELECTORS LOG -- runs the narrow check on the
# damaged sandbox and proves what it ran.  Leaves the failed-testcase count in
# DIRTY_FAILED.
DIRTY_FAILED=0
dirty_run() {
    local label="$1" source="$2" top="$3" selectors="$4" log="$5" rc=0
    DIRTY_FAILED=0
    run_mvn "${log}" "${top}" "${selectors}" || rc=$?
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "(${label}) the damaged sandbox does not compile -- see $(rel "${log}"). A red that never ran a test is not a result."
    fi
    [ "${rc}" -eq 0 ] \
        || harness_error "(${label}) Maven exited ${rc} although test failures do not stop it here (-Dmaven.test.failure.ignore): the build itself failed -- see $(rel "${log}")."
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException: org\.cometgui' "${log}"; then
        harness_error "(${label}) $(rel "${log}") carries NoClassDefFoundError/ClassNotFoundException for project code: the red is the harness's, not the gate's."
    fi
    compare_tree "${log}" "${RUN_MODULES}" "${source}"
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

# inject_and_run LABEL SOURCE TOP SELECTORS MODE EXPECTED OLD NEW -- damage one
# production file once, run the selection that grades it, require the red with
# its own words.  The caller then makes further assertions and restores.
DIRTY_LOG=""
inject_and_run() {
    local label="$1" source="$2" top="$3" selectors="$4" mode="$5" expected="$6" old="$7" new="$8"
    save_pristine "${source}"
    replace_once "${label}" "${source}" "${old}" "${new}"
    assert_modified "${label}" "${source}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${top}" "${selectors}")"
    dirty_run "${label}" "${source}" "${top}" "${selectors}" "${DIRTY_LOG}"
    grade_red "${mode}" "${label}" "${DIRTY_FAILED}" "${DIRTY_LOG}" "${expected}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"
    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."
    # scratch/ is gitignored: the pinned binaries and the real inputs.  The
    # real tests read it through the repository root they find above their
    # working directory, which is the sandbox.  They copy what they use.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    # tools/ is gitignored too, and the headless JavaFX tests resolve the font
    # stack through the sandbox's own project directory.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    local needed
    for needed in "${SCRATCH_NEEDED[@]}"; do
        [ -e "${SANDBOX}/${needed}" ] \
            || harness_error "the sandbox's scratch/ symlink does not resolve ${needed}. Every real-binary control would fail for the wrong reason."
    done
    [ -d "${SANDBOX}/${FONTSTACK}" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve the font stack. Every GUI control would fail for the wrong reason."
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
# One place for every selector and module set, so the baseline and the final
# clean run select exactly what the dirty runs select.
readonly SEL_RESOLVER="${T_RESOLVER}\$LinuxSet#limelightOn+limelightOff,${T_RESOLVER}\$NoXml#only309+macosInferred,${T_RESOLVER}\$FutureRelease#futureWithXmlWins+futureWithoutXmlSkipped+futureInferredSkipped,${T_CHANGE}#switchedOn+switchedOff+switchedOnButUnavailable"
readonly SEL_WEIGHTS="${T_WEIGHTS}\$SplitCount#twoSplits+fourSplits+oneSplit,${T_WEIGHTS}\$Refusals#splitMismatch+splitOrderMismatch,${T_OUTPUT}#realWeights"
readonly SEL_RAW="${T_FILTERING}#rawFilesUntouched"
readonly SEL_PROBE="${T_PROBE}#aDamagedPsmTable+damagedDecoyTables+aFullyCapableBuild"
readonly SEL_PARSE="${T_OUTPUT}#everyRealTableParses+realWeights,${T_RUN}"
readonly SEL_STEP="${T_STEP}#aRunWritesParsesAndRecords+aMissingArtefactFails+anEmptyArtefactFails+aPinWithoutDecoysIsRefusedBeforeLaunch+aNonZeroExitFails"
readonly SEL_XML="${T_COMMANDS}#fullSetWithXml+capableButNotNeeded+neededButNotCapable,${T_RUN}"
readonly SEL_RUN="${SEL_STEP},${T_RUN}"
readonly SEL_PLAN="${T_RERUN}#planWithIndex+derivedRunShape,${T_REAL_RERUN}"
readonly SEL_REFUSALS="${T_RERUN}#cometResultNotRecorded+nothingChanged+changedMergedPin+rerun"
readonly SEL_UI3="${T_VM}\$Resolution#toggling+noXmlCapableBuild+inferredClaim,${T_UNAVAILABLE}#limelightUnavailable"
readonly SEL_UI4="${T_RESOLVER}\$LinuxSet#limelightOn,${T_VM}\$Resolution#toggling,${T_RUN}#gate4TheSkippedVersionIsRecorded+gate2ThreeNineWithoutLimelight,${T_SECTION}#gate4TheSkipReasonAndTheAdvisories"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 a selection naming a method that does not exist.
readonly SEL_H="${T_CHANGE}#switchedOn+switchedOnButUnavailable"
readonly SEL_H4="${T_CHANGE}#noSuchTestMethodInThisClass"

control_selectors() {
    case "$1" in
        3v|3w) printf '%s' "${SEL_RESOLVER}" ;;
        8a|8b) printf '%s' "${SEL_WEIGHTS}" ;;
        9b) printf '%s' "${SEL_RAW}" ;;
        P) printf '%s' "${SEL_PROBE}" ;;
        1a) printf '%s' "${SEL_PARSE}" ;;
        1b) printf '%s' "${SEL_STEP}" ;;
        2a|2b) printf '%s' "${SEL_XML}" ;;
        5|7|9a) printf '%s' "${SEL_RUN}" ;;
        6a) printf '%s' "${SEL_PLAN}" ;;
        6b|6c) printf '%s' "${SEL_REFUSALS}" ;;
        3u) printf '%s' "${SEL_UI3}" ;;
        4) printf '%s' "${SEL_UI4}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

control_top() {
    case "$1" in
        3v|3w|H) printf '%s' "${PPERC}" ;;
        8a|8b|9b) printf '%s' "${RESULTS}" ;;
        P) printf '%s' "${TOOLS}" ;;
        1a|1b|2a|2b|5|6a|6b|6c|7|9a) printf '%s' "${WORKFLOW}" ;;
        3u|4) printf '%s' "${APP}" ;;
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
    begin_control "0" "baseline: the undamaged sandbox passes every graded selection"
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"
    local log="${LOGS}/0-baseline.log" rc=0 failed
    printf '   %s\n' "$(gate_command "${APP}" "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${APP}" "${BASELINE_SELECTORS}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the graded selections (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, 0 failed testcases, every graded selection executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree "${RUN_MODULES}"
    end_control
}

# ------------------------------------------------------------- the controls --

control_3v() {
    begin_control "3v" "item 3, VERSION-BLIND [recorded, unit 2]: a stage available iff a candidate is older than 3.09"
    # The rule a reader of "3.09 removed XML" would write.  On the real pair it
    # gives the same answer as the capability rule; only the inferred claim (a
    # macOS-like 3.07.1 whose XML_OUTPUT is inferred from bytes) tells them
    # apart.
    inject_and_run "Limelight availability by version number" "${RESOLVER}" "${PPERC}" "${SEL_RESOLVER}" regex \
        'expected: <The default Percolator is still 3\.09 \(Limelight conversion was switched on and needs XML_OUTPUT, but no Percolator here has been observed to have it, so Limelight conversion is unavailable\)\.> but was: <The default Percolator is still 3\.09 \(Limelight conversion was switched on and needs XML_OUTPUT\)\.>' \
        '            if (stage.isSatisfiedBy(observedCapabilities(candidate))) {' \
        '            if (candidate.version().compareTo(org.cometgui.domain.tools.ToolVersion.parse("3.09")) < 0) {'
    assert_testcase "the inferred-claim test went red" failed "${T_RESOLVER}" macosInferred
    assert_testcase "switching Limelight on with only an inferred claim went red" failed \
        "${T_CHANGE}" switchedOnButUnavailable
    assert_testcase "the REAL pair, Limelight on, cannot tell the rules apart (stays green)" passed \
        "${T_RESOLVER}" limelightOn
    assert_testcase "the REAL 3.09 alone cannot tell them apart either (stays green)" passed \
        "${T_RESOLVER}" only309
    restore_pristine "${RESOLVER}"
    end_control
}

control_3w() {
    begin_control "3w" "item 3, VERSION-BLIND [NEW]: the default chosen by 'Limelight needs a version below 3.09'"
    inject_and_run "the default selected by version number" "${RESOLVER}" "${PPERC}" "${SEL_RESOLVER}" regex \
        'expected: <3\.09> but was: <3\.07\.1>' \
        '            if (stage.isSatisfiedBy(observed)) {' \
        '            if (candidate.version().compareTo(org.cometgui.domain.tools.ToolVersion.parse("3.09")) < 0) {'
    assert_testcase "the inferred-claim test went red: the uninstalled, inferred 3.07.1 chosen over 3.09" failed \
        "${T_RESOLVER}" macosInferred
    assert_testcase "an unreleased 3.10 with observed XML_OUTPUT no longer wins" failed \
        "${T_RESOLVER}" futureWithXmlWins
    assert_log_contains "it lost to an older build, which the resolution's own invariant refuses: 3.10 skipped for no missing capability" \
        "${DIRTY_LOG}" 'message: a skipped version must name at least one missing capability'
    assert_testcase "the REAL pair, Limelight on, stays green" passed "${T_RESOLVER}" limelightOn
    assert_testcase "the REAL pair, Limelight off, stays green" passed "${T_RESOLVER}" limelightOff
    restore_pristine "${RESOLVER}"
    end_control
}

control_8a() {
    begin_control "8a" "item 8 [NEW]: the weights reader reads at most three splits"
    inject_and_run "the split count hard-coded at three" "${WEIGHTS_READER}" "${RESULTS}" "${SEL_WEIGHTS}" regex \
        'expected: <4> but was: <3>' \
        '        for (int start = 0; start < dataLines.size(); start += LINES_PER_SPLIT) {' \
        '        for (int start = 0; start < 3 * LINES_PER_SPLIT; start += LINES_PER_SPLIT) {'
    assert_testcase "the constructed two-split file went red" failed "${T_WEIGHTS}" twoSplits
    assert_testcase "the constructed four-split file went red" failed "${T_WEIGHTS}" fourSplits
    assert_testcase "the REAL three-split files stay green: they cannot see a hard-coded 3" passed \
        "${T_OUTPUT}" realWeights
    restore_pristine "${WEIGHTS_READER}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [recorded, unit 4]: the same-features-in-every-split check disabled"
    inject_and_run "splits naming different features accepted" "${WEIGHTS_READER}" "${RESULTS}" "${SEL_WEIGHTS}" regex \
        'Expected (org\.cometgui\.results\.parser\.)?PercolatorOutputException to be thrown, but nothing was thrown' \
        '            } else if (!featureNames.equals(names)) {' \
        '            } else if (featureNames == null) {'
    assert_testcase "a split naming other features is no longer refused" failed "${T_WEIGHTS}" splitMismatch
    assert_testcase "the split count itself stays right" passed "${T_WEIGHTS}" fourSplits
    restore_pristine "${WEIGHTS_READER}"
    end_control
}

control_9b() {
    begin_control "9b" "item 9 [NEW]: parsing writes to the raw file it parsed"
    inject_and_run "the weights reader writes back what it read" "${WEIGHTS_READER}" "${RESULTS}" "${SEL_RAW}" regex \
        '/weights\.txt changed ==> expected: <[0-9a-f]{64}> but was: <[0-9a-f]{64}>' \
        '            return Files.readAllLines(file, StandardCharsets.UTF_8);' \
        '            List<String> read = Files.readAllLines(file, StandardCharsets.UTF_8);
            Files.writeString(file, String.join("\r\n", read) + "\r\n", StandardCharsets.UTF_8);
            return read;'
    assert_testcase "gate 9's byte-identity test is the one that failed" failed "${T_FILTERING}" rawFilesUntouched
    restore_pristine "${WEIGHTS_READER}"
    # The defect wrote into the real fixtures under target/test-classes, which
    # the resources plugin does not copy over a newer file.  Removing the
    # compiled tests makes the next run copy and compile them afresh.
    rm -rf -- "${SANDBOX}/${RESULTS}/target/test-classes"
    printf '   removed %s/target/test-classes, which the defect wrote into\n' "${RESULTS}"
    end_control
}

control_P() {
    begin_control "P" "probe [recorded, unit 1]: a probe table's target/decoy judgement disabled"
    inject_and_run "a capability granted without its observable" "${PROBE_ARTEFACTS}" "${TOOLS}" "${SEL_PROBE}" fixed \
        'a decoy among the targets ==> expected: <' \
        '            if (decoyRow != decoys) {' \
        '            if (decoyRow != decoys && fields.length < 0) {'
    assert_testcase "a PSM table with a decoy among its targets still earned PSM_TSV_OUTPUT" failed \
        "${T_PROBE}" aDamagedPsmTable
    assert_testcase "a decoy table holding a target still earned DECOY_OUTPUT" failed \
        "${T_PROBE}" damagedDecoyTables
    assert_testcase "a fully capable build is still fully capable" passed "${T_PROBE}" aFullyCapableBuild
    restore_pristine "${PROBE_ARTEFACTS}"
    end_control
}

control_1a() {
    begin_control "1a" "item 1 [NEW]: the table reader drops the first data row"
    inject_and_run "a row dropped by the parser" "${TABLE_READER}" "${WORKFLOW}" "${SEL_PARSE}" regex \
        'tables\.percolator-psms\.rows.*expected: <3285> but was: <3284>' \
        '            return new ResultTableReader(file, reader, ResultTableHeader.parse(first, file));' \
        '            ResultTableHeader parsed = ResultTableHeader.parse(first, file);
            readLine(reader, file);
            return new ResultTableReader(file, reader, parsed);'
    assert_testcase "the REAL run: rows parsed against rows counted independently" failed \
        "${T_RUN}" gate1And2WithAnXmlCapableBuild
    assert_testcase "the real 3.07.1, 3.06.5 and 3.09 tables against awk's counts" failed \
        "${T_OUTPUT}" everyRealTableParses
    assert_testcase "the real weights, read by the other reader, stay green" passed "${T_OUTPUT}" realWeights
    restore_pristine "${TABLE_READER}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: run-percolator no longer checks that an artefact exists"
    inject_and_run "a missing artefact let through" "${STEPS}" "${WORKFLOW}" "${SEL_STEP}" regex \
        'message: [^ ]*/outputs/percolator/weights\.txt ==> expected: <true> but was: <false>' \
        '                if (!Files.isRegularFile(file) || Files.size(file) == 0) {' \
        '                if (Files.isRegularFile(file) && Files.size(file) == 0) {'
    assert_testcase "the artefact Percolator did not write is no longer refused by name" failed \
        "${T_STEP}" aMissingArtefactFails
    assert_testcase "an EMPTY artefact is still refused" passed "${T_STEP}" anEmptyArtefactFails
    assert_testcase "a run that writes everything stays green" passed "${T_STEP}" aRunWritesParsesAndRecords
    restore_pristine "${STEPS}"
    end_control
}

control_2a() {
    begin_control "2a" "item 2 [recorded, unit 3]: -X whenever the build is capable, needed or not"
    inject_and_run "pout XML requested without a stage needing it" "${COMMANDS}" "${WORKFLOW}" "${SEL_XML}" regex \
        '-X, [^],]*/outputs/percolator/pout\.xml, .*==> expected: <false> but was: <true>' \
        '        if (request.xmlNeeded()) {' \
        '        if (request.xmlNeeded() || builder.accepts(PercolatorOption.XML_OUTPUT)) {'
    assert_testcase "the REAL 3.07.1 run with Limelight off: -X in the recorded argv" failed \
        "${T_RUN}" anXmlCapableBuildWithoutLimelightWritesNoXml
    assert_testcase "the builder test of a capable build no stage needs XML from" failed \
        "${T_COMMANDS}" capableButNotNeeded
    assert_testcase "the REAL 3.09 run, Limelight off, stays green" passed \
        "${T_RUN}" gate2ThreeNineWithoutLimelight
    assert_testcase "the REAL 3.07.1 run, Limelight on, stays green" passed \
        "${T_RUN}" gate1And2WithAnXmlCapableBuild
    restore_pristine "${COMMANDS}"
    end_control
}

control_2b() {
    begin_control "2b" "item 2 [NEW]: -X whenever a stage needs it, capable or not"
    inject_and_run "pout XML requested from a build without XML_OUTPUT" "${COMMANDS}" "${WORKFLOW}" "${SEL_XML}" regex \
        'RUN_PERCOLATOR=invocation percolator exited with code [1-9][0-9]*; its log is [^}]*/logs/percolator\.log\} ==> expected: <SUCCEEDED> but was: <FAILED>' \
        '            if (accepts(artefact.option())) {' \
        '            if (accepts(artefact.option()) || artefact == PercolatorArtefact.POUT_XML) {'
    assert_testcase "the REAL 3.09, chosen with Limelight on, is handed -X and fails" failed \
        "${T_RUN}" gate2ThreeNineChosenWithLimelight
    assert_testcase "the builder test of a needed but incapable -X" failed "${T_COMMANDS}" neededButNotCapable
    assert_testcase "the REAL 3.09 with Limelight off, which needs no XML, stays green" passed \
        "${T_RUN}" gate2ThreeNineWithoutLimelight
    assert_testcase "the REAL 3.07.1 with Limelight on stays green" passed \
        "${T_RUN}" gate1And2WithAnXmlCapableBuild
    restore_pristine "${COMMANDS}"
    end_control
}

control_5() {
    begin_control "5" "item 5 [recorded, unit 5]: the pre-launch PIN check bypassed"
    # The check is still compiled in, behind a condition that is never true.
    inject_and_run "the merged PIN not checked before launch" "${STEPS}" "${WORKFLOW}" "${SEL_RUN}" regex \
        'expected: <Percolator was not started: the PIN file [^>]*/merged\.pin holds 198 target rows and no decoy row [^>]*> but was: <invocation percolator exited with code [1-9]' \
        '                pin = PercolatorPinCheck.check(run.layout().mergedPinFile(), run.decoys());' \
        '                pin =
                        run.decoys() == null
                                ? PercolatorPinCheck.check(run.layout().mergedPinFile(), run.decoys())
                                : new PinSummary(run.layout().mergedPinFile(), List.of(), 0, 0);'
    assert_testcase "the REAL zero-decoy PIN reached the real Percolator" failed "${T_RUN}" gate5TheRealZeroDecoyPin
    assert_testcase "the constructed zero-decoy PIN was launched" failed \
        "${T_STEP}" aPinWithoutDecoysIsRefusedBeforeLaunch
    restore_pristine "${STEPS}"
    end_control
}

control_6a() {
    begin_control "6a" "item 6 [NEW]: the derived run no longer records run-comet among the Comet results it reuses"
    # Comet is still not executed -- planning it would need an action no
    # derived run has -- but the rerun's plan and record no longer say that the
    # Comet stage it stands on is the original's, untouched.
    inject_and_run "run-comet dropped from the derived run's provided steps" "${RERUN}" "${WORKFLOW}" "${SEL_PLAN}" regex \
        'provided\[serialise-comet-params, build-comet-index, run-comet, validate-comet-outputs, merge-pin\]> but was: <Plan\[[^]]*\] provided\[serialise-comet-params, build-comet-index, validate-comet-outputs, merge-pin\]>' \
        '            if (step.kind() == StepKind.RESULT && !WANTED.contains(step)) {' \
        '            if (step.kind() == StepKind.RESULT && !WANTED.contains(step) && step != EngineStep.RUN_COMET) {'
    assert_testcase "the derived plan, written out" failed "${T_RERUN}" planWithIndex
    assert_testcase "the REAL rerun's second execution record" failed "${T_REAL_RERUN}" gate6TheSecondExecutionRecord
    assert_log_contains "in the real record's own words: the reused Comet steps lack run-comet" "${DIRTY_LOG}" \
        'expected: <serialise-comet-params run-comet validate-comet-outputs merge-pin> but was: <serialise-comet-params validate-comet-outputs merge-pin>'
    restore_pristine "${RERUN}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 6]: 'every Comet result step must be reused' disabled"
    inject_and_run "a rerun from a Comet result that cannot be reused" "${RERUN}" "${WORKFLOW}" "${SEL_REFUSALS}" regex \
        'Expected (org\.cometgui\.workflow\.steps\.)?RerunRefusedException to be thrown, but nothing was thrown' \
        '            if (verdict.decision() != RerunDecision.REUSE) {' \
        '            if (verdict.decision() == null) {'
    assert_testcase "the refusal test went red" failed "${T_RERUN}" cometResultNotRecorded
    assert_testcase "the rerun itself stays green" passed "${T_RERUN}" rerun
    assert_testcase "a changed merged PIN is still refused" passed "${T_RERUN}" changedMergedPin
    restore_pristine "${RERUN}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 6]: the 'nothing changed' refusal disabled"
    inject_and_run "a rerun that repeats the run" "${RERUN}" "${WORKFLOW}" "${SEL_REFUSALS}" regex \
        'Expected (org\.cometgui\.workflow\.steps\.)?RerunRefusedException to be thrown, but nothing was thrown' \
        '        if (against.verdict(EngineStep.RUN_PERCOLATOR).decision() == RerunDecision.REUSE) {' \
        '        if (against.verdict(EngineStep.RUN_PERCOLATOR).decision() == null) {'
    assert_testcase "the refusal test went red" failed "${T_RERUN}" nothingChanged
    assert_testcase "the rerun itself stays green" passed "${T_RERUN}" rerun
    restore_pristine "${RERUN}"
    end_control
}

control_7() {
    begin_control "7" "item 7 [NEW]: the effective seed dropped from provenance of every run that failed"
    inject_and_run "the seed recorded only on success" "${RUN_EXECUTION}" "${WORKFLOW}" "${SEL_RUN}" fixed \
        'expected: <1> but was: <null>' \
        '            Map<String, String> settings = new TreeMap<>(request.settings());' \
        '            Map<String, String> settings = new TreeMap<>(request.settings());
            if (outcome != AttemptOutcome.SUCCEEDED) {
                settings.remove("percolator.seed");
            }'
    assert_testcase "the REAL failed runs (the zero-decoy PIN) lost their seed" failed \
        "${T_RUN}" gate5TheRealZeroDecoyPin
    assert_testcase "the constructed zero-decoy run lost its seed" failed \
        "${T_STEP}" aPinWithoutDecoysIsRefusedBeforeLaunch
    assert_testcase "gate 7's own test of the four SUCCESSFUL real runs cannot see it (stays green)" passed \
        "${T_RUN}" gate7TheSeedOfEveryRun
    restore_pristine "${RUN_EXECUTION}"
    end_control
}

control_9a() {
    begin_control "9a" "item 9 [recorded, unit 5]: raw outputs never made read-only"
    inject_and_run "raw outputs left writable" "${STEPS}" "${WORKFLOW}" "${SEL_RUN}" regex \
        '(decoy-peptides|decoy-psms|peptides|psms)\.tsv is writable|(weights\.txt|pout\.xml) is writable' \
        '            for (Path file : artefacts.values()) {
                makeReadOnly(file);
            }
' \
        ''
    assert_testcase "gate 9 over the four REAL runs" failed "${T_RUN}" gate9RawOutputsAreUnchangedAndReadOnly
    assert_testcase "the real runs themselves, gate 1, stay green" passed "${T_RUN}" gate1And2WithAnXmlCapableBuild
    restore_pristine "${STEPS}"
    end_control
}

control_3u() {
    begin_control "3u" "item 3, VERSION-BLIND [NEW]: the section says Limelight can run when a build older than 3.09 is known"
    inject_and_run "Limelight's status on screen by version number" "${VIEW_MODEL}" "${APP}" "${SEL_UI3}" regex \
        "rerunning Percolator there from this run's merged PIN\\.> but was: <Limelight conversion can run: a Percolator on this computer was observed" \
        '        if (stage.available()) {' \
        '        if (offers.stream().anyMatch(offer -> offer.version().compareTo(org.cometgui.domain.tools.ToolVersion.parse("3.09")) < 0)) {'
    assert_testcase "the view-model's inferred-claim test went red" failed "${T_VM}" inferredClaim
    assert_testcase "the GUI's inferred-claim test went red" failed "${T_UNAVAILABLE}" limelightUnavailable
    assert_testcase "the REAL pair (3.07.1 and 3.09) cannot tell the rules apart (stays green)" passed \
        "${T_VM}" toggling
    assert_testcase "3.09 alone cannot tell them apart either (stays green)" passed "${T_VM}" noXmlCapableBuild
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_4() {
    begin_control "4" "item 4 [NEW]: the skip reason no longer names the missing capability"
    inject_and_run "a skip reason without its capability" "${MESSAGES}" "${APP}" "${SEL_UI4}" regex \
        'rather than 3\.09 \(registered local binary\) because 3\.09 \(registered local binary\) lacks a capability\.' \
        '                clauses.add(name + " lacks " + gap.capability().id() + needs);' \
        '                clauses.add(name + " lacks a capability");'
    assert_testcase "the resolver's real-pair test" failed "${T_RESOLVER}" limelightOn
    assert_testcase "the view-model's skipped text" failed "${T_VM}" toggling
    assert_testcase "on screen: the GUI's skip reason" failed "${T_SECTION}" gate4TheSkipReasonAndTheAdvisories
    assert_testcase "in provenance.json of the REAL run" failed "${T_RUN}" gate4TheSkippedVersionIsRecorded
    assert_log_matches "the real run's provenance reason, in that test's own words" "${DIRTY_LOG}" \
        'message: Using Percolator 3\.07\.1 rather than 3\.09 \(registered local binary\) because 3\.09 \(registered local binary\) lacks a capability\. ==> expected: <true> but was: <false>'
    assert_testcase "a real run with nothing skipped stays green" passed "${T_RUN}" gate2ThreeNineWithoutLimelight
    restore_pristine "${MESSAGES}"
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

readonly H_FILE="${RESOLVER}"
readonly H_ANCHOR='            if (stage.isSatisfiedBy(observedCapabilities(candidate))) {'

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
    dirty_run "H3" "${H_FILE}" "${PPERC}" "${SEL_H}" "${LOGS}/H3-comment-only.log"
}

h_zero_tests() {
    local log="${LOGS}/H4-zero-tests.log" rc=0
    run_mvn "${log}" "${PPERC}" "${SEL_H4}" || rc=$?
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
        grade_red regex "H5 (deliberately graded with no defect injected)" "${failed}" "${log}" \
        'expected: <false> but was: <true>'

    log="${LOGS}/H6-wrong-reason.log"
    printf 'FAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red regex "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <false> but was: <true>'

    USED_SELECTORS+=("${SEL_H}")
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        3v) control_3v ;; 3w) control_3w ;; 3u) control_3u ;;
        8a) control_8a ;; 8b) control_8b ;;
        9a) control_9a ;; 9b) control_9b ;;
        P) control_P ;;
        1a) control_1a ;; 1b) control_1b ;;
        2a) control_2a ;; 2b) control_2b ;;
        4) control_4 ;; 5) control_5 ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;;
        7) control_7 ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log="${LOGS}/C-clean.log" rc=0 selectors failed
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    printf '   %s\n' "$(gate_command "${APP}" "${selectors}")"
    run_mvn "${log}" "${APP}" "${selectors}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
    else
        verify_classes_ran "${log}" "${selectors}"
        record_pass "the clean sandbox passes again: exit 0, 0 failed testcases, every selection the dirty runs used executed ($(printf '%s\n' "${selectors}" | tr ',' '\n' | grep -c .) selectors)"
    fi
    compare_tree "${log}" "${RUN_MODULES}"
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | sort -u); do
        record_pass "restored compiled form of ${file##*/} is byte-identical to the clean baseline"
    done
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
    local needed
    for needed in "${SCRATCH_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] \
            || die "${needed} is missing. The real-binary gate tests read the pinned Comet and Percolator binaries, the 3.09 wrapper and the D-006 inputs from scratch/ and FAIL rather than skip without them; refill scratch/ as docs/developer/testing.rst and docs/developer/version_capabilities.rst describe (the 3.09 wrapper: scratch/percolator/3.09/run-percolator-3.09.sh's own header)." 3
    done
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing; run bash scripts/fetch-fontstack.sh first. Without it every GUI control fails for the wrong reason." 3

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
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-09 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
    [ -z "${COMETGUI_REPO_DIGEST}" ] || [ "$(cometgui_repo_digest)" = "${COMETGUI_REPO_DIGEST}" ] \
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
        die "${FAILED} percolator-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
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
    printf '\n  PHASE-09 exit gate items 1 to 9 and the capability probe were proved here,\n'
    printf '  each by a production defect in cometgui-params-percolator, cometgui-results,\n'
    printf '  cometgui-tools, cometgui-workflow or cometgui-ui in a git-archive sandbox,\n'
    printf '  proved in the bytecode and graded on the failing\n'
    printf '  assertion'"'"'s own words in the named testcase -- items 1, 2, 4, 5, 7 and 9\n'
    printf '  against the real Percolator 3.07.1 and 3.09 on a merged PIN the real Comet\n'
    printf '  wrote, and items 3 and 4 also through the headless GUI.\n'
    printf '  Three controls were version-blind (3v, 3w and 3u): the real 3.07.1/3.09\n'
    printf '  pair stayed green under each, and only the future-version and\n'
    printf '  inferred-claim tests caught them.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
