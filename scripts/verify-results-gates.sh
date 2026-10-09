#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-10 results model and UI gates can fail.
#
#   bash scripts/verify-results-gates.sh                every control
#   bash scripts/verify-results-gates.sh --self-test    control H only
#   bash scripts/verify-results-gates.sh --only 1a,6b,H named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 10's exit gate is eight claims
# about Percolator's results as CometGUI shows and exports them -- both default
# filters 0.01, independent, inclusive and range-checked; a filter change that
# launches no process; displayed counts equal to independent counts; raw files
# byte-identical after filtering and export; an export's metadata; the large
# fixture within its budget with the interface bound to a page; the learned
# weights against an independent computation; and the unknown-q-value category.
# Each is checked by tests in cometgui-results, cometgui-workflow, cometgui-ui
# and cometgui-app, and a green test says nothing about whether it would notice
# the defect it exists to catch.  So this script injects, one at a time, a
# defect each item exists to catch -- into PRODUCTION code of a `git archive
# HEAD` sandbox -- proves the injection reached the compiled class, runs the
# narrowest test selection that should catch it, requires it red WITH THAT
# DEFECT'S OWN DIAGNOSTIC in the named testcase -- and, where a test can show
# it, requires a sibling testcase the defect does not touch to STAY GREEN --
# then restores the file and requires the clean sandbox green again on the
# baseline's bytecode.
#
# It is the sibling of scripts/verify-percolator-gates.sh and takes its shape
# from it: a git-archive sandbox, anchors that must match exactly once, a
# pristine copy per damaged file, bytecode digests, graded failures read from
# surefire's XML, one closed module set per control (-pl TOP -am, nothing
# installed), one batched clean re-run, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-10-worklog.rst whose sign-off made that injection (or
# whose agent did, as that sign-off lists) and saw it red.  [NEW] marks this
# script's own (unit 11): a defect the unit-11 brief names for its gate item,
# held to the same standard and labelled so nobody mistakes it for history.
#
# WHAT IT COVERS (PHASE-10 exit gate items; see phases/PHASE-10-results.rst)
#
#   0   baseline: the undamaged sandbox passes every graded selection in one
#       run, and every compiled module is digested
#   1a  item 1 [recorded, unit 2]: the one q-value predicate made exclusive
#       (q < cutoff) -- the rows at exactly the cutoff fail it, in both stores
#       and in the predicate's own tests; the unknown category stays green
#   1b  item 1 [NEW]: a refused filter text still changes the filter (the PSM
#       filter reset to the default) -- the view-model's refusal test red, its
#       accepted-value test green
#   1c  item 1 [NEW]: the peptide filter's edit also sets the PSM filter (the
#       filters no longer independent) -- red in the view-model's independence
#       test; the refusal test green
#   1d  item 1 [NEW]: the range check widened to [0, 10] -- 1.01 accepted, in
#       the predicate's range tests and the view-model's refusal; a non-number
#       still refused
#   2   item 2 [recorded, unit 8]: a listener on the one display-filter state
#       launches /bin/true through the application's one process runner -- the
#       GUI gate test's runner record grows
#   3a  item 3 [recorded, unit 3]: the disk store's count loop skips the last
#       row -- red against the independent counts; the in-memory store green
#   3b  item 3 [NEW]: the in-memory count tally counts failing rows as passing
#       -- red against the independent counts; the disk store green
#   3c  item 3 [recorded, unit 9]: the Passing count on screen bound to the
#       failing count -- red in the GUI's displayed-counts test
#   4a  item 4 [recorded, unit 6]: the export opens the raw table for writing
#       as well as reading -- refused by the read-only raw output under
#       outputs/; an export from a writable table elsewhere stays green
#   4b  item 4 [NEW]: finalise-results writes the store's index under outputs/
#       instead of results/index/; a table small enough to stay in memory green
#   5a  item 5 [recorded, units 6 and 9]: the sidecar's cutoff taken from the
#       default filter rather than the applied one
#   5b  item 5 [recorded, unit 6]: the sidecar's rowsWritten one too many
#   6a  item 6 [NEW]: the store factory never switches to the disk store -- the
#       -Xmx64m child opening the 1 000 000-row fixture dies of
#       OutOfMemoryError; the readAll negative control stays as it was
#   6b  item 6 [recorded, unit 8]: the results table's items accumulate
#       (addAll for setAll) -- the large-fixture GUI test sees more than one
#       page held
#   6c  item 6 [recorded, unit 7]: the table view-model asks the store for
#       every row (up to the store's maximum) as one page
#   7a  item 7 [recorded, unit 9]: the sample standard deviation (n - 1)
#   7b  item 7 [recorded, unit 4]: the mean absolute weight sums the signed
#       values, so ranks follow the signed mean
#   7c  item 7 [NEW]: equal mean absolute weights no longer share a rank (ties
#       broken by file order) -- red only where the tests have ties; the real
#       K562 files, which have none, stay green
#   7d  item 7 [NEW]: the summary divides by three splits whatever the file
#       holds -- the two- and four-split files red, the real three-split files
#       green
#   7e  item 7 [NEW]: a zero weight counted as positive in the sign
#       consistency
#   8a  item 8 [recorded, unit 6]: unknown-q-value rows written into the
#       passing export; the unknown export stays green
#   8b  item 8 [NEW]: an empty q-value classed as failing rather than unknown
#       -- red in both stores' unknown category and in the unknown export; a
#       known q-value at the boundary stays green
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
#   * Windows and macOS: nothing here has run anywhere but Linux.
#   * Item 8's "identically in UI and export" is one predicate and one category
#     rule shared by store and exporter (P10-1, P10-5); 8a damages the
#     exporter alone and 8b the shared predicate.  A defect confined to the
#     results PANE's unknown label is caught by the GUI tests unit 9 recorded
#     (ResultsExportUiTest), which this script does not run, to save a 35 s
#     class in three runs.
#   * The GUI export tests (ResultsExportUiTest, gates 4, 5 and 8 through the
#     interface), the GUI counts test on the real K562 run (ResultsCountsUiTest's
#     K562 methods, 75 s), the weights GUI test (ResultsWeightsUiTest) and the
#     real Comet + Percolator run (RealRunUiTest): each was seen red by unit 9's
#     sign-off; items 4, 5, 7 and 8 are graded here on the unit tests below
#     them, items 2, 3 and 6 also through the headless GUI.
#   * Timing budgets themselves: DiskStoreBudgetTest's time limits are graded
#     only as far as control 6a's child running out of heap.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/results-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a POM
# are reported loudly.  scratch/ (the large fixture under scratch/phase10/large
# and the real K562 Percolator outputs under scratch/scientific-path -- all
# gitignored, and D-006 forbids committing the second) is symlinked in, and
# tools/ (the font stack the headless JavaFX tests need) likewise.
#
# ONE CLOSED MODULE SET PER CONTROL.  Each control names the top module of the
# narrowest set that holds both its damaged file and its graded tests, and
# every run is `mvn -o -pl TOP -am test`: the damaged module and everything
# downstream of it up to TOP are compiled from the sandbox in the same reactor,
# so a test never sees an undamaged copy from a repository.  Nothing is ever
# installed: the shared _build/m2repo is only read, and its org/cometgui jars
# are digested before and after.  The module sets used here nest
# (cometgui-results < cometgui-workflow < cometgui-ui < cometgui-app), so the
# baseline and the clean re-run use the largest set any selected control needs.
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
#     to be there (as scripts/verify-percolator-gates.sh does).
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
# populated _build/m2repo, scratch/phase10/large (made by
# python3 scripts/fixtures/large-results-fixture.py) and the real K562 outputs
# under scratch/scientific-path/percolator-3.07.1 and percolator-3.09.
# Offline; writes only under _build/.
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
readonly SANDBOX="${ROOT}/_build/results-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/results-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/results-gate-logs"

