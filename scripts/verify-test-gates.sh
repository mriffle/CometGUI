#!/usr/bin/env bash
#
# CometGUI -- prove the coverage, architecture and mutation gates can fail.
#
#   bash scripts/verify-test-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  This script injects, one at a time,
# the defect each gate exists to catch, requires the narrowest command that
# should catch it to exit non-zero WITH THE EXPECTED DIAGNOSTIC, and then
# requires the same command to pass once the defect is removed.
#
# It is the sibling of scripts/verify-quality-gates.sh, which does the same job
# for unit 2's Spotless, Checkstyle and SpotBugs gates, and it deliberately
# follows that script's shape rather than inventing a second one.
#
# WHAT IT COVERS (phase 01 unit 3, gate items 3 and 4):
#
#   1  ArchUnit rejects a JavaFX import in cometgui-domain            gate item 3
#   2  ArchUnit rejects `new ProcessBuilder` outside the process
#      service (R-PROC-02)                                            gate item 3
#   3  The ArchUnit import census rejects a truncated class import,
#      which is the failure that would make every rule above pass
#      vacuously
#   4  The JaCoCo core gate rejects an untested class in a gated
#      package (90% line / 85% branch)                                gate item 4
#   5  The JaCoCo view-model gate rejects an untested view-model in
#      org.cometgui.ui.viewmodel (80% line)                           gate item 4
#   6  PIT rejects a test that runs the code and checks nothing about
#      it -- the weakness line coverage cannot see                    R-TEST-02
#   7  `bash scripts/build.sh` rejects a module whose coverage was never
#      measured -- the vacuous pass JaCoCo offers for free, where the
#      rule is never evaluated and `mvn verify` still exits 0
#   S  The harness itself: a control whose defect was NOT injected is
#      reported as a harness failure, not as a pass
#
# WHERE IT WORKS.  Never in the working tree.  It builds a copy of the modules,
# POMs and rule sets under _build/test-gate-sandbox/ and damages that, so a
# half-injured file can never be committed by accident.  tools/ is symlinked in
# rather than copied, because the headless JavaFX tests need the project-local
# font stack and it is 30 MB.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip.  Those are
# unit 2's gates, they have their own harness, and two of the injected defects
# here (a class with no licence header, a deliberately misformatted one) would
# be rejected by them first -- the control would then "fail" for the wrong
# reason and prove nothing about coverage or architecture.  SpotBugs alone also
# costs about 45 s per module.  Nothing that this script is testing is skipped.
#
# EVERY NUMBER HERE IS DERIVED FROM THE TREE, NOT WRITTEN DOWN.  A control is
# only as strong as the defect it injects, and how big a defect has to be to
# move a ratio depends on how big the module is.  This script was first written
# against a cometgui-domain of ~35 covered lines and 22 mutations, and its
# injections were sized by hand for that: a class with a handful of uncovered
# lines, one weakened test class.  Phase 02 took the same module to 301 covered
# lines and 152 mutations and both injections stopped biting -- the module
# stayed near 0.97 line coverage and 89% mutation score WITH THE DEFECT PRESENT,
# so the gates correctly said nothing and this harness reported the GATES as
# broken when what had gone stale were its own INJECTIONS.  A control that has
# silently stopped injecting anything is worse than no control at all, so:
#
#   * injection sizes are computed each run from the module's own JaCoCo and PIT
#     reports (see "sizing from the module" below), never from a constant;
#   * expected diagnostics that carry a number -- a coverage ratio, a mutation
#     score, a class count -- are matched by SHAPE with assert_log_matches, so
#     that the assertion says what must be true rather than what happened to be
#     true on one day in August;
#   * a size that cannot be computed is a fatal harness error, never a guess.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/test-gate-sandbox"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/test-gate-logs"

readonly DOMAIN_PKG="cometgui-domain/src/main/java/org/cometgui/domain/build"
readonly TOOLS_PKG="cometgui-tools/src/main/java/org/cometgui/tools/comet"
readonly VIEWMODEL_PKG="cometgui-ui/src/main/java/org/cometgui/ui/viewmodel"
# Control S's specimen for the "the file was not actually modified" guard, and
# nothing else: no control damages this file any more.  Control 6 used to
# weaken it, and stopped when weakening one small test class stopped moving the
# module's mutation score (see that control).
readonly DOMAIN_TEST="cometgui-domain/src/test/java/org/cometgui/domain/build/BuildIdentityTest.java"

# Control 5 APPENDS its uncovered view-model to a file the gated package already
# has, instead of adding a new one.  It would rather add one, and may not:
# org.cometgui.ui.viewmodel.ViewModelIndependenceTest asserts the exact list of
# source files in that package, so a new file makes THAT test -- a real gate,
# and not the one under test here -- fail first, before JaCoCo evaluates
# anything, and the control then proves nothing about coverage.  A second,
# package-private class appended to a file that test already knows about leaves
# the list and the existing class untouched and still puts an entirely
# uncovered class into the gated package, which is the defect this control
# exists to inject.  Do not "fix" this by excluding or weakening that test.
readonly VIEWMODEL_HOST="${VIEWMODEL_PKG}/NonNullProperty.java"

# Control 6's pair: a class whose every statement a test executes, and the test
# that executes it without checking anything it computed.
readonly MUTATION_CONTROL="${DOMAIN_PKG}/UnassertedControl.java"
readonly MUTATION_CONTROL_TEST="cometgui-domain/src/test/java/org/cometgui/domain/build/UnassertedControlTest.java"

# The three gates of unit 2 are off in the sandbox; see the header comment.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
)

