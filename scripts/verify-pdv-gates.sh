#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-11 PDV integration gates can fail.
#
#   bash scripts/verify-pdv-gates.sh                every control
#   bash scripts/verify-pdv-gates.sh --self-test    control H only
#   bash scripts/verify-pdv-gates.sh --only 2a,7b,H named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 11's exit gate is eight claims
# about PDV as CometGUI installs, launches, records and drives it, and about the
# mzTab it writes for PDV -- PDV installed on first use with the interface
# usable meanwhile; a real annotated figure judged by its content; an
# unsupported spectrum format refused before launch; the JAR's checksum and
# version recorded whenever PDV is launched; an ephemeral port, /ready polled
# and a selection changing the drawn spectrum; the mzTab true to its sources;
# spectra_ref resolving to the producing spectrum where file position and scan
# number differ; and an unrepresentable PSM failing the export by name.  Each
# is checked by tests in cometgui-tools, cometgui-workflow, cometgui-ui and
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
# It is the sibling of scripts/verify-results-gates.sh and takes its shape from
# it: a git-archive sandbox, anchors that must match exactly once, a pristine
# copy per damaged file, bytecode digests, graded failures read from surefire's
# XML, one closed module set per control (-pl TOP -am, nothing installed), one
# batched clean re-run, and a control H on itself.  What is new here: an
# injection may need several anchors in one file (control 7's, which must also
# switch off the exporter's own read-back); real PDV and the project-local Xvfb
# run under the tests, so every Maven run is followed by a sweep for processes
# left under the sandbox; and the sandbox's build directories are returned to
# their post-baseline state between controls.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-11-worklog.rst whose sign-off made that injection and
# saw it red.  [NEW] marks this script's own (unit 9): a defect the unit-9 brief
# names for its gate item, held to the same standard and labelled so nobody
# mistakes it for history.
#
# WHAT IT COVERS (PHASE-11 exit gate items; see phases/PHASE-11-pdv.rst)
#
#   0   baseline: the undamaged sandbox passes every graded selection in one
#       run, and every compiled module is digested
#   1a  item 1 [recorded, unit 3 K3]: Draw draws without installing PDV -- the
#       view-model's install-then-draw test red (see below for the GUI half)
#   1b  item 1 [NEW]: starting the install holds the interface thread for 40 s
#       -- the first-use GUI test (real Comet, Percolator and PDV from an empty
#       cache through a held loopback transfer) finds the interface frozen
#   2a  item 2 [recorded, unit 2 J1]: the blank-figure floor removed from the
#       PNG judge -- a blank canvas accepted
#   2b  item 2 [NEW]: PDV exiting 0 with a requested figure missing is no
#       longer NO_FIGURE -- red against the real PDV's absent spectrum
#   2c  item 2 [NEW]: PDV told the ids are peptide sequences (-k p, not -k s)
#       -- the real PDV draws nothing for a known PSM
#   3a  item 3 [NEW]: the format check bypassed before launch
#   3b  item 3 [NEW]: mzXML marked supported by PDV 2.7.0
#   4a  item 4 [recorded, unit 3 K2]: the launcher's other-program refusal
#       removed
#   4b  item 4 [NEW]: PDV started before its launch is recorded
#   4c  item 4 [NEW]: the JAR's MD5 recorded as its SHA-256
#   4d  item 4 [NEW]: the manifest-version check removed
#   5a  item 5 [recorded, unit 7 Q2]: the denovo-gui command given port + 1
#   5b  item 5 [NEW]: the controller sends the NEXT row's reference -- PDV
#       answers 200 and draws another PSM, which only the in-JVM observer of
#       the real PDV can see
#   5c  item 5 [NEW]: a window's shutdown no longer ends PDV's process
#   5d  item 5 [recorded, unit 7 Q1]: another Results run no longer closes the
#       window
#   5e  item 5 [NEW]: the application's stop() no longer closes PDV's windows
#       -- the live-window GUI test finds PDV still running after the exit
#   6a  item 6 [recorded, unit 6 N1]: score and q-value columns swapped
#   6b  item 6 [NEW]: the PEP column fed the q-value
#   6c  item 6 [recorded, unit 5 M2]: accessions cut to the first protein --
#       here graded on the fidelity suite, not the exporter's own tests
#   6d  item 6 [NEW]: a static modification's delta rounded to two places
#   6e  item 6 [NEW]: the decoy flag written 0 for every row
#   7a  item 7 [NEW]: spectra_ref by instrument scan number
#   7b  item 7 [NEW]: spectra_ref by the spectrum's 1-based file position
#   7c  item 7 [NEW]: spectra_ref to the first row of a shared spectrum
#       -- each of 7a-7c with the exporter's own read-back of the references
#       ALSO switched off, so that the fidelity suite, not the exporter's
#       self-check, is what catches it
#   8a  item 8 [recorded, unit 5 M1]: the C-terminal refusal disabled
#   8b  item 8 [NEW]: the PSM's name dropped from the refusal's message
#   8c  item 8 [NEW]: a refused export leaves its staging directory behind
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
#     real-PDV test is @EnabledOnOs(LINUX).
#   * Control 5a is graded on DenovoGuiCommandTest only.  Unit 7's sign-off saw
#     the same injection red in PdvControllerRealPdvTest too -- "no state change
#     within PT3M" -- but that costs the test's full three-minute readiness
#     bound; the real PDV is graded here by 5b instead, which fails in about a
#     minute.
#   * Control 1a is graded on the view-model only.  Unit 3's sign-off saw the
#     same injection red in PdvFirstUseUiTest -- "the PDV transfer did not
#     reach 30000000 bytes" -- and this script reproduced it (control 1a took
#     401 s), but only after the test's full six-minute PDV_BOUND; the GUI
#     test is graded here by 1b, which fails in 30 s.
#   * The bind-race relaunch and a foreign server answering /ready are proved
#     against the fake only (see docs/developer/pdv_integration.rst).
#   * The figure test inside PdvFirstUseUiTest is graded only as far as 1a and
#     1b break it; the figure itself is graded on PdvFiguresRealTest (2b, 2c).
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/pdv-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a POM
# are reported loudly.  scratch/ (the PDV, Comet and Percolator artefacts, the
# K562 mzML, FASTA, pepXML and Percolator outputs -- all gitignored, and D-006
# forbids committing the K562 ones) is symlinked in, and tools/ (the X11 and
# font stacks real PDV and the headless JavaFX tests need) likewise.
#
# ONE CLOSED MODULE SET PER CONTROL.  Each control names the top module of the
# narrowest set that holds both its damaged file and its graded tests, and
# every run is `mvn -o -pl TOP -am test`: nothing is ever installed, and the
# shared _build/m2repo's org/cometgui jars are digested before and after.  The
# module sets nest (cometgui-tools < cometgui-workflow < cometgui-ui <
# cometgui-app), so the baseline and the clean re-run use the largest set any
# selected control needs.
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
# PROCESSES.  Real PDV and Xvfb run under the tests, under the host's
# `timeout` (test support's own caps).  After every Maven run this script waits
# up to 20 s for every process whose command line or working directory lies in
# the sandbox to end, then ends what is left (TERM, then KILL) and says so; one
# it cannot end is a HARNESS ERROR.  A control that injects a lifecycle defect
# (5c, 5e) is expected to leave one.
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
# wrong reason.  Offline; writes only under _build/.
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
readonly SANDBOX="${ROOT}/_build/pdv-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/pdv-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/pdv-gate-logs"

