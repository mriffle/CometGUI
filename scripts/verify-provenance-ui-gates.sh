#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-13 provenance UI and report gates can fail.
#
#   bash scripts/verify-provenance-ui-gates.sh                every control
#   bash scripts/verify-provenance-ui-gates.sh --self-test    control H only
#   bash scripts/verify-provenance-ui-gates.sh --only 1a,d5,H named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 13's exit gate is six claims
# about the provenance record of a real run -- every listed file's digests
# recomputed from disk; every tool's version, path, digests (and capabilities,
# artefact identity and release tag) against the tool cache; every argument
# array present, one Comet per spectrum file, displayable; a failed and a
# cancelled run opening with their partial outputs marked; JSON and RST
# exported from one model, the RST's facts asserted against the JSON; and no
# seeded secret in either export.  The phase also made design obligations it
# must keep: the Windows rename retry and a viewer that holds no descriptor
# (P13-1), a product verification that compares both digests (P13-7's product
# half), and nothing slow on the JavaFX thread (P13-8).  Each is checked by
# tests in cometgui-provenance, cometgui-workflow, cometgui-ui and
# cometgui-app, and a green test says nothing about whether it would notice the
# defect it exists to catch.  So this script injects, one at a time, a defect
# each item exists to catch -- into PRODUCTION code of a `git archive HEAD`
# sandbox -- proves the injection reached the compiled class, runs the
# narrowest test selection that should catch it, requires it red WITH THAT
# DEFECT'S OWN DIAGNOSTIC in the named testcase -- and, where a test can show
# it, requires a sibling testcase the defect does not touch to STAY GREEN --
# then restores the file and requires the clean sandbox green again on the
# baseline's bytecode.
#
# It is the sibling of scripts/verify-limelight-gates.sh (Phase 12) and takes
# its shape and its plumbing from it unchanged where it could: a git-archive
# sandbox, anchors that must match exactly once, a pristine copy per damaged
# file, bytecode digests, graded failures read from surefire's XML, one closed
# module set per control (-pl TOP -am, nothing installed), one batched clean
# re-run, processes left under the sandbox ended after every run, and a
# control H on itself.  What is new here: the number of tests a run executed
# is read TWICE -- from the testcase elements of every surefire XML report and
# from the module summary lines Maven prints -- because Phase 13 unit 1 found
# that two tests with one method name in different @Nested classes make the
# XML report one test fewer than ran; zero in either is a harness error, never
# a pass, and a red is a failed testcase in either.  The two must AGREE: this
# harness found that surefire runs each @Nested class a selection names as a
# test set of its own and writes every set's report under the outer class's
# name, so a selection naming two nested classes of RealProvenanceGateTest
# keeps only the last one's testcases -- a run whose XML holds fewer testcases
# than Maven ran is a harness error.  The baseline and the final clean run are
# therefore split into as few Maven runs as keep every report whole
# (batches_of), and no control's selection names two classes of one test
# class.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-13-worklog.rst whose sign-off made that injection and
# saw it red (O1, O2, O5, K1, K3, K4, M1, M3, M4, N3).  [NEW] marks this
# script's own (unit 7): a defect the unit-7 brief names for its gate item,
# held to the same standard and labelled so nobody mistakes it for history.
#
# WHAT IT COVERS (PHASE-13 exit gate items; see phases/PHASE-13-provenance-ui.rst)
#
#   0   baseline: the undamaged sandbox passes every graded selection in one
#       run, and every compiled module is digested
#   1a  item 1 [recorded, unit 4 M3]: the database recorded as an input by
#       neither hash-inputs nor run-comet -- the REAL run's census
#   1b  item 1 [NEW]: the product's one hasher wrong in the MD5 alone (one byte
#       short) -- red only because the gate's digests are its own (P13-7); the
#       record agrees with the product, the disk does not
#   2a  item 2 [NEW]: Comet's tool record without the capabilities its install
#       probed -- Percolator's record stays green
#   2b  item 2 [NEW]: Percolator's tool record without its artefact identity --
#       Comet's stays green
#   2c  item 2 [NEW]: Comet's tool record without its release tag
#   3a  item 3 [NEW]: one Comet execution of a two-file run dropped from the
#       record -- Percolator's argument array stays green
#   4a  item 4 [NEW]: the engine recording a failed or cancelled stage's
#       outputs as completed -- the REAL failed and cancelled runs
#   4b  item 4 [recorded, unit 3 K4]: the read model warning only of FAILED
#       files, not PARTIAL or CANCELLED ones
#   5a  item 5 [recorded, unit 4 M4]: the RST report printing a log's MD5
#       under "SHA-256" -- red in the facts asserted against the JSON while the
#       byte comparison of export and on-disk report stays green, as it must
#   5b  item 5 [NEW]: Export JSON copying the sealed file's bytes instead of
#       rendering the loaded model -- the byte-identical test stays green
#   6a  item 6 [recorded, unit 4 M1]: the JSON writer skipping redaction for
#       values over 200 characters -- the REAL run's long carrier
#   6b  item 6 [NEW]: the RST writer redacting nothing -- the REAL run's sweep
#   6c  item 6 [recorded, unit 3 K3]: a read-model warning's message not
#       redacted
#   6d  item 6 [recorded, unit 5 N3]: the app's read model built over the
#       pattern rules instead of the session's redactor
#   d1  P13-1 [recorded, unit 1 O2]: AccessDeniedException no longer retried
#   d2  P13-1 [recorded, unit 1 O1]: one rename attempt fewer than the bound
#   d3  P13-1 [recorded, unit 1 O5]: ManifestReader.readFrom keeping a
#       FileChannel open -- the event-log reader's tests stay green
#   d4  P13-7 [recorded, unit 3 K1]: Verify against disk comparing the SHA-256
#       only, so a record whose MD5 alone is wrong verifies as matching
#   d5  P13-8 [NEW]: the view-model opening a record on the interface thread --
#       the view-model's test and the headless GUI's thread test
#   d6  unit 5 [NEW]: Copy SHA-256 of a tool copying its MD5 -- the
#       view-model's test and the headless GUI's clipboard
#   H   the harness itself: an unchanged file, an anchor that matches nothing,
#       an injection that reaches the source but not the bytecode, and a test
#       method renamed in the sandbox so its selection runs zero tests must each
#       be reported as a HARNESS ERROR; a green run graded as a red, and a red
#       without its diagnostic, as a recorded failure -- never as a control that
#       bit
#   C   the clean sandbox passes every selection the dirty runs used, on the
#       baseline's own bytecode
#
# WHAT IT DOES NOT COVER, said plainly:
#
#   * Windows and macOS.  P13-1's retry exists for Windows and is exercised here
#     only through its seam; whether Windows reports a holder with these
#     exception types, and releases within the bound, is unverified (see
#     docs/developer/provenance_schema.rst).  P13-6's macOS and Windows file
#     manager argv are unverified and not graded.  Every real-run and GUI test
#     is @EnabledOnOs(LINUX).
#   * A run killed before it seals, on a real engine: nothing in the product or
#     its test support can crash one honestly (unit 4's sign-off); unit 3's
#     tests read such a directory constructed by hand, and no control here
#     grades them.
#   * The parameter-origin settings (unit 2) and the R-TOOL-07 discrepancy
#     warnings (unit 3b): neither is an exit gate item, and both are graded by
#     the workflow harness's selections only in so far as they are already
#     there.  Their injections (J1-J3, L1-L3) are in the work log.
#   * Unit 5's Replace offer (N1) and Linux xdg-open status (N2): design
#     details of the section, not gate items; recorded in the work log.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/provenance-ui-gate-sandbox and damages that.  `git archive HEAD`
# is the COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or
# a POM are reported loudly.  scratch/ (Comet, Percolator and the converter as
# the loopback mirror serves them, and the K562 mzML and UniProt FASTA -- all
# gitignored, and D-006 forbids committing the K562 ones) is symlinked in, and
# tools/ (the JDK, the X11 and font stacks the headless JavaFX tests need)
# likewise.
#
# ONE CLOSED MODULE SET PER CONTROL.  Each control names the top module of the
# narrowest set that holds both its damaged file and its graded tests, and
# every run is `mvn -o -pl TOP -am test`: nothing is ever installed, and the
# shared _build/m2repo's org/cometgui jars are digested before and after.  So
# an injected cometgui-provenance class reaches cometgui-app's real-run gate
# test through the reactor's target/classes, proved by the bytecode digest
# below, and the coverage checks (bound to verify) never run.  The baseline
# and the clean re-run use the smallest set that holds every selected
# control's set; that it does is checked from the sandbox's own POMs.
#
# RED IS READ FROM THE TEST REPORTS, NOT FROM THE EXIT CODE.  Every run passes
# -Dmaven.test.failure.ignore=true, so a non-zero Maven exit is a HARNESS ERROR
# (a build that did not run its tests), and red means: at least one selected
# testcase failed or errored in a fresh surefire report, or Maven's module
# summary counted a failure or an error.  Every failing testcase's message is
# copied, unescaped, from the XML into the run's log, and the expected
# diagnostic is matched there.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * every anchor must match EXACTLY ONCE before, and be gone after (unless
#     its replacement deliberately keeps it), or the run stops as a harness
#     error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled form of the damaged file -- Foo.class
#     and every Foo$*.class -- must DIFFER from the clean baseline;
#   * and every OTHER file under target/classes of the run's modules must be
#     byte-identical to the baseline, cometgui-app's Maven-filtered
#     build-identity.properties being digested without its timestamp line.
#
# EVERY RUN IS PROVED TO HAVE RUN WHAT IT NAMED.  Each selector's class must
# have a fresh surefire report with tests >= 1, every method it names must
# appear in it, and a bare class selector naming a class with @Nested tests is
# refused (surefire drops them without a word); nested tests are named as
# Outer$Inner#method.  The number of tests each run executed, by both counts,
# is printed, and either count at zero is a harness error.
#
# PROCESSES.  Real Comet, Percolator, the converter and Xvfb run under the
# tests.  After every Maven run this script waits up to 20 s for every process
# whose command line or working directory lies in the sandbox to end, then
# ends what is left (TERM, then KILL) and says so; one it cannot end is a
# HARNESS ERROR.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip: Phase 01's gates, with their own harnesses; an injection would
# otherwise be stopped by a formatter before a test ran.  Nothing this script
# grades is skipped, no test is excluded, and every run is checked to have
# EXECUTED what it named.
#
# WHAT IT NEEDS.  A built tree: tools/ (JDK, Maven, the font stack and the
# project-local X11 stack), a populated _build/m2repo, the scratch/ artefacts
# and fixtures listed in SCRATCH_NEEDED below, the host's gcc (the test support
# compiles Xvfb's xkbcomp path shim with it) and /usr/bin/timeout.  It refuses
# to start, naming what is missing, rather than let a control fail for the
# wrong reason.  Offline; writes only under _build/; contacts no host but this
# one.  It is NOT a stage of scripts/build.sh: it is run through
# scripts/verify-all-gates.sh (as provenanceui) at a phase's exit gate, or
# alone after changing what it grades.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the SUMMARY
# line.  Measured when it shipped (2026-10-14, at 44c4617, the 64-core
# development machine): 96 controls in 1241 s (20 m 41 s).  The baseline
# (251 s) and the final clean run (236 s) are each six Maven runs, because each
# of RealProvenanceGateTest's six nested classes is its own run and rebuilds
# the shared world (three installs from the loopback mirror, a real two-file
# Comet and Percolator run, a conversion, two uploads) in about 20 s; the
# thirteen cometgui-app controls take 33-69 s each (the real run again, or
# the headless GUI's real run); the cometgui-provenance and cometgui-workflow
# controls 7-17 s; control H 14 s.  It is over the 20 minutes its brief aimed
# for by about 40 s: the real world is built 22 times in all and cannot be
# shared between Maven runs.  See docs/developer/testing.rst, "The provenance
# UI harness".
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
#      build, a command ran none of the tests it named, or a process under the
#      sandbox could not be ended.  The run proves nothing and must not be read
#      as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/provenance-ui-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/provenance-ui-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/provenance-ui-gate-logs"