# The gitignored fixtures the graded tests read; they fail rather than skip
# without them.  How to make or refill each is in
# docs/developer/results_model.rst and in the tests' own failure messages.
readonly -a SCRATCH_NEEDED=(
    "scratch/phase10/large/psms.tsv"
    "scratch/phase10/large/peptides.tsv"
    "scratch/phase10/large/manifest.json"
    "scratch/scientific-path/percolator-3.07.1/psms.target.txt"
    "scratch/scientific-path/percolator-3.07.1/peptides.target.txt"
    "scratch/scientific-path/percolator-3.07.1/psms.decoy.txt"
    "scratch/scientific-path/percolator-3.07.1/peptides.decoy.txt"
    "scratch/scientific-path/percolator-3.07.1/weights.txt"
    "scratch/scientific-path/percolator-3.09/psms.target.txt"
    "scratch/scientific-path/percolator-3.09/psms.decoy.txt"
)
readonly FONTSTACK="tools/fontstack-bookworm-20260829/root"
# The one normalised file (see the header).
readonly IDENTITY="cometgui-app/org/cometgui/app/config/build-identity.properties"
readonly IDENTITY_LINE='^cometgui\.buildTimestamp='

# The module sets (the top of each `-pl TOP -am`), smallest first: each holds
# the one before it.
readonly RESULTS="cometgui-results"
readonly WORKFLOW="cometgui-workflow"
readonly UI="cometgui-ui"
readonly APP="cometgui-app"
readonly -a NESTED_TOPS=("${RESULTS}" "${WORKFLOW}" "${UI}" "${APP}")

# The production files the controls damage.
readonly RF="${RESULTS}/src/main/java/org/cometgui/results/filtering"
readonly PREDICATE="${RF}/QValueFilter.java"
readonly TALLY="${RF}/FilterTally.java"
readonly DISK_STORE="${RF}/store/DiskResultStore.java"
readonly STORES="${RF}/store/ResultStores.java"
readonly EXPORTER="${RESULTS}/src/main/java/org/cometgui/results/export/ResultExporter.java"
readonly SUMMARY="${RESULTS}/src/main/java/org/cometgui/results/parser/WeightsSummary.java"
readonly RESULT_STEPS="${WORKFLOW}/src/main/java/org/cometgui/workflow/steps/ResultSteps.java"
readonly UVR="${UI}/src/main/java/org/cometgui/ui/viewmodel/results"
readonly FILTERS_VM="${UVR}/DisplayFiltersViewModel.java"
readonly TABLE_VM="${UVR}/ResultTableViewModel.java"
readonly PANE="${UI}/src/main/java/org/cometgui/ui/controls/results/ResultsPane.java"
readonly APPLICATION="${APP}/src/main/java/org/cometgui/app/bootstrap/CometGuiApplication.java"