# The gitignored artefacts and fixtures the graded tests read; they fail rather
# than skip without them.  Where each comes from is in
# docs/developer/pdv_integration.rst and in the tests' own failure messages.
readonly -a SCRATCH_NEEDED=(
    "scratch/phase05/artefacts/v2.7.0__PDV-2.7.0.zip"
    "scratch/phase05/artefacts/v2026.02.2__comet.linux.exe"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML"
    "scratch/fixture/UP000005640_9606.fasta"
    "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml"
    "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_4.pep.xml"
    "scratch/scientific-path/percolator-3.07.1/psms.target.txt"
    "scratch/scientific-path/percolator-3.07.1/psms.decoy.txt"
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

# The module sets (the top of each `-pl TOP -am`), smallest first: each holds
# the one before it.
readonly TOOLS="cometgui-tools"
readonly WORKFLOW="cometgui-workflow"
readonly UI="cometgui-ui"
readonly APP="cometgui-app"
readonly -a NESTED_TOPS=("${TOOLS}" "${WORKFLOW}" "${UI}" "${APP}")

# The production files the controls damage.
readonly TP="${TOOLS}/src/main/java/org/cometgui/tools/pdv"
readonly PNG="${TP}/PngFigure.java"
readonly FIGURES="${TP}/PdvFigures.java"
readonly SUPPORT="${TP}/PdvSpectrumSupport.java"
readonly EXPORTER="${TP}/PdvMzTabExporter.java"
readonly PSM_HIT="${TP}/PsmHit.java"
readonly REFUSAL="${TP}/PdvMzTabException.java"
readonly COMMAND="${TP}/derived/DenovoGuiCommand.java"
readonly CONTROLLER="${TP}/derived/PdvController.java"
readonly LAUNCHES="${WORKFLOW}/src/main/java/org/cometgui/workflow/pdv/PdvLaunches.java"
readonly VIEW_MODEL="${UI}/src/main/java/org/cometgui/ui/viewmodel/visualisation/VisualisationViewModel.java"
readonly APPLICATION="${APP}/src/main/java/org/cometgui/app/bootstrap/CometGuiApplication.java"

# The graded test classes.
readonly T_PNG="PngFigureTest"
readonly T_FIGURES="PdvFiguresTest"
readonly T_FIGURES_REAL="PdvFiguresRealTest"
readonly T_SUPPORT="PdvSpectrumSupportTest"
readonly T_COMMAND="DenovoGuiCommandTest"
readonly T_CONTROLLER="PdvControllerTest"
readonly T_OBSERVED="PdvControllerRealPdvTest"
readonly T_EXPORTER="PdvMzTabExporterTest"
readonly T_K562="K562FidelityTest"
readonly T_CONSTRUCTED="ConstructedFidelityTest"
readonly T_UNREPRESENTABLE="UnrepresentableTest"
readonly T_LAUNCHES="PdvLaunchesTest"
readonly T_VIEW_MODEL="VisualisationViewModelTest"
readonly T_WINDOW_VM="VisualisationWindowTest"
readonly T_FIRST_USE="PdvFirstUseUiTest"
readonly T_LIVE_WINDOW="PdvLiveWindowUiTest"

# Phase 01's gates have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run: the cheap module sets first.
readonly -a ALL_CONTROLS=(2a 2b 2c 3a 3b 5a 5b 5c 6a 6b 6c 6d 6e 7a 7b 7c 8a 8b 8c 4a 4b 4c 4d 1a 5d 1b 5e H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the eight PHASE-11 exit gate items fail on the defects
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
populated _build/m2repo, the PDV, Comet and Percolator artefacts under
scratch/phase05/artefacts, the K562 fixtures and outputs under scratch/fixture
and scratch/scientific-path, the host's gcc and /usr/bin/timeout.  It runs
Maven offline, damages only a git-archive sandbox under _build/, and writes
only under _build/.

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

# require_nested -- the module sets this script uses must nest as the header
# says, or the baseline (built at the largest) would not have compiled every
# module a smaller run compares against.
require_nested() {
    local smaller="" top module
    for top in "${NESTED_TOPS[@]}"; do
        if [ -n "${smaller}" ]; then
            for module in ${smaller//,/ }; do
                case ",$(reactor_of "${top}")," in
                    *",${module},"*) ;;
                    *) harness_error "${module} is in a smaller module set but not in ${top}'s: the module sets no longer nest, so the baseline cannot cover every run." ;;
                esac
            done
        fi
        smaller="$(reactor_of "${top}")"
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
# in the sandbox.  Real PDV runs from the PDV the tests unpack under a module's
# target/, and Xvfb from the sandbox's tools/ link, so both are found; this
# script's own processes are not.
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
    # tools/ is gitignored too: the X11 and font closures.
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
readonly SEL_PNG="${T_PNG}#floor+everyFilter"
readonly SEL_FIGURES="${T_FIGURES}#noFigure+drawn+command+formatGateBeforeLaunch,${T_FIGURES_REAL}#aKnownPsmIsDrawn+anAbsentSpectrumIsANamedFailure"
readonly SEL_SUPPORT="${T_SUPPORT}#supported+unsupportedFormats+accepted"
readonly SEL_COMMAND="${T_COMMAND}#theProductCommand+thePortRange"
readonly SEL_OBSERVED="${T_OBSERVED}#eachSelectionDrawsThatPsm+anUnknownReferenceIsNotFound,${T_CONTROLLER}#selectionsAreAnswered"
readonly SEL_SHUTDOWN="${T_CONTROLLER}#shutdownEndsTheProcess+shutdownAfterDeath+closeShutsEverything"
readonly SEL_FIDELITY="${T_K562}#everyPsmExactlyOnce+scores+charge+accessions+modificationsAgainstPepXml+fixedModifications+sequence+positionAndScanDiffer+spectraRefIsTheProducingSpectrum,${T_CONSTRUCTED}#everyCheckPasses+sharedPsmIds"
readonly SEL_UNREPRESENTABLE="${T_UNREPRESENTABLE}#cTerminalModification+percolatorRowWithoutHit+peptideDisagreement+modificationDisagreement+spectrumMissing,${T_EXPORTER}\$Refuses#unrepresentableModification+noHit"
readonly SEL_LAUNCHES="${T_LAUNCHES}#theRecordPrecedesTheStart+anotherProgramIsRefused+aVersionMismatchIsRefused+everyStartHasItsOwnEvent"
readonly SEL_WINDOW_VM="${T_WINDOW_VM}#anotherRunClosesTheWindow+aStaleOpeningIsClosed"
readonly SEL_INSTALL_VM="${T_VIEW_MODEL}#installThenDraw+drawAtOnce"
readonly SEL_FIRST_USE="${SEL_INSTALL_VM},${T_FIRST_USE}"
readonly SEL_LIVE_WINDOW="${T_LIVE_WINDOW}"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 renames a test method the selection names.
readonly SEL_H="${T_SUPPORT}#supported+accepted"
readonly H4_TEST_METHOD="supported"

control_selectors() {
    case "$1" in
        1a) printf '%s' "${SEL_INSTALL_VM}" ;;
        1b) printf '%s' "${SEL_FIRST_USE}" ;;
        2a) printf '%s' "${SEL_PNG}" ;;
        2b|2c|3a) printf '%s' "${SEL_FIGURES}" ;;
        3b) printf '%s' "${SEL_SUPPORT}" ;;
        4a|4b|4c|4d) printf '%s' "${SEL_LAUNCHES}" ;;
        5a) printf '%s' "${SEL_COMMAND}" ;;
        5b) printf '%s' "${SEL_OBSERVED}" ;;
        5c) printf '%s' "${SEL_SHUTDOWN}" ;;
        5d) printf '%s' "${SEL_WINDOW_VM}" ;;
        5e) printf '%s' "${SEL_LIVE_WINDOW}" ;;
        6a|6b|6c|6d|6e|7a|7b|7c) printf '%s' "${SEL_FIDELITY}" ;;
        8a|8b|8c) printf '%s' "${SEL_UNREPRESENTABLE}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