PASSED=0
FAILED=0
FAILURES=()

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the JaCoCo, ArchUnit and PIT gates fail on the defects
they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME} [-h|--help]

It needs a populated ${M2REPO#"${ROOT}/"} and a built tree (run
bash scripts/build.sh first), the project-local toolchain in tools/, and the
font stack in tools/fontstack-bookworm-20260829.  It writes only under _build/.
USAGE
}

die() {
    printf '\nFATAL: %s\n' "$*" >&2
    exit 1
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

# Run Maven inside the sandbox.  The absolute -Dmaven.repo.local overrides the
# relative one in .mvn/maven.config, which would otherwise create a second,
# empty repository inside the sandbox.
run_mvn() {
    local log="$1"
    shift
    local rc=0
    ( cd "${SANDBOX}" && mvn -B -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" "$@" ) \
        >"${log}" 2>&1 || rc=$?
    return "${rc}"
}

# TWO WAYS TO STATE AN EXPECTED DIAGNOSTIC, AND WHEN TO USE WHICH.  A diagnostic
# with no number in it is asserted literally (fixed), because a literal cannot
# drift into matching something it was not meant to.  A diagnostic that carries a
# number -- a coverage ratio, a mutation score, a class count -- is asserted by
# SHAPE (regex), because the number depends on the size of the tree and an
# assertion pinned to today's value is an assertion that a later phase will
# delete rather than fix.  The shape still has to name the threshold, the element
# and the counter: "the gate failed somehow" is not an assertion.
log_has() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -qF -- "${expected}" "${log}" ;;
        regex) grep -qE -- "${expected}" "${log}" ;;
        *) die "HARNESS ERROR: unknown match mode '${mode}'" ;;
    esac
}

log_first_match() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -F -- "${expected}" "${log}" | head -1 ;;
        regex) grep -E -- "${expected}" "${log}" | head -1 ;;
    esac
}

# assert_fails <label> <expected diagnostic> <log> <maven args...>
# assert_fails_matching <label> <expected ERE> <log> <maven args...>
# The gate must exit non-zero AND say why.  A non-zero exit with some other
# message means something else broke, and is reported as a failure.
assert_fails() { graded_failure fixed "$@"; }
assert_fails_matching() { graded_failure regex "$@"; }

graded_failure() {
    local mode="$1" label="$2" expected="$3" log="$4"
    shift 4
    local rc=0
    run_mvn "${log}" "$@" || rc=$?
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: the gate exited 0 with the defect present (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! log_has "${log}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        printf '         last error line: %s\n' "$(grep -m1 'ERROR' "${log}" | cut -c1-140)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | cut -c1-160)"
}

# assert_log_contains <label> <log> <expected>
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

# assert_pit_killed_everything <label> <log>
# The baseline mutation run must have mutated something and killed all of it.
# BOTH halves matter and neither is a constant: a run that generated no
# mutations is the vacuous pass PIT offers when its target classes match
# nothing, and a run that left survivors means the clean tree does not meet the
# gate, which would make every dirty run below meaningless.  The count itself is
# read out of the log (22 when this harness was written, 152 after phase 02);
# what is asserted is that it is greater than zero and that every one died.
assert_pit_killed_everything() {
    local label="$1" log="$2" summary generated killed percent
    summary="$(pit_summary "${log}")"
    if [ -z "${summary}" ]; then
        record_fail "${label}: no '>> Generated N mutations Killed K (P%)' line in ${log#"${ROOT}/"}; PIT did not report a mutation summary at all"
        return
    fi
    read -r generated killed percent <<<"${summary}"
    if [ "${generated}" -lt 1 ]; then
        record_fail "${label}: PIT generated 0 mutations, so the 80% gate was evaluated over nothing"
        return
    fi
    if [ "${killed}" -ne "${generated}" ] || [ "${percent}" -ne 100 ]; then
        record_fail "${label}: the clean tree left survivors -- ${killed}/${generated} killed (${percent}%)"
        return
    fi
    record_pass "${label}: ${generated} mutations, all ${killed} killed (${percent}%)"
}

# assert_pit_survivors_were_covered <label> <log>
# The survivors this control injects must be COVERED code that no test checks,
# not code no test runs: that is the whole claim of control 6, and it is what
# makes the mutation gate worth having on top of the coverage gate.  PIT reports
# it directly -- "Mutations with no coverage 0" -- and 0 is the only acceptable
# value, so this one really is a constant and not a measurement.
assert_pit_survivors_were_covered() {
    local label="$1" log="$2" uncovered
    uncovered="$(sed -n 's/.*>> Mutations with no coverage \([0-9][0-9]*\)\..*/\1/p' "${log}" | tail -1)"
    if [ -z "${uncovered}" ]; then
        record_fail "${label}: no '>> Mutations with no coverage N' line in ${log#"${ROOT}/"}"
        return
    fi
    if [ "${uncovered}" -ne 0 ]; then
        record_fail "${label}: ${uncovered} mutation(s) had no coverage, so the coverage gate would have caught this defect too and the control proves nothing about mutation testing"
        return
    fi
    record_pass "${label}: every mutation was executed by a passing test (0 with no coverage)"
    printf '         %s\n' "$(grep -F -- '>> Mutations with no coverage' "${log}" | head -1 | cut -c1-160)"
}

# assert_passes <label> <log> <maven args...>
assert_passes() {
    local label="$1" log="$2"
    shift 2
    local rc=0
    run_mvn "${log}" "$@" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "${label}: the gate still fails after the defect was removed (log: ${log#"${ROOT}/"})"
        printf '         %s\n' "$(grep -m3 '^\[ERROR\]' "${log}" | head -3 | cut -c1-140)"
        return
    fi
    record_pass "${label}: exit 0"
}