# The gitignored artefacts and fixtures the graded tests read; they fail rather
# than skip without them.  RealProvenanceGateTest's GateWorld serves Comet,
# Percolator and the converter from scratch/phase05/artefacts as a loopback
# mirror and searches the K562 files; ProvenanceSectionUiTest runs Comet and
# Percolator from the same artefacts.
readonly -a SCRATCH_NEEDED=(
    "scratch/phase05/artefacts/v2026.03.0__comet.linux.exe"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"
    "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML"
    "scratch/fixture/UP000005640_9606.fasta"
)
# The project-local display: Xvfb, xkbcomp and the keyboard data from the X11
# closure, the font closure, and the shim the test support compiles.
readonly X11_ROOT="tools/x11-bookworm-20260829/root"
readonly FONTSTACK="tools/fontstack-bookworm-20260829/root"
readonly -a TOOLS_NEEDED=(
    "${X11_ROOT}/usr/bin/Xvfb"
    "${X11_ROOT}/usr/bin/xkbcomp"
    "${X11_ROOT}/usr/share/X11/xkb"
    "${FONTSTACK}/etc/fonts/fonts.conf"
)
readonly SHIM="scripts/feasibility/gui-spike/xkbcomp-path-shim.c"
# The one normalised file (see the header).
readonly IDENTITY="cometgui-app/org/cometgui/app/config/build-identity.properties"
readonly IDENTITY_LINE='^cometgui\.buildTimestamp='

# The module sets (the top of each `-pl TOP -am`), in the order the baseline
# looks for one that holds every selected control's set.
readonly PROV="cometgui-provenance"
readonly WORKFLOW="cometgui-workflow"
readonly UI="cometgui-ui"
readonly APP="cometgui-app"
readonly -a ORDERED_TOPS=("${PROV}" "${WORKFLOW}" "${UI}" "${APP}")

# The production files the controls damage.
readonly PV="${PROV}/src/main/java/org/cometgui/provenance"
readonly ATOMIC="${PV}/io/AtomicDocumentWriter.java"
readonly MANIFEST_READER="${PV}/manifest/ManifestReader.java"
readonly HASHER="${PV}/hashing/StreamingHashService.java"
readonly JSON_WRITER="${PV}/json/JsonWriter.java"
readonly RST_WRITER="${PV}/report/RstWriter.java"
readonly REPORT_WRITER="${PV}/report/ProvenanceReportWriter.java"
readonly WF="${WORKFLOW}/src/main/java/org/cometgui/workflow"
readonly DECLARATIONS="${WF}/steps/RunDeclarations.java"
readonly COMET_WORKFLOW="${WF}/steps/CometWorkflow.java"
readonly STEP_CONTEXT="${WF}/engine/StepContext.java"
readonly RUN_EXECUTION="${WF}/engine/RunExecution.java"
readonly VIEW_BUILDER="${WF}/provenance/ViewBuilder.java"
readonly DISK_VERIFIER="${WF}/provenance/DiskVerifier.java"
readonly LOADED_RUN="${WF}/provenance/LoadedRun.java"
readonly VIEW_MODEL="${UI}/src/main/java/org/cometgui/ui/viewmodel/provenance/ProvenanceViewModel.java"
readonly RUN_WIRING="${APP}/src/main/java/org/cometgui/app/config/RunWiring.java"

# The graded test classes.
readonly T_RETRY="AtomicDocumentWriterRetryTest"
readonly T_DESCRIPTORS="ReadersHoldNoDescriptorTest"
readonly T_VERIFY="DiskVerificationTest"
readonly T_WARNINGS="ProvenanceWarningsTest"
readonly T_STATES="ProvenanceStatesTest"
readonly T_EXPORT="ProvenanceExportTest"
readonly T_SEEDED="SeededSecretTest"
readonly T_VIEW_MODEL="ProvenanceViewModelTest"
readonly T_GATE="RealProvenanceGateTest"
readonly T_SESSION="ProvenanceSessionRedactionTest"
readonly T_GUI="ProvenanceSectionUiTest"

# Phase 01's gates have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run: the cheap module sets first.
readonly -a ALL_CONTROLS=(d1 d2 d3 d4 4b 5b 6c d6 1a 1b 2a 2b 2c 3a 4a 5a 6a 6b 6d d5 H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the six PHASE-13 exit gate items, and the phase's
design obligations P13-1, P13-7 and P13-8, fail on the defects they exist to
catch.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/ (JDK, Maven, the font and X11 stacks), a
populated _build/m2repo, the Comet 2026.03.0, Percolator 3.07.1 and converter
2.8.1 artefacts under scratch/phase05/artefacts, the K562 mzML files and the
UniProt FASTA under scratch/fixture, the host's gcc and /usr/bin/timeout.  It
runs Maven offline, damages only a git-archive sandbox under _build/, writes
only under _build/, and contacts no host but this one.  It is not a stage of
scripts/build.sh.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing, a run that ran nothing, or a process it could not end).
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