control_top() {
    case "$1" in
        2a|2b|2c|3a|3b|5a|5b|5c|6a|6b|6c|6d|6e|7a|7b|7c|8a|8b|8c|H) printf '%s' "${TOOLS}" ;;
        4a|4b|4c|4d) printf '%s' "${WORKFLOW}" ;;
        1a|5d) printf '%s' "${UI}" ;;
        1b|5e) printf '%s' "${APP}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

BASELINE_SELECTORS=""
BASELINE_TOP=""
plan_baseline() {
    local id top rank best=-1 index
    local -a selectors=()
    for id in "$@"; do
        selectors+=("$(control_selectors "${id}")")
        top="$(control_top "${id}")"
        rank=-1
        for index in "${!NESTED_TOPS[@]}"; do
            [ "${NESTED_TOPS[${index}]}" = "${top}" ] && rank="${index}"
        done
        [ "${rank}" -ge 0 ] || die "control ${id}'s module set ${top} is not one of ${NESTED_TOPS[*]}" 2
        [ "${rank}" -gt "${best}" ] && best="${rank}"
    done
    BASELINE_TOP="${NESTED_TOPS[${best}]}"
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the undamaged sandbox passes every graded selection"
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"
    require_nested
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

control_1a() {
    begin_control "1a" "item 1 [recorded, unit 3 K3]: Draw draws without installing PDV"
    inject_and_run "a draw with PDV absent goes straight to drawing" "${VIEW_MODEL}" "${UI}" "${SEL_INSTALL_VM}" fixed \
        'expected: <Installing PDV 2.7.0: starting.> but was: <PDV 2.7.0 is not installed. It is installed the first time a spectrum is drawn' \
        '        if (status.state() == PdvStatus.State.INSTALLED) {
            drawNow(run, psmId);' \
        '        if (status.state() != null) {
            drawNow(run, psmId);'
    assert_log_contains "  ... and nothing was asked of the Tool Manager" "${DIRTY_LOG}" \
        'AssertionFailedError: expected: <[2.7]> but was: <[]>'
    assert_testcase "the view-model's install-then-draw" failed "${T_VIEW_MODEL}" installThenDraw
    assert_testcase "drawing with PDV installed stays green" passed "${T_VIEW_MODEL}" drawAtOnce
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: starting the install holds the interface thread for 40 s"
    inject_and_run "the install's start blocking the JavaFX thread" "${VIEW_MODEL}" "${APP}" "${SEL_FIRST_USE}" fixed \
        'pressing Draw did not return within PT30S: the interface is frozen' \
        '        after.started(version);
        publish();
        return true;' \
        '        after.started(version);
        publish();
        try {
            Thread.sleep(40_000L);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
        return true;'
    assert_testcase "the gate-1 GUI test from an empty cache" failed "${T_FIRST_USE}" pdvInstallsOnFirstUseWhileASearchRuns
    assert_testcase "the view-model's own tests, which do not time the thread, stay green" passed \
        "${T_VIEW_MODEL}" drawAtOnce
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_2a() {
    begin_control "2a" "item 2 [recorded, unit 2 J1]: the blank-figure floor removed from the PNG judge"
    inject_and_run "a blank figure accepted" "${PNG}" "${TOOLS}" "${SEL_PNG}" regex \
        'Expected org\.cometgui\.tools\.pdv\.InvalidFigureException to be thrown, but nothing was thrown' \
        '        if (nonWhite < MINIMUM_NON_WHITE_FRACTION * total) {' \
        '        if (nonWhite < 0) {'
    assert_testcase "the floor test" failed "${T_PNG}" floor
    assert_testcase "a drawn figure of every filter type stays green" passed "${T_PNG}" everyFilter
    restore_pristine "${PNG}"
    end_control
}

control_2b() {
    begin_control "2b" "item 2 [NEW]: PDV exiting 0 with a requested figure missing is no longer NO_FIGURE"
    inject_and_run "a missing figure on exit 0 passed as success" "${FIGURES}" "${TOOLS}" "${SEL_FIGURES}" regex \
        'org\.opentest4j\.AssertionFailedError: expected: <NO_FIGURE> but was: <PDV_REPORTED_ERRORS>' \
        '        if (!missing.isEmpty()) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.NO_FIGURE,' \
        '        if (missing.isEmpty() && missing.size() > 0) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.NO_FIGURE,'
    assert_testcase "the real PDV asked for a spectrum the file lacks" failed \
        "${T_FIGURES_REAL}" anAbsentSpectrumIsANamedFailure
    assert_testcase "the fake run's exit 0 with no figure" failed "${T_FIGURES}" noFigure
    assert_testcase "the real PDV's figure of a known PSM stays green" passed "${T_FIGURES_REAL}" aKnownPsmIsDrawn
    restore_pristine "${FIGURES}"
    end_control
}

control_2c() {
    begin_control "2c" "item 2 [NEW]: PDV told the ids are peptide sequences (-k p)"
    inject_and_run "the id list read as peptides" "${FIGURES}" "${TOOLS}" "${SEL_FIGURES}" regex \
        'message: PDV exited 0 and wrote no figure for "controllerType=0 controllerNumber=1 scan=11367"' \
        '                        "-k",
                        "s",' \
        '                        "-k",
                        "p",'
    assert_testcase "the real PDV draws nothing for a known PSM" failed "${T_FIGURES_REAL}" aKnownPsmIsDrawn
    assert_testcase "the argument array as specified" failed "${T_FIGURES}" command
    restore_pristine "${FIGURES}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3 [NEW]: the format check bypassed before launch"
    inject_and_run "any named spectrum format launched" "${FIGURES}" "${TOOLS}" "${SEL_FIGURES}" regex \
        'Unexpected exception type thrown, expected: <org\.cometgui\.tools\.pdv\.UnsupportedSpectrumFileException> but was: <java\.lang\.IllegalArgumentException>' \
        '                PdvSpectrumSupport.forVersion(request.pdvVersion()).check(request.spectrumFile());' \
        '                SpectrumFileFormat.ofFileName(
                                request.spectrumFile().getFileName().toString())
                        .orElseThrow();'
    assert_testcase "an unsupported format refused before anything is launched" failed \
        "${T_FIGURES}" formatGateBeforeLaunch
    assert_testcase "a supported source still draws (stays green)" passed "${T_FIGURES}" drawn
    restore_pristine "${FIGURES}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [NEW]: mzXML marked supported by PDV 2.7.0"
    inject_and_run "mzXML offered to PDV" "${SUPPORT}" "${TOOLS}" "${SEL_SUPPORT}" regex \
        'AssertionFailedError: expected: <\[(MZML, MGF|MGF, MZML)\]> but was: <\[MZML, MZXML, MGF\]>' \
        '                        ? EnumSet.of(SpectrumFileFormat.MZML, SpectrumFileFormat.MGF)' \
        '                        ? EnumSet.of(
                                SpectrumFileFormat.MZML,
                                SpectrumFileFormat.MGF,
                                SpectrumFileFormat.MZXML)'
    # The expected set is a Set.of, whose iteration order is salted per JVM:
    # either order of the two is matched.
    assert_testcase "the supported set" failed "${T_SUPPORT}" supported
    assert_testcase "the unsupported formats, mzXML among them" failed "${T_SUPPORT}" unsupportedFormats
    assert_testcase "an mzML and an MGF accepted (stays green)" passed "${T_SUPPORT}" accepted
    restore_pristine "${SUPPORT}"
    end_control
}

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 3 K2]: the launcher's other-program refusal removed"
    inject_and_run "a command not launching the JAR started and recorded" "${LAUNCHES}" "${WORKFLOW}" "${SEL_LAUNCHES}" regex \
        'message: Expected java\.io\.IOException to be thrown, but nothing was thrown\.' \
        '            if (!command.argv().contains(jar.toString())) {' \
        '            if (command.argv().isEmpty()) {'
    assert_testcase "another program refused" failed "${T_LAUNCHES}" anotherProgramIsRefused
    assert_testcase "a PDV launch still recorded first (stays green)" passed "${T_LAUNCHES}" theRecordPrecedesTheStart
    restore_pristine "${LAUNCHES}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [NEW]: PDV started before its launch is recorded"
    inject_and_run "the start before the record" "${LAUNCHES}" "${WORKFLOW}" "${SEL_LAUNCHES}" regex \
        'AssertionFailedError: recorded first ==> expected: <\[1\]> but was: <\[0\]>' \
        '            try (ProvenanceEventLog log =
                    ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
                log.append(ProvenanceEventType.PDV_LAUNCHED, payload);
            }
            return processes.start(command, listener);' \
        '            RunningProcess started = processes.start(command, listener);
            try (ProvenanceEventLog log =
                    ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
                log.append(ProvenanceEventType.PDV_LAUNCHED, payload);
            }
            return started;'
    assert_testcase "the record precedes the start" failed "${T_LAUNCHES}" theRecordPrecedesTheStart
    assert_testcase "a version mismatch still refused (stays green)" passed "${T_LAUNCHES}" aVersionMismatchIsRefused
    restore_pristine "${LAUNCHES}"
    end_control
}