# assert_build_script_fails <label> <expected diagnostic, an ERE> <log>
# The whole documented build command, run inside the sandbox.  Some defects are
# invisible to any single Maven goal and are caught only by the evidence checks
# in scripts/build.sh; this is how those are proved to work.  The expected
# diagnostic is a regular expression because the one it checks counts classes.
assert_build_script_fails() {
    local label="$1" expected="$2" log="$3" rc=0
    ( cd "${SANDBOX}" && bash scripts/build.sh --offline ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: scripts/build.sh exited 0 with the defect present (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! grep -qE -- "${expected}" "${log}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(grep -E -- "${expected}" "${log}" | head -1 | cut -c1-160)"
}

# Harness falsifiability: a control whose defect was never written to disk must
# be a harness failure, not a silent pass.
assert_injected() {
    local label="$1" file="$2"
    if [ ! -e "${SANDBOX}/${file}" ]; then
        die "HARNESS ERROR (${label}): ${file} was not created in the sandbox. The control would have tested nothing."
    fi
    printf '   injected %s (%s bytes)\n' "${file}" "$(stat -Lc %s "${SANDBOX}/${file}")"
}

# The same, for a defect injected by editing an existing file rather than by
# adding one: the file must exist AND must differ from the pristine copy.
assert_modified() {
    local label="$1" file="$2"
    [ -e "${SANDBOX}/${file}" ] \
        || die "HARNESS ERROR (${label}): ${file} is missing from the sandbox."
    if cmp -s "${SANDBOX}/${file}" "${ROOT}/${file}"; then
        die "HARNESS ERROR (${label}): ${file} is byte-identical to the working tree copy. The defect was not injected and the control would have tested nothing."
    fi
    printf '   injected %s (differs from the working tree copy)\n' "${file}"
}

assert_removed() {
    local label="$1" file="$2"
    if [ -e "${SANDBOX}/${file}" ]; then
        die "HARNESS ERROR (${label}): ${file} still exists after removal. The clean re-run would not be clean."
    fi
}

restore_from_tree() {
    local file="$1"
    cp "${ROOT}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${ROOT}/${file}" \
        || die "HARNESS ERROR: could not restore ${file} in the sandbox."
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf "${SANDBOX}"
    mkdir -p "${SANDBOX}"

    cp "${ROOT}/pom.xml" "${SANDBOX}/pom.xml"
    cp -r "${ROOT}/.mvn" "${SANDBOX}/.mvn"
    cp -r "${ROOT}/config" "${SANDBOX}/config"
    # The headless JavaFX tests resolve the font stack through
    # maven.multiModuleProjectDirectory, which is the sandbox root here.  A
    # symlink, not a copy: tools/ holds a JDK, Maven and 30 MB of fonts.
    ln -s "${ROOT}/tools" "${SANDBOX}/tools"

    # Control 7 runs the documented build command inside the sandbox, so it
    # needs the scripts (copied, not symlinked: build.sh derives the repository
    # root from its own path), the virtualenv and the populated local
    # repository.  The last two are symlinks because they are large and are only
    # read.
    cp -r "${ROOT}/scripts" "${SANDBOX}/scripts"
    ln -s "${ROOT}/.venv" "${SANDBOX}/.venv"
    mkdir -p "${SANDBOX}/_build"
    ln -s "${M2REPO}" "${SANDBOX}/_build/m2repo"

    local module
    for module in "${ROOT}"/cometgui-*/; do
        module="$(basename -- "${module}")"
        mkdir -p "${SANDBOX}/${module}"
        cp "${ROOT}/${module}/pom.xml" "${SANDBOX}/${module}/pom.xml"
        [ -d "${ROOT}/${module}/src" ] && cp -r "${ROOT}/${module}/src" "${SANDBOX}/${module}/src"
    done

    # Only the modules: the sandbox also holds a copy of scripts/, which brings
    # the Phase 00 feasibility spike sources with it.
    local copied original
    copied="$(find "${SANDBOX}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    original="$(find "${ROOT}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    [ "${copied}" -eq "${original}" ] \
        || die "sandbox has ${copied} java files but the working tree has ${original}; the copy is wrong."
    echo "Sandbox: ${SANDBOX#"${ROOT}/"} (${copied} java files copied from the working tree)"
}

# A class carrying the project licence header, so that only the gate under test
# can reject it.
write_class() {
    local path="$1" body="$2"
    mkdir -p "$(dirname -- "${SANDBOX}/${path}")"
    {
        cat "${ROOT}/config/license/java-header.txt"
        printf '%s\n' "${body}"
    } >"${SANDBOX}/${path}"
}

# The same, for a class whose SIZE is computed rather than written out: the body
# arrives on standard input and the package declaration is written here, so that
# a generator can stay a plain sequence of printfs.
write_generated_class() {
    local path="$1" package="$2"
    mkdir -p "$(dirname -- "${SANDBOX}/${path}")"
    {
        cat "${ROOT}/config/license/java-header.txt"
        printf 'package %s;\n\n' "${package}"
        cat
    } >"${SANDBOX}/${path}"
}

# ------------------------------------------------- sizing from the module --
#
# WHY THE SIZES ARE MEASURED AND NOT WRITTEN DOWN, in one paragraph, because
# this is the part of the harness that rotted: to push a bundle of C covered
# lines below a 0.90 ratio the injection needs more than C/9 uncovered lines,
# and C is whatever the module happens to be today.  Twelve uncovered lines were
# plenty against a 35-line module and are noise against a 301-line one.  So each
# control asks the module how big it is -- out of the JaCoCo XML and the PIT XML
# the build itself produced -- and then generates an injection that is a
# MULTIPLE of the smallest one that could work.  The multiple is deliberate
# headroom: the report a size is read from may be one build behind the sources
# in the sandbox, and an injection that is merely large enough is an injection
# that will be one line too small the next time somebody adds a test.

# jacoco_report_of <module> -- the JaCoCo XML to size from: the sandbox's own,
# if a control has already produced one this run, otherwise the working tree's.
# There is no third option: a size that cannot be measured must stop the
# harness, because a guessed one is exactly the silent under-injection that
# brought this script here.
jacoco_report_of() {
    local module="$1" candidate
    for candidate in "${SANDBOX}/${module}/target/site/jacoco/jacoco.xml" \
                     "${ROOT}/${module}/target/site/jacoco/jacoco.xml"; do
        if [ -s "${candidate}" ]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done
    die "HARNESS ERROR: no JaCoCo report for ${module} in the sandbox or the working tree, so this control cannot be sized from the module. Run bash scripts/build.sh first."
}

# jacoco_covered_lines <module> [<package, in VM form>] -- covered LINE count
# for the bundle (no second argument, i.e. the counters JaCoCo puts at the end
# of the document) or for one package element.  The package form is what the
# view-model rule needs: that rule is PACKAGE-scoped, so sizing it from the
# bundle would size it against the whole UI module and inject far too little.
jacoco_covered_lines() {
    local module="$1" package="${2:-}" xml
    xml="$(jacoco_report_of "${module}")"
    python3 - "${xml}" "${package}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

path, package = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
if package:
    matches = [p for p in root.findall("package") if p.get("name") == package]
    if not matches:
        raise SystemExit("no package %r in %s" % (package, path))
    node, what = matches[0], "package " + package
else:
    # Direct children of <report> are the bundle totals.
    node, what = root, "the bundle"
counters = [c for c in node.findall("counter") if c.get("type") == "LINE"]
if not counters:
    raise SystemExit("no LINE counter for %s in %s" % (what, path))
covered = int(counters[0].get("covered"))
if covered <= 0:
    raise SystemExit("%s covers %d lines in %s; nothing to size against" % (what, covered, path))
print(covered)
PYTHON
}

# pit_report_of / pit_mutation_total <module> -- the same, for PIT's mutations.
pit_report_of() {
    local module="$1" candidate
    for candidate in "${SANDBOX}/${module}/target/pit-reports/mutations.xml" \
                     "${ROOT}/${module}/target/pit-reports/mutations.xml"; do
        if [ -s "${candidate}" ]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done
    die "HARNESS ERROR: no PIT report for ${module} in the sandbox or the working tree, so the mutation control cannot be sized from the module. Run bash scripts/build.sh first."
}

pit_mutation_total() {
    local module="$1" xml
    xml="$(pit_report_of "${module}")"
    python3 - "${xml}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
total = len(ET.parse(path).getroot().findall("mutation"))
if total <= 0:
    raise SystemExit("no <mutation> elements in %s; nothing to size against" % path)
print(total)
PYTHON
}

# pit_summary <log> -- "generated killed percent" from PIT's own summary line,
# or nothing if the run printed none.
pit_summary() {
    sed -n 's/.*>> Generated \([0-9][0-9]*\) mutations Killed \([0-9][0-9]*\) (\([0-9][0-9]*\)%).*/\1 \2 \3/p' \
        "$1" | tail -1
}

# lines_to_break_ratio <covered> <numerator> <denominator>
# How many uncovered lines to inject so that a rule requiring
# numerator/denominator covered lines is violated.  C/(C+U) < n/d as soon as
# U > C*(d-n)/n, so the smallest injection that could work is
# C*(d-n)/n + 1; this returns twice that -- and never fewer than 24 lines, so
# that the control still injects something visible into a module that is small
# today.
lines_to_break_ratio() {
    local covered="$1" numerator="$2" denominator="$3" smallest doubled
    smallest=$(( covered * (denominator - numerator) / numerator + 1 ))
    doubled=$(( smallest * 2 ))
    [ "${doubled}" -lt 24 ] && doubled=24
    printf '%d\n' "${doubled}"
}

# mutations_to_break_score <killed> <threshold percent>
# The same arithmetic for the mutation gate.  With every existing mutation
# killed, a score of K/(K+S) falls below t% as soon as S > K*(100-t)/t, so the
# smallest useful injection is K*(100-t)/t + 1 surviving mutations; this returns
# twice that.  The generated class yields at least one mutation per statement
# (see uncovered_class_source), so statements are a safe lower bound for
# survivors.
mutations_to_break_score() {
    local killed="$1" threshold="$2" smallest doubled
    smallest=$(( killed * (100 - threshold) / threshold + 1 ))
    doubled=$(( smallest * 2 ))
    [ "${doubled}" -lt 24 ] && doubled=24
    printf '%d\n' "${doubled}"
}

# uncovered_class_source <class name> <statements> [<modifiers>]
# A class of <statements> arithmetic statements, one per line, written to be
# boring on purpose:
#
#   * no branches, so it moves the LINE counter and leaves the BRANCH counter
#     alone -- the diagnostic under test is then unambiguously the line rule,
#     not the branch rule that happens to fail at the same time;
#   * no exceptions and constant operands, so that a PIT mutant cannot be killed
#     by a throw (`*` mutated to `/` must not divide by zero) rather than by an
#     assertion that does not exist;
#   * one arithmetic operator per statement, so PIT's MathMutator generates at
#     least one mutation per line and the size arithmetic above holds.
uncovered_class_source() {
    local name="$1" statements="$2" modifiers="${3:-public final}" i
    printf '/** Temporary class written by scripts/%s. Never committed. */\n' "${SCRIPT_NAME}"
    printf '%s class %s {\n\n' "${modifiers}" "${name}"
    printf '    private %s() {}\n\n' "${name}"
    printf '    /**\n'
    printf '     * %d statements that no test covers and no assertion checks.\n' "${statements}"
    printf '     *\n'
    printf '     * @param seed a number nobody tests\n'
    printf '     * @return a number nobody checks\n'
    printf '     */\n'
    printf '    static long uncounted(long seed) {\n'
    printf '        long value = seed;\n'
    for (( i = 1; i <= statements; i++ )); do
        case $(( i % 3 )) in
            0) printf '        value = value + %d;\n' "$(( i + 1 ))" ;;
            1) printf '        value = value - %d;\n' "$(( i + 3 ))" ;;
            2) printf '        value = value * %d;\n' "$(( (i % 7) + 2 ))" ;;
        esac
    done
    printf '        return value;\n'
    printf '    }\n'
    printf '}\n'
}