# holds BIGGER SMALLER -- every module of SMALLER's set is in BIGGER's.
holds() {
    local bigger smaller module
    bigger="$(reactor_of "$1")"
    smaller="$(reactor_of "$2")"
    for module in ${smaller//,/ }; do
        case ",${bigger}," in
            *",${module},"*) ;;
            *) return 1 ;;
        esac
    done
    return 0
}

# require_held -- the baseline's set must hold every selected control's set,
# or the baseline would not have compiled a module some run compares against.
require_held() {
    local top
    for top in "${SELECTED_TOPS[@]}"; do
        holds "${BASELINE_TOP}" "${top}" \
            || harness_error "${top}'s module set is not held by the baseline's (${BASELINE_TOP}): the baseline cannot cover that run."
    done
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
    case ",${modules}," in
        *",${APP},"*)
            grep -q "  ${IDENTITY}\$" "${BASELINE_TREE}" \
                || harness_error "the baseline's cometgui-app/target/classes holds no build-identity.properties; the one normalised file is not where this script says it is."
            ;;
    esac
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

# ------------------------------------------------------ processes and state --
#
# sandbox_pids -- every live process (zombies excepted: they hold nothing and
# only their parent can reap them) whose command line or working directory lies
# in the sandbox.  Comet, Percolator and the converter run from the tool cache
# the tests install under a module's target/, and Xvfb from the sandbox's tools/
# link, so all are found; this script's own processes are not.
sandbox_pids() {
    python3 - "${SANDBOX}" "$$" <<'PYTHON'
import os
import sys

sandbox, shell = sys.argv[1], sys.argv[2]
mine = {str(os.getpid()), str(os.getppid()), shell}
for pid in os.listdir("/proc"):
    if not pid.isdigit() or pid in mine:
        continue
    try:
        with open("/proc/%s/stat" % pid, encoding="utf-8", errors="replace") as handle:
            state = handle.read().rsplit(")", 1)[1].split()[0]
        if state == "Z":
            continue
        with open("/proc/%s/cmdline" % pid, "rb") as handle:
            command = handle.read().replace(b"\0", b" ").decode("utf-8", "replace")
        try:
            cwd = os.readlink("/proc/%s/cwd" % pid)
        except OSError:
            cwd = ""
    except (OSError, IndexError):
        continue
    if sandbox in command or cwd == sandbox or cwd.startswith(sandbox + "/"):
        print("%s %s" % (pid, command[:160]))
PYTHON
}

# reap LABEL -- after a Maven run: wait for whatever it left under the sandbox
# to end by itself, then end it.  A process that survives KILL is a harness
# error: the next control's result could not be attributed.
LEFT_BEHIND=0
reap() {
    local label="$1" waited=0 left pid
    left="$(sandbox_pids)"
    while [ -n "${left}" ] && [ "${waited}" -lt 20 ]; do
        sleep 1
        waited=$((waited + 1))
        left="$(sandbox_pids)"
    done
    [ -n "${left}" ] || return 0
    LEFT_BEHIND=$((LEFT_BEHIND + $(printf '%s\n' "${left}" | grep -c .)))
    printf '   processes: %d still running under the sandbox %ds after %s; ending them:\n' \
        "$(printf '%s\n' "${left}" | grep -c .)" "${waited}" "${label}"
    printf '%s\n' "${left}" | sed 's/^/     /'
    for pid in $(printf '%s\n' "${left}" | cut -d' ' -f1); do
        kill -TERM "${pid}" 2>/dev/null || true
    done
    sleep 3
    for pid in $(sandbox_pids | cut -d' ' -f1); do
        kill -KILL "${pid}" 2>/dev/null || true
    done
    sleep 1
    left="$(sandbox_pids)"
    [ -z "${left}" ] \
        || harness_error "(${label}) processes under the sandbox survived KILL: ${left}"
}

# The build directories' top-level entries after the baseline: what a control
# may rely on.  Anything a later run leaves beside them -- a test's temporary
# directory its halted JVM or an injected failure did not delete -- is removed
# between controls, so that no control reads another's leftovers.
readonly TARGET_SNAPSHOT="${LOGS}/baseline-target-entries.txt"
snapshot_targets() {
    ( cd -- "${SANDBOX}" && for dir in cometgui-*/target; do
        [ -d "${dir}" ] && find "${dir}" -mindepth 1 -maxdepth 1 -printf '%p\n'
    done ) | sort >"${TARGET_SNAPSHOT}"
}

clean_targets() {
    [ -s "${TARGET_SNAPSHOT}" ] || return 0
    local extra count
    extra="$( ( cd -- "${SANDBOX}" && for dir in cometgui-*/target; do
        [ -d "${dir}" ] && find "${dir}" -mindepth 1 -maxdepth 1 -printf '%p\n'
    done ) | sort | comm -13 "${TARGET_SNAPSHOT}" - )"
    [ -n "${extra}" ] || return 0
    count="$(printf '%s\n' "${extra}" | grep -c .)"
    ( cd -- "${SANDBOX}" && printf '%s\n' "${extra}" | while read -r path; do
        rm -rf -- "${path}"
    done )
    printf '   state: removed %d entr(ies) left in the build directories since the baseline: %s\n' \
        "${count}" "$(printf '%s\n' "${extra}" | head -3 | tr '\n' ' ')"
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG TOP SELECTORS -- the one test command shape in this script: TOP
# and its upstream modules in one reactor, nothing installed, test failures
# not stopping the reactor.  A non-zero exit therefore means the build itself
# failed.  The exit status, the tests executed (both counts) and every failing
# testcase's message (from the XML, unescaped) are written INTO the log.
# Afterwards the processes the run left under the sandbox are ended.
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
    append_console_counts "${log}"
    printf '   %s test(s) executed by the XML reports, %s by Maven'"'"'s module summaries\n' \
        "$(executed_count "${log}")" "$(console_executed "${log}")"
    reap "$(rel "${log}")"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s -am test -Dmaven.test.failure.ignore=true -Dtest=%s' "$1" "$2"
}