control_4c() {
    begin_control "4c" "item 4 [NEW]: the JAR's MD5 recorded as its SHA-256"
    inject_and_run "pdv.jar.sha256 carrying the MD5" "${LAUNCHES}" "${WORKFLOW}" "${SEL_LAUNCHES}" regex \
        'AssertionFailedError: expected: <[0-9a-f]{64}> but was: <[0-9a-f]{32}>' \
        '            payload.put(JAR_SHA256, jarHashes.sha256());' \
        '            payload.put(JAR_SHA256, jarHashes.md5());'
    assert_testcase "the JAR's independent SHA-256 in the record" failed "${T_LAUNCHES}" theRecordPrecedesTheStart
    assert_testcase "another program still refused (stays green)" passed "${T_LAUNCHES}" anotherProgramIsRefused
    restore_pristine "${LAUNCHES}"
    end_control
}

control_4d() {
    begin_control "4d" "item 4 [NEW]: the manifest-version check removed"
    inject_and_run "a JAR reporting another version launched" "${LAUNCHES}" "${WORKFLOW}" "${SEL_LAUNCHES}" regex \
        'message: Expected java\.io\.IOException to be thrown, but nothing was thrown\.' \
        '            if (!reported.equals(pdv.version())) {' \
        '            if (reported == null) {'
    assert_testcase "a version mismatch refused" failed "${T_LAUNCHES}" aVersionMismatchIsRefused
    assert_testcase "the record of a matching JAR stays green" passed "${T_LAUNCHES}" theRecordPrecedesTheStart
    restore_pristine "${LAUNCHES}"
    end_control
}

