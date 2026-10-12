#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-12 Limelight conversion and upload gates can fail.
#
#   bash scripts/verify-limelight-gates.sh                every control
#   bash scripts/verify-limelight-gates.sh --self-test    control H only
#   bash scripts/verify-limelight-gates.sh --only 2a,7b,H named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 12's exit gate is eight claims
# about converting a run to Limelight XML and uploading it -- a real conversion
# structurally validated and checksummed into provenance; a converter's exit 0
# with a missing or empty file failing the stage; the incompatible state
# explained before any attempt, with the action the platform can really take;
# the compatible rerun converting without rerunning Comet, provenance showing
# both Percolator executions; the Limelight q cutoff defaulting to 0.01,
# independent of the display filters; upload only on an explicit action, the
# destination shown first, against the fake in tests; a seeded credential in
# no file the run wrote; and decoy import against a run without decoys
# prevented at configuration time.  Each is checked by tests in
# cometgui-domain, cometgui-params-percolator, cometgui-tools,
# cometgui-workflow, cometgui-ui and cometgui-app, and a green test says
# nothing about whether it would notice the defect it exists to catch.  So
# this script injects, one at a time, a defect each item exists to catch --
# into PRODUCTION code of a `git archive HEAD` sandbox -- proves the injection
# reached the compiled class, runs the narrowest test selection that should
# catch it, requires it red WITH THAT DEFECT'S OWN DIAGNOSTIC in the named
# testcase -- and, where a test can show it, requires a sibling testcase the
# defect does not touch to STAY GREEN -- then restores the file and requires
# the clean sandbox green again on the baseline's bytecode.
#
# It is the sibling of scripts/verify-pdv-gates.sh and takes its shape from it:
# a git-archive sandbox, anchors that must match exactly once, a pristine copy
# per damaged file, bytecode digests, graded failures read from surefire's XML,
# one closed module set per control (-pl TOP -am, nothing installed), one
# batched clean re-run, processes left under the sandbox ended after every
# run, and a control H on itself.  What is new here: the module sets do not
# form one chain (cometgui-domain and cometgui-params-percolator are both below
# cometgui-workflow and neither holds the other), so the baseline is the
# smallest set that holds every selected control's set, checked rather than
# assumed; and one control (6e) points the upload client at this machine's own
# non-loopback address and nothing else, because the defect it injects is the
# one that would let a test reach a real host.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-12-worklog.rst whose sign-off made that injection and
# saw it red (K1-K3, L3, N1-N3, P1, P3, Q1, Q2, R1, R2).  [NEW] marks this
# script's own (unit 9): a defect the unit-9 brief names for its gate item,
# held to the same standard and labelled so nobody mistakes it for history.
#
# WHAT IT COVERS (PHASE-12 exit gate items; see phases/PHASE-12-limelight.rst)
#
#   0   baseline: the undamaged sandbox passes every graded selection in one
#       run, and every compiled module is digested
#   1a  item 1 [recorded, unit 2 K1]: the output judge's root check reduced to
#       "the element has a name"
#   1b  item 1 [NEW]: the output judge's schema validation skipped -- Phase
#       00's two schema-invalid corruptions accepted
#   1c  item 1 [NEW]: the limelight.converted event's output.sha256 recorded
#       as the MD5 -- red against the REAL Comet, Percolator 3.07.1 and
#       converter run, whose own SHA-256 the test computes
#   2a  item 2 [NEW]: the converter adapter trusting exit 0 when the staging
#       file is missing or empty -- both gate-2 tests find a "conversion"
#   2b  item 2 [NEW]: the judge's existence check removed -- red against the
#       REAL converter exiting 0 on an unknown option and writing nothing
#   2c  item 2 [NEW]: the judge's emptiness check removed
#   3a  item 3 [recorded, unit 4 N1]: CompatibilityAction.forStage offering
#       the rerun whenever resolution selects any build (R-LL-02's branch)
#   3b  item 3 [recorded, unit 4 N2]: the action's label built from the
#       literal 3.07.1 -- caught only by the hypothetical 3.08.2 and the
#       unreleased 3.10, while the real Linux set stays green
#   3c  item 3 [recorded, unit 6 Q1]: the conversion controls enabled for any
#       run with a readiness answer, compatible or not -- the pane's test and
#       the headless GUI against the REAL 3.09 run
#   4a  item 4 [recorded, unit 4 N3]: the conversion of a run that is not
#       ready no longer refused -- the REAL 3.09 run's conversion is attempted
#   4b  item 4 [NEW]: the compatible rerun's choice enabling no downstream
#       stage, so the rerun writes no pout XML -- the headless GUI's real rerun
#       and its conversion
#   5a  item 5 [NEW]: the Limelight q cutoff's default 0.05, not 0.01
#   5b  item 5 [NEW]: -q not emitted -- red only where the cutoff is not the
#       converter's own default, against the REAL converter
#   5c  item 5 [NEW]: saving the Limelight cutoff also writes it into the
#       display filters' view state
#   6a  item 6 [NEW]: the workflow's upload confirmation reusable
#   6b  item 6 [recorded, unit 7 R2]: the port's single-send check always
#       passing
#   6c  item 6 [recorded, unit 7 R1]: a changed server no longer withdrawing
#       the destination shown
#   6d  item 6 [NEW]: Upload sending as soon as the destination is shown --
#       the view-model's test and the headless GUI against the fake
#   6e  item 6 [NEW]: the upload client given no proxy -- the test guard's
#       sinkhole no longer sees it; graded ONLY on the test that aims at this
#       machine's own non-loopback address, so nothing leaves the machine
#   6f  item 6 [recorded, unit 5 P1]: plain http accepted to any host
#   7a  item 7 [NEW]: the upload no longer adds the key to the redactor it
#       writes with -- a server message quoting the key reaches the log, the
#       events and the caller
#   7b  item 7 [NEW]: the key written to the upload log, unredacted -- the
#       leak test's walk of every file finds it
#   7c  item 7 [NEW]: a credential admitted without being registered with the
#       session's redactor
#   7d  item 7 [NEW]: the key field not emptied once its key is taken -- the
#       pane's test and the headless GUI's gate-7 test of every window text
#   8a  item 8 [recorded, unit 3 L3]: saveSettings' decoy refusal unreachable
#   8b  item 8 [recorded, unit 6 Q2]: decoy import enabled whenever the
#       settings are
#   8c  item 8 [NEW]: a pout XML without decoy PSMs judged able to import
#       decoys -- the request's rule, and with it the workflow's
#   8d  item 8 [recorded, unit 2 K3]: the "decoys present but not imported"
#       refusal unreachable (the rule's other direction)
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
#   * Windows and macOS: nothing here has run anywhere but Linux, and every
#     real-tool and GUI test is @EnabledOnOs(LINUX).
#   * A real Limelight server: never contacted, by design (P12-11).  Item 6's
#     "fake or sandbox endpoint in tests" is graded by 6e, which removes the
#     one thing that routes a test's traffic into the guard and aims ONLY at
#     this machine's own address.  The tests that aim at TEST-NET or at a
#     .invalid name are NOT run under that injection: with the guard bypassed
#     they would send a packet or a DNS query off the machine.
#   * Two recorded grades are left to cheaper selections, because the GUI
#     test costs its whole real run: Q2 in LimelightConversionUiTest (8b is
#     graded on the pane's test) and R1 in the GUI (unit 7 saw it stay green
#     there, as the GUI test changes the project, not the server).
#   * Item 1's GUI test (LimelightConversionUiTest.gate1) is not graded here;
#     item 1 is graded on the judge and on the REAL workflow conversion.
#   * Two defensive layers no test can make fail from outside, recorded in the
#     work log (L2, P2), are not controls here; 7b grades P2's layer together
#     with a line that carries the key, which is the defect it exists for.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/limelight-gate-sandbox and damages that.  `git archive HEAD` is
# the COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a
# POM are reported loudly.  scratch/ (the converter, Comet and Percolator
# artefacts, the K562 mzML, FASTA, pepXML and Percolator outputs -- all
# gitignored, and D-006 forbids committing the K562 ones) is symlinked in, and
# tools/ (the X11 and font stacks the headless JavaFX tests need) likewise.
#
# ONE CLOSED MODULE SET PER CONTROL.  Each control names the top module of the
# narrowest set that holds both its damaged file and its graded tests, and
# every run is `mvn -o -pl TOP -am test`: nothing is ever installed, and the
# shared _build/m2repo's org/cometgui jars are digested before and after.  So
# an injected cometgui-ui class reaches cometgui-app's GUI tests through the
# reactor's target/classes, proved by the bytecode digest below, and the
# coverage checks (bound to verify) never run: no -Dcometgui.coverage.*.skip
# is needed, and none is passed.  The baseline and the clean re-run use the
# smallest set that holds every selected control's set; that it does is
# checked from the sandbox's own POMs, not assumed.
#
# RED IS READ FROM SUREFIRE'S XML, NOT FROM THE EXIT CODE.  Every run passes
# -Dmaven.test.failure.ignore=true, so a non-zero Maven exit is a HARNESS ERROR
# (a build that did not run its tests), and red means: at least one selected
# testcase failed or errored in a fresh surefire report.  Every failing
# testcase's message is copied, unescaped, from the XML into the run's log, and
# the expected diagnostic is matched there.
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
# Outer$Inner#method.  The number of tests each run executed is printed.
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
# one.
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
#      build, a command ran none of the tests it named, or a process under the
#      sandbox could not be ended.  The run proves nothing and must not be read
#      as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/limelight-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/limelight-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/limelight-gate-logs"