# The graded test classes.
readonly T_PREDICATE="QValueFilterTest"
readonly T_STATUS="QValueFilterStatusTest"
readonly T_MEMORY="InMemoryResultStoreTest"
readonly T_DISK="DiskResultStoreTest"
readonly T_BUDGET="DiskStoreBudgetTest"
readonly T_EXPORT="TableExportBehaviourTest"
readonly T_EXPORT_GATE="TableExportGateTest"
readonly T_SUMMARY="WeightsSummaryTest"
readonly T_SUMMARY_INDEPENDENT="WeightsSummaryIndependentTest"
readonly T_FINALISE="FinaliseResultsTest"
readonly T_FILTERS_VM="DisplayFiltersViewModelTest"
readonly T_TABLE_VM="ResultTableViewModelTest"
readonly T_NO_PROCESS="ResultsNoProcessUiTest"
readonly T_COUNTS_UI="ResultsCountsUiTest"
readonly T_LARGE_UI="ResultsLargeFixtureUiTest"
# A parameterised method's invocations by their surefire names.
readonly CHECKED_IN="checkedInFiles(String, String, int, List)"

# Phase 01's gates have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run: the cheap module sets first.
readonly -a ALL_CONTROLS=(1a 3a 3b 4a 5a 5b 6a 7a 7b 7c 7d 7e 8a 8b 4b 1b 1c 1d 6c 2 3c 6b H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a USED_SELECTORS=()
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the eight PHASE-10 exit gate items fail on the defects
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

It needs a built tree: tools/ (JDK, Maven, the font stack), a populated
_build/m2repo, scratch/phase10/large (python3
scripts/fixtures/large-results-fixture.py makes it) and the real K562
Percolator outputs under scratch/scientific-path.  It runs Maven offline,
damages only a git-archive sandbox under _build/, and writes only under
_build/.

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
    # scratch/ is gitignored: the large fixture and the real K562 outputs.  The
    # tests find it through the repository root above their working directory,
    # which is the sandbox.  None of the graded tests writes there.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    # tools/ is gitignored too, and the headless JavaFX tests resolve the font
    # stack through the sandbox's own project directory.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    local needed
    for needed in "${SCRATCH_NEEDED[@]}"; do
        [ -e "${SANDBOX}/${needed}" ] \
            || harness_error "the sandbox's scratch/ symlink does not resolve ${needed}. Every control grading a test that reads it would fail for the wrong reason."
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
readonly SEL_BOUNDARY="${T_MEMORY}#inclusiveAtTheCutoff+unknownKindsInTheirOwnCategory,${T_DISK}#inclusiveAtTheCutoff,${T_PREDICATE}\$Boundary#atDefault+zero+one"
readonly SEL_COUNTS="${T_MEMORY}#countsEqualIndependentCounts,${T_DISK}#countsEqualIndependentCounts"
readonly SEL_RAW_READ="${T_EXPORT}#aSourceInsideTheRunIsRelativeAndOnlyRead+theNameIsPinned"
readonly SEL_EXPORT_GATE="${T_EXPORT_GATE}#everyCategoryAtEveryCutoff+theUnknownExportHoldsEveryUnknownKind"
readonly SEL_BUDGET="${T_BUDGET}#largeFixtureWithinBudget+readAllDoesNotFit"
readonly SEL_WEIGHTS="${T_SUMMARY_INDEPENDENT}#checkedInFiles+realK562,${T_SUMMARY}\$Ties#everyRow+splitCount,${T_SUMMARY}\$Inline#mixedWithZero"
readonly SEL_UNKNOWN="${T_MEMORY}#unknownKindsInTheirOwnCategory,${T_DISK}#unknownKindsInTheirOwnCategory,${T_EXPORT_GATE}#theUnknownExportHoldsEveryUnknownKind,${T_PREDICATE}\$Boundary#atDefault"
readonly SEL_FINALISE="${T_FINALISE}#aTableAboveTheLimitIsIndexedUnderResults+aTableAtTheLimitStaysInMemory"
readonly SEL_FILTERS_VM="${T_FILTERS_VM}#defaults+bothEndsAndIndependence+refused,${T_PREDICATE}\$Range#outOfRange+notANumber"
readonly SEL_PAGE_VM="${T_TABLE_VM}\$Page#paging+pageBound"
readonly SEL_NO_PROCESS="${T_NO_PROCESS}"
readonly SEL_COUNTS_UI="${T_COUNTS_UI}#theSyntheticRun"
readonly SEL_LARGE_UI="${T_LARGE_UI}"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H4 a selection naming a method that does not exist.
readonly SEL_H="${T_STATUS}#inclusiveBoundary+agreesWithTheQValueForm"
readonly SEL_H4="${T_STATUS}#noSuchTestMethodInThisClass"

control_selectors() {
    case "$1" in
        1a) printf '%s' "${SEL_BOUNDARY}" ;;
        1b|1c|1d) printf '%s' "${SEL_FILTERS_VM}" ;;
        2) printf '%s' "${SEL_NO_PROCESS}" ;;
        3a|3b) printf '%s' "${SEL_COUNTS}" ;;
        3c) printf '%s' "${SEL_COUNTS_UI}" ;;
        4a) printf '%s' "${SEL_RAW_READ}" ;;
        4b) printf '%s' "${SEL_FINALISE}" ;;
        5a|5b|8a) printf '%s' "${SEL_EXPORT_GATE}" ;;
        6a) printf '%s' "${SEL_BUDGET}" ;;
        6b) printf '%s' "${SEL_LARGE_UI}" ;;
        6c) printf '%s' "${SEL_PAGE_VM}" ;;
        7a|7b|7c|7d|7e) printf '%s' "${SEL_WEIGHTS}" ;;
        8b) printf '%s' "${SEL_UNKNOWN}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