# append_failures LOG MODULES -- the number of testcase elements in the fresh
# reports, then every failed or errored testcase, with its message and the
# first lines of its trace, unescaped, appended to the log under a marker.  The
# grading reads these lines.
append_failures() {
    local log="$1" modules="$2"
    # shellcheck disable=SC2086
    python3 - "${SANDBOX}" ${modules//,/ } >>"${log}" <<'PYTHON'
import glob
import sys
import xml.etree.ElementTree as ET

root, modules = sys.argv[1], sys.argv[2:]
executed = 0
failed = []
for module in modules:
    for path in sorted(glob.glob("%s/%s/target/surefire-reports/TEST-*.xml" % (root, module))):
        suite = ET.parse(path).getroot()
        for case in suite.iter("testcase"):
            executed += 1
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is not None:
                    failed.append((suite.get("name"), case.get("name"), kind, node))
print("\n=== %d TESTCASE(S) EXECUTED ===" % executed)
print("=== FAILED TESTCASES FROM SUREFIRE XML ===")
for suite, name, kind, node in failed:
    print("FAILED %s#%s [%s] %s" % (suite, name, kind, node.get("type", "")))
    message = node.get("message") or ""
    for line in message.splitlines()[:40]:
        print("  message: %s" % line)
    for line in (node.text or "").splitlines()[:25]:
        print("  trace: %s" % line)
print("=== %d FAILED TESTCASE(S) ===" % len(failed))
PYTHON
}

# append_console_counts LOG -- the second count.  Maven prints, after each
# module's tests, a summary "Tests run: N, Failures: F, Errors: E, Skipped: S"
# with no "-- in <class>" suffix and no elapsed time; the per-class lines carry
# both.  The summary counts every test that ran, two @Nested methods of one
# name included, which the XML's testcase elements do not always (Phase 13
# unit 1).  Summed over the reactor and appended under a marker.
append_console_counts() {
    local log="$1"
    python3 - "${log}" >>"${log}" <<'PYTHON'
import re
import sys

summary = re.compile(
    r"^\[(?:INFO|WARNING|ERROR)\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)$")
run = bad = modules = 0
with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
    for line in handle:
        match = summary.match(line.rstrip("\n"))
        if match:
            modules += 1
            run += int(match.group(1))
            bad += int(match.group(2)) + int(match.group(3))
print("=== CONSOLE: %d TEST(S) RUN, %d FAILED OR IN ERROR, IN %d MODULE SUMMARY LINE(S) ===" % (run, bad, modules))
PYTHON
}

# failed_count LOG -- the number of failed testcases append_failures counted.
failed_count() {
    sed -n 's/^=== \([0-9][0-9]*\) FAILED TESTCASE(S) ===$/\1/p' "$1" | tail -1
}

executed_count() {
    sed -n 's/^=== \([0-9][0-9]*\) TESTCASE(S) EXECUTED ===$/\1/p' "$1" | tail -1
}

console_executed() {
    sed -n 's/^=== CONSOLE: \([0-9][0-9]*\) TEST(S) RUN, .*/\1/p' "$1" | tail -1
}

console_failed() {
    sed -n 's/^=== CONSOLE: [0-9][0-9]* TEST(S) RUN, \([0-9][0-9]*\) FAILED.*/\1/p' "$1" | tail -1
}

# require_counts LABEL LOG -- both counts present, both at least one.  Zero
# tests run is never a pass and never a red: it is this harness not knowing
# what it ran.
require_counts() {
    local label="$1" log="$2" xml console
    xml="$(executed_count "${log}")"
    console="$(console_executed "${log}")"
    [ -n "${xml}" ] && [ -n "${console}" ] \
        || harness_error "(${label}) $(rel "${log}") carries no test count from the XML (${xml:-none}) or from Maven's module summaries (${console:-none})."
    [ "${xml}" -ge 1 ] && [ "${console}" -ge 1 ] \
        || harness_error "(${label}) $(rel "${log}") ran ${xml} test(s) by the XML reports and ${console} by Maven's module summaries. A run that executes no test cannot prove or disprove a gate, so this run tested nothing."
    # Surefire runs each @Nested class a selection names as a test set of its
    # own and writes each set's report under the OUTER class's name, so a
    # selection naming two nested classes of one test class keeps only the
    # last one's testcases.  Two tests of one name fold the same way.  Either
    # way the outcome of a named test can no longer be read from the XML.
    [ "${xml}" -ge "${console}" ] \
        || harness_error "(${label}) $(rel "${log}"): surefire's XML reports hold ${xml} testcase(s) and Maven counted ${console} run, so a report was overwritten (two @Nested classes of one test class in one selection) or two tests share a name. The outcome of a named test cannot be read; split the selection (see batches_of)."
}

# batches_of SELECTORS -- the selection split into as few runs as keep every
# report whole: no run names two different classes (an outer class and one of
# its @Nested classes, or two of them) of one test class.  One run per line.
batches_of() {
    python3 - "$1" <<'PYTHON'
import sys

batches = []
for selector in (part for part in sys.argv[1].split(",") if part):
    named = selector.split("#")[0]
    outer = named.split("$")[0]
    for classes, items in batches:
        if classes.get(outer, named) == named:
            classes[outer] = named
            items.append(selector)
            break
    else:
        batches.append(({outer: named}, [selector]))
for _, items in batches:
    print(",".join(items))
PYTHON
}

# red_count LOG -- failed testcases by the XML, or by the console where it
# counts more (a failure the XML folded away is still a failure).
red_count() {
    local log="$1" xml console
    xml="$(failed_count "${log}")"
    console="$(console_failed "${log}")"
    [ -n "${xml}" ] || { printf ''; return; }
    if [ -n "${console}" ] && [ "${console}" -gt "${xml}" ]; then
        printf '%s' "${console}"
    else
        printf '%s' "${xml}"
    fi
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
# testcases (see the header), never the exit code.  The expected diagnostic is
# matched only in the failure section the XML wrote into the log, never in
# what the build printed, so a test that merely PRINTS the words cannot pass a
# control.
grade_red() {
    local mode="$1" label="$2" failed="$3" log="$4" expected="$5"
    if [ "${failed}" -eq 0 ]; then
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: $(rel "${log}"))"
        return
    fi
    local section="${log}.failures"
    sed -n '/^=== FAILED TESTCASES FROM SUREFIRE XML ===$/,/^=== [0-9][0-9]* FAILED TESTCASE(S) ===$/p' \
        "${log}" >"${section}"
    if ! log_has "${section}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: $(rel "${log}"))"
        printf '         first failure: %s\n' \
            "$(grep -m1 -A1 '^FAILED ' "${log}" | tr '\n' ' ' | cut -c1-240 || true)"
        return
    fi
    record_pass "${label}: rejected, ${failed} testcase(s) red"
    printf '         %s\n' "$(log_first_match "${section}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
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
    require_counts "${label}" "${log}"
    verify_classes_ran "${log}" "${selectors}"
    DIRTY_FAILED="$(red_count "${log}")"
    [ -n "${DIRTY_FAILED}" ] || harness_error "(${label}) $(rel "${log}") carries no failed-testcase count."
    USED_SELECTORS+=("${selectors}")
}

# ------------------------------------------------- injection and its guards --

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -p -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
}

# replace_once LABEL FILE OLD NEW -- the anchor must match EXACTLY ONCE before;
# afterwards the replacement must be there, and the anchor gone unless the
# replacement itself contains it.
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
text = text.replace(old, new)
if new not in text or (old not in new and old in text):
    sys.stderr.write("the replacement for %r did not land in %s\n" % (label, path))
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text)
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
    clean_targets
}

# inject_and_run LABEL SOURCE TOP SELECTORS MODE EXPECTED OLD NEW [OLD NEW]...
# -- damage one production file at one or more anchors, each matching once,
# run the selection that grades it, require the red with its own words.  The
# caller then makes further assertions and restores.
DIRTY_LOG=""
inject_and_run() {
    local label="$1" source="$2" top="$3" selectors="$4" mode="$5" expected="$6"
    shift 6
    [ "$#" -ge 2 ] && [ $(( $# % 2 )) -eq 0 ] \
        || harness_error "(${label}) an injection needs anchor and replacement pairs."
    save_pristine "${source}"
    local anchors=0
    while [ "$#" -gt 0 ]; do
        anchors=$((anchors + 1))
        replace_once "${label} [anchor ${anchors}]" "${source}" "$1" "$2"
        shift 2
    done
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
    # scratch/ is gitignored: the tool artefacts and the K562 data.  The tests
    # find it through the repository root above their working directory, which
    # is the sandbox.  None of the graded tests writes there.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    # tools/ is gitignored too: the X11 and font closures and the JDK.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    local needed
    for needed in "${SCRATCH_NEEDED[@]}" "${TOOLS_NEEDED[@]}" "${SHIM}"; do
        [ -e "${SANDBOX}/${needed}" ] \
            || harness_error "the sandbox does not resolve ${needed}. Every control grading a test that reads it would fail for the wrong reason."
    done
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
readonly SEL_RETRY="${T_RETRY}\$RefusedThenAccepted#threeAccessDeniedThenSuccess+bareFileSystemExceptionThenSuccess+successOnTheLastAttempt+noRefusalNoPause,${T_RETRY}\$RefusedThroughTheBound#existingTargetSurvives+absentTargetStaysAbsent+sameInstanceEveryTime,${T_RETRY}#nonRetryableIsAttemptedOnce+nonRetryableAfterRefusals"
readonly SEL_DESCRIPTORS="${T_DESCRIPTORS}\$Manifest#afterAValidRead+afterAnUnparsableDocument+afterBytesThatAreNotUtf8+afterAManifestReadThatFailsMidway,${T_DESCRIPTORS}\$EventLog#afterAnIntactLog+afterADamagedLog+afterALogReadThatFailsMidway"
readonly SEL_VERIFY="${T_VERIFY}#allMatch+missingAndDiffers+eitherDigestAloneDiffers"
readonly SEL_PARTIAL_WARNINGS="${T_WARNINGS}#census+eventWarnings,${T_STATES}#failedRun+cancelledRun+unsealedRun"
readonly SEL_EXPORT="${T_EXPORT}#oneObject+byteIdentical+renderedNotCopied+existingTarget"
readonly SEL_SEEDED="${T_SEEDED}#absentEverywhere"
readonly SEL_VM_COPIES="${T_VIEW_MODEL}\$Copies#exactValues+nothingSelected"
readonly SEL_VM_THREAD="${T_VIEW_MODEL}\$RunList#nothingIsReadUntilRefresh+theChosenRunAcrossRefreshes"
readonly SEL_SESSION="${T_SESSION}#theSessionsRedactor"
# RealProvenanceGateTest builds one shared world in its @BeforeAll (three
# installs from the loopback mirror, a real two-file run, a conversion, two
# uploads to the fake, both exports), so every selection pays for it once.
readonly SEL_GATE_FILES="${T_GATE}\$ListedFiles#everyListedFileMatchesTheDisk+everyListedLogMatchesTheDisk+everyOutputOnDiskIsListed"
readonly SEL_GATE_TOOLS="${T_GATE}\$Tools#comet+percolator+converter+threeInstalls"
readonly SEL_GATE_COMMANDS="${T_GATE}\$Commands#onePerSpectrumFile+percolatorReadsTheMergedPin+displayedIsRecorded"
readonly SEL_GATE_FAILED="${T_GATE}\$FailedAndCancelled#failedRun+cancelledRun"
readonly SEL_GATE_EXPORTS="${T_GATE}\$Exports#exportsAreTheRunsOwn+everyFactIsInTheReport"
readonly SEL_GATE_SECRETS="${T_GATE}\$Secrets#noSeededSecretAnywhere+theEnvironmentIsTheProductsOwn+theSearchFindsEveryPlantedForm"
# The headless GUI test runs in @Order: the completed, cancelled and failed runs
# (1-3) are what its thread test (5) lists and opens.
readonly SEL_GUI="${T_GUI}#aCompletedRun+aCancelledRun+aFailedRun+noPortCallOnTheFxThread"
readonly SEL_GUI_COPY="${T_GUI}#aCompletedRun"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 renames a test method the selection names.
readonly SEL_H="${T_DESCRIPTORS}\$Manifest#afterAValidRead+afterAnUnparsableDocument"
readonly H4_TEST_METHOD="afterAValidRead"

control_selectors() {
    case "$1" in
        d1|d2) printf '%s' "${SEL_RETRY}" ;;
        d3) printf '%s' "${SEL_DESCRIPTORS}" ;;
        d4) printf '%s' "${SEL_VERIFY}" ;;
        4b) printf '%s' "${SEL_PARTIAL_WARNINGS}" ;;
        5b) printf '%s' "${SEL_EXPORT}" ;;
        6c) printf '%s' "${SEL_SEEDED}" ;;
        d6) printf '%s' "${SEL_VM_COPIES},${SEL_GUI_COPY}" ;;
        1a|1b) printf '%s' "${SEL_GATE_FILES}" ;;
        2a|2b|2c) printf '%s' "${SEL_GATE_TOOLS}" ;;
        3a) printf '%s' "${SEL_GATE_COMMANDS}" ;;
        4a) printf '%s' "${SEL_GATE_FAILED}" ;;
        5a) printf '%s' "${SEL_GATE_EXPORTS}" ;;
        6a|6b) printf '%s' "${SEL_GATE_SECRETS}" ;;
        6d) printf '%s' "${SEL_SESSION}" ;;
        d5) printf '%s' "${SEL_VM_THREAD},${SEL_GUI}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

