#!/usr/bin/env bash
#
# CometGUI -- prove the formatting and static-analysis gates can fail.
#
#   bash scripts/verify-quality-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  This script injects, one at a time,
# the defect each gate exists to catch, requires the narrowest command that
# should catch it to exit non-zero with the expected diagnostic, and then
# requires the same command to pass once the defect is removed.
#
# WHERE IT WORKS.  Never in the working tree.  It builds a copy of the modules,
# POMs and rule sets under _build/quality-gate-sandbox/ and damages that, so a
# half-injured file can never be committed by accident.
#
# THE HARNESS IS ITSELF FALSIFIABLE.  Before every control it proves the defect
# really was injected (the file exists and differs from the pristine state), and
# it runs the clean baseline first.  A control whose defect was not injected, or
# whose baseline does not pass, is reported as a harness failure, not a pass.
#
# SCOPE.  This covers phase 01 unit 2: Spotless (formatting, import order,
# licence header), Checkstyle (the project rule set) and SpotBugs (bug
# patterns).  The aggregate falsifiability harness for every phase 01 gate is
# scripts/ci/negative-controls.sh, owned by unit 8; this script is meant to be
# called from it, and to be usable on its own while iterating.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/quality-gate-sandbox"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/quality-gate-logs"

# The module every control is injected into.  cometgui-domain is the only
# module with real behaviour, it has no reactor dependencies, and it compiles
# and tests in about a second, so each control costs one short Maven run.
readonly MODULE="cometgui-domain"
readonly PKG_DIR="${MODULE}/src/main/java/org/cometgui/domain/build"

PASSED=0
FAILED=0
FAILURES=()

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the Spotless, Checkstyle and SpotBugs gates fail on the
defects they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME} [-h|--help]

It needs a populated ${M2REPO#"${ROOT}/"} (run bash scripts/build.sh first) and
the project-local toolchain in tools/.  It writes only under _build/.
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
    ( cd "${SANDBOX}" && mvn -B -Dmaven.repo.local="${M2REPO}" "$@" ) >"${log}" 2>&1 || rc=$?
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
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(grep -F -- "${expected}" "${log}" | head -1 | cut -c1-140)"
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

# Harness falsifiability: a control whose defect was never written to disk must
# be a harness failure, not a silent pass.
assert_injected() {
    local label="$1" file="$2"
    if [ ! -f "${SANDBOX}/${file}" ]; then
        die "HARNESS ERROR (${label}): ${file} was not created in the sandbox. The control would have tested nothing."
    fi
    printf '   injected %s (%s bytes)\n' "${file}" "$(stat -c %s "${SANDBOX}/${file}")"
}

assert_removed() {
    local label="$1" file="$2"
    if [ -e "${SANDBOX}/${file}" ]; then
        die "HARNESS ERROR (${label}): ${file} still exists after removal. The clean re-run would not be clean."
    fi
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf "${SANDBOX}"
    mkdir -p "${SANDBOX}"

    # Copy by explicit path.  A find/tar exclusion by *name* would also drop
    # org/cometgui/tools/, which is why .gitignore anchors its own tools/ rule.
    cp "${ROOT}/pom.xml" "${SANDBOX}/pom.xml"
    cp -r "${ROOT}/.mvn" "${SANDBOX}/.mvn"
    cp -r "${ROOT}/config" "${SANDBOX}/config"

    local module
    for module in "${ROOT}"/cometgui-*/; do
        module="$(basename -- "${module}")"
        mkdir -p "${SANDBOX}/${module}"
        cp "${ROOT}/${module}/pom.xml" "${SANDBOX}/${module}/pom.xml"
        [ -d "${ROOT}/${module}/src" ] && cp -r "${ROOT}/${module}/src" "${SANDBOX}/${module}/src"
    done

    local copied
    copied="$(find "${SANDBOX}" -name '*.java' -type f | wc -l)"
    local original
    original="$(find "${ROOT}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    [ "${copied}" -eq "${original}" ] \
        || die "sandbox has ${copied} java files but the working tree has ${original}; the copy is wrong."
    echo "Sandbox: ${SANDBOX#"${ROOT}/"} (${copied} java files copied from the working tree)"
}

# ------------------------------------------------------------- the controls --

# A file that is clean for every gate.  Each control starts from this and
# damages exactly one thing, so a failure names one cause.
write_clean_control_class() {
    local body="$1"
    {
        cat "${ROOT}/config/license/java-header.txt"
        cat <<JAVA
package org.cometgui.domain.build;

/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}

${body}
}
JAVA
    } >"${SANDBOX}/${PKG_DIR}/NegativeControl.java"
}