control_top() {
    case "$1" in
        1a|3a|3b|4a|5a|5b|6a|7a|7b|7c|7d|7e|8a|8b|H) printf '%s' "${RESULTS}" ;;
        4b) printf '%s' "${WORKFLOW}" ;;
        1b|1c|1d|6c) printf '%s' "${UI}" ;;
        2|3c|6b) printf '%s' "${APP}" ;;
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
    record_pass "baseline: exit 0, 0 failed testcases, every graded selection executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree "${RUN_MODULES}"
    end_control
}

# ------------------------------------------------------------- the controls --

control_1a() {
    begin_control "1a" "item 1 [recorded, unit 2]: the one q-value predicate made exclusive (q < cutoff)"
    inject_and_run "a q-value equal to the cutoff fails it" "${PREDICATE}" "${RESULTS}" "${SEL_BOUNDARY}" fixed \
        'message: expected: <PASSES> but was: <FAILS>' \
        '        return value <= comparable ? Visibility.PASSES : Visibility.FAILS;' \
        '        return value < comparable ? Visibility.PASSES : Visibility.FAILS;'
    assert_testcase "the in-memory store: the rows at exactly 0.01, 0.005, 0 and 1" failed \
        "${T_MEMORY}" inclusiveAtTheCutoff
    assert_testcase "the disk store: the same rows" failed "${T_DISK}" inclusiveAtTheCutoff
    assert_testcase "the predicate's own boundary test at the default" failed "${T_PREDICATE}" atDefault
    assert_testcase "the predicate at cutoff 0 and q 0" failed "${T_PREDICATE}" zero
    # Set.of's iteration order is salted per JVM, so the six q-values passing
    # 0.01 and the five left are matched by count, not by order.
    assert_log_matches "in the stores' own words: six q-values pass 0.01 on the shuffled table, five are shown" "${DIRTY_LOG}" \
        'message: expected: <\[([^],]+, ){5}[^],]+\]> but was: <\[([^],]+, ){4}[^],]+\]>'
    assert_testcase "the unknown category does not compare and stays green" passed \
        "${T_MEMORY}" unknownKindsInTheirOwnCategory
    restore_pristine "${PREDICATE}"
    end_control
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: a refused filter text still changes the filter"
    inject_and_run "a refused PSM text resets the PSM filter to the default" "${FILTERS_VM}" "${UI}" "${SEL_FILTERS_VM}" fixed \
        'AssertionFailedError: expected: <DisplayFilters[psm=PsmQValueFilter(q <= 0.05), peptide=PeptideQValueFilter(q <= 0.01)]> but was: <DisplayFilters[psm=PsmQValueFilter(q <= 0.01), peptide=PeptideQValueFilter(q <= 0.01)]>' \
        '            psmRefusal = refused.getMessage();' \
        '            psmRefusal = refused.getMessage();
            filters.set(filters.get().withPsm(PsmQValueFilter.DEFAULT));'
    assert_testcase "refused text changes no filter" failed "${T_FILTERS_VM}" refused
    assert_log_contains "and the refused text raised a change event" "${DIRTY_LOG}" \
        'AssertionFailedError: no change event for refused text ==> expected: <[]> but was: <[DisplayFilters[psm=PsmQValueFilter(q <= 0.01)'
    assert_testcase "accepted values at both ends stay green" passed "${T_FILTERS_VM}" bothEndsAndIndependence
    restore_pristine "${FILTERS_VM}"
    end_control
}

control_1c() {
    begin_control "1c" "item 1 [NEW]: the peptide filter's edit also sets the PSM filter"
    inject_and_run "the two filters no longer independent" "${FILTERS_VM}" "${UI}" "${SEL_FILTERS_VM}" fixed \
        'message: expected: <DisplayFilters[psm=PsmQValueFilter(q <= 0), peptide=PeptideQValueFilter(q <= 1)]> but was: <DisplayFilters[psm=PsmQValueFilter(q <= 1), peptide=PeptideQValueFilter(q <= 1)]>' \
        '            filters.set(filters.get().withPeptide(filter));' \
        '            filters.set(filters.get().withPeptide(filter).withPsm(new PsmQValueFilter(filter.cutoff())));'
    assert_testcase "each filter alone, the other untouched" failed "${T_FILTERS_VM}" bothEndsAndIndependence
    assert_testcase "the refusals stay green" passed "${T_FILTERS_VM}" refused
    assert_testcase "the defaults stay green" passed "${T_FILTERS_VM}" defaults
    restore_pristine "${FILTERS_VM}"
    end_control
}