control_top() {
    case "$1" in
        d1|d2|d3|H) printf '%s' "${PROV}" ;;
        d4|4b|5b|6c) printf '%s' "${WORKFLOW}" ;;
        1a|1b|2a|2b|2c|3a|4a|5a|6a|6b|6d|d5|d6) printf '%s' "${APP}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

BASELINE_SELECTORS=""
BASELINE_TOP=""
declare -a SELECTED_TOPS=()
# plan_baseline IDS... -- the union of the selections, and the first module
# set in ORDERED_TOPS that holds every selected control's set (read from the
# sandbox's POMs once it exists; see require_held).
plan_baseline() {
    local id
    local -a selectors=()
    SELECTED_TOPS=()
    for id in "$@"; do
        selectors+=("$(control_selectors "${id}")")
        SELECTED_TOPS+=("$(control_top "${id}")")
    done
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
}

choose_baseline_top() {
    local candidate top fits
    for candidate in "${ORDERED_TOPS[@]}"; do
        fits=1
        for top in "${SELECTED_TOPS[@]}"; do
            holds "${candidate}" "${top}" || { fits=0; break; }
        done
        if [ "${fits}" -eq 1 ]; then
            BASELINE_TOP="${candidate}"
            return 0
        fi
    done
    harness_error "no module set in ${ORDERED_TOPS[*]} holds every selected control's set (${SELECTED_TOPS[*]})."
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the undamaged sandbox passes every graded selection"
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"
    choose_baseline_top
    require_held
    local -a batches=()
    mapfile -t batches < <(batches_of "${BASELINE_SELECTORS}")
    local log rc failed batch runs=0 tests=0
    for batch in "${batches[@]}"; do
        runs=$((runs + 1))
        log="${LOGS}/0-baseline-${runs}.log"
        rc=0
        printf '   %s\n' "$(gate_command "${BASELINE_TOP}" "${batch}")"
        run_mvn "${log}" "${BASELINE_TOP}" "${batch}" || rc=$?
        failed="$(red_count "${log}")"
        if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
            record_fail "baseline: the undamaged sandbox does NOT pass the graded selections (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}")). Nothing below could be attributed to an injection."
            printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
            end_control
            return 1
        fi
        require_counts "baseline" "${log}"
        verify_classes_ran "${log}" "${batch}"
        tests=$((tests + $(console_executed "${log}")))
        if [ "${runs}" -eq 1 ]; then
            record_baseline_tree "${RUN_MODULES}"
            snapshot_targets
        else
            compare_tree "${log}" "${RUN_MODULES}"
        fi
    done
    record_pass "baseline: exit 0, 0 failed testcases, ${tests} tests in ${runs} run(s), every graded selection executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    end_control
}

# ------------------------------------------------------------- the controls --

# ---- item 1: every listed file recomputed from disk -------------------------

control_1a() {
    begin_control "1a" "item 1 [recorded, unit 4 M3]: the database recorded as an input by neither hash-inputs nor run-comet"
    inject_and_run "a searched FASTA the record does not list" "${DECLARATIONS}" "${APP}" "${SEL_GATE_FILES}" regex \
        'message: the files the record lists ==> expected: <\[output comet-params comet\.params, input spectrum k562_3\.mzML, input spectrum k562_4\.mzML, input fasta subset\.fasta, ' \
        '        files.add(DeclaredFile.input(databaseRole(run), run.database()));
        return new StepDeclaration(files, List.of());' \
        '        return new StepDeclaration(files, List.of());' \
        '        files.add(
                DeclaredFile.input(
                        run.cacheEntry().isPresent() ? INDEX : databaseRole(run), run.searched()));
' \
        ''
    assert_testcase "the REAL run's listed files, against the disk" failed "${T_GATE}" everyListedFileMatchesTheDisk
    assert_testcase "the archived logs stay green" passed "${T_GATE}" everyListedLogMatchesTheDisk
    restore_pristine "${DECLARATIONS}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: the product's one hasher wrong in the MD5 alone -- the gate's digests are its own (P13-7)"
    # The record and the product's hasher now agree with each other and not
    # with the file.  A gate that compared the record with a value the product
    # computed would stay green here; this one recomputes with its own
    # MessageDigest loop, so it cannot.
    inject_and_run "every recorded MD5 a byte short" "${HASHER}" "${APP}" "${SEL_GATE_FILES}" regex \
        'MD5 recomputed from disk ==> expected: <[0-9a-f]{32}> but was: <[0-9a-f]{32}>' \
        '                md5.update(buffer, 0, read);' \
        '                md5.update(buffer, 0, Math.max(0, read - 1));'
    assert_testcase "every listed input and output, recomputed" failed "${T_GATE}" everyListedFileMatchesTheDisk
    assert_testcase "every archived log, recomputed" failed "${T_GATE}" everyListedLogMatchesTheDisk
    # The defect is in the MD5 alone, so the SHA-256 of every file must still
    # agree: a red SHA-256 line would mean the control broke more than it says.
    if grep -qF 'SHA-256 recomputed from disk ==>' "${DIRTY_LOG}.failures"; then
        record_fail "  ... a SHA-256 line went red too, so this control broke more than the MD5 (log: $(rel "${DIRTY_LOG}"))"
    else
        record_pass "  ... and no file's SHA-256 is red: the defect is the MD5's alone"
    fi
    restore_pristine "${HASHER}"
    end_control
}

# ---- item 2: every tool against the tool cache ------------------------------