# sizing <lines...> -- the arithmetic behind an injection, printed into the log
# beside the control it belongs to, because a control whose size nobody can see
# is a control nobody can check.
sizing() {
    printf '   sizing   %s\n' "$@"
}

# ------------------------------------------------------------- the controls --

control_baseline() {
    banner "0  baseline: the undamaged sandbox passes all three gates"
    assert_passes "baseline architecture rules" "${LOGS}/00-baseline-arch.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest,ClassImportCensusTest' \
        -Dsurefire.failIfNoSpecifiedTests=false
    assert_passes "baseline coverage gate (cometgui-domain, 90% line / 85% branch)" \
        "${LOGS}/00-baseline-coverage.log" -pl cometgui-domain verify
    assert_passes "baseline mutation gate (cometgui-domain, 80% score)" \
        "${LOGS}/00-baseline-mutation.log" \
        -pl cometgui-domain test-compile org.pitest:pitest-maven:mutationCoverage
    assert_pit_killed_everything \
        "the baseline mutation run really mutated something, and killed all of it" \
        "${LOGS}/00-baseline-mutation.log"
}

control_javafx_in_domain() {
    banner "1  layering: a JavaFX import in cometgui-domain (gate item 3)"
    write_class "${DOMAIN_PKG}/NegativeControl.java" 'package org.cometgui.domain.build;

import javafx.scene.control.Label;

/** Temporary class written by scripts/verify-test-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}

    /**
     * @return a JavaFX control, which the domain has no business creating
     */
    public static Label aControlInTheDomain() {
        return new Label("the domain must not depend on JavaFX");
    }
}'
    assert_injected "javafx in domain" "${DOMAIN_PKG}/NegativeControl.java"

    assert_fails "ArchUnit rejects a JavaFX dependency in the domain" \
        "Architecture Violation" \
        "${LOGS}/01-javafx-dirty.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
    assert_log_contains "the violation names the offending class" \
        "${LOGS}/01-javafx-dirty.log" "NegativeControl"
    assert_log_contains "the violation names the rule that caught it" \
        "${LOGS}/01-javafx-dirty.log" "org.cometgui.domain.."

    rm -f "${SANDBOX}/${DOMAIN_PKG}/NegativeControl.java"
    assert_removed "javafx in domain" "${DOMAIN_PKG}/NegativeControl.java"
    assert_passes "ArchUnit accepts the tree once the import is gone" \
        "${LOGS}/01-javafx-clean.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
}