control_1d() {
    begin_control "1d" "item 1 [NEW]: the range check widened to [0, 10]"
    inject_and_run "a cutoff above 1 accepted" "${PREDICATE}" "${UI}" "${SEL_FILTERS_VM}" regex \
        'message: Expected java\.lang\.IllegalArgumentException to be thrown, but nothing was thrown\.' \
        '        if (cutoff.signum() < 0 || cutoff.compareTo(BigDecimal.ONE) > 0) {' \
        '        if (cutoff.signum() < 0 || cutoff.compareTo(BigDecimal.TEN) > 0) {'
    assert_testcase "the predicate's out-of-range cutoffs" failed "${T_PREDICATE}" outOfRange
    assert_testcase "the view-model's refusal of 1.01" failed "${T_FILTERS_VM}" refused
    assert_testcase "a cutoff that is not a number is still refused" passed "${T_PREDICATE}" notANumber
    restore_pristine "${PREDICATE}"
    end_control
}

control_2() {
    begin_control "2" "item 2 [recorded, unit 8]: a filter change launches a process through the one runner"
    # The listener sits on the one display-filter state the composition root
    # builds, and calls the application's own process service -- the runner the
    # GUI test puts a recorder in front of.
    inject_and_run "a filter listener launching /bin/true" "${APPLICATION}" "${APP}" "${SEL_NO_PROCESS}" fixed \
        'AssertionFailedError: the one process runner was called while the filters changed ==> expected: <[]> but was: <[[/bin/true]' \
        '        DisplayFiltersViewModel displayFilters = new DisplayFiltersViewModel();' \
        '        DisplayFiltersViewModel displayFilters = new DisplayFiltersViewModel();
        displayFilters
                .displayFiltersProperty()
                .addListener(
                        (seen, before, after) -> {
                            try {
                                services.requireProcessRunner()
                                        .start(
                                                new org.cometgui.domain.ports.ToolCommand(
                                                        java.util.List.of("/bin/true"),
                                                        java.nio.file.Path.of("/"),
                                                        java.util.Map.of()),
                                                new org.cometgui.domain.ports.ProcessListener() {
                                                    @Override
                                                    public void onStandardOutput(String line) {}

                                                    @Override
                                                    public void onStandardError(String line) {}

                                                    @Override
                                                    public void onExit(int exitCode) {}
                                                });
                            } catch (java.io.IOException failed) {
                                throw new java.io.UncheckedIOException(failed);
                            }
                        });'
    assert_testcase "the gate-2 GUI test" failed "${T_NO_PROCESS}" changingTheFiltersLaunchesNoProcess
    restore_pristine "${APPLICATION}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3 [recorded, unit 3]: the disk store's count loop skips the last row"
    inject_and_run "the last row never counted on disk" "${DISK_STORE}" "${RESULTS}" "${SEL_COUNTS}" fixed \
        'counts() ==> expected: <FilterCounts[total=23, passing=2, failing=14, unknownQValue=7]> but was: <FilterCounts[total=22, passing=2, failing=13, unknownQValue=7]>' \
        '        for (long row = 0; row < rows; row++) {' \
        '        for (long row = 0; row < rows - 1; row++) {'
    assert_testcase "the disk store against the independent counts" failed "${T_DISK}" countsEqualIndependentCounts
    assert_testcase "the in-memory store stays green" passed "${T_MEMORY}" countsEqualIndependentCounts
    restore_pristine "${DISK_STORE}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [NEW]: the in-memory tally counts a failing row as passing"
    inject_and_run "failing rows counted as passing" "${TALLY}" "${RESULTS}" "${SEL_COUNTS}" fixed \
        'counts() ==> expected: <FilterCounts[total=23, passing=2, failing=14, unknownQValue=7]> but was: <FilterCounts[total=23, passing=16, failing=0, unknownQValue=7]>' \
        '            case FAILS -> failing++;' \
        '            case FAILS -> passing++;'
    assert_testcase "the in-memory store against the independent counts" failed "${T_MEMORY}" countsEqualIndependentCounts
    assert_testcase "the disk store, which counts its index itself, stays green" passed \
        "${T_DISK}" countsEqualIndependentCounts
    restore_pristine "${TALLY}"
    end_control
}

control_3c() {
    begin_control "3c" "item 3 [recorded, unit 9]: the Passing count on screen bound to the failing count"
    inject_and_run "the passing label showing the failing count" "${PANE}" "${APP}" "${SEL_COUNTS_UI}" fixed \
        'message: counts shown that differ from the independent counts ==> expected: <[]> but was: <[Target PSMs at 0: shown [64, 64, 64, 0], expected [64, 0, 64, 0]' \
        '        Label passing = count(UiIds.RESULTS_COUNT_PASSING, "Passing", TableCounts::passing);' \
        '        Label passing = count(UiIds.RESULTS_COUNT_PASSING, "Passing", TableCounts::failing);'
    assert_testcase "the displayed counts at six cutoffs on the real synthetic-PIN run" failed \
        "${T_COUNTS_UI}" theSyntheticRun
    restore_pristine "${PANE}"
    end_control
}

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 6]: the export opens the raw table for writing as well"
    inject_and_run "the raw table opened READ and WRITE" "${EXPORTER}" "${RESULTS}" "${SEL_RAW_READ}" regex \
        'AccessDeniedException: [^ ]*/outputs/percolator/psms\.tsv' \
        '                    FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {' \
        '                    FileChannel channel =
                            FileChannel.open(
                                    source, StandardOpenOption.READ, StandardOpenOption.WRITE)) {'
    assert_testcase "the read-only raw output under outputs/ refuses it" failed \
        "${T_EXPORT}" aSourceInsideTheRunIsRelativeAndOnlyRead
    assert_testcase "a writable table elsewhere cannot show it (stays green)" passed "${T_EXPORT}" theNameIsPinned
    restore_pristine "${EXPORTER}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [NEW]: finalise-results writes the store's index under outputs/"
    inject_and_run "the result index under outputs/" "${RESULT_STEPS}" "${WORKFLOW}" "${SEL_FINALISE}" regex \
        '^FAILED [^ ]*FinaliseResultsTest#aTableAboveTheLimitIsIndexedUnderResults\(Path\) \[error\] java\.nio\.file\.NoSuchFileException$' \
        '            Path index = run.layout().resultIndexDirectory();' \
        '            Path index = run.layout().outputsDirectory().resolve("index");'
    assert_testcase "a table above the limit is indexed under results/ and nowhere else" failed \
        "${T_FINALISE}" aTableAboveTheLimitIsIndexedUnderResults
    assert_log_matches "the missing file is the run's results/ directory: nothing was indexed there" "${DIRTY_LOG}" \
        '^  message: /[^ ]*/runs/[^/ ]+/results$'
    assert_testcase "a table at the limit, in memory, writes no index (stays green)" passed \
        "${T_FINALISE}" aTableAtTheLimitStaysInMemory
    restore_pristine "${RESULT_STEPS}"
    end_control
}

