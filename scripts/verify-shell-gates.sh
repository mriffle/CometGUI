#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-02 application-shell gates can fail.
#
#   bash scripts/verify-shell-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 02's exit gate is five claims
# about a running JavaFX application.  Each is checked by a headless GUI test,
# and a green test says nothing about whether that test would notice the defect
# it exists to catch.  This script injects, one at a time, the defect each item
# exists to catch, requires the narrowest command that should catch it to exit
# non-zero WITH THE EXPECTED DIAGNOSTIC, and then requires the same command to
# pass once the defect is removed.
#
# It is the sibling of scripts/verify-quality-gates.sh (unit 2's formatting and
# static-analysis gates) and scripts/verify-test-gates.sh (unit 3's coverage,
# architecture and mutation gates), and it deliberately follows their shape
# rather than inventing a third one.
#
# WHAT IT COVERS -- the five PHASE-02 exit gate items:
#
#   0   baseline: the undamaged sandbox passes all five test classes, and the
#       application STARTS -- the first clause of item 1
#   1a  item 1, mouse: every navigation entry selects the same section
#   1b  item 1, keyboard: the arrow-key handler moves two sections at a time,
#       so a section cannot be reached by keyboard alone
#   2a  item 2: SectionPane.setId(null), so no pane carries its stable
#       identifier and the test can no longer find one
#   2b  item 2, sharper: the identifiers stay, but every pane is shown at once,
#       so "present by identifier" is true of panes that must not be showing
#   3   item 3: DELEGATED to scripts/verify-test-gates.sh, and the delegation
#       is enforced rather than asserted in prose -- see control 3
#   4a  item 4: an accessible name removed at a site where it is assigned ONCE
#   4b  item 4, the harness itself: the same name removed at StageStepper's
#       OTHER assignment site, which showStage() re-assigns moments later.  The
#       test then PASSES, and a harness must report that as a HARNESS FAILURE
#       rather than as a control that bit
#   5a  item 5: the console's rendered-document window removed (int from = 0)
#   5b  item 5: the eviction removed from BoundedMessageLog.append, so the
#       model is unbounded and the retained heap grows past its documented cap
#   H   the harness itself: a control whose defect was NOT injected, and one
#       whose file was not modified, must both be harness failures, not passes
#
# WHY ITEM 3 IS NOT INJECTED HERE.  scripts/verify-test-gates.sh controls 1, 2
# and 3 already inject a JavaFX import into cometgui-domain, require ArchUnit
# to reject it, and prove the import census would notice a rule set that passed
# vacuously.  Duplicating that injection here would create two things to keep
# in step with one gate, which is the drift these scripts exist to prevent.
# Control 3 therefore asserts that the other harness still contains that
# control -- by its banner and by the `javafx.scene.control.Label` injection it
# makes -- and FAILS if it does not, so the cross-reference cannot silently
# rot.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/shell-gate-sandbox and damages that, so a half-injured file can
# never be committed by accident.  Two consequences worth stating:
#
#   * tools/ is GITIGNORED, so a git-archive sandbox does not contain it, and
#     the headless JavaFX tests resolve the project-local font stack through
#     ${maven.multiModuleProjectDirectory}/tools/... -- which is the SANDBOX
#     root here.  Without a tools/ symlink every control would fail with "the
#     project-local font stack is missing", which is correct behaviour and NOT
#     the defect being injected: every control would be a false positive.  The
#     sandbox therefore symlinks the working tree's tools/ before anything
#     runs, and refuses to continue if the link does not resolve.
#   * `git archive HEAD` is the COMMITTED tree.  If the working tree has
#     uncommitted changes under cometgui-*/src the script says so, loudly,
#     because what it then proves is that HEAD's gates bite -- not the tree's.
#
# EVERY INJECTION IS ANCHORED, AND A MISSING ANCHOR IS FATAL.  Each injection
# is a literal replacement that must match EXACTLY ONCE in the file.  If the
# source moves under this script, it stops with a harness error naming the
# anchor rather than injecting nothing and reporting a green run -- the silent
# under-injection that scripts/verify-test-gates.sh had to be repaired for.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip.  Those are units 2 and 3's gates, they have their own
# harnesses, and several injections here (a commented-out call, a
# deliberately mis-shaped statement) would be rejected by formatting first --
# the control would then "fail" for the wrong reason and prove nothing about
# the shell.  Nothing this script is testing is skipped, and no test class is
# excluded, filtered or marked skipped: the -Dtest selection names the class
# whose gate is under test, and every run is checked to have actually EXECUTED
# that class's tests.
#
# WHY IT IS NOT PART OF scripts/build.sh.  build.sh is the one documented build
# command and must stay fast enough to run constantly.  This script rebuilds
# and re-runs a damaged copy of the reactor seventeen times; it belongs before
# a phase sign-off, after touching the shell, the console model or the GUI
# tests, and in the nightly pipeline.  It is registered in
# scripts/verify-all-gates.sh, which is the aggregate entry point.
#
# WHAT IT NEEDS.  A built tree: tools/ (JDK, Maven, Monocle, the font stack)
# and a populated _build/m2repo -- run `bash scripts/build.sh` first.  It runs
# Maven offline and needs no network.  It writes only under _build/.
#
# EXIT STATUS
#   0  every control bit: each gate rejected its defect and accepted the clean
#      tree, and the harness's own falsifiability controls held
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, or an injection reached the file without reaching the
#      behaviour
#   2  misuse (unknown option)
#   3  the environment is not ready (no tools/, no _build/m2repo, no font
#      stack, no git)
#   4  HARNESS ERROR: an injection did not reach the sandbox, an anchor no
#      longer exists, or a command that was supposed to run a test class ran
#      none.  The run proves nothing and must not be read as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/shell-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/shell-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/shell-gate-logs"