# The gitignored artefacts and fixtures the graded tests read; they fail rather
# than skip without them.  Where each comes from is in
# docs/developer/limelight_integration.rst, "Test support and evidence".
# (scratch/limelight-upload-spike/ -- the real importer JAR -- is read only by
# FakeLimelightServerTest, which no control here selects.)
readonly -a SCRATCH_NEEDED=(
    "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar"
    "scratch/phase05/artefacts/v2026.03.0__comet.linux.exe"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"
    "scratch/percolator/3.09"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML"
    "scratch/fixture/UP000005640_9606.fasta"
    "scratch/scientific-path/comet/comet.params"
    "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml"
    "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_4.pep.xml"
    "scratch/scientific-path/percolator-3.07.1/percolator.pout.xml"
    "scratch/scientific-path/limelight/limelight.xml"
    "scratch/scientific-path/corrupt/bogus-element.xml"
    "scratch/scientific-path/corrupt/missing-attribute.xml"
    "scratch/scientific-path/corrupt/truncated.xml"
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
# looks for one that holds every selected control's set.  They do NOT form
# one chain: cometgui-domain and cometgui-params-percolator are siblings.
readonly DOMAIN="cometgui-domain"
readonly PARAMS="cometgui-params-percolator"
readonly TOOLS="cometgui-tools"
readonly WORKFLOW="cometgui-workflow"
readonly UI="cometgui-ui"
readonly APP="cometgui-app"
readonly -a ORDERED_TOPS=("${DOMAIN}" "${PARAMS}" "${TOOLS}" "${WORKFLOW}" "${UI}" "${APP}")

# The production files the controls damage.
readonly TL="${TOOLS}/src/main/java/org/cometgui/tools/limelight"
readonly JUDGE="${TL}/LimelightXmlJudge.java"
readonly CONVERTER="${TL}/LimelightConverter.java"
readonly CUTOFF="${TL}/LimelightQValueCutoff.java"
readonly COMMAND="${TL}/LimelightConverterCommand.java"
readonly REQUEST="${TL}/LimelightConversionRequest.java"
readonly CLIENT="${TL}/LimelightUploadClient.java"
readonly SERVER="${TL}/LimelightServer.java"
readonly RESOLUTION="${PARAMS}/src/main/java/org/cometgui/params/percolator/resolution"
readonly ACTION="${RESOLUTION}/CompatibilityAction.java"
readonly MESSAGES="${RESOLUTION}/ResolutionMessages.java"
readonly WL="${WORKFLOW}/src/main/java/org/cometgui/workflow/limelight"
readonly CONVERSIONS="${WL}/LimelightConversions.java"
readonly UPLOADS="${WL}/LimelightUploads.java"
readonly CONFIRMATION="${WL}/LimelightUploadConfirmation.java"
readonly SETTINGS_STORE="${WORKFLOW}/src/main/java/org/cometgui/workflow/storage/LimelightSettingsStore.java"
readonly RERUN="${WORKFLOW}/src/main/java/org/cometgui/workflow/steps/PercolatorRerun.java"
readonly RERUN_PROVENANCE="${WORKFLOW}/src/main/java/org/cometgui/workflow/steps/RerunProvenance.java"
readonly SECRETS="${DOMAIN}/src/main/java/org/cometgui/domain/secrets/SessionSecrets.java"
readonly VIEW_MODEL="${UI}/src/main/java/org/cometgui/ui/viewmodel/limelight/LimelightViewModel.java"
readonly UPLOAD_VM="${UI}/src/main/java/org/cometgui/ui/viewmodel/limelight/LimelightUploadViewModel.java"
readonly UPLOAD_PANE="${UI}/src/main/java/org/cometgui/ui/controls/limelight/LimelightUploadPane.java"
readonly UPLOAD_PORT="${APP}/src/main/java/org/cometgui/app/config/ProjectLimelightUploadPort.java"

# The graded test classes.
readonly T_JUDGE="LimelightXmlJudgeTest"
readonly T_CONVERTER="LimelightConverterTest"
readonly T_CONVERTER_REAL="LimelightConverterRealTest"
readonly T_CUTOFF="LimelightQValueCutoffTest"
readonly T_COMMAND="LimelightConverterCommandTest"
readonly T_REQUEST="LimelightConversionRequestTest"
readonly T_GUARD="LoopbackGuardTest"
readonly T_SERVER="LimelightServerTest"
readonly T_ACTION="CompatibilityActionTest"
readonly T_CONVERSIONS="LimelightConversionsTest"
readonly T_SETTINGS="LimelightSettingsTest"
readonly T_REAL_CONVERSION="RealLimelightConversionTest"
readonly T_REAL_RERUN="RealLimelightCompatibleRerunTest"
readonly T_UPLOADS="LimelightUploadsTest"
readonly T_LEAK="LimelightUploadLeakTest"
readonly T_SECRETS="SessionSecretsTest"
readonly T_PANE="LimelightPaneTest"
readonly T_VIEW_MODEL="LimelightViewModelTest"
readonly T_UPLOAD_VM="LimelightUploadViewModelTest"
readonly T_UPLOAD_PANE="LimelightUploadPaneTest"
readonly T_UPLOAD_PORT="ProjectLimelightUploadPortTest"
readonly T_GUI_RERUN="LimelightRerunUiTest"
readonly T_GUI_UPLOAD="LimelightUploadUiTest"

# Phase 01's gates have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run: the cheap module sets first.
readonly -a ALL_CONTROLS=(7c 3a 3b 1a 1b 2a 2b 2c 5b 6e 6f 8c 8d 1c 4a 4c 5a 5c 6a 7a 7b 8a 6b 6c 8b 3c 4b 6d 7d H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the eight PHASE-12 exit gate items fail on the defects
they exist to catch.

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
populated _build/m2repo, the converter, Comet 2026.03.0 and Percolator 3.07.1
artefacts under scratch/phase05/artefacts, Percolator 3.09 under
scratch/percolator/3.09, the K562 fixtures and outputs under scratch/fixture
and scratch/scientific-path, the host's gcc and /usr/bin/timeout.  It runs
Maven offline, damages only a git-archive sandbox under _build/, writes only
under _build/, and contacts no host but this one.

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
# Read from the sandbox's POMs, so a module edge added later is noticed here
# rather than as an unattributable bytecode difference.
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
# in the sandbox.  Comet, Percolator and the converter run from copies the
# tests stage under a module's target/, and Xvfb from the sandbox's tools/
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
# failed.  The exit status, the tests executed and every failing testcase's
# message (from the XML, unescaped) are written INTO the log.  Afterwards the
# processes the run left under the sandbox are ended.
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
    printf '   %s test(s) executed\n' "$(executed_count "${log}")"
    reap "$(rel "${log}")"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s -am test -Dmaven.test.failure.ignore=true -Dtest=%s' "$1" "$2"
}

# append_failures LOG MODULES -- the number of tests executed, then every
# failed or errored testcase in the fresh reports, with its message and the
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

# failed_count LOG -- the number of failed testcases append_failures counted.
failed_count() {
    sed -n 's/^=== \([0-9][0-9]*\) FAILED TESTCASE(S) ===$/\1/p' "$1" | tail -1
}

executed_count() {
    sed -n 's/^=== \([0-9][0-9]*\) TESTCASE(S) EXECUTED ===$/\1/p' "$1" | tail -1
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
readonly SEL_JUDGE="${T_JUDGE}#theRealOutputIsAccepted+wrongRoot+phase00BogusElement+phase00MissingAttribute+phase00Truncated+absentEmptyUnreadable"
readonly SEL_CONVERTER="${T_CONVERTER}#exitZeroWithNoFile+exitZeroWithAnEmptyFile+exitZeroWithAMalformedFile+aNonZeroExitFails+aValidFileIsKept"
readonly SEL_CONVERTER_REAL="${T_CONVERTER_REAL}#anUnknownOptionExitsZero+aRealConversion+theCutoffReachesTheFile"
readonly SEL_COMMAND="${T_COMMAND}#theMinimalCommand+theOptionList"
readonly SEL_REQUEST="${T_REQUEST}#aRequest+decoyImportWithoutDecoysIsRefusedBeforeAnyProcess+theRuleForAControl+decoysWithoutImportAreRefused"
# 6e: ONLY the test aimed at this machine's own non-loopback address.  Its
# siblings aim at TEST-NET and a .invalid name, which with the guard bypassed
# would leave the machine; they are never selected here.
readonly SEL_GUARD="${T_GUARD}#theMachinesOwnAddressIsCaught"
readonly SEL_SERVER="${T_SERVER}#plainHttpOnlyToLoopback+normalised"
readonly SEL_ACTION="${T_ACTION}\$Rerun#linuxSet+whateverResolutionReturns+aFutureBuild+aRegisteredLocalBinary,${T_ACTION}\$NoRerun#noManagedBuildForThisPlatform+nothingOffered+aLocalClaimIsNotAManagedBuild+anInferredManagedClaim+aFailedManagedBuild"
readonly SEL_SECRETS="${T_SECRETS}#admittingRegisters+duplicatesCollapse"
readonly SEL_CUTOFF="${T_CUTOFF}#theDefault+bothEndsAreAccepted,${T_SETTINGS}#defaults,${T_CONVERSIONS}#gate5TheDefault+gate5ChangingTheLimelightCutoff+gate5ChangingTheDisplayFilters"
readonly SEL_CONVERSIONS_GATE8="${T_REQUEST}#aRequest+decoyImportWithoutDecoysIsRefusedBeforeAnyProcess+theRuleForAControl+decoysWithoutImportAreRefused,${T_CONVERSIONS}#gate8DecoyImportAgainstANoDecoyRun+gate8ARunWithDecoys+aConversionIsRecordedAndKept"
readonly SEL_REAL_CONVERSION="${T_REAL_CONVERSION}"
readonly SEL_REAL_RERUN="${T_REAL_RERUN}"
readonly SEL_UPLOADS="${T_UPLOADS}#aConfirmationIsUsedOnce+theConvertedFileIsUploadedAndRecorded+theServersWordsAreRedacted"
readonly SEL_LEAK="${T_LEAK}#theSeededCredentialIsInNoFile+theSearchFindsAPlantedCredential"
readonly SEL_PANE="${T_PANE}#incompatible+convertible"
readonly SEL_DECOY_VM="${T_VIEW_MODEL}#aConvertibleRun+decoysWhenAllowed"
readonly SEL_UPLOAD_VM="${T_UPLOAD_VM}#uploadSendsNothing+sendSendsTheShownDestinationOnce+aChangeWithdrawsTheDestination"
readonly SEL_UPLOAD_PORT="${T_UPLOAD_PORT}#prepareThenSend+aKeyIsAdmitted"
# The headless GUI tests run in @Order; each selection names the steps the
# graded one stands on.
readonly SEL_GUI_GATE3="${SEL_PANE},${T_GUI_RERUN}#aRunWith309+gate3"
readonly SEL_GUI_GATE4="${T_GUI_RERUN}#aRunWith309+gate4"
readonly SEL_GUI_GATE6="${SEL_UPLOAD_VM},${T_GUI_UPLOAD}#aRealRunAndConversionSendNothing+gate6"
readonly SEL_GUI_GATE7="${T_UPLOAD_PANE}#theKeyField+uploadThenSend,${T_GUI_UPLOAD}#aRealRunAndConversionSendNothing+gate6+gate7"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 renames a test method the selection names.
readonly SEL_H="${T_CUTOFF}#theDefault+bothEndsAreAccepted"
readonly H4_TEST_METHOD="theDefault"

control_selectors() {
    case "$1" in
        1a|1b|2c) printf '%s' "${SEL_JUDGE}" ;;
        1c) printf '%s' "${SEL_REAL_CONVERSION}" ;;
        2a) printf '%s' "${SEL_CONVERTER}" ;;
        2b) printf '%s' "${SEL_JUDGE},${SEL_CONVERTER_REAL}" ;;
        3a|3b) printf '%s' "${SEL_ACTION}" ;;
        3c) printf '%s' "${SEL_GUI_GATE3}" ;;
        4a|4c) printf '%s' "${SEL_REAL_RERUN}" ;;
        4b) printf '%s' "${SEL_GUI_GATE4}" ;;
        5a|5c) printf '%s' "${SEL_CUTOFF}" ;;
        5b) printf '%s' "${SEL_COMMAND},${SEL_CONVERTER_REAL}" ;;
        6a|7a) printf '%s' "${SEL_UPLOADS}" ;;
        6b) printf '%s' "${SEL_UPLOAD_PORT}" ;;
        6c) printf '%s' "${SEL_UPLOAD_VM}" ;;
        6d) printf '%s' "${SEL_GUI_GATE6}" ;;
        6e) printf '%s' "${SEL_GUARD}" ;;
        6f) printf '%s' "${SEL_SERVER}" ;;
        7b) printf '%s' "${SEL_LEAK}" ;;
        7c) printf '%s' "${SEL_SECRETS}" ;;
        7d) printf '%s' "${SEL_GUI_GATE7}" ;;
        8a|8c) printf '%s' "${SEL_CONVERSIONS_GATE8}" ;;
        8b) printf '%s' "${SEL_PANE},${SEL_DECOY_VM}" ;;
        8d) printf '%s' "${SEL_REQUEST}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

