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
#   5  The JaCoCo view-model gate rejects an untested class in
#      org.cometgui.ui.viewmodel (80% line)                           gate item 4
#   6  PIT rejects a weakened test suite that lets mutations survive  R-TEST-02
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
readonly DOMAIN_TEST="cometgui-domain/src/test/java/org/cometgui/domain/build/BuildIdentityTest.java"

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

# assert_fails <label> <expected diagnostic> <log> <maven args...>
# The gate must exit non-zero AND say why.  A non-zero exit with some other
# message means something else broke, and is reported as a failure.
assert_fails() {
    local label="$1" expected="$2" log="$3"
    shift 3
    local rc=0
    run_mvn "${log}" "$@" || rc=$?
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: the gate exited 0 with the defect present (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! grep -qF -- "${expected}" "${log}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        printf '         last error line: %s\n' "$(grep -m1 'ERROR' "${log}" | cut -c1-140)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(grep -F -- "${expected}" "${log}" | head -1 | cut -c1-160)"
}

# assert_log_contains <label> <log> <expected>
# Extra evidence from a log a control has already produced, so that one control
# can require several strings without paying for a second Maven run.
assert_log_contains() {
    local label="$1" log="$2" expected="$3"
    if grep -qF -- "${expected}" "${log}"; then
        record_pass "${label}"
        printf '         %s\n' "$(grep -F -- "${expected}" "${log}" | head -1 | cut -c1-160)"
    else
        record_fail "${label}: '${expected}' is not in ${log#"${ROOT}/"}"
    fi
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

# assert_build_script_fails <label> <expected diagnostic> <log>
# The whole documented build command, run inside the sandbox.  Some defects are
# invisible to any single Maven goal and are caught only by the evidence checks
# in scripts/build.sh; this is how those are proved to work.
assert_build_script_fails() {
    local label="$1" expected="$2" log="$3" rc=0
    ( cd "${SANDBOX}" && bash scripts/build.sh --offline ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: scripts/build.sh exited 0 with the defect present (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! grep -qF -- "${expected}" "${log}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(grep -F -- "${expected}" "${log}" | head -1 | cut -c1-160)"
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
    assert_log_contains "the baseline mutation run really mutated something" \
        "${LOGS}/00-baseline-mutation.log" "Generated 22 mutations Killed 22"
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
    # Enough lines and branches that one uncovered class moves the module's
    # ratio below 0.90 on its own.
    write_class "${DOMAIN_PKG}/NegativeControl.java" 'package org.cometgui.domain.build;

/** Temporary class written by scripts/verify-test-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}

    /**
     * @param value a number nobody tests
     * @return a description of the number
     */
    public static String describe(int value) {
        String description;
        if (value < 0) {
            description = "negative";
        } else if (value == 0) {
            description = "zero";
        } else if (value < 10) {
            description = "small";
        } else if (value < 100) {
            description = "medium";
        } else {
            description = "large";
        }
        String suffix = value % 2 == 0 ? " and even" : " and odd";
        String result = description + suffix;
        return result.toUpperCase(java.util.Locale.ROOT);
    }
}'
    assert_injected "untested class" "${DOMAIN_PKG}/NegativeControl.java"

    assert_fails "the coverage gate rejects an untested class in cometgui-domain" \
        "Rule violated for bundle cometgui-domain" \
        "${LOGS}/04-coverage-dirty.log" -pl cometgui-domain verify
    assert_log_contains "the diagnostic names the specification's line threshold" \
        "${LOGS}/04-coverage-dirty.log" "expected minimum is 0.90"

    rm -f "${SANDBOX}/${DOMAIN_PKG}/NegativeControl.java"
    assert_removed "untested class" "${DOMAIN_PKG}/NegativeControl.java"
    assert_passes "the coverage gate accepts the tree once the class is gone" \
        "${LOGS}/04-coverage-clean.log" -pl cometgui-domain clean verify
}

control_untested_viewmodel() {
    banner "5  coverage: an untested class in org.cometgui.ui.viewmodel (gate item 4)"
    # The view-model rule is scoped to a PACKAGE rather than to the whole UI
    # module, because the specification gives the JavaFX rendering glue no
    # numeric target.  It has no classes to judge today, so this control is the
    # only thing that proves the pattern in the POM actually matches -- an
    # element pattern that matches nothing would make the rule permanently
    # inert without anyone noticing.
    write_class "${VIEWMODEL_PKG}/NegativeControl.java" 'package org.cometgui.ui.viewmodel;

/** Temporary class written by scripts/verify-test-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}

    /**
     * @param enabled whether the run button should be enabled
     * @return the label an untested view-model would show
     */
    public static String runButtonLabel(boolean enabled) {
        if (enabled) {
            return "Run search";
        }
        return "Select inputs first";
    }
}'
    assert_injected "untested view-model class" "${VIEWMODEL_PKG}/NegativeControl.java"

    assert_fails "the view-model coverage gate rejects an untested view-model class" \
        "Rule violated for package org.cometgui.ui.viewmodel" \
        "${LOGS}/05-viewmodel-dirty.log" -pl cometgui-ui -am verify
    assert_log_contains "the diagnostic names the specification's view-model threshold" \
        "${LOGS}/05-viewmodel-dirty.log" "expected minimum is 0.80"

    rm -f "${SANDBOX}/${VIEWMODEL_PKG}/NegativeControl.java"
    assert_removed "untested view-model class" "${VIEWMODEL_PKG}/NegativeControl.java"
    assert_passes "the view-model coverage gate accepts the tree once the class is gone" \
        "${LOGS}/05-viewmodel-clean.log" -pl cometgui-ui -am clean verify
}

control_weakened_tests() {
    banner "6  mutation: a test suite weakened until mutations survive (R-TEST-02)"
    # The injected test is exactly what CONTRIBUTING.rst forbids -- it asserts
    # that nothing threw -- and it passes.  Line coverage stays high, which is
    # the point: coverage cannot tell the difference and the mutation score can.
    write_class "${DOMAIN_TEST}" 'package org.cometgui.domain.build;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Temporary replacement written by scripts/verify-test-gates.sh. Never committed. */
class BuildIdentityTest {

    @Test
    void doesNotThrow() {
        assertNotNull(
                BuildIdentity.of(
                        "0.1.0",
                        "0123456789abcdef0123456789abcdef01234567",
                        Instant.parse("2026-08-29T12:34:56Z")));
        assertNotNull(BuildIdentity.of("0.1.0", "unknown", Instant.EPOCH).toString());
    }
}'
    assert_modified "weakened tests" "${DOMAIN_TEST}"

    assert_fails "the mutation gate rejects a suite that kills too few mutations" \
        "is below threshold of 80" \
        "${LOGS}/06-mutation-dirty.log" \
        -pl cometgui-domain clean test-compile org.pitest:pitest-maven:mutationCoverage
    assert_log_contains "the weakened suite still passes, so only the mutation gate saw it" \
        "${LOGS}/06-mutation-dirty.log" "Generated 22 mutations"

    restore_from_tree "${DOMAIN_TEST}"
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

    assert_build_script_fails "the documented build command rejects unmeasured coverage" \
        "1 class(es) with code but no jacoco.xml" \
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
    control_weakened_tests
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