# The five files the controls damage, and the module that holds them.  The GUI
# tests all live in cometgui-app; cometgui-ui holds the views and controls, and
# cometgui-domain the console's message model, so a shell injection is a
# cometgui-ui or cometgui-domain edit proved by a cometgui-app test.
readonly SHELL_VIEW="cometgui-ui/src/main/java/org/cometgui/ui/view/ShellView.java"
readonly SECTION_PANE="cometgui-ui/src/main/java/org/cometgui/ui/view/SectionPane.java"
readonly STAGE_STEPPER="cometgui-ui/src/main/java/org/cometgui/ui/controls/StageStepper.java"
readonly CONSOLE_PANE="cometgui-ui/src/main/java/org/cometgui/ui/controls/derived/ConsolePane.java"
readonly MESSAGE_LOG="cometgui-domain/src/main/java/org/cometgui/domain/log/BoundedMessageLog.java"

# The five test classes, by the gate item each one proves.
readonly TEST_STARTUP="CometGuiApplicationStartupTest"
readonly TEST_MOUSE="SectionNavigationUiTest"
readonly TEST_KEYBOARD="KeyboardOnlyNavigationUiTest"
readonly TEST_NAMES="AccessibleNameEnumerationUiTest"
readonly TEST_CONSOLE="ConsoleFloodUiTest"

# Control 3's delegation.  These three strings are what makes the delegation
# load-bearing: if the other harness loses its ArchUnit control, this script
# fails rather than continuing to claim item 3 is covered somewhere else.
readonly TEST_GATES="scripts/verify-test-gates.sh"
readonly TEST_GATES_BANNER="1  layering: a JavaFX import in cometgui-domain (gate item 3)"
readonly TEST_GATES_INJECTION="import javafx.scene.control.Label;"
readonly TEST_GATES_ASSERTION="ArchUnit rejects a JavaFX dependency in the domain"

# Units 2 and 3's gates are off in the sandbox; see the header comment.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

PASSED=0
FAILED=0
FAILURES=()

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the five PHASE-02 exit gate items fail on the defects
they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME} [-h|--help]

It needs a built tree: the project-local toolchain in tools/ (JDK, Maven,
Monocle and the font stack) and a populated ${M2REPO#"${ROOT}/"}.  Run
bash scripts/build.sh first.  It runs Maven offline, damages only a git-archive
sandbox under _build/, and writes only under _build/.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing).
USAGE
}

# die MESSAGE [EXIT CODE].  Only \$1 is the message: \$* would print the code.
die() {
    printf '\nFATAL: %s\n' "$1" >&2
    exit "${2:-1}"
}

harness_error() {
    printf '\nHARNESS ERROR: %s\n' "$1" >&2
    printf 'The run proves nothing and must not be read as a pass.\n' >&2
    exit 4
}