control_top() {
    case "$1" in
        7c) printf '%s' "${DOMAIN}" ;;
        3a|3b) printf '%s' "${PARAMS}" ;;
        1a|1b|2a|2b|2c|5b|6e|6f|8d|H) printf '%s' "${TOOLS}" ;;
        1c|4a|4c|5a|5c|6a|7a|7b|8a|8c) printf '%s' "${WORKFLOW}" ;;
        6c|8b) printf '%s' "${UI}" ;;
        3c|4b|6b|6d|7d) printf '%s' "${APP}" ;;
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
    local log="${LOGS}/0-baseline.log" rc=0 failed
    printf '   %s\n' "$(gate_command "${BASELINE_TOP}" "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${BASELINE_TOP}" "${BASELINE_SELECTORS}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the graded selections (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, 0 failed testcases, $(executed_count "${log}") tests, every graded selection executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree "${RUN_MODULES}"
    snapshot_targets
    end_control
}

# ------------------------------------------------------------- the controls --

# ---- item 1: a real conversion, structurally validated, checksummed --------

control_1a() {
    begin_control "1a" "item 1 [recorded, unit 2 K1]: the output judge's root check reduced to 'the element has a name'"
    inject_and_run "a document of another root judged Limelight XML" "${JUDGE}" "${TOOLS}" "${SEL_JUDGE}" regex \
        'expected: <WRONG_ROOT> but was: <SCHEMA_INVALID>' \
        '                if (!ROOT_ELEMENT.equals(local) || !namespace.isEmpty()) {' \
        '                if (local.isEmpty()) {'
    assert_testcase "a pout XML, a namespaced and a foreign root" failed "${T_JUDGE}" wrongRoot
    assert_testcase "the real known-good output stays accepted" passed "${T_JUDGE}" theRealOutputIsAccepted
    restore_pristine "${JUDGE}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: the output judge's schema validation skipped"
    inject_and_run "a schema-invalid file accepted" "${JUDGE}" "${TOOLS}" "${SEL_JUDGE}" regex \
        'Expected org\.cometgui\.tools\.limelight\.LimelightConversionException to be thrown, but nothing was thrown' \
        '        validate(file, schema);
' \
        ''
    assert_testcase "Phase 00's injected element" failed "${T_JUDGE}" phase00BogusElement
    assert_testcase "Phase 00's missing required attribute" failed "${T_JUDGE}" phase00MissingAttribute
    assert_testcase "the truncated file is still not well-formed (stays green)" passed "${T_JUDGE}" phase00Truncated
    restore_pristine "${JUDGE}"
    end_control
}