control_2a() {
    begin_control "2a" "item 2 [NEW]: Comet's tool record without the capabilities its install probed"
    inject_and_run "a managed Comet recorded with no capabilities" "${COMET_WORKFLOW}" "${APP}" "${SEL_GATE_TOOLS}" regex \
        'comet: the capabilities its install probed ==> expected: <\[COMPLETE_PARAMS_QUERY, PEPXML_OUTPUT, PIN_OUTPUT\]> but was: <\[\]>' \
        '                        capabilityIds(comet.capabilities()),' \
        '                        Set.of(),'
    assert_testcase "Comet's two records, against their install" failed "${T_GATE}" comet
    assert_testcase "Percolator's record stays green" passed "${T_GATE}" percolator
    restore_pristine "${COMET_WORKFLOW}"
    end_control
}

control_2b() {
    begin_control "2b" "item 2 [NEW]: Percolator's tool record without its artefact identity"
    inject_and_run "a managed Percolator recorded with no artefact" "${COMET_WORKFLOW}" "${APP}" "${SEL_GATE_TOOLS}" regex \
        'percolator: the artefact its install came from ==> expected: <https://github\.com/percolator/percolator/releases/download/rel-3-07-01/percolator-noxml-ubuntu-portable\.zip sha256:[0-9a-f]{64}> but was: <null>' \
        '                        selection.offer().artefactIdentity(),' \
        '                        Optional.empty(),'
    assert_testcase "Percolator's record, against its install" failed "${T_GATE}" percolator
    assert_testcase "Comet's records stay green" passed "${T_GATE}" comet
    restore_pristine "${COMET_WORKFLOW}"
    end_control
}

control_2c() {
    begin_control "2c" "item 2 [NEW]: Comet's tool record without its release tag"
    inject_and_run "a managed Comet recorded with no release tag" "${COMET_WORKFLOW}" "${APP}" "${SEL_GATE_TOOLS}" regex \
        'comet: the release tag its install recorded ==> expected: <v2026\.03\.0> but was: <null>' \
        '                        comet.releaseTag(),' \
        '                        Optional.empty(),'
    assert_testcase "Comet's two records, against their install" failed "${T_GATE}" comet
    assert_testcase "Percolator's record stays green" passed "${T_GATE}" percolator
    assert_testcase "the walk of the cache stays green" passed "${T_GATE}" threeInstalls
    restore_pristine "${COMET_WORKFLOW}"
    end_control
}

# ---- item 3: every argument array, one per spectrum file, displayable -------