banner() {
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' CONTROL %s\n' "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

record_pass() {
    PASSED=$((PASSED + 1))
    printf '   PASS  %s\n' "$*"
}

record_fail() {
    FAILED=$((FAILED + 1))
    FAILURES+=("$*")
    printf '   FAIL  %s\n' "$*"
}

# ------------------------------------------------------------ running tests --
#
# One command shape for every control: the narrowest thing that exercises the
# gate under test, which for a GUI gate is the one test class that asserts it.
# -am is not optional -- cometgui-app's test class path is the reactor, and the
# injections live in cometgui-ui and cometgui-domain, so the damaged module has
# to be rebuilt for the defect to reach the running application at all.
run_gate_tests() {
    local log="$1" classes="$2"
    local rc=0
    ( cd "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" \
            -pl cometgui-app -am test \
            -Dtest="${classes}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl cometgui-app -am test -Dtest=%s' "$1"
}

# THE VACUOUS PASS THIS SCRIPT COULD OFFER FOR FREE.  -Dtest with a name that
# matches nothing, plus -Dsurefire.failIfNoSpecifiedTests=false, exits 0 having
# run no test at all -- so a mistyped class name would turn every "the gate
# accepts the clean tree" control into a green line proving nothing.  Every run
# is therefore required to have EXECUTED each class it named, and a run that
# did not is a harness error rather than a control failure.
verify_classes_ran() {
    local log="$1" classes="$2" class count
    local -a wanted=()
    IFS=',' read -r -a wanted <<< "${classes}"
    for class in "${wanted[@]}"; do
        count="$(sed -n "s/.*Tests run: \([0-9][0-9]*\),.*-- in .*[.]${class}\$/\1/p" \
            "${log}" | tail -1)"
        if [ -z "${count}" ]; then
            harness_error "${class} reported no test run in ${log#"${ROOT}/"}. The command named a class that surefire did not execute, so this control tested nothing."
        fi
        if [ "${count}" -lt 1 ]; then
            harness_error "${class} executed ${count} tests in ${log#"${ROOT}/"}. A class that runs no test cannot prove or disprove a gate."
        fi
    done
}

# TWO WAYS TO STATE AN EXPECTED DIAGNOSTIC.  A diagnostic with no number in it
# is asserted literally, because a literal cannot drift into matching something
# it was not meant to.  One that carries a measured number -- a control count,
# a byte count, a section identifier that depends on the display order -- is
# asserted by SHAPE, so that the assertion says what must be true rather than
# what happened to be true on one day in August.  The shape still has to name
# the thing: "the test failed somehow" is not an assertion.
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

# assert_gate_fails         <label> <expected literal> <log> <test classes>
# assert_gate_fails_matching <label> <expected ERE>    <log> <test classes>
#
# The gate must exit non-zero AND say why.  A non-zero exit with some other
# message means something else broke -- a compile error, a missing font stack,
# another agent's half-built tree -- and is a failure, not the gate biting.
assert_gate_fails() { graded_failure fixed "$@"; }
assert_gate_fails_matching() { graded_failure regex "$@"; }

graded_failure() {
    local mode="$1" label="$2" expected="$3" log="$4" classes="$5"
    local rc=0
    run_gate_tests "${log}" "${classes}" || rc=$?
    if [ "${rc}" -eq 0 ]; then
        # The two ways this happens are a dead gate and an injection that
        # reached the FILE without reaching the BEHAVIOUR.  Both are failures
        # of this harness's claim and neither may be recorded as a pass; the
        # marker below is what control 4b requires the harness to print.
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! log_has "${log}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        printf '         first error line: %s\n' \
            "$(grep -m1 '^\[ERROR\]' "${log}" | cut -c1-140)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | cut -c1-160)"
}

# assert_log_contains <label> <log> <expected literal>
# assert_log_matches  <label> <log> <expected ERE>
# Extra evidence from a log a control has already produced, so that one control
# can require several strings without paying for a second Maven run.
assert_log_contains() { graded_log fixed "$@"; }
assert_log_matches() { graded_log regex "$@"; }

graded_log() {
    local mode="$1" label="$2" log="$3" expected="$4"
    if log_has "${log}" "${expected}" "${mode}"; then
        record_pass "${label}"
        printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | cut -c1-160)"
    else
        record_fail "${label}: nothing in ${log#"${ROOT}/"} matches '${expected}'"
    fi
}