control_1c() {
    begin_control "1c" "item 1 [NEW]: the conversion's output.sha256 recorded as its MD5 (the REAL run)"
    inject_and_run "the checksum in provenance not the file's SHA-256" "${CONVERSIONS}" "${WORKFLOW}" "${SEL_REAL_CONVERSION}" regex \
        'expected: <[0-9a-f]{64}> but was: <[0-9a-f]{32}>' \
        '        done.put(OUTPUT_SHA256, outputHashes.sha256());' \
        '        done.put(OUTPUT_SHA256, outputHashes.md5());'
    assert_testcase "the real conversion checksummed with this test's own SHA-256" failed \
        "${T_REAL_CONVERSION}" gate1ARealConversion
    restore_pristine "${CONVERSIONS}"
    end_control
}

# ---- item 2: exit 0 with a missing or empty XML fails the stage -------------

control_2a() {
    begin_control "2a" "item 2 [NEW]: the converter adapter trusting exit 0 when the file is missing or empty"
    inject_and_run "exit 0 taken for a conversion" "${CONVERTER}" "${TOOLS}" "${SEL_CONVERTER}" regex \
        'Expected org\.cometgui\.tools\.limelight\.LimelightConversionException to be thrown, but nothing was thrown' \
        '            summary = LimelightXmlJudge.judge(staging, schema);' \
        '            summary =
                    Files.isRegularFile(staging) && Files.size(staging) > 0L
                            ? LimelightXmlJudge.judge(staging, schema)
                            : new LimelightXmlSummary(
                                    staging,
                                    1L,
                                    0,
                                    0,
                                    Optional.of(request.qCutoff().argument()));' \
        'import java.util.Objects;' \
        'import java.util.Objects;
import java.util.Optional;' \
        '        Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);' \
        '        if (Files.exists(staging)) {
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        }'
    assert_testcase "exit 0 with NO file" failed "${T_CONVERTER}" exitZeroWithNoFile
    assert_testcase "exit 0 with an EMPTY file" failed "${T_CONVERTER}" exitZeroWithAnEmptyFile
    assert_testcase "a malformed file is still judged (stays green)" passed "${T_CONVERTER}" exitZeroWithAMalformedFile
    assert_testcase "a non-zero exit still fails (stays green)" passed "${T_CONVERTER}" aNonZeroExitFails
    restore_pristine "${CONVERTER}"
    end_control
}