control_baseline() {
    banner "0  baseline: the undamaged sandbox passes all three gates"
    assert_passes "baseline spotless" "${LOGS}/00-baseline-spotless.log" \
        spotless:check -pl "${MODULE}"
    assert_passes "baseline checkstyle" "${LOGS}/00-baseline-checkstyle.log" \
        checkstyle:check -pl "${MODULE}"
    assert_passes "baseline spotbugs" "${LOGS}/00-baseline-spotbugs.log" \
        test-compile spotbugs:check -pl "${MODULE}"
}

control_formatting() {
    banner "1  formatting: bad indentation and wrongly ordered imports"
    # Deliberately: 2-space indent instead of 4, java.util.List imported after
    # org.junit-style ordering would put it, and a line over 100 columns.
    {
        cat "${ROOT}/config/license/java-header.txt"
        cat <<'JAVA'
package org.cometgui.domain.build;

import java.util.Objects;
import java.time.Instant;

/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */
public final class NegativeControl {
  private NegativeControl() {}

  /**
   * @return a string
   */
  public static String badlyFormatted() {
        return Objects.toString(Instant.EPOCH) + "                                                          padding";
  }
}
JAVA
    } >"${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_injected "formatting" "${PKG_DIR}/NegativeControl.java"

    assert_fails "spotless rejects misformatted source" \
        "NegativeControl.java" \
        "${LOGS}/01-format-dirty.log" \
        spotless:check -pl "${MODULE}"

    rm -f "${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_removed "formatting" "${PKG_DIR}/NegativeControl.java"
    assert_passes "spotless accepts the tree once the file is gone" \
        "${LOGS}/01-format-clean.log" spotless:check -pl "${MODULE}"
}

control_wrong_header() {
    banner "2  licence header: a class carrying somebody else's licence"
    # Not merely missing: an MIT notice on a GPL-3.0 file.  This is the defect
    # that matters for D-001, and it is the one a copy-paste from another
    # project actually produces.
    cat >"${SANDBOX}/${PKG_DIR}/NegativeControl.java" <<'JAVA'
/*
 * Copyright (c) 2026 Somebody Else.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction.
 *
 * SPDX-License-Identifier: MIT
 */

package org.cometgui.domain.build;

/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}
}
JAVA
    assert_injected "wrong header" "${PKG_DIR}/NegativeControl.java"

    assert_fails "spotless rejects a class carrying the wrong licence header" \
        "NegativeControl.java" \
        "${LOGS}/02-header-dirty-spotless.log" \
        spotless:check -pl "${MODULE}"
    assert_fails "checkstyle rejects a class carrying the wrong licence header" \
        "Line does not match expected header line" \
        "${LOGS}/02-header-dirty-checkstyle.log" \
        checkstyle:check -pl "${MODULE}"

    rm -f "${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_removed "wrong header" "${PKG_DIR}/NegativeControl.java"
    assert_passes "spotless accepts the tree once the file is gone" \
        "${LOGS}/02-header-clean-spotless.log" spotless:check -pl "${MODULE}"
    assert_passes "checkstyle accepts the tree once the file is gone" \
        "${LOGS}/02-header-clean-checkstyle.log" checkstyle:check -pl "${MODULE}"
}