control_processbuilder_outside_process_service() {
    banner "2  R-PROC-02: new ProcessBuilder outside the process service (gate item 3)"
    # cometgui-tools is a tool adapter module: exactly the place a "just run the
    # binary here" shortcut would appear.
    write_class "${TOOLS_PKG}/NegativeControl.java" 'package org.cometgui.tools.comet;

import java.io.IOException;

/** Temporary class written by scripts/verify-test-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}

    /**
     * @return the exit code of a process this class had no business starting
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the wait is interrupted
     */
    public static int runCometDirectly() throws IOException, InterruptedException {
        return new ProcessBuilder("comet", "-P/tmp/comet.params").start().waitFor();
    }
}'
    assert_injected "ProcessBuilder outside the process service" "${TOOLS_PKG}/NegativeControl.java"

    assert_fails "ArchUnit rejects process creation outside the process service" \
        "Architecture Violation" \
        "${LOGS}/02-processbuilder-dirty.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
    assert_log_contains "the violation names R-PROC-02" \
        "${LOGS}/02-processbuilder-dirty.log" "R-PROC-02"
    assert_log_contains "the violation names ProcessBuilder" \
        "${LOGS}/02-processbuilder-dirty.log" "ProcessBuilder"

    rm -f "${SANDBOX}/${TOOLS_PKG}/NegativeControl.java"
    assert_removed "ProcessBuilder outside the process service" "${TOOLS_PKG}/NegativeControl.java"
    assert_passes "ArchUnit accepts the tree once process creation is gone" \
        "${LOGS}/02-processbuilder-clean.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest' -Dsurefire.failIfNoSpecifiedTests=false
}