control_2b() {
    begin_control "2b" "item 2 [NEW]: the judge's existence check removed (the REAL converter's exit 0)"
    inject_and_run "no file is no longer a named failure" "${JUDGE}" "${TOOLS}" "${SEL_JUDGE},${SEL_CONVERTER_REAL}" regex \
        'expected: <org\.cometgui\.tools\.limelight\.LimelightConversionException> but was: <java\.nio\.file\.NoSuchFileException>' \
        '        if (!Files.isRegularFile(file)) {' \
        '        if (file == null) {'
    assert_testcase "the real converter on an unknown option: exit 0, nothing written" failed \
        "${T_CONVERTER_REAL}" anUnknownOptionExitsZero
    assert_testcase "the judge's own absent/empty/unreadable test" failed "${T_JUDGE}" absentEmptyUnreadable
    assert_testcase "a real conversion is still kept (stays green)" passed "${T_CONVERTER_REAL}" aRealConversion
    restore_pristine "${JUDGE}"
    end_control
}

control_2c() {
    begin_control "2c" "item 2 [NEW]: the judge's emptiness check removed"
    inject_and_run "an empty file not named empty" "${JUDGE}" "${TOOLS}" "${SEL_JUDGE}" regex \
        'expected: <EMPTY_OUTPUT> but was: <NOT_WELL_FORMED>' \
        '        if (size == 0L) {' \
        '        if (size < 0L) {'
    assert_testcase "the judge's absent/empty/unreadable test" failed "${T_JUDGE}" absentEmptyUnreadable
    assert_testcase "the real output stays accepted" passed "${T_JUDGE}" theRealOutputIsAccepted
    restore_pristine "${JUDGE}"
    end_control
}

# ---- item 3: the incompatible state, explained first, the right action ------

control_3a() {
    begin_control "3a" "item 3 [recorded, unit 4 N1]: the rerun offered whenever resolution selects any build"
    inject_and_run "R-LL-02's branch offering a rerun it cannot make" "${ACTION}" "${PARAMS}" "${SEL_ACTION}" regex \
        'expected: <REGISTER_LOCAL_BINARY> but was: <RERUN_PERCOLATOR>' \
        '        if (resolution.isAvailable(stage)) {' \
        '        if (resolution.selected().isPresent()) {'
    assert_testcase "no managed build for this platform" failed "${T_ACTION}" noManagedBuildForThisPlatform
    assert_testcase "a local claim is not a managed build" failed "${T_ACTION}" aLocalClaimIsNotAManagedBuild
    assert_testcase "the real Linux set's rerun stays green" passed "${T_ACTION}" linuxSet
    restore_pristine "${ACTION}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [recorded, unit 4 N2]: the action's label from the literal 3.07.1"
    inject_and_run "a label that is not what resolution returns" "${MESSAGES}" "${PARAMS}" "${SEL_ACTION}" regex \
        'expected: <Rerun Percolator with 3\.08\.2 for Limelight> but was: <Rerun Percolator with 3\.07\.1 for Limelight>' \
        '        return "Rerun Percolator with " + label(build) + " for " + stage.purpose();' \
        '        return "Rerun Percolator with 3.07.1 for " + stage.purpose();'
    assert_testcase "a hypothetical 3.08.2 resolved" failed "${T_ACTION}" whateverResolutionReturns
    assert_testcase "the unreleased 3.10" failed "${T_ACTION}" aFutureBuild
    assert_testcase "the real Linux set CANNOT see it -- 3.07.1 is what resolution returns today (stays green)" passed \
        "${T_ACTION}" linuxSet
    restore_pristine "${MESSAGES}"
    end_control
}

control_3c() {
    begin_control "3c" "item 3 [recorded, unit 6 Q1]: the conversion controls enabled for an incompatible run (pane and GUI)"
    inject_and_run "controls enabled before the explanation is acted on" "${VIEW_MODEL}" "${APP}" "${SEL_GUI_GATE3}" regex \
        'message: the section before anything is attempted \(4 failures\)' \
        '        boolean controls = canConvert && !busy();' \
        '        boolean controls = readiness != null && !busy();'
    assert_testcase "the pane's incompatible run" failed "${T_PANE}" incompatible
    assert_testcase "the headless GUI against the REAL 3.09 run" failed "${T_GUI_RERUN}" gate3
    assert_testcase "the pane's convertible run stays green" passed "${T_PANE}" convertible
    restore_pristine "${VIEW_MODEL}"
    end_control
}

# ---- item 4: the compatible rerun converts, Comet untouched -----------------

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 4 N3]: a run that is not ready no longer refused (the REAL 3.09 run)"
    inject_and_run "the 3.09 run's conversion attempted" "${CONVERSIONS}" "${WORKFLOW}" "${SEL_REAL_RERUN}" regex \
        'expected: <org\.cometgui\.workflow\.limelight\.LimelightConversionRefusedException> but was: <java\.util\.NoSuchElementException>' \
        '        if (!readiness.ready()) {
            throw new LimelightConversionRefusedException(readiness.refusals());' \
        '        if (readiness == null) {
            throw new LimelightConversionRefusedException(readiness.refusals());'
    # Every step of this test runs in its @BeforeAll -- the 3.09 run, the
    # refused conversion, the rerun and the derived run's conversion -- so the
    # red is the class's own testcase (surefire names it with no method), and
    # its four test methods never start.
    assert_log_contains "the REAL test's @BeforeAll, which requires the 3.09 run's conversion refused" "${DIRTY_LOG}" \
        'FAILED org.cometgui.workflow.steps.RealLimelightCompatibleRerunTest# [failure]'
    restore_pristine "${CONVERSIONS}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [NEW]: the compatible rerun's choice enabling no downstream stage (GUI, real rerun)"
    inject_and_run "a rerun that cannot feed Limelight" "${RERUN}" "${APP}" "${SEL_GUI_GATE4}" regex \
        'message: the runs: \[[^],]*\] ==> expected: <true> but was: <false>' \
        '                action.resolution().enabledStages(),' \
        '                java.util.EnumSet.noneOf(
                        org.cometgui.params.percolator.resolution.DownstreamStage.class),'
    assert_testcase "the GUI's rerun and conversion" failed "${T_GUI_RERUN}" gate4
    assert_testcase "the 3.09 run itself stays green" passed "${T_GUI_RERUN}" aRunWith309
    restore_pristine "${RERUN}"
    end_control
}

control_4c() {
    begin_control "4c" "item 4 [NEW]: the derived run no longer records which Percolator its source ran (the REAL rerun)"
    inject_and_run "provenance showing one Percolator execution, not both" "${RERUN_PROVENANCE}" "${WORKFLOW}" "${SEL_REAL_RERUN}" regex \
        'expected: <3\.09> but was: <none>' \
        '                source.manifest().settings().getOrDefault(PercolatorProvenance.VERSION, NONE));' \
        '                NONE);'
    assert_testcase "both Percolator executions in provenance" failed "${T_REAL_RERUN}" gate4BothExecutionsInProvenance
    assert_testcase "the derived run still converts (stays green)" passed "${T_REAL_RERUN}" gate4TheRerunConverts
    assert_testcase "gate 3's explanation and action stay green" passed "${T_REAL_RERUN}" gate3TheExplanationAndTheOfferedAction
    restore_pristine "${RERUN_PROVENANCE}"
    end_control
}