control_5a() {
    begin_control "5a" "item 5 [recorded, unit 7 Q2]: the denovo-gui command given port + 1"
    inject_and_run "PDV told to listen where nobody polls" "${COMMAND}" "${TOOLS}" "${SEL_COMMAND}" regex \
        '--port, 41547\]> but was: <\[.*--port, 41548\]>' \
        '        argv.add(Integer.toString(port));' \
        '        argv.add(Integer.toString(port + 1));'
    assert_testcase "the product command's --port" failed "${T_COMMAND}" theProductCommand
    assert_testcase "the port range check stays green" passed "${T_COMMAND}" thePortRange
    restore_pristine "${COMMAND}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [NEW]: the controller sends the NEXT row's reference"
    # PDV answers 200 selected for the neighbouring row, so neither the control
    # API nor the GUI test can see it; the in-JVM observer of the real PDV reads
    # which row it drew.
    inject_and_run "the selection sent one row on" "${CONTROLLER}" "${TOOLS}" "${SEL_OBSERVED}" regex \
        'message: PDV drew nothing on row 0 within PT1M; seen: \[PDVOBS displayed.row=1[^0-9]' \
        '                                        + URLEncoder.encode(sending.ref(), StandardCharsets.UTF_8),' \
        '                                        + URLEncoder.encode(
                                                sending.ref()
                                                                .substring(
                                                                        0,
                                                                        sending.ref()
                                                                                        .lastIndexOf(
                                                                                                '"'"'='"'"')
                                                                                + 1)
                                                        + (Long.parseLong(
                                                                        sending.ref()
                                                                                .substring(
                                                                                        sending.ref()
                                                                                                        .lastIndexOf(
                                                                                                                '"'"'='"'"')
                                                                                                + 1))
                                                                + 1),
                                                StandardCharsets.UTF_8),'
    assert_testcase "the real PDV, observed: each selection draws that PSM" failed \
        "${T_OBSERVED}" eachSelectionDrawsThatPsm
    assert_testcase "an unknown reference is still 404 (stays green)" passed \
        "${T_OBSERVED}" anUnknownReferenceIsNotFound
    restore_pristine "${CONTROLLER}"
    end_control
}