control_5a() {
    begin_control "5a" "item 5 [recorded, units 6 and 9]: the sidecar's cutoff from the default filter"
    inject_and_run "the default cutoff written instead of the applied one" "${EXPORTER}" "${RESULTS}" "${SEL_EXPORT_GATE}" fixed \
        'message: constructed/psms-shuffled.tsv at 0, PASSING: the cutoff applied ==> expected: <0> but was: <0.01>' \
        '                                    ExportVocabulary.filter(filter),
                                    filter.text(),' \
        '                                    ExportVocabulary.filter(filter),
                                    org.cometgui.results.filtering.QValueFilter.DEFAULT_CUTOFF
                                            .toPlainString(),'
    assert_testcase "every category at every gate cutoff, against the oracle" failed \
        "${T_EXPORT_GATE}" everyCategoryAtEveryCutoff
    assert_testcase "the unknown export's rows stay green" passed "${T_EXPORT_GATE}" theUnknownExportHoldsEveryUnknownKind
    restore_pristine "${EXPORTER}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [recorded, unit 6]: the sidecar's rowsWritten one too many"
    inject_and_run "rows written miscounted in the metadata" "${EXPORTER}" "${RESULTS}" "${SEL_EXPORT_GATE}" fixed \
        'message: constructed/psms-shuffled.tsv at 0, PASSING ==> expected: <2> but was: <3>' \
        '                                    before,
                                    written,
                                    redactor);' \
        '                                    before,
                                    written + 1,
                                    redactor);'
    assert_testcase "every category at every gate cutoff, against the oracle" failed \
        "${T_EXPORT_GATE}" everyCategoryAtEveryCutoff
    assert_log_contains "on the real 3.07.1 table, where nothing passes 0: the sidecar says one row" "${DIRTY_LOG}" \
        'message: real/percolator-3.07.1/psms.tsv at 0, PASSING ==> expected: <0> but was: <1>'
    restore_pristine "${EXPORTER}"
    end_control
}

control_6a() {
    begin_control "6a" "item 6 [NEW]: the store factory never switches to the disk store"
    inject_and_run "every table held in memory" "${STORES}" "${RESULTS}" "${SEL_BUDGET}" fixed \
        'message: the child failed: [OUT_OF_MEMORY Java heap space] ==> expected: <0> but was: <3>' \
        '        return rowsUpTo(file, limit + 1) <= limit' \
        '        return rowsUpTo(file, limit + 1) >= 0'
    assert_testcase "the 1 000 000-row fixture in the -Xmx64m child" failed "${T_BUDGET}" largeFixtureWithinBudget
    assert_testcase "the readAll negative control is unchanged (stays green)" passed "${T_BUDGET}" readAllDoesNotFit
    restore_pristine "${STORES}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 8]: the results table's items accumulate"
    inject_and_run "each page added to the table's items" "${PANE}" "${APP}" "${SEL_LARGE_UI}" fixed \
        'message: at 0: the results table holds 400 items, more than one page of 200 ==> expected: <true> but was: <false>' \
        '            pageRows.setAll(page.rows());' \
        '            pageRows.addAll(page.rows());'
    assert_testcase "the large fixture on screen" failed "${T_LARGE_UI}" theLargeFixture
    restore_pristine "${PANE}"
    end_control
}

control_6c() {
    begin_control "6c" "item 6 [recorded, unit 7]: the table view-model asks for every row as one page"
    inject_and_run "a page of up to the store's maximum" "${TABLE_VM}" "${UI}" "${SEL_PAGE_VM}" regex \
        'message: the page shown, with the table.s status: The table could not be read: a page holds at most 200 rows, not 950 ==> expected: <1> but was: <0>' \
        '                        ResultPage answered = asked.query(question.withPage(start, PAGE_SIZE));' \
        '                        ResultPage answered =
                                asked.query(question.withPage(0, ResultQuery.MAX_PAGE_SIZE));'
    assert_testcase "950 rows come 200 at a time" failed "${T_TABLE_VM}" paging
    assert_testcase "the page type's own bound stays green" passed "${T_TABLE_VM}" pageBound
    restore_pristine "${TABLE_VM}"
    end_control
}