# ---- item 5: the q cutoff, 0.01, independent of the display filters --------

control_5a() {
    begin_control "5a" "item 5 [NEW]: the Limelight q cutoff's default 0.05"
    inject_and_run "a default that is not 0.01" "${CUTOFF}" "${WORKFLOW}" "${SEL_CUTOFF}" regex \
        'expected: <LimelightQValueCutoff\[value=0\.01\]> but was: <LimelightQValueCutoff\[value=0\.05\]>' \
        '    public static final LimelightQValueCutoff DEFAULT = new LimelightQValueCutoff(0.01);' \
        '    public static final LimelightQValueCutoff DEFAULT = new LimelightQValueCutoff(0.05);'
    assert_testcase "the cutoff's own default" failed "${T_CUTOFF}" theDefault
    assert_testcase "the workflow's default with nothing saved" failed "${T_CONVERSIONS}" gate5TheDefault
    assert_testcase "a saved cutoff still reaches -q (stays green)" passed "${T_CONVERSIONS}" gate5ChangingTheDisplayFilters
    restore_pristine "${CUTOFF}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [NEW]: -q not emitted (the REAL converter)"
    inject_and_run "the cutoff left to the converter" "${COMMAND}" "${TOOLS}" "${SEL_COMMAND},${SEL_CONVERTER_REAL}" regex \
        'carries the Percolator PSM q-value filter 0\.01, not the cutoff 0\.05 the conversion asked for' \
        '        arguments.add(Q_VALUE);
        arguments.add(request.qCutoff().argument());
' \
        ''
    assert_testcase "the minimal command" failed "${T_COMMAND}" theMinimalCommand
    assert_log_matches "  ... whose argument array ends without -q" "${DIRTY_LOG}" \
        'out\.xml\.partial, -q, 0\.01\]> but was: <\[-c, '
    assert_testcase "-q 0.05 reaches the REAL converter's file" failed "${T_CONVERTER_REAL}" theCutoffReachesTheFile
    assert_testcase "the REAL conversion at 0.01 CANNOT see it -- the converter's own default is 0.01 (stays green)" passed \
        "${T_CONVERTER_REAL}" aRealConversion
    restore_pristine "${COMMAND}"
    end_control
}

control_5c() {
    begin_control "5c" "item 5 [NEW]: saving the Limelight cutoff also writes the display filters"
    inject_and_run "the cutoff bound to the PSM display filter" "${SETTINGS_STORE}" "${WORKFLOW}" "${SEL_CUTOFF}" regex \
        'expected: <\[results/limelight-settings\.json\]> but was: <\[results/limelight-settings\.json, results/view-state\.json\]>' \
        '        AtomicDocumentWriter.write(file, LimelightSettingsJson.render(settings));' \
        '        AtomicDocumentWriter.write(file, LimelightSettingsJson.render(settings));
        ViewStateStore.write(
                run,
                new org.cometgui.results.filtering.DisplayFilters(
                        org.cometgui.results.filtering.PsmQValueFilter.parse(
                                settings.qCutoff().argument()),
                        org.cometgui.results.filtering.PeptideQValueFilter.parse("0.01")));'
    assert_testcase "changing the cutoff writes only its own file" failed "${T_CONVERSIONS}" gate5ChangingTheLimelightCutoff
    assert_testcase "the default with nothing saved stays green" passed "${T_CONVERSIONS}" gate5TheDefault
    restore_pristine "${SETTINGS_STORE}"
    end_control
}

# ---- item 6: upload only on an explicit action, destination first, the fake --

control_6a() {
    begin_control "6a" "item 6 [NEW]: the workflow's upload confirmation reusable"
    inject_and_run "one confirmation, many uploads" "${CONFIRMATION}" "${WORKFLOW}" "${SEL_UPLOADS}" regex \
        'Expected org\.cometgui\.workflow\.limelight\.LimelightUploadRefusedException to be thrown, but nothing was thrown' \
        '        return used.compareAndSet(false, true);' \
        '        return true;'
    assert_testcase "a confirmation is used once" failed "${T_UPLOADS}" aConfirmationIsUsedOnce
    assert_testcase "an upload is still made and recorded (stays green)" passed "${T_UPLOADS}" theConvertedFileIsUploadedAndRecorded
    restore_pristine "${CONFIRMATION}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 7 R2]: the port's single-send check always passing"
    inject_and_run "a shown destination sent twice" "${UPLOAD_PORT}" "${APP}" "${SEL_UPLOAD_PORT}" regex \
        'Expected org\.cometgui\.workflow\.limelight\.LimelightUploadRefusedException to be thrown, but nothing was thrown' \
        '            return sent.compareAndSet(false, true);' \
        '            return true;'
    assert_testcase "prepare, then send once" failed "${T_UPLOAD_PORT}" prepareThenSend
    assert_testcase "a key is still admitted (stays green)" passed "${T_UPLOAD_PORT}" aKeyIsAdmitted
    restore_pristine "${UPLOAD_PORT}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 7 R1]: a changed server no longer withdrawing the destination shown"
    inject_and_run "a destination shown for a server no longer typed" "${UPLOAD_VM}" "${UI}" "${SEL_UPLOAD_VM}" regex \
        'expected: <The destination shown was withdrawn because the file, the server, the project, the key or the run changed; nothing was sent\.' \
        '        serverGeneration++;
        long mine = serverGeneration;
        withdraw(DESTINATION_CHANGED);' \
        '        serverGeneration++;
        long mine = serverGeneration;'
    assert_testcase "a change withdraws the destination" failed "${T_UPLOAD_VM}" aChangeWithdrawsTheDestination
    assert_testcase "Upload still sends nothing (stays green)" passed "${T_UPLOAD_VM}" uploadSendsNothing
    restore_pristine "${UPLOAD_VM}"
    end_control
}