control_truncated_import() {
    banner "3  the vacuous pass itself: an ArchUnit import that is not the whole product"
    # This is the failure mode the census exists for.  A rule set that imports
    # the wrong class path passes every rule while the violation sits in front
    # of it, and the rules themselves cannot tell: only a test that asserts what
    # came back can.
    #
    # 3a drops cometgui-app from cometgui-archtests' dependencies.  It has to be
    # that module: dropping, say, cometgui-results changes nothing, because
    # cometgui-ui depends on it and it arrives transitively.  Nothing depends on
    # cometgui-app, so dropping it really does remove its classes -- a detail
    # this control discovered the hard way, by failing.
    python3 - "${SANDBOX}/cometgui-archtests/pom.xml" <<'PYTHON'
import re
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
block = re.search(
    r"\n    <dependency>\s*\n\s*<groupId>org\.cometgui</groupId>\s*\n"
    r"\s*<artifactId>cometgui-app</artifactId>\s*\n\s*<scope>test</scope>\s*\n\s*</dependency>",
    text,
)
if block is None:
    raise SystemExit("could not find the cometgui-app dependency to remove")
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text[: block.start()] + text[block.end() :])
PYTHON
    assert_modified "truncated import" "cometgui-archtests/pom.xml"

    assert_fails "the census rejects an import that is missing a module" \
        "no classes were imported from org.cometgui.app" \
        "${LOGS}/03-census-dirty.log" \
        -pl cometgui-archtests -am test \
        -Dtest='LayeringRulesTest,ClassImportCensusTest' -Dsurefire.failIfNoSpecifiedTests=false
    assert_log_contains "the layering rules stayed green, which is exactly why the census exists" \
        "${LOGS}/03-census-dirty.log" "LayeringRulesTest"

    restore_from_tree "cometgui-archtests/pom.xml"
    assert_passes "the census accepts the restored dependency list" \
        "${LOGS}/03-census-clean.log" \
        -pl cometgui-archtests -am test \
        -Dtest='ClassImportCensusTest' -Dsurefire.failIfNoSpecifiedTests=false

    # 3b is the blunter version of the same defect: the importer pointed at a
    # package that does not exist.  Every noClasses() rule would pass; the size
    # assertion is what stops it.
    banner "3b the same failure, blunter: the importer pointed at the wrong package"
    sed -i 's|static final String ROOT_PACKAGE = "org.cometgui";|static final String ROOT_PACKAGE = "org.cometgui.nosuchproduct";|' \
        "${SANDBOX}/cometgui-archtests/src/test/java/org/cometgui/archtests/ProductClasses.java"
    assert_modified "wrong import package" \
        "cometgui-archtests/src/test/java/org/cometgui/archtests/ProductClasses.java"

    assert_fails "the census rejects an empty import" \
        "which is below the floor of 50" \
        "${LOGS}/03b-wrongpackage-dirty.log" \
        -pl cometgui-archtests -am test \
        -Dtest='ClassImportCensusTest' -Dsurefire.failIfNoSpecifiedTests=false
    assert_log_contains "the diagnostic says what an empty import would have meant" \
        "${LOGS}/03b-wrongpackage-dirty.log" "would pass vacuously"

    restore_from_tree "cometgui-archtests/src/test/java/org/cometgui/archtests/ProductClasses.java"
    assert_passes "the census accepts the correct import package" \
        "${LOGS}/03b-wrongpackage-clean.log" \
        -pl cometgui-archtests -am test \
        -Dtest='ClassImportCensusTest' -Dsurefire.failIfNoSpecifiedTests=false
}

control_untested_class_in_gated_package() {
    banner "4  coverage: an untested class in a gated package (gate item 4)"
    # SIZED FROM THE MODULE, not from a constant.  The rule this control has to
    # provoke is BUNDLE-scoped at 0.90 line, so the injection has to out-weigh
    # everything cometgui-domain covers: a class with a handful of uncovered
    # lines left a 301-line module at 0.97, where the gate is right to say
    # nothing.  How many lines that takes is asked of the module, every run.
    local covered statements
    covered="$(jacoco_covered_lines cometgui-domain)" \
        || die "HARNESS ERROR: control 4 could not size its injection from cometgui-domain (see above). An injection of guessed size proves nothing."
    statements="$(lines_to_break_ratio "${covered}" 9 10)"
    sizing "cometgui-domain covers ${covered} lines; C/(C+U) < 0.90 needs U > C/9 = $(( covered / 9 ))." \
           "${statements} uncovered statements are injected, taking the bundle to about $(( covered * 100 / (covered + statements) ))% -- below the 90% the rule requires."
    uncovered_class_source NegativeControl "${statements}" \
        | write_generated_class "${DOMAIN_PKG}/NegativeControl.java" org.cometgui.domain.build
    assert_injected "untested class" "${DOMAIN_PKG}/NegativeControl.java"

    assert_fails "the coverage gate rejects an untested class in cometgui-domain" \
        "Rule violated for bundle cometgui-domain" \
        "${LOGS}/04-coverage-dirty.log" -pl cometgui-domain verify
    assert_log_matches "the diagnostic names the counter, the ratio it measured and the specification's line threshold" \
        "${LOGS}/04-coverage-dirty.log" \
        'lines covered ratio is [0-9]+\.[0-9]+, but expected minimum is 0\.90'

    rm -f "${SANDBOX}/${DOMAIN_PKG}/NegativeControl.java"
    assert_removed "untested class" "${DOMAIN_PKG}/NegativeControl.java"
    assert_passes "the coverage gate accepts the tree once the class is gone" \
        "${LOGS}/04-coverage-clean.log" -pl cometgui-domain clean verify
}