control_5c() {
    begin_control "5c" "item 5 [NEW]: a window's shutdown no longer ends PDV's process"
    inject_and_run "/shutdown alone, the process left running" "${CONTROLLER}" "${TOOLS}" "${SEL_SHUTDOWN}" regex \
        'its process had not ended 10\.0 s after it was told to stop\. ==> expected: <CLOSED> but was: <FAILED>' \
        '            last.process.requestCancellation();
' \
        ''
    assert_testcase "shutdown ends the process" failed "${T_CONTROLLER}" shutdownEndsTheProcess
    assert_testcase "a window whose PDV already died closes (stays green)" passed "${T_CONTROLLER}" shutdownAfterDeath
    restore_pristine "${CONTROLLER}"
    end_control
}

control_5d() {
    begin_control "5d" "item 5 [recorded, unit 7 Q1]: another Results run no longer closes the window"
    inject_and_run "the window kept open for a run no longer shown" "${VIEW_MODEL}" "${UI}" "${SEL_WINDOW_VM}" regex \
        'expected: <PDV.s window for run run-1 was closed: the Results section no longer shows that run\.> but was: <PDV is ready on loopback port' \
        '                            runChanged(after);
' \
        ''
    assert_testcase "another run closes the window" failed "${T_WINDOW_VM}" anotherRunClosesTheWindow
    assert_testcase "a stale opening is closed" failed "${T_WINDOW_VM}" aStaleOpeningIsClosed
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_5e() {
    begin_control "5e" "item 5 [NEW]: the application's stop() no longer closes PDV's windows"
    inject_and_run "PDV outliving the application" "${APPLICATION}" "${APP}" "${SEL_LIVE_WINDOW}" regex \
        'message: PDV ended \(1 failure\)|AssertionFailedError: the launched process ended ==> expected: <false> but was: <true>' \
        '        if (visualisation != null) {
            visualisation.closeAllWindows();
        }
' \
        ''
    assert_testcase "the live-window GUI test" failed "${T_LIVE_WINDOW}" theLiveWindow
    restore_pristine "${APPLICATION}"
    end_control
}

# Control 6's anchors: the row's fields, in mzTab column order.
readonly ROW_SCORES='                                row.scoreText(),
                                row.qValue().text(),
                                row.posteriorErrorProbabilityText(),
                                hit.modifications(),'

control_6a() {
    begin_control "6a" "item 6 [recorded, unit 6 N1]: score and q-value columns swapped"
    inject_and_run "score and q-value transposed" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'target PSM 20100614_Velos1_TaGe_SA_K562_3_11367_2_1: search_engine_score\[1\] is "0\.00112905", the source.s score is "2\.37114"' \
        "${ROW_SCORES}" \
        '                                row.qValue().text(),
                                row.scoreText(),
                                row.posteriorErrorProbabilityText(),
                                hit.modifications(),'
    assert_testcase "the K562 scores against Percolator's table" failed "${T_K562}" scores
    assert_testcase "the charge stays green" passed "${T_K562}" charge
    restore_pristine "${EXPORTER}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [NEW]: the PEP column fed the q-value"
    inject_and_run "PEP transcribed from the q-value" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'target PSM 20100614_Velos1_TaGe_SA_K562_3_11367_2_1: search_engine_score\[3\] is "0\.00112905", the source.s posterior_error_prob is "9\.27998e-07"' \
        "${ROW_SCORES}" \
        '                                row.scoreText(),
                                row.qValue().text(),
                                row.qValue().text(),
                                hit.modifications(),'
    assert_testcase "the K562 scores against Percolator's table" failed "${T_K562}" scores
    assert_testcase "the accessions stay green" passed "${T_K562}" accessions
    restore_pristine "${EXPORTER}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 5 M2]: accessions cut to the first protein"
    inject_and_run "only the first protein written" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'accession as Percolator.s proteinIds: 6670 compared, 402 failed' \
        '            return text(String.join(ACCESSION_SEPARATOR, proteins), row.psmId(), file, "accession");' \
        '            return text(
                    String.join(ACCESSION_SEPARATOR, proteins.subList(0, 1)),
                    row.psmId(),
                    file,
                    "accession");'
    assert_testcase "the K562 accessions against Percolator's proteinIds" failed "${T_K562}" accessions
    assert_testcase "the scores stay green" passed "${T_K562}" scores
    restore_pristine "${EXPORTER}"
    end_control
}