control_6d() {
    begin_control "6d" "item 6 [NEW]: Upload sending as soon as it has checked the destination (view-model and GUI)"
    # The send is made in the same background task as the check, before the
    # destination is shown: so by the time anything asserts on the shown
    # destination, the send has already happened -- no race with the GUI.
    inject_and_run "no explicit Send" "${UPLOAD_VM}" "${APP}" "${SEL_GUI_GATE6}" regex \
        'Upload sent nothing ==> expected: <\[\]> but was: <\[' \
        '        outcome.set(CHECKING);
        publish();
        background.execute(' \
        '        outcome.set(CHECKING);
        publish();
        Optional<Credential> held = Optional.ofNullable(typedKey);
        background.execute(' \
        '                        checked = port.prepare(target, chosen, to, into);' \
        '                        checked = port.prepare(target, chosen, to, into);
                        port.send(checked, held, false, line -> {});'
    assert_testcase "the view-model's Upload sends nothing" failed "${T_UPLOAD_VM}" uploadSendsNothing
    assert_testcase "the headless GUI against the fake" failed "${T_GUI_UPLOAD}" gate6
    assert_log_contains "  ... the GUI's destination, shown with an upload already recorded" "${DIRTY_LOG}" \
        'message: the destination shown before anything is sent ('
    assert_log_contains "  ... and the loopback fake had already been sent the first request" "${DIRTY_LOG}" \
        'Upload sent nothing ==> expected: <[]> but was: <[Received[method=POST, path=/limelight/d/rws/for-page/'
    restore_pristine "${UPLOAD_VM}"
    end_control
}

control_6e() {
    begin_control "6e" "item 6 [NEW]: the upload client given no proxy -- the test guard bypassed (this machine's own address only)"
    inject_and_run "the client routing around the loopback sinkhole" "${CLIENT}" "${TOOLS}" "${SEL_GUARD}" regex \
        'expected: <\[CONNECT [0-9.]+:9 HTTP/1\.1\]> but was: <\[\]>' \
        '                .proxy(proxies)' \
        '                .proxy(HttpClient.Builder.NO_PROXY)'
    assert_testcase "this machine's own non-loopback address, caught by the guard" failed \
        "${T_GUARD}" theMachinesOwnAddressIsCaught
    restore_pristine "${CLIENT}"
    end_control
}

control_6f() {
    begin_control "6f" "item 6 [recorded, unit 5 P1]: plain http accepted to any host"
    inject_and_run "the key sent in clear to another host" "${SERVER}" "${TOOLS}" "${SEL_SERVER}" regex \
        'Expected java\.lang\.IllegalArgumentException to be thrown, but nothing was thrown' \
        '        if ("http".equals(scheme) && !isLoopback(host)) {' \
        '        if ("http".equals(scheme) && host == null) {'
    assert_testcase "plain http only to loopback" failed "${T_SERVER}" plainHttpOnlyToLoopback
    assert_testcase "an address is still normalised (stays green)" passed "${T_SERVER}" normalised
    restore_pristine "${SERVER}"
    end_control
}

# ---- item 7: a seeded credential in no log, provenance or export ------------

control_7a() {
    begin_control "7a" "item 7 [NEW]: the upload no longer adds the key to the redactor it writes with"
    inject_and_run "a server message quoting the key written as it came" "${UPLOADS}" "${WORKFLOW}" "${SEL_UPLOADS}" regex \
        'expected: <Optional\[no such key: \[REDACTED\]\]> but was: <Optional\[no such key: LLworkflow-seeded-key-' \
        '        SecretRedactor redactor = secrets.redactor().withSecret(key.reveal());' \
        '        SecretRedactor redactor = secrets.redactor();'
    assert_testcase "the server's words are redacted" failed "${T_UPLOADS}" theServersWordsAreRedacted
    assert_testcase "an upload is still made and recorded (stays green)" passed "${T_UPLOADS}" theConvertedFileIsUploadedAndRecorded
    restore_pristine "${UPLOADS}"
    end_control
}

control_7b() {
    begin_control "7b" "item 7 [NEW]: the key written to the upload log, unredacted (the leak test's walk)"
    inject_and_run "the key in the upload log" "${UPLOADS}" "${WORKFLOW}" "${SEL_LEAK}" regex \
        'the seeded credential was written to a file ==> expected: <\{\}> but was: <\{[^}]*/logs/limelight-upload\.log=utf-8' \
        '            log.line("Upload to Limelight: " + destination);' \
        '            log.line("Upload to Limelight: " + destination + " with the key " + key.reveal());' \
        '            String clean = redactor.redactText(text);' \
        '            String clean = text;'
    assert_testcase "every file the run wrote, walked" failed "${T_LEAK}" theSeededCredentialIsInNoFile
    assert_testcase "the walk's own control stays green" passed "${T_LEAK}" theSearchFindsAPlantedCredential
    restore_pristine "${UPLOADS}"
    end_control
}

control_7c() {
    begin_control "7c" "item 7 [NEW]: a credential admitted without being registered with the redactor"
    inject_and_run "a key the redactor does not know" "${SECRETS}" "${DOMAIN}" "${SEL_SECRETS}" regex \
        'message: expected: <1> but was: <0>' \
        '        registry.updateAndGet(current -> current.with(value));
' \
        ''
    assert_testcase "admitting registers" failed "${T_SECRETS}" admittingRegisters
    restore_pristine "${SECRETS}"
    end_control
}

control_7d() {
    begin_control "7d" "item 7 [NEW]: the key field not emptied once its key is taken (pane and GUI)"
    inject_and_run "the key left in the window" "${UPLOAD_PANE}" "${APP}" "${SEL_GUI_GATE7}" regex \
        'message: the window and the console \(2 failures\)' \
        '                    if (!typed.isEmpty()) {
                        key.clear();' \
        '                    if (!typed.isEmpty()) {'
    assert_testcase "the pane's key field" failed "${T_UPLOAD_PANE}" theKeyField
    assert_testcase "the headless GUI's gate 7" failed "${T_GUI_UPLOAD}" gate7
    assert_log_matches "  ... the seeded key read back from the window" "${DIRTY_LOG}" \
        'expected: <> but was: <CGSEED-'
    assert_log_contains "  ... and the pane's own key, left in its field" "${DIRTY_LOG}" \
        'emptied ==> expected: <> but was: <PANESEEDKEY-'
    restore_pristine "${UPLOAD_PANE}"
    end_control
}

# ---- item 8: decoy import prevented at configuration time --------------------

control_8a() {
    begin_control "8a" "item 8 [recorded, unit 3 L3]: saveSettings' decoy refusal unreachable"
    inject_and_run "a decoy import saved for a run without decoys" "${CONVERSIONS}" "${WORKFLOW}" "${SEL_CONVERSIONS_GATE8}" regex \
        'Expected org\.cometgui\.workflow\.limelight\.LimelightConversionRefusedException to be thrown, but nothing was thrown' \
        '            if (unavailable.isPresent()) {
                throw new LimelightConversionRefusedException(' \
        '            if (unavailable.isPresent() && unavailable.isEmpty()) {
                throw new LimelightConversionRefusedException('
    assert_testcase "gate 8 against a run without decoys" failed "${T_CONVERSIONS}" gate8DecoyImportAgainstANoDecoyRun
    assert_testcase "a run with decoys stays green" passed "${T_CONVERSIONS}" gate8ARunWithDecoys
    restore_pristine "${CONVERSIONS}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [recorded, unit 6 Q2]: decoy import enabled whenever the settings are"
    inject_and_run "the decoy control enabled for a run without decoys" "${VIEW_MODEL}" "${UI}" "${SEL_PANE},${SEL_DECOY_VM}" regex \
        'message:  org\.opentest4j\.AssertionFailedError: expected: <false> but was: <true>' \
        '        decoyEnabled.set(controls && readiness.decoyImportUnavailable().isEmpty());' \
        '        decoyEnabled.set(controls);'
    assert_testcase "the view-model's convertible run without decoys" failed "${T_VIEW_MODEL}" aConvertibleRun
    # The words are only "expected: <false> but was: <true>" -- the assertion
    # has no message -- so the trace must place it in aConvertibleRun's
    # assertAll, where the decoy control's enabled state is read.
    assert_log_matches "  ... at the decoy control's enabled state, inside aConvertibleRun" "${DIRTY_LOG}" \
        'at org\.cometgui\.ui\.viewmodel\.limelight\.LimelightViewModelTest\.lambda\$aConvertibleRun\$'
    assert_testcase "the pane's convertible run without decoys" failed "${T_PANE}" convertible
    assert_testcase "a run whose Percolator wrote decoys still allows it (stays green)" passed "${T_VIEW_MODEL}" decoysWhenAllowed
    assert_testcase "the incompatible run stays green" passed "${T_PANE}" incompatible
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_8c() {
    begin_control "8c" "item 8 [NEW]: a pout XML without decoy PSMs judged able to import decoys"
    inject_and_run "the one decoy rule inverted for zero" "${REQUEST}" "${WORKFLOW}" "${SEL_CONVERSIONS_GATE8}" regex \
        'expected: <java\.lang\.IllegalArgumentException> but was: <org\.cometgui\.tools\.limelight\.LimelightConversionException>' \
        '        if (percolatorXml.decoyPsmCount() > 0) {
            return Optional.empty();' \
        '        if (percolatorXml.decoyPsmCount() >= 0) {
            return Optional.empty();'
    assert_testcase "the request refuses decoy import before any process" failed "${T_REQUEST}" decoyImportWithoutDecoysIsRefusedBeforeAnyProcess
    assert_testcase "the workflow's gate 8" failed "${T_CONVERSIONS}" gate8DecoyImportAgainstANoDecoyRun
    restore_pristine "${REQUEST}"
    end_control
}

control_8d() {
    begin_control "8d" "item 8 [recorded, unit 2 K3]: the 'decoys present but not imported' refusal unreachable"
    inject_and_run "a run with decoys converted without them" "${REQUEST}" "${TOOLS}" "${SEL_REQUEST}" regex \
        'Expected java\.lang\.IllegalArgumentException to be thrown, but nothing was thrown' \
        '        } else if (percolatorXml.decoyPsmCount() > 0) {' \
        '        } else if (percolatorXml.decoyPsmCount() < 0) {'
    assert_testcase "decoys without import refused" failed "${T_REQUEST}" decoysWithoutImportAreRefused
    assert_testcase "decoy import without decoys still refused (stays green)" passed "${T_REQUEST}" decoyImportWithoutDecoysIsRefusedBeforeAnyProcess
    restore_pristine "${REQUEST}"
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

readonly H_FILE="${CUTOFF}"
readonly H_ANCHOR='    public static final LimelightQValueCutoff DEFAULT = new LimelightQValueCutoff(0.01);'
readonly H_TEST="${TOOLS}/src/test/java/org/cometgui/tools/limelight/${T_CUTOFF}.java"

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
    dirty_run "H3" "${H_FILE}" "${TOOLS}" "${SEL_H}" "${LOGS}/H3-comment-only.log"
}

# H4: the test method a selection names is renamed in the sandbox, as a refactor
# would rename it.  Surefire, told not to fail on a selection that matches
# nothing, runs zero of the tests named and Maven exits 0.  A real Maven run.
h_zero_tests() {
    local log="${LOGS}/H4-zero-tests.log" rc=0
    run_mvn "${log}" "${TOOLS}" "${SEL_H}" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "H4's Maven run itself failed (exit ${rc}); it must build and select nothing of what it names."
    verify_classes_ran "${log}" "${SEL_H}"
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
    replace_once "H4 rename" "${H_TEST}" "    void ${H4_TEST_METHOD}() " "    void ${H4_TEST_METHOD}Renamed() "
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
    failed="$(failed_count "${log}")"
    [ "${failed}" = "0" ] \
        || harness_error "H3's run of the clean code did not pass (${failed:-unknown} failed); H5 needs a green run to grade."
    expect_recorded_failure "H5 a control run with NO defect injected is recorded as a failure, not a pass" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red regex "H5 (deliberately graded with no defect injected)" "${failed}" "${log}" \
        'expected: <0\.01> but was'

    log="${LOGS}/H6-wrong-reason.log"
    printf 'FAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red regex "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <0\.01> but was'

    USED_SELECTORS+=("${SEL_H}")
    clean_targets
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1a) control_1a ;; 1b) control_1b ;; 1c) control_1c ;;
        2a) control_2a ;; 2b) control_2b ;; 2c) control_2c ;;
        3a) control_3a ;; 3b) control_3b ;; 3c) control_3c ;;
        4a) control_4a ;; 4b) control_4b ;; 4c) control_4c ;;
        5a) control_5a ;; 5b) control_5b ;; 5c) control_5c ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;; 6d) control_6d ;; 6e) control_6e ;; 6f) control_6f ;;
        7a) control_7a ;; 7b) control_7b ;; 7c) control_7c ;; 7d) control_7d ;;
        8a) control_8a ;; 8b) control_8b ;; 8c) control_8c ;; 8d) control_8d ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log="${LOGS}/C-clean.log" rc=0 selectors failed
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    printf '   %s\n' "$(gate_command "${BASELINE_TOP}" "${selectors}")"
    run_mvn "${log}" "${BASELINE_TOP}" "${selectors}" || rc=$?
    failed="$(failed_count "${log}")"
    if [ "${rc}" -ne 0 ] || [ "${failed:-x}" != "0" ]; then
        record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, ${failed:-no} failed testcase(s), log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m3 '^FAILED ' "${log}" | cut -c1-200 || true)"
    else
        verify_classes_ran "${log}" "${selectors}"
        record_pass "the clean sandbox passes again: exit 0, 0 failed testcases, $(executed_count "${log}") tests, every selection the dirty runs used executed ($(printf '%s\n' "${selectors}" | tr ',' '\n' | grep -c .) selectors)"
    fi
    compare_tree "${log}" "${RUN_MODULES}"
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
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} (see docs/developer/limelight_integration.rst, 'Test support and evidence')")
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
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256 "${LOGS}"/*.txt

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-12 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
        die "${FAILED} limelight-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an unchanged file, a missing anchor, an\n'
        printf '  injection that reached the source but not the bytecode and a renamed test\n'
        printf '  whose selection ran zero tests as a HARNESS ERROR, and a run with no defect\n'
        printf '  and a red for the wrong reason as a FAILURE -- never as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-12 exit gate items 1 to 8 were proved here, each by a production\n'
    printf '  defect in cometgui-domain, cometgui-params-percolator, cometgui-tools,\n'
    printf '  cometgui-workflow, cometgui-ui or cometgui-app in a git-archive sandbox,\n'
    printf '  proved in the bytecode and graded on the failing assertion'"'"'s own words in\n'
    printf '  the named testcase -- items 1, 2, 4 and 5 also against the real converter,\n'
    printf '  Comet and Percolator, items 3, 4, 6 and 7 also through the headless GUI,\n'
    printf '  and item 6'"'"'s test guard only against this machine'"'"'s own address.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