control_untested_viewmodel() {
    banner "5  coverage: an untested view-model in org.cometgui.ui.viewmodel (gate item 4)"
    # The view-model rule is scoped to a PACKAGE rather than to the whole UI
    # module, because the specification gives the JavaFX rendering glue no
    # numeric target.  Two consequences, and this control has been wrong about
    # both at some point in its life:
    #
    #   * it must be sized from THAT PACKAGE's counters, not the bundle's.  The
    #     UI module covers several times what the view-model package does, so an
    #     injection sized from the bundle would be far larger than it needs to
    #     be, and one sized from the core rule's 0.90 would be too small for
    #     this rule's 0.80;
    #   * the diagnostic asserted below names the package, with the colon that
    #     ends the element name, so that a violation reported for some other
    #     package cannot be mistaken for this rule biting.
    #
    # An element pattern that matched nothing would make the rule permanently
    # inert without anyone noticing, and this control is what stops that: it
    # already caught the pattern once, when it was written with slashes.
    #
    # WHY IT APPENDS INSTEAD OF ADDING A FILE: see VIEWMODEL_HOST above.
    local covered statements
    covered="$(jacoco_covered_lines cometgui-ui org/cometgui/ui/viewmodel)" \
        || die "HARNESS ERROR: control 5 could not size its injection from org.cometgui.ui.viewmodel (see above). An injection of guessed size proves nothing."
    statements="$(lines_to_break_ratio "${covered}" 8 10)"
    sizing "org.cometgui.ui.viewmodel covers ${covered} lines; C/(C+U) < 0.80 needs U > C/4 = $(( covered / 4 ))." \
           "${statements} uncovered statements are injected, taking the package to about $(( covered * 100 / (covered + statements) ))% -- below the 80% the rule requires."
    {
        printf '\n'
        uncovered_class_source UntestedViewModel "${statements}" final
    } >>"${SANDBOX}/${VIEWMODEL_HOST}"
    assert_modified "untested view-model class" "${VIEWMODEL_HOST}"

    assert_fails "the view-model coverage gate rejects an untested view-model class" \
        "Rule violated for package org.cometgui.ui.viewmodel:" \
        "${LOGS}/05-viewmodel-dirty.log" -pl cometgui-ui -am verify
    assert_log_matches "the diagnostic names the package, the ratio it measured and the specification's view-model threshold" \
        "${LOGS}/05-viewmodel-dirty.log" \
        'Rule violated for package org\.cometgui\.ui\.viewmodel: lines covered ratio is [0-9]+\.[0-9]+, but expected minimum is 0\.80'

    restore_from_tree "${VIEWMODEL_HOST}"
    assert_passes "the view-model coverage gate accepts the tree once the class is gone" \
        "${LOGS}/05-viewmodel-clean.log" -pl cometgui-ui -am clean verify
}

control_unasserted_test() {
    banner "6  mutation: a test that runs the code and checks nothing about it (R-TEST-02)"
    # The defect is exactly what CONTRIBUTING.rst forbids -- a test that asserts
    # that nothing threw -- and it passes.  It also EXECUTES every line of the
    # class it exercises, so line coverage does not move: that is the whole
    # claim of this control, and two assertions below prove it rather than
    # asserting it in a comment.  PIT reports "Mutations with no coverage 0",
    # meaning every survivor was run by a passing test, and the coverage gate is
    # then run over the very same defect and required to ACCEPT it.  What is
    # left is a weakness only the mutation score can see, which is why R-TEST-02
    # exists on top of the coverage gate.
    #
    # WHY IT IS NO LONGER A WEAKENED BuildIdentityTest.  It was, and one
    # weakened test class was enough when the module generated 22 mutations.  It
    # now generates 152, so weakening one small class leaves about 89% -- above
    # the threshold, no rejection, and a control that injected nothing.
    # Weakening enough of the real suite to move a score computed over the whole
    # module would mean hand-picking a set of test classes to gut, and that
    # choice would go stale again with the next phase.  A generated class of
    # measured size cannot: it is re-sized from PIT's own report every run.
    local mutations statements
    mutations="$(pit_mutation_total cometgui-domain)" \
        || die "HARNESS ERROR: control 6 could not size its injection from cometgui-domain's PIT report (see above). An injection of guessed size proves nothing."
    statements="$(mutations_to_break_score "${mutations}" 80)"
    sizing "cometgui-domain generates ${mutations} mutations and kills all of them; K/(K+S) < 80% needs S > K/4 = $(( mutations / 4 )) survivors." \
           "A class of ${statements} mutable statements is injected, with a test that checks none of them: a score of about $(( mutations * 100 / (mutations + statements) ))%, below the threshold of 80."
    uncovered_class_source UnassertedControl "${statements}" \
        | write_generated_class "${MUTATION_CONTROL}" org.cometgui.domain.build
    write_class "${MUTATION_CONTROL_TEST}" 'package org.cometgui.domain.build;

import org.junit.jupiter.api.Test;

/** Temporary test written by scripts/verify-test-gates.sh. Never committed. */
class UnassertedControlTest {

    /**
     * Runs every statement of {@link UnassertedControl} and checks nothing that it computed.
     *
     * <p>This is the injected defect. It passes, and it executes every line of the class, so
     * the line and branch counters stay where they were and the coverage gate has nothing to
     * say. Every mutation of the arithmetic it runs therefore survives, and the mutation score
     * is the only thing that can tell.
     */
    @Test
    void runsTheCodeAndAssertsNothingAboutIt() {
        UnassertedControl.uncounted(1L);
        UnassertedControl.uncounted(-7L);
    }
}'
    assert_injected "the unasserted class" "${MUTATION_CONTROL}"
    assert_injected "the test that runs it and checks nothing" "${MUTATION_CONTROL_TEST}"

    assert_fails_matching "the mutation gate rejects a suite that kills too few mutations" \
        'Mutation score of [0-9]+ is below threshold of 80' \
        "${LOGS}/06-mutation-dirty.log" \
        -pl cometgui-domain clean test-compile org.pitest:pitest-maven:mutationCoverage
    assert_pit_survivors_were_covered \
        "the survivors were executed by a passing test, so they are not merely untested code" \
        "${LOGS}/06-mutation-dirty.log"
    assert_passes "the coverage gate accepts the very same defect, which is why R-TEST-02 exists" \
        "${LOGS}/06-mutation-coverage-blind.log" -pl cometgui-domain verify

    rm -f "${SANDBOX}/${MUTATION_CONTROL}" "${SANDBOX}/${MUTATION_CONTROL_TEST}"
    assert_removed "the unasserted class" "${MUTATION_CONTROL}"
    assert_removed "the test that runs it and checks nothing" "${MUTATION_CONTROL_TEST}"
    assert_passes "the mutation gate accepts the real test suite" \
        "${LOGS}/06-mutation-clean.log" \
        -pl cometgui-domain clean test-compile org.pitest:pitest-maven:mutationCoverage
}