control_3a() {
    begin_control "3a" "item 3 [NEW]: one Comet execution of a two-file run dropped from the record"
    inject_and_run "the last of several invocations not recorded" "${STEP_CONTEXT}" "${APP}" "${SEL_GATE_COMMANDS}" regex \
        'two spectrum files, two Comet invocations ==> expected: <2> but was: <1>' \
        '                for (ToolRecord record : records) {
                    if (record != null) {' \
        '                for (ToolRecord record : records) {
                    if (record != null
                            && (records.length < 2 || record != records[records.length - 1])) {'
    assert_testcase "one Comet per spectrum file" failed "${T_GATE}" onePerSpectrumFile
    assert_testcase "Percolator's argument array stays green" passed "${T_GATE}" percolatorReadsTheMergedPin
    restore_pristine "${STEP_CONTEXT}"
    end_control
}

# ---- item 4: failed and cancelled runs open, partial outputs marked ---------

control_4a() {
    begin_control "4a" "item 4 [NEW]: the engine recording a stage's outputs as completed when it did not succeed (the REAL failed and cancelled runs)"
    inject_and_run "partial outputs recorded as completed" "${RUN_EXECUTION}" "${APP}" "${SEL_GATE_FAILED}" regex \
        'partial in the record: \[k562_[34] (failed|cancelled)' \
        '                            ? ProvenanceStatus.PARTIAL' \
        '                            ? ProvenanceStatus.COMPLETED'
    assert_testcase "the REAL failed run" failed "${T_GATE}" failedRun
    assert_testcase "the REAL cancelled run" failed "${T_GATE}" cancelledRun
    restore_pristine "${RUN_EXECUTION}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [recorded, unit 3 K4]: the read model warning only of FAILED files, not PARTIAL or CANCELLED"
    inject_and_run "a partial output with no warning" "${VIEW_BUILDER}" "${WORKFLOW}" "${SEL_PARTIAL_WARNINGS}" regex \
        'expected: <Warning\[source=PARTIAL_FILE, subject=[^,]*/outputs/comet/k562_2\.pin, message=the output file [^ ]* \(pin\) is recorded as partial\]> but was: <Warning\[source=VERSION_ADVISORY' \
        '            if (file.status() != ProvenanceStatus.COMPLETED) {' \
        '            if (file.status() == ProvenanceStatus.FAILED) {'
    assert_testcase "the census of every warning source" failed "${T_WARNINGS}" census
    assert_testcase "a failed run's partial outputs" failed "${T_STATES}" failedRun
    assert_testcase "a cancelled run's partial outputs" failed "${T_STATES}" cancelledRun
    assert_testcase "the event warnings stay green" passed "${T_WARNINGS}" eventWarnings
    restore_pristine "${VIEW_BUILDER}"
    end_control
}

# ---- item 5: JSON and RST from one model, the RST against the JSON ----------

control_5a() {
    begin_control "5a" "item 5 [recorded, unit 4 M4]: the RST report printing a log's MD5 under 'SHA-256'"
    inject_and_run "a fact under the wrong label" "${REPORT_WRITER}" "${APP}" "${SEL_GATE_EXPORTS}" regex \
        'Tools/Tool 1, comet ==> expected: <\{.*Stdout SHA-256=\[[0-9a-f]{64}\]' \
        '        rst.fieldValue(stream + " SHA-256", present.hashes().sha256());' \
        '        rst.fieldValue(stream + " SHA-256", present.hashes().md5());'
    assert_testcase "the report's facts, asserted against the JSON" failed "${T_GATE}" everyFactIsInTheReport
    assert_testcase "the export equals the run's own report byte for byte -- one writer made both (stays green)" passed \
        "${T_GATE}" exportsAreTheRunsOwn
    restore_pristine "${REPORT_WRITER}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [NEW]: Export JSON copying the sealed file's bytes instead of rendering the loaded model"
    inject_and_run "an export that is a copy" "${LOADED_RUN}" "${WORKFLOW}" "${SEL_EXPORT}" regex \
        'FAILED org\.cometgui\.workflow\.provenance\.ProvenanceExportTest#renderedNotCopied \[error\] java\.nio\.file\.NoSuchFileException' \
        '        return write(target, replaceExisting, renderer.json(redactor.get(), record));' \
        '        return write(
                target,
                replaceExisting,
                Files.readString(
                        layout.provenanceJsonFile(), java.nio.charset.StandardCharsets.UTF_8));'
    assert_testcase "both exports rendered from the one loaded object" failed "${T_EXPORT}" oneObject
    assert_log_contains "  ... the renderer was never asked for the JSON" "${DIRTY_LOG}.failures" \
        'message:  org.opentest4j.AssertionFailedError: expected: <2> but was: <0>'
    assert_testcase "rendered from memory: it survives the file's removal" failed "${T_EXPORT}" renderedNotCopied
    assert_testcase "a copy is byte-identical to a rendering with the run's redactor (stays green)" passed \
        "${T_EXPORT}" byteIdentical
    restore_pristine "${LOADED_RUN}"
    end_control
}

# ---- item 6: no secret in either export ------------------------------------

control_6a() {
    begin_control "6a" "item 6 [recorded, unit 4 M1]: the JSON writer skipping redaction for values over 200 characters"
    inject_and_run "a long value written as it came" "${JSON_WRITER}" "${APP}" "${SEL_GATE_SECRETS}" regex \
        'a seeded secret was written to a file ==> expected: <\{\}> but was: <\{[^ ]*provenance\.json holds the secret as [^]]*\], seeded as the password of a URL in a long \(> 256 characters\)' \
        '        escapeInto(redactor.redactText(value));' \
        '        escapeInto(value.length() > 200 ? value : redactor.redactText(value));'
    assert_testcase "every file of the project and both exports, swept" failed "${T_GATE}" noSeededSecretAnywhere
    assert_testcase "the environment check stays green" passed "${T_GATE}" theEnvironmentIsTheProductsOwn
    restore_pristine "${JSON_WRITER}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [NEW]: the RST writer redacting nothing"
    inject_and_run "a report written unredacted" "${RST_WRITER}" "${APP}" "${SEL_GATE_SECRETS}" regex \
        'a seeded secret was written to a file ==> expected: <\{\}> but was: <\{[^ ]*/provenance/provenance\.rst holds the secret as ' \
        '        String redacted = redactor.redactText(text);' \
        '        String redacted = text;'
    assert_testcase "every file of the project and both exports, swept" failed "${T_GATE}" noSeededSecretAnywhere
    # The exported report is NOT among the hits, and must not be: Export RST
    # re-renders the record read from provenance.json, which the (undamaged)
    # JSON writer had already redacted.  A JSON among the hits would mean the
    # control broke more than the RST writer.
    if grep -qF 'provenance.json holds the secret' "${DIRTY_LOG}.failures"; then
        record_fail "  ... a provenance.json is among the hits, so this control broke more than the RST writer (log: $(rel "${DIRTY_LOG}"))"
    else
        record_pass "  ... and no JSON is among the hits: the leak is the RST writer's alone"
    fi
    assert_testcase "the environment check stays green" passed "${T_GATE}" theEnvironmentIsTheProductsOwn
    restore_pristine "${RST_WRITER}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 3 K3]: a read-model warning's message not redacted"
    inject_and_run "a warning shown as it came" "${VIEW_BUILDER}" "${WORKFLOW}" "${SEL_SEEDED}" regex \
        'expected: <\[\]> but was: <\[failed reading Zq7Kx-limelight-91xW, the output file [^ ]*/Zq7Kx-limelight-91xW\.pin \(pin\) is recorded as partial, advisory with Zq7Kx-limelight-91xW' \
        '        return new ProvenanceView.Warning(source, display.clean(subject), display.clean(message));' \
        '        return new ProvenanceView.Warning(source, display.clean(subject), message);'
    assert_testcase "the seeded secret absent from every displayed text and export" failed "${T_SEEDED}" absentEverywhere
    restore_pristine "${VIEW_BUILDER}"
    end_control
}

control_6d() {
    begin_control "6d" "item 6 [recorded, unit 5 N3]: the app's read model built over the pattern rules, not the session's redactor"
    inject_and_run "a registry-only secret the section does not know" "${RUN_WIRING}" "${APP}" "${SEL_SESSION}" regex \
        'once admitted, nowhere \([0-9]+ failures\)' \
        '                new ProvenanceReader(secrets::redactor, hashes),' \
        '                new ProvenanceReader(
                        org.cometgui.domain.secrets.SecretRedactor::patternsOnly, hashes),'
    assert_testcase "the section, its copies and both exports, through the production composition" failed \
        "${T_SESSION}" theSessionsRedactor
    restore_pristine "${RUN_WIRING}"
    end_control
}

# ---- the phase's design obligations ----------------------------------------

control_d1() {
    begin_control "d1" "P13-1 [recorded, unit 1 O2]: AccessDeniedException no longer retried"
    inject_and_run "Windows' access-denied refusal reported at once" "${ATOMIC}" "${PROV}" "${SEL_RETRY}" regex \
        'expected: <10> but was: <1>' \
        '        return failure instanceof AccessDeniedException
                || failure.getClass() == FileSystemException.class;' \
        '        return failure.getClass() == FileSystemException.class;'
    assert_testcase "three access-denied refusals, then success" failed "${T_RETRY}" threeAccessDeniedThenSuccess
    assert_testcase "the bound, then the existing target survives" failed "${T_RETRY}" existingTargetSurvives
    assert_testcase "a bare FileSystemException is still retried (stays green)" passed "${T_RETRY}" bareFileSystemExceptionThenSuccess
    restore_pristine "${ATOMIC}"
    end_control
}

control_d2() {
    begin_control "d2" "P13-1 [recorded, unit 1 O1]: one rename attempt fewer than the bound"
    inject_and_run "the tenth attempt never made" "${ATOMIC}" "${PROV}" "${SEL_RETRY}" regex \
        'expected: <refusal #10> but was: <refusal #9>' \
        '                if (!isTransientRefusal(failure) || attempt >= MOVE_ATTEMPTS) {' \
        '                if (!isTransientRefusal(failure) || attempt >= MOVE_ATTEMPTS - 1) {'
    assert_testcase "success on the last attempt" failed "${T_RETRY}" successOnTheLastAttempt
    assert_testcase "no refusal, no pause (stays green)" passed "${T_RETRY}" noRefusalNoPause
    restore_pristine "${ATOMIC}"
    end_control
}

control_d3() {
    begin_control "d3" "P13-1 [recorded, unit 1 O5]: ManifestReader.readFrom keeping a FileChannel open"
    inject_and_run "a viewer that holds provenance.json" "${MANIFEST_READER}" "${PROV}" "${SEL_DESCRIPTORS}" regex \
        'expected: <0> but was: <1>' \
        '        return parse(Files.readString(file, StandardCharsets.UTF_8));' \
        '        java.nio.channels.FileChannel kept = java.nio.channels.FileChannel.open(file);
        return parse(Files.readString(file, StandardCharsets.UTF_8));'
    assert_testcase "after a valid read" failed "${T_DESCRIPTORS}" afterAValidRead
    assert_testcase "the event-log reader holds nothing (stays green)" passed "${T_DESCRIPTORS}" afterAnIntactLog
    restore_pristine "${MANIFEST_READER}"
    end_control
}

control_d4() {
    begin_control "d4" "P13-7 [recorded, unit 3 K1]: Verify against disk comparing the SHA-256 only"
    inject_and_run "a record whose MD5 alone is wrong verified as matching" "${DISK_VERIFIER}" "${WORKFLOW}" "${SEL_VERIFY}" regex \
        'but was: <\[Check\[kind=output, role=pin, [^]]*outcome=MATCHES, recordedMd5=f96b697d7cb7938d525a2f31aaf161d0, recordedSha256=ba7816bf' \
        '                found.md5().equals(target.hashes().md5())
                        && found.sha256().equals(target.hashes().sha256());' \
        '                found.sha256().equals(target.hashes().sha256());'
    assert_testcase "either digest alone differs" failed "${T_VERIFY}" eitherDigestAloneDiffers
    assert_testcase "every file matches (stays green)" passed "${T_VERIFY}" allMatch
    assert_testcase "a file changed in both digests still differs (stays green)" passed "${T_VERIFY}" missingAndDiffers
    restore_pristine "${DISK_VERIFIER}"
    end_control
}

control_d5() {
    begin_control "d5" "P13-8 [NEW]: the view-model opening a record on the interface thread (view-model and headless GUI)"
    inject_and_run "the port read on the JavaFX thread" "${VIEW_MODEL}" "${APP}" "${SEL_VM_THREAD},${SEL_GUI}" regex \
        'a port call on the JavaFX application thread stalls the window' \
        '        clearRecord(OPENING);
        background.execute(' \
        '        clearRecord(OPENING);
        ui.execute('
    assert_testcase "the view-model's chosen run across refreshes" failed "${T_VIEW_MODEL}" theChosenRunAcrossRefreshes
    assert_log_contains "  ... the view-model test's port saw a call outside the background executor" "${DIRTY_LOG}.failures" \
        'the port was called outside the background executor'
    assert_testcase "the headless GUI's thread test over the application's own port" failed "${T_GUI}" noPortCallOnTheFxThread
    assert_testcase "the run list is still read in the background (stays green)" passed "${T_VIEW_MODEL}" nothingIsReadUntilRefresh
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_d6() {
    begin_control "d6" "unit 5 [NEW]: Copy SHA-256 of a tool copying its MD5 (view-model and headless GUI clipboard)"
    inject_and_run "the wrong digest on the clipboard" "${VIEW_MODEL}" "${APP}" "${SEL_VM_COPIES},${SEL_GUI_COPY}" regex \
        'Copy SHA-256 of Comet ==> expected: <[0-9a-f]{64}> but was: <[0-9a-f]{32}>' \
        '                ProvenanceView.Tool::sha256,' \
        '                ProvenanceView.Tool::md5,'
    assert_testcase "the view-model's copies" failed "${T_VIEW_MODEL}" exactValues
    assert_testcase "the headless GUI's real run, the clipboard read back" failed "${T_GUI}" aCompletedRun
    assert_testcase "nothing selected, nothing copied (stays green)" passed "${T_VIEW_MODEL}" nothingSelected
    restore_pristine "${VIEW_MODEL}"
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

readonly H_FILE="${MANIFEST_READER}"
readonly H_ANCHOR='        return parse(Files.readString(file, StandardCharsets.UTF_8));'
readonly H_TEST="${PROV}/src/test/java/org/cometgui/provenance/${T_DESCRIPTORS}.java"

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
    dirty_run "H3" "${H_FILE}" "${PROV}" "${SEL_H}" "${LOGS}/H3-comment-only.log"
}

# H4: the test method a selection names is renamed in the sandbox, as a refactor
# would rename it.  Surefire, told not to fail on a selection that matches
# nothing, runs zero of the tests named and Maven exits 0.  A real Maven run.
h_zero_tests() {
    local log="${LOGS}/H4-zero-tests.log" rc=0
    run_mvn "${log}" "${PROV}" "${SEL_H}" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "H4's Maven run itself failed (exit ${rc}); it must build and select nothing of what it names."
    verify_classes_ran "${log}" "${SEL_H}"
}

# H8: a run whose XML holds fewer testcases than Maven counted -- a report
# overwritten by a second @Nested class of the same test class -- is refused.
h_folded_report() {
    local log="${LOGS}/H8-folded-report.log"
    printf '[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0\n[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0\n\n=== 3 TESTCASE(S) EXECUTED ===\n' >"${log}"
    append_console_counts "${log}"
    require_counts "H8" "${log}"
}

# H7: a run whose reports and summaries say nothing ran must be refused by the
# counting itself, before any grading -- graded on a log made for the purpose.
h_zero_count() {
    local log="${LOGS}/H7-zero-count.log"
    printf '[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0\n\n=== 0 TESTCASE(S) EXECUTED ===\n' >"${log}"
    append_console_counts "${log}"
    require_counts "H7" "${log}"
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

    save_pristine "${H_TEST}"
    replace_once "H4 rename" "${H_TEST}" "        void ${H4_TEST_METHOD}() " "        void ${H4_TEST_METHOD}Renamed() "
    assert_modified "H4 rename" "${H_TEST}"
    expect_harness_error "H4 a selection whose named test was renamed, so that it runs zero of the tests it names, is refused" \
        "this run tested nothing" h_zero_tests
    cp -- "${PRISTINE}/${H_TEST}" "${SANDBOX}/${H_TEST}"
    cmp -s "${SANDBOX}/${H_TEST}" "${PRISTINE}/${H_TEST}" || harness_error "could not restore ${H_TEST}."
    touch -- "${SANDBOX}/${H_TEST}"
    printf '   restored %s\n' "${H_TEST}"

    # H3's run tested the clean code; graded as a red, the grader must record a
    # failure rather than a pass.
    local log="${LOGS}/H3-comment-only.log" failed
    [ -s "${log}" ] || harness_error "H3 left no log, so H5 has nothing to grade."
    failed="$(red_count "${log}")"
    [ "${failed}" = "0" ] \
        || harness_error "H3's run of the clean code did not pass (${failed:-unknown} failed); H5 needs a green run to grade."
    expect_recorded_failure "H5 a control run with NO defect injected is recorded as a failure, not a pass" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red regex "H5 (deliberately graded with no defect injected)" "${failed}" "${log}" \
        'expected: <0> but was: <1>'

    log="${LOGS}/H6-wrong-reason.log"
    printf '=== FAILED TESTCASES FROM SUREFIRE XML ===\nFAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n=== 1 FAILED TESTCASE(S) ===\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red regex "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <0> but was: <1>'

    expect_harness_error "H7 a run whose reports and Maven's summaries count zero tests is refused" \
        "this run tested nothing" h_zero_count
    expect_harness_error "H8 a run whose XML reports hold fewer testcases than Maven ran (a report overwritten) is refused" \
        "so a report was overwritten" h_folded_report

    USED_SELECTORS+=("${SEL_H}")
    clean_targets
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1a) control_1a ;; 1b) control_1b ;;
        2a) control_2a ;; 2b) control_2b ;; 2c) control_2c ;;
        3a) control_3a ;;
        4a) control_4a ;; 4b) control_4b ;;
        5a) control_5a ;; 5b) control_5b ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;; 6d) control_6d ;;
        d1) control_d1 ;; d2) control_d2 ;; d3) control_d3 ;; d4) control_d4 ;; d5) control_d5 ;; d6) control_d6 ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log rc selectors failed batch runs=0 tests=0 green=1
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    local -a batches=()
    mapfile -t batches < <(batches_of "${selectors}")
    for batch in "${batches[@]}"; do
        runs=$((runs + 1))
        log="${LOGS}/C-clean-${runs}.log"
        rc=0
        printf '   %s\n' "$(gate_command "${BASELINE_TOP}" "${batch}")"
        run_mvn "${log}" "${BASELINE_TOP}" "${batch}" || rc=$?
        failed="$(red_count "${log}")"
        if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
            green=0
            record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}"))"
            printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
        else
            require_counts "the clean re-run" "${log}"
            verify_classes_ran "${log}" "${batch}"
            tests=$((tests + $(console_executed "${log}")))
        fi
        compare_tree "${log}" "${RUN_MODULES}"
    done
    [ "${green}" -eq 0 ] \
        || record_pass "the clean sandbox passes again: exit 0, 0 failed testcases, ${tests} tests in ${runs} run(s), every selection the dirty runs used executed ($(printf '%s\n' "${selectors}" | tr ',' '\n' | grep -c .) selectors)"
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | sort -u); do
        record_pass "restored compiled form of ${file##*/} is byte-identical to the clean baseline"
    done
    end_control
}