control_6d() {
    begin_control "6d" "item 6 [NEW]: a static modification's delta rounded to two places"
    inject_and_run "+57.021464 written +57.02" "${PSM_HIT}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'residue 4 \(C\) lacks the fixed 57\.021464: EEEC\[\+57\.02\]HFYAGGQVYPGEASR' \
        '                String delta = signed(residue.staticDelta().get().text());' \
        '                String delta =
                        signed(
                                new java.math.BigDecimal(residue.staticDelta().get().text())
                                        .setScale(2, java.math.RoundingMode.HALF_UP)
                                        .toPlainString());'
    assert_testcase "the K562 modifications against the pepXML" failed "${T_K562}" modificationsAgainstPepXml
    assert_testcase "the scores stay green" passed "${T_K562}" scores
    restore_pristine "${PSM_HIT}"
    end_control
}

control_6e() {
    begin_control "6e" "item 6 [NEW]: the decoy flag written 0 for every row"
    inject_and_run "every row a target" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'counts differ: the sources hold 7 target and 2 decoy PSMs, the mzTab 9 and 0' \
        '                                t == 1 ? "1" : "0",' \
        '                                "0",'
    assert_testcase "every K562 PSM exactly once, none invented" failed "${T_K562}" everyPsmExactlyOnce
    assert_testcase "target and decoy rows sharing a PSMId" failed "${T_CONSTRUCTED}" sharedPsmIds
    assert_log_contains "the K562 decoy rows have vanished as decoys: no row to compare them against" "${DIRTY_LOG}" \
        'charge as pepXML assumed_charge: 3897 compared, 1 failed'
    assert_log_contains "  ... all 2773 of them" "${DIRTY_LOG}" \
        '2773 joined source PSMs have no mzTab row to compare'
    restore_pristine "${EXPORTER}"
    end_control
}