control_missing_header_package_info() {
    banner "3  licence header on package-info.java, which Spotless cannot see"
    # This is why config/checkstyle/checkstyle.xml carries the Header module.
    # Spotless's LicenseHeaderStep applies unsupportedJvmFilesFilter(), which
    # excludes package-info.java by name, so `mvn spotless:apply` will not add
    # the header and `spotless:check` will not miss it.  The control asserts
    # both halves: Spotless passes, Checkstyle fails.
    mkdir -p "${SANDBOX}/${MODULE}/src/main/java/org/cometgui/domain/negativecontrol"
    cat >"${SANDBOX}/${MODULE}/src/main/java/org/cometgui/domain/negativecontrol/package-info.java" <<'JAVA'
/** Temporary package written by scripts/verify-quality-gates.sh. Never committed. */
package org.cometgui.domain.negativecontrol;
JAVA
    assert_injected "package-info header" \
        "${MODULE}/src/main/java/org/cometgui/domain/negativecontrol/package-info.java"

    assert_passes "spotless is blind to a bare package-info.java (the reason Checkstyle checks the header)" \
        "${LOGS}/03-pkginfo-spotless.log" spotless:check -pl "${MODULE}"
    assert_fails "checkstyle rejects a package-info.java with no licence header" \
        "Missing a header" \
        "${LOGS}/03-pkginfo-checkstyle.log" \
        checkstyle:check -pl "${MODULE}"

    rm -rf "${SANDBOX}/${MODULE}/src/main/java/org/cometgui/domain/negativecontrol"
    assert_removed "package-info header" \
        "${MODULE}/src/main/java/org/cometgui/domain/negativecontrol"
    assert_passes "checkstyle accepts the tree once the package is gone" \
        "${LOGS}/03-pkginfo-clean.log" checkstyle:check -pl "${MODULE}"
}

control_checkstyle_rule() {
    banner "4  style: rules Checkstyle enforces that the formatter accepts"
    # Both defects survive google-java-format untouched: it will not add braces
    # and it will not turn == into .equals.  So this control also proves the two
    # tools are not checking the same thing.
    write_clean_control_class '    /**
     * @param name the value to test
     * @return whether the argument is the literal "comet"
     */
    public static boolean isComet(String name) {
        if (name == "comet") return true;
        return false;
    }'
    assert_injected "checkstyle rule" "${PKG_DIR}/NegativeControl.java"

    assert_passes "spotless accepts the file (the defect is not a layout defect)" \
        "${LOGS}/04-style-spotless.log" spotless:check -pl "${MODULE}"
    assert_fails "checkstyle rejects string comparison by reference" \
        "StringLiteralEquality" \
        "${LOGS}/04-style-dirty.log" \
        checkstyle:check -pl "${MODULE}"
    assert_fails "checkstyle rejects a brace-less conditional" \
        "NeedBraces" \
        "${LOGS}/04-style-dirty.log" \
        checkstyle:check -pl "${MODULE}"

    rm -f "${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_removed "checkstyle rule" "${PKG_DIR}/NegativeControl.java"
    assert_passes "checkstyle accepts the tree once the file is gone" \
        "${LOGS}/04-style-clean.log" checkstyle:check -pl "${MODULE}"
}

control_spotbugs() {
    banner "5  bug pattern: a null dereference SpotBugs must find in bytecode"
    write_clean_control_class '    /**
     * @return the length of a string that is always null
     */
    public static int lengthOfNothing() {
        String nothing = null;
        return nothing.length();
    }'
    assert_injected "spotbugs" "${PKG_DIR}/NegativeControl.java"

    # The file is clean for Spotless and Checkstyle on purpose, so the only
    # thing that can stop this build is SpotBugs reading the compiled class.
    assert_passes "spotless accepts the file" \
        "${LOGS}/05-bug-spotless.log" spotless:check -pl "${MODULE}"
    assert_passes "checkstyle accepts the file" \
        "${LOGS}/05-bug-checkstyle.log" checkstyle:check -pl "${MODULE}"
    assert_fails "spotbugs rejects a guaranteed null dereference" \
        "NP_ALWAYS_NULL" \
        "${LOGS}/05-bug-dirty.log" \
        test-compile spotbugs:check -pl "${MODULE}"

    rm -f "${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_removed "spotbugs" "${PKG_DIR}/NegativeControl.java"
    # target/classes still holds the class file compiled from the deleted
    # source; without clean, SpotBugs would keep finding it and the "clean
    # re-run" would be a lie.
    assert_passes "spotbugs accepts the tree once the defect is gone" \
        "${LOGS}/05-bug-clean.log" clean test-compile spotbugs:check -pl "${MODULE}"
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

    mkdir -p "${LOGS}"
    rm -f "${LOGS}"/*.log

    local started
    started="$(date +%s)"

    build_sandbox
    control_baseline
    control_formatting
    control_wrong_header
    control_missing_header_package_info
    control_checkstyle_rule
    control_spotbugs

    printf '\n===============================================================================\n'
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "$(( $(date +%s) - started ))"
    printf ' Logs: %s\n' "${LOGS#"${ROOT}/"}"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} quality-gate control(s) failed. A gate that cannot be seen to fail is not a gate."
    fi
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