control_7a() {
    begin_control "7a" "item 7 [recorded, unit 9]: the sample standard deviation (n - 1)"
    inject_and_run "SD divided by n - 1" "${SUMMARY}" "${RESULTS}" "${SEL_WEIGHTS}" fixed \
        'AssertionFailedError: lnrSp sd ==> expected: <0.07918894423395786> but was: <0.09698625332145443>' \
        '                            Math.sqrt(squares / splits),' \
        '                            Math.sqrt(squares / (splits - 1)),'
    assert_testcase "the checked-in two-, three- and four-split files" failed \
        "${T_SUMMARY_INDEPENDENT}" checkedInFiles
    assert_testcase "the real K562 weights" failed "${T_SUMMARY_INDEPENDENT}" realK562
    restore_pristine "${SUMMARY}"
    end_control
}

control_7b() {
    begin_control "7b" "item 7 [recorded, unit 4]: the mean absolute weight sums signed values"
    inject_and_run "ranked by the signed mean" "${SUMMARY}" "${RESULTS}" "${SEL_WEIGHTS}" fixed \
        'AssertionFailedError: lnrSp mean |w| ==> expected: <0.3363333333333333> but was: <-0.3363333333333334>' \
        '                absoluteSum += Math.abs(value);' \
        '                absoluteSum += value;'
    assert_testcase "the real K562 weights against the independent computation" failed \
        "${T_SUMMARY_INDEPENDENT}" realK562
    assert_testcase "the checked-in files" failed "${T_SUMMARY_INDEPENDENT}" checkedInFiles
    assert_log_contains "the real K562 rank 1, lnrSp, ranked last by its signed mean" "${DIRTY_LOG}" \
        'AssertionFailedError: lnrSp rank ==> expected: <1> but was: <21>'
    restore_pristine "${SUMMARY}"
    end_control
}

control_7c() {
    begin_control "7c" "item 7 [NEW]: equal mean absolute weights no longer share a rank"
    inject_and_run "ties broken by file order" "${SUMMARY}" "${RESULTS}" "${SEL_WEIGHTS}" fixed \
        'AssertionFailedError: Charge1 rank ==> expected: <18> but was: <19>' \
        '            if (!isBias(names.get(other)) && meanAbsolutes[other] > meanAbsolutes[feature]) {' \
        '            if (!isBias(names.get(other))
                    && (meanAbsolutes[other] > meanAbsolutes[feature]
                            || (other < feature && meanAbsolutes[other] == meanAbsolutes[feature]))) {'
    assert_testcase "the constructed file with ties" failed "${T_SUMMARY}" everyRow
    assert_testcase "the real K562 weights, whose four all-zero features share rank 18" failed \
        "${T_SUMMARY_INDEPENDENT}" realK562
    assert_testcase "the values themselves are untouched: the sign consistency stays green" passed \
        "${T_SUMMARY}" mixedWithZero
    restore_pristine "${SUMMARY}"
    end_control
}

control_7d() {
    begin_control "7d" "item 7 [NEW]: the summary divides by three splits whatever the file holds"
    inject_and_run "the split count fixed at three" "${SUMMARY}" "${RESULTS}" "${SEL_WEIGHTS}" fixed \
        'AssertionFailedError: lnrSp mean ==> expected: <0.1275> but was: <0.085>' \
        '        int splits = weights.splitCount();' \
        '        int splits = 3;'
    # The checked-in files' invocations, in WeightsSummaryIndependentTest's
    # own order: [1] two splits, [2] four, [3]-[5] the real 3.06.5, 3.07.1 and
    # 3.09 files of three.
    assert_testcase "the constructed two-split file" failed "${T_SUMMARY_INDEPENDENT}" "${CHECKED_IN}[1]"
    assert_testcase "the constructed four-split file" failed "${T_SUMMARY_INDEPENDENT}" "${CHECKED_IN}[2]"
    assert_log_contains "the four-split file's mean, divided by three" "${DIRTY_LOG}" \
        'AssertionFailedError: feat_a mean ==> expected: <0.3125> but was: <0.4166666666666667>'
    assert_testcase "the real three-split 3.07.1 file cannot see a hard-coded 3 (stays green)" passed \
        "${T_SUMMARY_INDEPENDENT}" "${CHECKED_IN}[4]"
    assert_testcase "the real three-split K562 files cannot see it either (stay green)" passed \
        "${T_SUMMARY_INDEPENDENT}" realK562
    restore_pristine "${SUMMARY}"
    end_control
}

control_7e() {
    begin_control "7e" "item 7 [NEW]: a zero weight counted as positive in the sign consistency"
    inject_and_run "zero taken for a positive sign" "${SUMMARY}" "${RESULTS}" "${SEL_WEIGHTS}" fixed \
        'AssertionFailedError: deltLCn sign ==> expected: <ALL_ZERO> but was: <ALL_POSITIVE>' \
        '                if (value > 0) {' \
        '                if (value >= 0) {'
    assert_testcase "zeros mixed with one sign are mixed" failed "${T_SUMMARY}" mixedWithZero
    assert_testcase "the real K562 weights: four all-zero features" failed "${T_SUMMARY_INDEPENDENT}" realK562
    assert_log_contains "the four-split file: zeros and positives are mixed" "${DIRTY_LOG}" \
        'AssertionFailedError: feat_a sign ==> expected: <MIXED> but was: <ALL_POSITIVE>'
    restore_pristine "${SUMMARY}"
    end_control
}