# assert_gate_passes <label> <log> <test classes>
# A control that only shows red proves the test is broken, not that it is
# discriminating.  This is the other half of every control.
assert_gate_passes() {
    local label="$1" log="$2" classes="$3"
    local rc=0
    run_gate_tests "${log}" "${classes}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "${label}: the check still fails after the defect was removed (log: ${log#"${ROOT}/"})"
        printf '         %s\n' "$(grep -m3 '^\[ERROR\]' "${log}" | head -3 | cut -c1-140)"
        return
    fi
    verify_classes_ran "${log}" "${classes}"
    record_pass "${label}: exit 0, ${classes} executed"
}

# assert_file_contains <label> <file> <expected literal>
# Control 3's shape: the gate being proved lives in another script, so what is
# asserted here is that the other script still contains the control.
assert_file_contains() {
    local label="$1" file="$2" expected="$3"
    if grep -qF -- "${expected}" "${ROOT}/${file}"; then
        record_pass "${label}"
        printf '         %s: %s\n' "${file}" \
            "$(grep -nF -- "${expected}" "${ROOT}/${file}" | head -1 | cut -c1-140)"
    else
        record_fail "${label}: ${file} no longer contains '${expected}'"
    fi
}

# ------------------------------------------------- injection and its guards --
#
# Harness falsifiability, the same shape as verify-quality-gates.sh's
# assert_injected/assert_removed: a control whose defect was never written to
# disk must stop the run, not pass quietly.

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
}

# replace_once <label> <file> <old literal> <new literal>
# The anchor must match EXACTLY ONCE.  Zero matches means the source moved and
# the injection would test nothing; more than one means the injection is
# broader than the control describes.  Both stop the run.
replace_once() {
    local label="$1" file="$2" old="$3" new="$4"
    local rc=0
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

# assert_injected <label> <file>  -- the file exists in the sandbox
assert_injected() {
    local label="$1" file="$2"
    if [ ! -e "${SANDBOX}/${file}" ]; then
        harness_error "(${label}) ${file} is not in the sandbox. The control would have tested nothing."
    fi
    printf '   injected %s (%s bytes)\n' "${file}" "$(stat -Lc %s "${SANDBOX}/${file}")"
}

# assert_modified <label> <file>  -- and it really differs from the pristine
# copy taken before the injection.  A byte-identical file is an injection that
# never happened.
assert_modified() {
    local label="$1" file="$2"
    [ -e "${SANDBOX}/${file}" ] \
        || harness_error "(${label}) ${file} is missing from the sandbox."
    [ -e "${PRISTINE}/${file}" ] \
        || harness_error "(${label}) no pristine copy of ${file} was taken, so nothing can be compared."
    if cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}"; then
        harness_error "(${label}) ${file} is byte-identical to the pristine copy. The defect was not injected and the control would have tested nothing."
    fi
    printf '   injected %s (differs from the pristine copy: %s)\n' "${file}" \
        "$(diff <(cat "${PRISTINE}/${file}") <(cat "${SANDBOX}/${file}") | grep -c '^[<>]') changed line(s)"
}

# restore_pristine <file> -- and prove the restoration, or the "clean" re-run
# would not be clean and its green would mean nothing.
restore_pristine() {
    local file="$1"
    [ -e "${PRISTINE}/${file}" ] \
        || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}" \
        || harness_error "could not restore ${file} in the sandbox."
    printf '   restored %s\n' "${file}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"

    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."

    # tools/ is gitignored, so it is NOT in the archive, and the headless
    # JavaFX tests resolve the font stack through the SANDBOX's own
    # maven.multiModuleProjectDirectory.  Without this link every control fails
    # with "the project-local font stack is missing" -- correct behaviour, and
    # a false positive for every gate here.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    [ -d "${SANDBOX}/tools/fontstack-bookworm-20260829/root" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve to the font stack. Every GUI control would fail for the wrong reason."

    mkdir -p -- "${SANDBOX}/_build"
    ln -s -- "${M2REPO}" "${SANDBOX}/_build/m2repo"

    local copied original head
    copied="$(find "${SANDBOX}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    original="$(find "${ROOT}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: ${SANDBOX#"${ROOT}/"} (git archive ${head}, ${copied} java files)"

    if [ "${copied}" -ne "${original}" ]; then
        printf '\n  NOTE: the sandbox has %d java files and the working tree has %d.\n' \
            "${copied}" "${original}"
    fi
    local dirty
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' | head -20)"
    if [ -n "${dirty}" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes under cometgui-*/src.\n'
        printf '        This run proves the gates of HEAD (%s), not of the working tree:\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
}

# ------------------------------------------------------------- the controls --

control_baseline() {
    banner "0  baseline: the undamaged sandbox starts and passes all five classes"
    # Every control below is a difference from this run.  If the clean tree
    # does not pass, nothing further can be attributed to an injection.
    local log="${LOGS}/00-baseline.log"
    local classes="${TEST_STARTUP},${TEST_MOUSE},${TEST_KEYBOARD},${TEST_NAMES},${TEST_CONSOLE}"
    assert_gate_passes "baseline: the five gate test classes on the clean sandbox" \
        "${log}" "${classes}"
    # Item 1's FIRST clause, stated separately because it is a separate claim:
    # the application starts at all.
    assert_log_matches "gate item 1, first clause: the application starts (${TEST_STARTUP})" \
        "${log}" \
        "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: 0.*${TEST_STARTUP}"
}

control_mouse_navigation() {
    banner "1a gate item 1, MOUSE: every navigation entry selects the same section"
    # "every primary section is reachable by mouse".  The defect is the one a
    # copy-paste produces: a lambda that closes over a constant instead of the
    # loop variable, so every entry works and every entry goes to the same
    # place.
    save_pristine "${SHELL_VIEW}"
    replace_once "mouse navigation" "${SHELL_VIEW}" \
        'entry.setOnAction(event -> select(section));' \
        'entry.setOnAction(event -> select(SectionId.RUN));'
    assert_modified "mouse navigation" "${SHELL_VIEW}"

    local log="${LOGS}/01a-mouse-dirty.log"
    assert_gate_fails "${TEST_MOUSE} rejects a shell where every entry selects one section" \
        "the header's echo of the selected section ==> expected: <Comet Parameters> but was: <Run>" \
        "${log}" "${TEST_MOUSE}"
    assert_log_contains "the failure also names the pane that is showing instead" \
        "${log}" \
        "#section-run should not be showing while comet-parameters is selected"

    restore_pristine "${SHELL_VIEW}"
    assert_gate_passes "${TEST_MOUSE} accepts the shell once the entries select their own section" \
        "${LOGS}/01a-mouse-clean.log" "${TEST_MOUSE}"
}

control_keyboard_navigation() {
    banner "1b gate item 1, KEYBOARD: the arrow keys move two sections at a time"
    # "reachable ... by keyboard alone".  Double-stepping is the defect that a
    # green mouse test cannot see and that a human would call a glitch rather
    # than an inaccessible application: half the sections cannot be reached
    # with the keyboard at all.
    save_pristine "${SHELL_VIEW}"
    replace_once "keyboard navigation (up)" "${SHELL_VIEW}" \
        '            navigation.selectPrevious();
' \
        '            navigation.selectPrevious();
            navigation.selectPrevious();
'
    replace_once "keyboard navigation (down)" "${SHELL_VIEW}" \
        '            navigation.selectNext();
' \
        '            navigation.selectNext();
            navigation.selectNext();
'
    assert_modified "keyboard navigation" "${SHELL_VIEW}"

    local log="${LOGS}/01b-keyboard-dirty.log"
    assert_gate_fails_matching \
        "${TEST_KEYBOARD} names the section the keyboard could not reach" \
        '#section-[a-z-]+ showing, with [a-z-]+ chosen ==> expected: <true> but was: <false>' \
        "${log}" "${TEST_KEYBOARD}"
    assert_log_matches "the failure also shows the focus landing on the wrong entry" \
        "${log}" 'expected: <nav-[a-z-]+> but was: <nav-[a-z-]+>'

    restore_pristine "${SHELL_VIEW}"
    assert_gate_passes "${TEST_KEYBOARD} accepts the shell once the arrow keys step by one" \
        "${LOGS}/01b-keyboard-clean.log" "${TEST_KEYBOARD}"
}

control_stable_identifiers() {
    banner "2a gate item 2: SectionPane.setId(null) -- no pane carries its identifier"
    # "asserts each is present by STABLE IDENTIFIER".  With no identifier the
    # panes are all still there and all still correct; what is gone is the only
    # way a test can name one.  A test that found them by position or by index
    # would not notice.
    save_pristine "${SECTION_PANE}"
    replace_once "stable identifiers" "${SECTION_PANE}" \
        '        setId(UiIds.sectionPane(section));' \
        '        setId(null);'
    assert_modified "stable identifiers" "${SECTION_PANE}"

    assert_gate_fails "${TEST_MOUSE} rejects panes that carry no stable identifier" \
        "no node with the stable identifier #section-run exists in the running application" \
        "${LOGS}/02a-identifiers-dirty.log" "${TEST_MOUSE}"

    restore_pristine "${SECTION_PANE}"
    assert_gate_passes "${TEST_MOUSE} accepts the panes once their identifiers are back" \
        "${LOGS}/02a-identifiers-clean.log" "${TEST_MOUSE}"
}

control_one_pane_showing() {
    banner "2b gate item 2, sharper: the identifiers stay, but every pane is shown"
    # The blunt version above is caught by a lookup.  This one leaves every
    # identifier in place -- so "each section is present by stable identifier"
    # is still literally true -- and breaks what the item is FOR: the content
    # area showing exactly the selected section.  The failure must name the
    # pane that should not be showing, or the test is only counting.
    save_pristine "${SHELL_VIEW}"
    replace_once "one pane showing" "${SHELL_VIEW}" \
        '            pane.getValue().setVisible(isSelected);' \
        '            pane.getValue().setVisible(true);'
    assert_modified "one pane showing" "${SHELL_VIEW}"

    assert_gate_fails "${TEST_MOUSE} names the pane that must not be showing" \
        "#section-comet-parameters should not be showing while run is selected ==> expected: <false> but was: <true>" \
        "${LOGS}/02b-visibility-dirty.log" "${TEST_MOUSE}"

    restore_pristine "${SHELL_VIEW}"
    assert_gate_passes "${TEST_MOUSE} accepts the content area once one pane shows at a time" \
        "${LOGS}/02b-visibility-clean.log" "${TEST_MOUSE}"
}

control_item3_delegation() {
    banner "3  gate item 3: DELEGATED to ${TEST_GATES}, and the delegation is enforced"
    printf '   Item 3 -- "an ArchUnit test proves the domain module has no JavaFX dependency,\n'
    printf '   and it fails if one is introduced" -- is proved by %s\n' "${TEST_GATES}"
    printf '   controls 1, 2 and 3, which inject a JavaFX import into cometgui-domain and\n'
    printf '   require ArchUnit to reject it.  Injecting it a second time here would create\n'
    printf '   two things to keep in step with one gate.  What this control does instead is\n'
    printf '   fail if that harness ever loses the control this script is relying on.\n\n'

    if [ -x "${ROOT}/${TEST_GATES}" ]; then
        record_pass "the delegated harness exists and is executable: ${TEST_GATES}"
    else
        record_fail "gate item 3 is delegated to ${TEST_GATES}, which is missing or not executable; nothing proves item 3"
    fi
    assert_file_contains "the delegated harness still has its ArchUnit control (its banner)" \
        "${TEST_GATES}" "${TEST_GATES_BANNER}"
    assert_file_contains "and still makes the JavaFX injection that control depends on" \
        "${TEST_GATES}" "${TEST_GATES_INJECTION}"
    assert_file_contains "and still requires ArchUnit to reject it" \
        "${TEST_GATES}" "${TEST_GATES_ASSERTION}"
    printf '\n   To see item 3 bite, run: bash %s\n' "${TEST_GATES}"
}

control_accessible_name() {
    banner "4a gate item 4: an accessible name removed where it is assigned ONCE"
    # "every control that exists has an accessible name; a test enumerates them
    # and fails on a missing one".  StageStepper.stageBox names the stage-name
    # label once and nothing else touches it, so removing that call really does
    # leave eight controls unnamed.
    save_pristine "${STAGE_STEPPER}"
    replace_once "accessible name" "${STAGE_STEPPER}" \
        '        named(name, stage.displayName() + " stage");' \
        '        // injected defect: the stage name label is given no accessible name'
    assert_modified "accessible name" "${STAGE_STEPPER}"

    local log="${LOGS}/04a-accessible-dirty.log"
    assert_gate_fails_matching "${TEST_NAMES} counts the unnamed controls and names them" \
        '[0-9]+ of [0-9]+ controls have none: Label with id #stage-inputs-name under #stage-inputs' \
        "${log}" "${TEST_NAMES}"
    assert_log_contains "the enumeration names every unnamed control, not just the first" \
        "${log}" "Label with id #stage-limelight-upload-name under #stage-limelight-upload"

    restore_pristine "${STAGE_STEPPER}"
    assert_gate_passes "${TEST_NAMES} accepts the interface once the name is assigned again" \
        "${LOGS}/04a-accessible-clean.log" "${TEST_NAMES}"
}

control_ineffective_injection() {
    banner "4b the harness itself: an injection that reaches the FILE but not the BEHAVIOUR"
    # This control is the point of this unit, and it comes from a real event:
    # the phase orchestrator's first attempt at 4a removed the accessible name
    # at StageStepper's OTHER assignment site -- the state label, which
    # showStage() legitimately re-assigns a few lines later -- and the test
    # passed.  Nothing was wrong with the gate.  The injection never reached
    # the running code, and a harness that reported that as "the gate did not
    # bite", or worse as a pass, would be lying in both directions.
    #
    # So: make exactly that injection, run it through the ORDINARY grading
    # path, and require the harness to have called it a HARNESS FAILURE.  The
    # grading runs in a command substitution, so its own bookkeeping is
    # discarded and only this control's verdict is recorded.
    save_pristine "${STAGE_STEPPER}"
    replace_once "ineffective injection" "${STAGE_STEPPER}" \
        '        named(state, stage.displayName() + " stage state");' \
        '        // injected defect: the state label is given no accessible name HERE'
    assert_modified "ineffective injection" "${STAGE_STEPPER}"

    local log="${LOGS}/04b-ineffective-dirty.log"
    local out rc=0
    out="$(assert_gate_fails \
        "gate item 4, injected at the site showStage() re-assigns" \
        "controls have none" "${log}" "${TEST_NAMES}" 2>&1)" || rc=$?
    printf '%s\n' "${out}" | sed 's/^/       | /'
    if [ "${rc}" -eq 0 ] && printf '%s' "${out}" | grep -q 'HARNESS FAILURE'; then
        record_pass "the harness reports an injection that reached the file but not the behaviour as a HARNESS FAILURE, not as a pass"
    else
        record_fail "the harness did not report the known-ineffective injection as a harness failure (exit ${rc}). Either the grading changed, or showStage() no longer re-assigns the accessible text -- in which case this control's premise is stale and must be re-established, not deleted"
    fi

    restore_pristine "${STAGE_STEPPER}"
    assert_gate_passes "${TEST_NAMES} still passes once the re-assigned name is restored" \
        "${LOGS}/04b-ineffective-clean.log" "${TEST_NAMES}"
}

control_document_window() {
    banner "5a gate item 5: the console's rendered-document window removed (int from = 0)"
    # "the console pane discards oldest messages under a flood test without
    # heap growth beyond its documented cap".  Half of the cap is the rendered
    # document: the model may hold 10,000 lines and the TextArea must still
    # show only the newest 5,000.
    save_pristine "${CONSOLE_PANE}"
    replace_once "document window" "${CONSOLE_PANE}" \
        '        int from = Math.max(0, matched - maxRenderedLines);' \
        '        int from = 0;'
    assert_modified "document window" "${CONSOLE_PANE}"

    local log="${LOGS}/05a-document-dirty.log"
    assert_gate_fails "${TEST_CONSOLE} rejects a document that ignores its cap" \
        "the document must hold exactly its cap of lines, not 10000 ==> expected: <5000> but was: <10000>" \
        "${log}" "${TEST_CONSOLE}"
    assert_log_contains "and the summary stops telling the user what it is not showing" \
        "${log}" "Showing 10,000 matching lines."

    restore_pristine "${CONSOLE_PANE}"
    assert_gate_passes "${TEST_CONSOLE} accepts the console once the window is back" \
        "${LOGS}/05a-document-clean.log" "${TEST_CONSOLE}"
}

control_log_eviction() {
    banner "5b gate item 5: the eviction removed from BoundedMessageLog.append"
    # The other half, and the one the item names first: "discards oldest
    # messages".  With the eviction gone the model keeps all 250,001 flooded
    # messages, and the assertion that distinguishes a bounded console from an
    # unbounded one is the RETAINED HEAP -- which must name the bytes it
    # measured, or it is recording a verdict instead of a measurement.
    save_pristine "${MESSAGE_LOG}"
    replace_once "log eviction" "${MESSAGE_LOG}" \
        '            if (messages.size() == capacity) {
                messages.removeFirst();
                discarded++;
            }
' \
        '            // injected defect: the oldest message is not discarded
'
    assert_modified "log eviction" "${MESSAGE_LOG}"

    local log="${LOGS}/05b-eviction-dirty.log"
    assert_gate_fails_matching \
        "${TEST_CONSOLE} rejects an unbounded log, and names the heap it measured" \
        'retained heap growth over the flood was [0-9]+ bytes \([0-9]+ MB\), over the documented bound of [0-9]+ bytes' \
        "${log}" "${TEST_CONSOLE}"
    assert_log_matches "and the model itself is over its capacity, by count" \
        "${log}" \
        'after [0-9]+ lines the log must hold exactly its capacity ==> expected: <10000> but was: <[0-9]+>'

    restore_pristine "${MESSAGE_LOG}"
    assert_gate_passes "${TEST_CONSOLE} accepts the console once the oldest are discarded again" \
        "${LOGS}/05b-eviction-clean.log" "${TEST_CONSOLE}"
}

control_harness_self_test() {
    banner "H  the harness itself: an un-injected control must be a harness failure"
    # Every control above calls assert_injected or assert_modified before it
    # grades anything.  This proves those calls are not decoration: with the
    # defect deliberately not written, the harness must abort rather than run a
    # command that would pass and be recorded as a control passing.  Control 4b
    # is the same idea one level deeper -- a defect that WAS written and still
    # changed nothing.
    local out rc=0
    out="$(assert_injected "deliberately un-injected" \
        "cometgui-ui/src/main/java/org/cometgui/ui/view/NoSuchFile.java" 2>&1)" || rc=$?
    if [ "${rc}" -ne 0 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR'; then
        record_pass "the harness refuses to grade a control whose file never reached the sandbox"
        printf '         %s\n' "$(printf '%s' "${out}" | grep 'HARNESS ERROR' | cut -c1-160)"
    else
        record_fail "the harness accepted a control with no injected file (exit ${rc}): ${out}"
    fi

    # The same for a defect injected by editing rather than by adding: an
    # unchanged file must be rejected as well as a missing one.
    save_pristine "${SHELL_VIEW}"
    rc=0
    out="$(assert_modified "deliberately unmodified" "${SHELL_VIEW}" 2>&1)" || rc=$?
    if [ "${rc}" -ne 0 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR'; then
        record_pass "the harness refuses to grade an edit control whose file is unchanged"
        printf '         %s\n' "$(printf '%s' "${out}" | grep 'HARNESS ERROR' | cut -c1-160)"
    else
        record_fail "the harness accepted an unmodified edit control (exit ${rc}): ${out}"
    fi

    # And an anchor that no longer exists must stop the run rather than inject
    # nothing: the failure mode that made verify-test-gates.sh need repairing.
    rc=0
    out="$(replace_once "deliberately impossible anchor" "${SHELL_VIEW}" \
        'this text does not appear in ShellView.java' 'nor does this' 2>&1)" || rc=$?
    if [ "${rc}" -ne 0 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR'; then
        record_pass "the harness refuses to inject against an anchor that no longer exists"
        printf '         %s\n' "$(printf '%s' "${out}" | grep 'HARNESS ERROR' | cut -c1-160)"
    else
        record_fail "the harness accepted an injection whose anchor matched nothing (exit ${rc}): ${out}"
    fi
    cmp -s "${SANDBOX}/${SHELL_VIEW}" "${PRISTINE}/${SHELL_VIEW}" \
        || harness_error "the impossible-anchor control changed ${SHELL_VIEW} after all."
}

# -------------------------------------------------------------------- main --
main() {
    case "${1:-}" in
        -h|--help) usage; exit 0 ;;
        "") ;;
        *) usage >&2; die "unknown option: $1" 2 ;;
    esac

    cd -- "${ROOT}"
    command -v git >/dev/null || die "git is not on PATH; the sandbox is a git archive." 3
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run bash scripts/build.sh first." 3
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    [ -d "${M2REPO}" ] \
        || die "${M2REPO#"${ROOT}/"} does not exist; run bash scripts/build.sh first. Every control runs Maven offline." 3
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing; run bash scripts/fetch-fontstack.sh first. Without it every GUI control fails for the wrong reason." 3

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-02 exit gate item must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  logs         %s\n' "${LOGS#"${ROOT}/"}"
    printf '  each control %s\n' "$(gate_command '<the class that asserts the item>')"

    local started
    started="$(date +%s)"

    build_sandbox
    control_baseline
    control_mouse_navigation
    control_keyboard_navigation
    control_stable_identifiers
    control_one_pane_showing
    control_item3_delegation
    control_accessible_name
    control_ineffective_injection
    control_document_window
    control_log_eviction
    control_harness_self_test

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "${total}"
    printf ' Logs: %s\n' "${LOGS#"${ROOT}/"}"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} shell-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    printf '\n  PHASE-02 exit gate items 1, 2, 4 and 5 were proved here; item 3 is proved by\n'
    printf '  %s controls 1, 2 and 3, and this run failed if that\n' "${TEST_GATES}"
    printf '  harness had lost them.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