# preflight -- everything the graded tests need, checked before any Maven run:
# a missing piece would make every control that reads it fail for the wrong
# reason, which is worse than not starting.
preflight() {
    local -a missing=()
    local needed
    command -v git >/dev/null || missing+=("git on PATH (the sandbox is a git archive)")
    command -v python3 >/dev/null || missing+=("python3 on PATH")
    [ -f "${ROOT}/tools/env.sh" ] || missing+=("tools/env.sh (run bash scripts/build.sh first)")
    [ -d "${M2REPO}/org/cometgui" ] || missing+=("$(rel "${M2REPO}") populated (run bash scripts/build.sh first)")
    for needed in "${SCRATCH_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} (see docs/developer/testing.rst, the fixtures the real-run tests read)")
    done
    for needed in "${TOOLS_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} (the project-local display: bash scripts/feasibility/javafx-smoke.sh then bash scripts/feasibility/javafx-headed-xvfb.sh)")
    done
    [ -f "${ROOT}/${SHIM}" ] || missing+=("${SHIM} (Xvfb's xkbcomp path shim source)")
    command -v gcc >/dev/null || missing+=("the host's gcc on PATH (the test support compiles the xkbcomp shim with it)")
    [ -x /usr/bin/timeout ] || missing+=("/usr/bin/timeout (Xvfb runs under it in the test support)")
    command -v timeout >/dev/null || missing+=("timeout on PATH")
    if [ "${#missing[@]}" -gt 0 ]; then
        printf '\n%s: the environment is not ready; missing:\n' "${SCRIPT_NAME}" >&2
        printf '  - %s\n' "${missing[@]}" >&2
        die "refusing to start: the controls that read these would fail for the wrong reason." 3
    fi
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing or damaged; run bash scripts/fetch-fontstack.sh first. Without it every GUI control fails for the wrong reason." 3
    local left
    left="$(sandbox_pids)"
    [ -z "${left}" ] \
        || die "processes from an earlier run are still running in $(rel "${SANDBOX}"); end them first: ${left}" 3
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
    preflight

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.failures "${LOGS}"/*.sha256 "${LOGS}"/*.txt

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-13 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
    reap "the last run"

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' per-control wall clock:\n'
    printf '   %s\n' "${TIMINGS[@]}"
    printf ' processes ended by this script after their run: %d\n' "${LEFT_BEHIND}"
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds (%dm%02ds)\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} provenance-UI-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an unchanged file, a missing anchor, an\n'
        printf '  injection that reached the source but not the bytecode, a renamed test\n'
        printf '  whose selection ran zero tests and a run counted at zero tests as a\n'
        printf '  HARNESS ERROR, and a run with no defect and a red for the wrong reason as\n'
        printf '  a FAILURE -- never as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-13 exit gate items 1 to 6 were proved here, each by a production\n'
    printf '  defect in cometgui-provenance, cometgui-workflow, cometgui-ui or\n'
    printf '  cometgui-app in a git-archive sandbox, proved in the bytecode and graded on\n'
    printf '  the failing assertion'"'"'s own words in the named testcase -- items 1 to 6\n'
    printf '  against the REAL Comet, Percolator and converter run with digests the test\n'
    printf '  computes itself, item 6 also through the production composition -- and\n'
    printf '  the design obligations P13-1, P13-7 and P13-8 likewise, P13-8 also\n'
    printf '  through the headless GUI.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