control_unmeasured_coverage() {
    banner "7  the vacuous pass JaCoCo offers for free: a module with no execution data"
    # This is the failure the brief calls the main risk of this unit, and it is
    # not hypothetical: switch the coverage agent off and jacoco:check reports
    # "Skipping JaCoCo execution due to missing execution data file", exits 0,
    # and the 90% / 85% rule is never evaluated at all.  No Maven goal can catch
    # that, because from Maven's point of view nothing went wrong.  The evidence
    # check in scripts/build.sh is what catches it, and this control is what
    # proves that check works.
    python3 - "${SANDBOX}/cometgui-domain/pom.xml" <<'PYTHON'
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
marker = "  <properties>\n"
if marker not in text:
    raise SystemExit("could not find the properties block in cometgui-domain/pom.xml")
text = text.replace(marker, marker + "    <jacoco.skip>true</jacoco.skip>\n", 1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text)
PYTHON
    assert_modified "unmeasured coverage" "cometgui-domain/pom.xml"

    assert_passes "mvn verify still exits 0 with no coverage data at all (the vacuous pass)" \
        "${LOGS}/07-unmeasured-verify.log" -pl cometgui-domain clean verify
    assert_log_contains "the 90% / 85% rule was never evaluated" \
        "${LOGS}/07-unmeasured-verify.log" \
        "Skipping JaCoCo execution due to missing execution data file"

    # The count in that diagnostic is the number of classes the module compiles,
    # which grows with the module; what must be true is that it named THIS
    # module, counted at least one class, and said no report existed for them.
    assert_build_script_fails "the documented build command rejects unmeasured coverage" \
        'MISSING  cometgui-domain: [1-9][0-9]* class\(es\) with code but no jacoco\.xml' \
        "${LOGS}/07-unmeasured-buildsh.log"

    restore_from_tree "cometgui-domain/pom.xml"
    # The clean counterpart of this control is acceptance condition 1 itself:
    # `bash scripts/build.sh` in the working tree, which exits 0.  Running the
    # whole build a second time here would cost 90 s to prove the same thing.
}

control_harness_self_test() {
    banner "S  the harness itself: an un-injected control must be a harness failure"
    # Every control above calls assert_injected or assert_modified before it
    # grades anything.  This proves those calls are not decoration: with the
    # defect deliberately not written, the harness must abort rather than run a
    # Maven command that would pass and be recorded as a control passing.
    rm -f "${SANDBOX}/${DOMAIN_PKG}/NegativeControl.java"
    local out rc=0
    out="$(assert_injected "deliberately un-injected" "${DOMAIN_PKG}/NegativeControl.java" 2>&1)" \
        || rc=$?
    if [ "${rc}" -ne 0 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR'; then
        record_pass "the harness refuses to grade a control whose defect never reached the sandbox"
        printf '         %s\n' "$(printf '%s' "${out}" | grep 'HARNESS ERROR' | cut -c1-160)"
    else
        record_fail "the harness accepted a control with no injected defect (exit ${rc}): ${out}"
    fi

    # The same for a defect injected by editing rather than by adding: an
    # unchanged file must be rejected as well as a missing one.
    rc=0
    out="$(assert_modified "deliberately unmodified" "${DOMAIN_TEST}" 2>&1)" || rc=$?
    if [ "${rc}" -ne 0 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR'; then
        record_pass "the harness refuses to grade an edit control whose file is unchanged"
        printf '         %s\n' "$(printf '%s' "${out}" | grep 'HARNESS ERROR' | cut -c1-160)"
    else
        record_fail "the harness accepted an unmodified edit control (exit ${rc}): ${out}"
    fi
}

# -------------------------------------------------------------------- main --
main() {
    case "${1:-}" in
        -h|--help) usage; exit 0 ;;
        "") ;;
        *) usage >&2; die "unknown option: $1" ;;
    esac

    cd -- "${ROOT}"
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run bash scripts/build.sh first."
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh."
    [ -d "${M2REPO}" ] || die "${M2REPO#"${ROOT}/"} does not exist; run bash scripts/build.sh first."
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing; run bash scripts/fetch-fontstack.sh first."

    mkdir -p "${LOGS}"
    rm -f "${LOGS}"/*.log

    local started
    started="$(date +%s)"

    build_sandbox
    control_baseline
    control_javafx_in_domain
    control_processbuilder_outside_process_service
    control_truncated_import
    control_untested_class_in_gated_package
    control_untested_viewmodel
    control_unasserted_test
    control_unmeasured_coverage
    control_harness_self_test

    printf '\n===============================================================================\n'
    printf ' SUMMARY: %d assertion(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "$(( $(date +%s) - started ))"
    printf ' Logs: %s\n' "${LOGS#"${ROOT}/"}"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} test-gate control(s) failed. A gate that cannot be seen to fail is not a gate."
    fi
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