# Control 7's second anchor: the exporter's read-back of its own references,
# which would otherwise refuse every wrong reference itself.  Switched off so
# that the FIDELITY SUITE is what catches the reference.
readonly READ_BACK='                        if (s == null
                                || !ref.substring(at + "]:index=".length())
                                        .equals(Long.toString(next[s]))) {'
readonly READ_BACK_OFF='                        if (s == null) {'
readonly ROW_REF='                                "ms_run[" + run + "]:index=" + index,'

control_7a() {
    begin_control "7a" "item 7 [NEW]: spectra_ref by instrument scan number (the exporter's read-back off)"
    inject_and_run "the reference by scan" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'spectra_ref ms_run\[1\]:index=11367 is beyond ms_run-1\.mgf.s 3638 entries' \
        "${ROW_REF}" \
        '                                "ms_run[" + run + "]:index=" + hit.startScan(),' \
        "${READ_BACK}" "${READ_BACK_OFF}"
    assert_testcase "every spectra_ref resolves to its producing spectrum" failed \
        "${T_K562}" spectraRefIsTheProducingSpectrum
    assert_testcase "position and scan still differ in the file (stays green)" passed \
        "${T_K562}" positionAndScanDiffer
    restore_pristine "${EXPORTER}"
    end_control
}

control_7b() {
    begin_control "7b" "item 7 [NEW]: spectra_ref by 1-based file position (the exporter's read-back off)"
    inject_and_run "the reference by file position" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'ms_run\[1\]:index=181 \(entry 181\) states CHARGE=3\+, the PSM.s assumed_charge is 2' \
        '        private final long[] scan;
        private final Path file;' \
        '        private final long[] scan;
        private final long[] position;
        private final Path file;' \
        '            this.scan = new long[slotOf.size()];' \
        '            this.scan = new long[slotOf.size()];
            this.position = new long[slotOf.size()];' \
        '                            spectra.scan[slot] = spectrum.scanNumber().orElse(-1L);' \
        '                            spectra.scan[slot] = spectrum.scanNumber().orElse(-1L);
                            spectra.position[slot] = spectrum.position();' \
        "${ROW_REF}" \
        '                                "ms_run[" + run + "]:index=" + spectrum.position[slot],' \
        "${READ_BACK}" "${READ_BACK_OFF}"
    assert_testcase "every spectra_ref resolves to its producing spectrum" failed \
        "${T_K562}" spectraRefIsTheProducingSpectrum
    assert_testcase "position and scan still differ in the file (stays green)" passed \
        "${T_K562}" positionAndScanDiffer
    restore_pristine "${EXPORTER}"
    end_control
}

control_7c() {
    begin_control "7c" "item 7 [NEW]: spectra_ref to the first row of a shared spectrum (the exporter's read-back off)"
    inject_and_run "the reference of a spectrum's first row" "${EXPORTER}" "${TOOLS}" "${SEL_FIDELITY}" regex \
        'target PSM 20100614_Velos1_TaGe_SA_K562_3_11460_3_2: spectra_ref ms_run\[1\]:index=7 is also target PSM 20100614_Velos1_TaGe_SA_K562_3_11460_3_1.s, and PDV selects only the first row of a reference' \
        '            private final ByteBuffer prefix = ByteBuffer.allocate(Integer.BYTES);' \
        '            private final ByteBuffer prefix = ByteBuffer.allocate(Integer.BYTES);
            private final Map<Long, Long> firstEntry = new HashMap<>();' \
        "${ROW_REF}" \
        '                                "ms_run["
                                        + run
                                        + "]:index="
                                        + firstEntry.computeIfAbsent(
                                                ((long) located.source << 32) | slot,
                                                key -> index),' \
        "${READ_BACK}" "${READ_BACK_OFF}"
    assert_testcase "every spectra_ref resolves to its producing spectrum" failed \
        "${T_K562}" spectraRefIsTheProducingSpectrum
    assert_testcase "position and scan still differ in the file (stays green)" passed \
        "${T_K562}" positionAndScanDiffer
    restore_pristine "${EXPORTER}"
    end_control
}

control_8a() {
    begin_control "8a" "item 8 [recorded, unit 5 M1]: the C-terminal refusal disabled"
    inject_and_run "a C-terminal modification exported" "${PSM_HIT}" "${TOOLS}" "${SEL_UNREPRESENTABLE}" regex \
        'the message names the reason UNREPRESENTABLE_MODIFICATION: The mzTab for PDV was not written: PSM 20100614_Velos1_TaGe_SA_K562_3_11367_2_1 has the Percolator peptide' \
        '            if (info.ctermMass().isPresent()) {' \
        '            if (info.ctermMass().isPresent() && info.ctermMass().isEmpty()) {'
    assert_testcase "a C-terminal modification injected into copies of the real sources" failed \
        "${T_UNREPRESENTABLE}" cTerminalModification
    assert_testcase "the exporter's own refusal of it" failed "${T_EXPORTER}" unrepresentableModification
    assert_testcase "a Percolator row without a hit still refused (stays green)" passed \
        "${T_UNREPRESENTABLE}" percolatorRowWithoutHit
    restore_pristine "${PSM_HIT}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [NEW]: the PSM's name dropped from the refusal's message"
    inject_and_run "an anonymous refusal" "${REFUSAL}" "${TOOLS}" "${SEL_UNREPRESENTABLE}" regex \
        'the message names the PSM 20100614_Velos1_TaGe_SA_K562_3_11367_2_1: The mzTab for PDV was not written: a PSM ' \
        '            text.append("PSM ").append(psmId).append('"' '"');' \
        '            text.append("a PSM ");'
    assert_testcase "a Percolator row without a hit, named" failed "${T_UNREPRESENTABLE}" percolatorRowWithoutHit
    assert_testcase "a C-terminal modification, named" failed "${T_UNREPRESENTABLE}" cTerminalModification
    assert_testcase "a missing spectrum, named" failed "${T_UNREPRESENTABLE}" spectrumMissing
    restore_pristine "${REFUSAL}"
    end_control
}

control_8c() {
    begin_control "8c" "item 8 [NEW]: a refused export leaves its staging directory behind"
    inject_and_run "the staging directory not deleted" "${EXPORTER}" "${TOOLS}" "${SEL_UNREPRESENTABLE}" regex \
        'nothing at all is left beside it ==> expected: <\[\]> but was: <\[\.mztab-staging-[0-9]+\]>' \
        '            if (!moved) {
                deleteTree(staging);
            }' \
        '            if (!moved && staging == null) {
                deleteTree(staging);
            }'
    assert_testcase "the C-terminal refusal leaves nothing" failed "${T_UNREPRESENTABLE}" cTerminalModification
    assert_testcase "the exporter's own refusals leave nothing" failed "${T_EXPORTER}" noHit
    restore_pristine "${EXPORTER}"
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

readonly H_FILE="${SUPPORT}"
readonly H_ANCHOR='                        ? EnumSet.of(SpectrumFileFormat.MZML, SpectrumFileFormat.MGF)'
readonly H_TEST="${TOOLS}/src/test/java/org/cometgui/tools/pdv/${T_SUPPORT}.java"

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
        'MZXML'

    log="${LOGS}/H6-wrong-reason.log"
    printf 'FAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red regex "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'MZXML'

    USED_SELECTORS+=("${SEL_H}")
    clean_targets
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1a) control_1a ;; 1b) control_1b ;;
        2a) control_2a ;; 2b) control_2b ;; 2c) control_2c ;;
        3a) control_3a ;; 3b) control_3b ;;
        4a) control_4a ;; 4b) control_4b ;; 4c) control_4c ;; 4d) control_4d ;;
        5a) control_5a ;; 5b) control_5b ;; 5c) control_5c ;; 5d) control_5d ;; 5e) control_5e ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;; 6d) control_6d ;; 6e) control_6e ;;
        7a) control_7a ;; 7b) control_7b ;; 7c) control_7c ;;
        8a) control_8a ;; 8b) control_8b ;; 8c) control_8c ;;
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
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} (see docs/developer/pdv_integration.rst, 'Files the tests read outside their module')")
    done
    for needed in "${TOOLS_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} (the project-local display: bash scripts/feasibility/javafx-smoke.sh then bash scripts/feasibility/javafx-headed-xvfb.sh)")
    done
    [ -f "${ROOT}/${SHIM}" ] || missing+=("${SHIM} (Xvfb's xkbcomp path shim source)")
    command -v gcc >/dev/null || missing+=("the host's gcc on PATH (the test support compiles the xkbcomp shim with it)")
    [ -x /usr/bin/timeout ] || missing+=("/usr/bin/timeout (real PDV and Xvfb run under it in the test support)")
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
        || die "the font stack is missing or damaged; run bash scripts/fetch-fontstack.sh first. Without it every PDV and GUI control fails for the wrong reason." 3
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
    printf ' %s -- every PHASE-11 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
        die "${FAILED} pdv-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
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
    printf '\n  PHASE-11 exit gate items 1 to 8 were proved here, each by a production\n'
    printf '  defect in cometgui-tools, cometgui-workflow, cometgui-ui or cometgui-app\n'
    printf '  in a git-archive sandbox, proved in the bytecode and graded on the failing\n'
    printf '  assertion'"'"'s own words in the named testcase -- items 1 and 5 also through\n'
    printf '  the headless GUI with real Comet, Percolator and PDV, items 2 and 5 against\n'
    printf '  the real PDV 2.7.0, and items 6 to 8 against the real K562 sources.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