control_8a() {
    begin_control "8a" "item 8 [recorded, unit 6]: unknown-q-value rows written into the passing export"
    inject_and_run "the passing export holding the unknown rows" "${EXPORTER}" "${RESULTS}" "${SEL_EXPORT_GATE}" fixed \
        'message: constructed/psms-shuffled.tsv at 0, PASSING: the export is not the raw header and selected lines ==> array lengths differ, expected: <255> but was: <857>' \
        '                    if (category.includes(where)) {' \
        '                    if (category.includes(where)
                            || (category == Category.PASSING
                                    && where == Visibility.UNKNOWN_Q_VALUE)) {'
    assert_testcase "every category at every gate cutoff, against the oracle" failed \
        "${T_EXPORT_GATE}" everyCategoryAtEveryCutoff
    assert_testcase "the unknown export itself stays green" passed "${T_EXPORT_GATE}" theUnknownExportHoldsEveryUnknownKind
    restore_pristine "${EXPORTER}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [NEW]: an empty q-value classed as failing rather than unknown"
    inject_and_run "the empty q-value dropped from the unknown category" "${PREDICATE}" "${RESULTS}" "${SEL_UNKNOWN}" fixed \
        'message: the hand count of unknown rows at 0 ==> expected: <8> but was: <7>' \
        '        if (status != QValue.Status.KNOWN) {
            return Visibility.UNKNOWN_Q_VALUE;' \
        '        if (status == QValue.Status.MISSING) {
            return Visibility.FAILS;
        }
        if (status != QValue.Status.KNOWN) {
            return Visibility.UNKNOWN_Q_VALUE;'
    assert_testcase "the in-memory store's unknown category" failed "${T_MEMORY}" unknownKindsInTheirOwnCategory
    assert_testcase "the disk store's unknown category" failed "${T_DISK}" unknownKindsInTheirOwnCategory
    # Set.of's order is salted per JVM: eight unknown spellings expected, seven
    # shown (the empty one gone), matched by count; a spelling may hold a comma
    # (0,005), never a comma and a space.
    assert_log_matches "in the stores' own words: eight unknown spellings, seven shown" "${DIRTY_LOG}" \
        'message: constructed/psms-unknown-q\.tsv at 0 ==> expected: <\[(([^],]|,[^ ])*, ){7}([^],]|,[^ ])*\]> but was: <\[(([^],]|,[^ ])*, ){6}([^],]|,[^ ])*\]>'
    assert_testcase "the unknown export" failed "${T_EXPORT_GATE}" theUnknownExportHoldsEveryUnknownKind
    assert_testcase "a known q-value at the cutoff stays green" passed "${T_PREDICATE}" atDefault
    restore_pristine "${PREDICATE}"
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

readonly H_FILE="${PREDICATE}"
readonly H_ANCHOR='        return value <= comparable ? Visibility.PASSES : Visibility.FAILS;'

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
    dirty_run "H3" "${H_FILE}" "${RESULTS}" "${SEL_H}" "${LOGS}/H3-comment-only.log"
}

h_zero_tests() {
    local log="${LOGS}/H4-zero-tests.log" rc=0
    run_mvn "${log}" "${RESULTS}" "${SEL_H4}" || rc=$?
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
        'expected: <PASSES> but was: <FAILS>'

    log="${LOGS}/H6-wrong-reason.log"
    printf 'FAILED org.cometgui.SomeOtherTest#somethingElse [failure]\n  message: expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red regex "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <PASSES> but was: <FAILS>'

    USED_SELECTORS+=("${SEL_H}")
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1a) control_1a ;; 1b) control_1b ;; 1c) control_1c ;; 1d) control_1d ;;
        2) control_2 ;;
        3a) control_3a ;; 3b) control_3b ;; 3c) control_3c ;;
        4a) control_4a ;; 4b) control_4b ;;
        5a) control_5a ;; 5b) control_5b ;;
        6a) control_6a ;; 6b) control_6b ;; 6c) control_6c ;;
        7a) control_7a ;; 7b) control_7b ;; 7c) control_7c ;; 7d) control_7d ;; 7e) control_7e ;;
        8a) control_8a ;; 8b) control_8b ;;
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
            || die "${needed} is missing. The graded tests read the large fixture and the real K562 Percolator outputs from scratch/ and FAIL rather than skip without them: python3 scripts/fixtures/large-results-fixture.py makes scratch/phase10/large, and docs/developer/results_model.rst says where the K562 outputs come from." 3
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
    printf ' %s -- every PHASE-10 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
        die "${FAILED} results-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
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
    printf '\n  PHASE-10 exit gate items 1 to 8 were proved here, each by a production\n'
    printf '  defect in cometgui-results, cometgui-workflow, cometgui-ui or cometgui-app\n'
    printf '  in a git-archive sandbox, proved in the bytecode and graded on the failing\n'
    printf '  assertion'"'"'s own words in the named testcase -- items 3, 6 and 8 against\n'
    printf '  the large fixture and the real K562 Percolator outputs, and items 2, 3 and 6\n'
    printf '  also through the headless GUI.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero\n'
    printf '  tests as a HARNESS ERROR.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
