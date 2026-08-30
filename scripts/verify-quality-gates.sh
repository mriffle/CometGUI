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
# SCOPE.  Controls 0-5 cover phase 01 unit 2: Spotless (formatting, import
# order, licence header), Checkstyle (the project rule set) and SpotBugs (bug
# patterns).  Controls 6-12 cover phase 02 unit 4: the derived-file attribution
# machinery that discharges D-001 obligation 2 and R-SEC-01 -- two licence
# headers, two Spotless file sets, two Checkstyle executions with two rule sets,
# and the census in scripts/build.sh that proves the two sets are exhaustive and
# disjoint.  The aggregate falsifiability harness for every phase 01 gate is
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

# PHASE 02.  A file is DERIVED if and only if its path contains a `/derived/`
# segment: it is reused from Noble-Lab/CasanovoGUI, it keeps upstream's notices
# (D-001 obligation 2, R-SEC-01) and it therefore carries a different licence
# header and is graded by a different rule set.  Controls 6-12 inject into this
# package, in the same module, for the same reason: it is the cheapest module to
# build.
readonly DERIVED_PKG_DIR="${PKG_DIR}/derived"
readonly UPSTREAM_COMMIT="480b3013e7f8fb51a2b8c58681043821e3e7f865"
readonly UPSTREAM_FILE="src/main/java/org/casanovo/gui/ui/Themes.java"

# The four gates the derived machinery adds, each named by its Maven execution.
# A bare `spotless:check` or `checkstyle:check` runs the plugin-level
# configuration, which is NOT what the build runs: the file-set split lives in
# the executions.  Naming the execution is therefore the narrowest command that
# exercises the real gate, and the only one that can distinguish the two
# regimes.
readonly SPOTLESS_ORDINARY="spotless:check@spotless-check"
readonly SPOTLESS_DERIVED="spotless:check@spotless-check-derived"
readonly CHECKSTYLE_ORDINARY="checkstyle:check@checkstyle-check"
readonly CHECKSTYLE_DERIVED="checkstyle:check@checkstyle-check-derived"

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

# The same two assertions for a control whose gate is a script rather than a
# Maven goal -- the file-set census and the rule-set drift check in
# scripts/build.sh.  Written as separate functions rather than by generalising
# run_mvn, so that the Maven controls keep their one obvious way of running.

# assert_cmd_fails <label> <expected diagnostic> <log> <command...>
assert_cmd_fails() {
    local label="$1" expected="$2" log="$3"
    shift 3
    local rc=0
    "$@" >"${log}" 2>&1 || rc=$?
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

# assert_cmd_passes <label> <log> <command...>
assert_cmd_passes() {
    local label="$1" log="$2"
    shift 2
    local rc=0
    "$@" >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "${label}: the gate still fails after the defect was removed (log: ${log#"${ROOT}/"})"
        printf '         %s\n' "$(grep -m3 -E 'FATAL|DRIFT|UNCHECKED|OVERLAP|MISMATCH' "${log}" | head -3 | cut -c1-140)"
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


# ------------------------------------------- the derived-file controls (02) --
#
# THE OBLIGATION.  D-001 obligation 2 and R-SEC-01: a file reused from
# Noble-Lab/CasanovoGUI keeps upstream's copyright notices and records its
# derivation, and the obligation is enforced by the BUILD rather than by review.
# The machinery is two licence headers, two Spotless file sets and two
# Checkstyle executions, split on one mechanical rule -- a file is derived if
# and only if its path contains a `/derived/` segment.
#
# WHAT MUST BE PROVED, and what each control below proves:
#
#   6   the scaffolding these controls use is itself clean, so that a later
#       failure is the injected defect and not the harness;
#   7   a derived file with no upstream attribution is REJECTED, by Spotless and
#       by Checkstyle -- the obligation itself;
#   8   a derived file whose per-file derivation record is missing is REJECTED
#       by Checkstyle, which is the only tool that can see it;
#   9   a NON-derived file carrying the derived header is REJECTED by the
#       ordinary checks: nobody claims a derivation they did not make;
#  10   google-java-format still applies to the derived file set.  Splitting the
#       file set is only legitimate if the second set keeps every step of the
#       first, and the obvious wrong way to build it (Spotless's generic
#       <formats><format>) cannot run google-java-format at all;
#  11   a derived file that neither file set matches is caught.  This is the
#       failure mode that makes an exclusion dangerous: the build stays GREEN;
#  12   the derived rule set cannot quietly stop being a superset of the
#       ordinary one.

# A file in the derived tree that is clean for every gate: the derived header,
# the derivation record, and google-java-format's own layout.  Each control
# damages exactly one thing about it, so a failure names one cause.
#   $1  the licence header to prepend (default: the derived one)
#   $2  "record" to include the derivation record, anything else to omit it
#
# THE CLASS BODY IS PADDED ON PURPOSE.  Checkstyle's Header module compares the
# first N lines of the header file literally, and reports "Missing a header -
# not enough lines in file" when the file is SHORTER than the header rather than
# naming the line that differs.  The derived header is 31 lines, so a file with
# the 16-line ordinary header must be at least 31 lines long or control 7 would
# be proving the file's length rather than the header's content.
#
# THE JAVADOC SHAPE IS ALSO ON PURPOSE.  google-java-format collapses a
# single-paragraph documentation comment onto one line and keeps a multi-
# paragraph one expanded, so the two variants below are written the way the
# formatter would write them.  A control file that Spotless legitimately
# rejects would make every other assertion about it meaningless.
write_derived_control_class() {
    local header="${1:-${ROOT}/config/license/java-header-derived.txt}"
    local record="${2:-record}"
    mkdir -p "${SANDBOX}/${DERIVED_PKG_DIR}"
    {
        cat "${header}"
        printf 'package org.cometgui.domain.build.derived;\n\n'
        if [ "${record}" = "record" ]; then
            printf '/**\n'
            printf ' * Temporary class written by scripts/verify-quality-gates.sh. Never committed.\n'
            printf ' *\n'
            printf ' * <p>Derived from Noble-Lab/CasanovoGUI %s at commit\n' "${UPSTREAM_FILE}"
            printf ' * %s, GPL-3.0, modified.\n' "${UPSTREAM_COMMIT}"
            printf ' */\n'
        else
            printf '/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */\n'
        fi
        cat <<'JAVA'
public final class DerivedControl {

    private DerivedControl() {}

    /**
     * @return a fixed string
     */
    public static String text() {
        return "negative control";
    }
}
JAVA
    } >"${SANDBOX}/${DERIVED_PKG_DIR}/DerivedControl.java"
}

control_derived_baseline() {
    banner "6  the derived scaffolding itself: a properly attributed derived file passes"
    # Without this, controls 7-10 could all be passing for the wrong reason --
    # a file that no gate accepts proves nothing when one of them rejects it.
    write_derived_control_class
    assert_injected "derived baseline" "${DERIVED_PKG_DIR}/DerivedControl.java"

    assert_passes "spotless accepts a derived file with the derived header" \
        "${LOGS}/06-derived-baseline-spotless.log" "${SPOTLESS_DERIVED}" -pl "${MODULE}"
    assert_passes "checkstyle accepts a derived file with header and derivation record" \
        "${LOGS}/06-derived-baseline-checkstyle.log" "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"

    rm -rf "${SANDBOX}/${DERIVED_PKG_DIR}"
    assert_removed "derived baseline" "${DERIVED_PKG_DIR}"
}

control_derived_ordinary_header() {
    banner "7  a derived file carrying the ORDINARY CometGUI header, with no attribution"
    # The defect a copy-paste actually produces: upstream's code, presented as
    # this project's own work.  This is the D-001 obligation the whole machinery
    # exists to enforce, and both tools must refuse it.
    write_derived_control_class "${ROOT}/config/license/java-header.txt"
    assert_injected "derived file, ordinary header" "${DERIVED_PKG_DIR}/DerivedControl.java"

    assert_fails "spotless rejects a derived file with no upstream attribution" \
        "DerivedControl.java" \
        "${LOGS}/07-derived-ordinary-header-spotless.log" \
        "${SPOTLESS_DERIVED}" -pl "${MODULE}"
    assert_fails "checkstyle rejects a derived file with no upstream attribution" \
        "Line does not match expected header line" \
        "${LOGS}/07-derived-ordinary-header-checkstyle.log" \
        "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"

    rm -rf "${SANDBOX}/${DERIVED_PKG_DIR}"
    assert_removed "derived file, ordinary header" "${DERIVED_PKG_DIR}"
    assert_passes "spotless accepts the tree once the file is gone" \
        "${LOGS}/07-derived-ordinary-header-clean-spotless.log" \
        "${SPOTLESS_DERIVED}" -pl "${MODULE}"
    assert_passes "checkstyle accepts the tree once the file is gone" \
        "${LOGS}/07-derived-ordinary-header-clean-checkstyle.log" \
        "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"
}

control_derived_missing_record() {
    banner "8  a derived file with the right header but NO per-file derivation record"
    # The header is fixed and identical in every derived file, so it cannot say
    # WHICH upstream file this is or at which commit.  That is what the record
    # in the documentation comment is for, and Checkstyle is the only tool that
    # can require it.  Spotless must pass here: if it failed, this control would
    # not be testing the rule it claims to test.
    write_derived_control_class "" "no-record"
    assert_injected "derived file, no record" "${DERIVED_PKG_DIR}/DerivedControl.java"

    assert_passes "spotless is blind to a missing derivation record (the reason Checkstyle requires it)" \
        "${LOGS}/08-derived-no-record-spotless.log" "${SPOTLESS_DERIVED}" -pl "${MODULE}"
    assert_fails "checkstyle rejects a derived file with no derivation record" \
        "Required pattern 'the derivation record." \
        "${LOGS}/08-derived-no-record-checkstyle.log" \
        "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"

    rm -rf "${SANDBOX}/${DERIVED_PKG_DIR}"
    assert_removed "derived file, no record" "${DERIVED_PKG_DIR}"
    assert_passes "checkstyle accepts the tree once the file is gone" \
        "${LOGS}/08-derived-no-record-clean.log" "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"
}

control_derived_header_on_ordinary_file() {
    banner "9  a NON-derived file carrying the DERIVED header"
    # The converse obligation, and the one that keeps the derived header from
    # becoming a way out of anything: nobody claims a derivation they did not
    # make.  The file below is CometGUI's own work in an ordinary package, and
    # the ordinary checks must refuse the upstream attribution it asserts.
    {
        cat "${ROOT}/config/license/java-header-derived.txt"
        cat <<'JAVA'
package org.cometgui.domain.build;

/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */
public final class NegativeControl {

    private NegativeControl() {}
}
JAVA
    } >"${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_injected "derived header, ordinary file" "${PKG_DIR}/NegativeControl.java"

    assert_fails "spotless rejects an unearned upstream attribution" \
        "NegativeControl.java" \
        "${LOGS}/09-derived-header-elsewhere-spotless.log" \
        "${SPOTLESS_ORDINARY}" -pl "${MODULE}"
    assert_fails "checkstyle rejects an unearned upstream attribution" \
        "Line does not match expected header line" \
        "${LOGS}/09-derived-header-elsewhere-checkstyle.log" \
        "${CHECKSTYLE_ORDINARY}" -pl "${MODULE}"

    rm -f "${SANDBOX}/${PKG_DIR}/NegativeControl.java"
    assert_removed "derived header, ordinary file" "${PKG_DIR}/NegativeControl.java"
    assert_passes "spotless accepts the tree once the file is gone" \
        "${LOGS}/09-derived-header-elsewhere-clean-spotless.log" \
        "${SPOTLESS_ORDINARY}" -pl "${MODULE}"
    assert_passes "checkstyle accepts the tree once the file is gone" \
        "${LOGS}/09-derived-header-elsewhere-clean-checkstyle.log" \
        "${CHECKSTYLE_ORDINARY}" -pl "${MODULE}"
}

control_derived_formatting() {
    banner "10  a badly formatted derived file: google-java-format must still apply"
    # THE WEAKENING THIS CONTROL EXISTS TO CATCH.  The obvious way to give
    # derived files their own header is Spotless's generic <formats><format>
    # block -- and a <format> block cannot run google-java-format, so the
    # derived files would keep a header check and silently lose the formatter.
    # The defects below (2-space indent, imports in the wrong order) are
    # invisible to Checkstyle by design, so only the formatter can reject them.
    mkdir -p "${SANDBOX}/${DERIVED_PKG_DIR}"
    {
        cat "${ROOT}/config/license/java-header-derived.txt"
        cat <<'JAVA'
package org.cometgui.domain.build.derived;

import java.util.Objects;
import java.time.Instant;

/**
 * Temporary class written by scripts/verify-quality-gates.sh. Never committed.
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/ui/Themes.java at commit
 * 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 */
public final class DerivedControl {
  private DerivedControl() {}

  /**
   * @return a string
   */
  public static String badlyFormatted() {
    return Objects.toString(Instant.EPOCH);
  }
}
JAVA
    } >"${SANDBOX}/${DERIVED_PKG_DIR}/DerivedControl.java"
    assert_injected "derived formatting" "${DERIVED_PKG_DIR}/DerivedControl.java"

    assert_fails "spotless still formats the derived file set" \
        "DerivedControl.java" \
        "${LOGS}/10-derived-format-spotless.log" \
        "${SPOTLESS_DERIVED}" -pl "${MODULE}"
    assert_passes "checkstyle accepts it (the defect is a layout defect, and only the formatter sees it)" \
        "${LOGS}/10-derived-format-checkstyle.log" "${CHECKSTYLE_DERIVED}" -pl "${MODULE}"

    rm -rf "${SANDBOX}/${DERIVED_PKG_DIR}"
    assert_removed "derived formatting" "${DERIVED_PKG_DIR}"
    assert_passes "spotless accepts the tree once the file is gone" \
        "${LOGS}/10-derived-format-clean.log" "${SPOTLESS_DERIVED}" -pl "${MODULE}"
}

control_census_hole() {
    banner "11  a derived file that NEITHER file set matches -- and the build stays green"
    # THE HOLE THAT MAKES AN EXCLUSION DANGEROUS.  The ordinary executions
    # exclude `**/derived/**`.  If the derived executions' include pattern ever
    # fails to match something that exclusion drops, the file is checked by
    # nothing at all -- no header check, no formatter, no rule set -- and `mvn
    # verify` is GREEN and says so.  No Maven command can catch that; only a
    # census of what the tools actually reported against what is on disk.
    #
    # The injection is a plausible typo: `**/derived/*.java` instead of
    # `**/derived/**/*.java` in the two derived includes, which still matches a
    # file directly under derived/ but misses one in a nested package.
    sed -i \
        -e 's|<include>src/main/java/\*\*/derived/\*\*/\*\.java</include>|<include>src/main/java/**/derived/*.java</include>|' \
        -e 's|<includes>\*\*/derived/\*\*/\*\.java</includes>|<includes>**/derived/*.java</includes>|' \
        "${SANDBOX}/pom.xml"
    grep -qF '<include>src/main/java/**/derived/*.java</include>' "${SANDBOX}/pom.xml" \
        || die "HARNESS ERROR (census hole): the sandbox POM's spotless include was not narrowed."
    grep -qF '<includes>**/derived/*.java</includes>' "${SANDBOX}/pom.xml" \
        || die "HARNESS ERROR (census hole): the sandbox POM's checkstyle include was not narrowed."

    mkdir -p "${SANDBOX}/${DERIVED_PKG_DIR}/nested"
    {
        cat "${ROOT}/config/license/java-header-derived.txt"
        cat <<'JAVA'
package org.cometgui.domain.build.derived.nested;

/** Temporary class written by scripts/verify-quality-gates.sh. Never committed. */
public final class NestedControl {

    private NestedControl() {}
}
JAVA
    } >"${SANDBOX}/${DERIVED_PKG_DIR}/nested/NestedControl.java"
    assert_injected "census hole" "${DERIVED_PKG_DIR}/nested/NestedControl.java"
    # Deliberately WITHOUT a derivation record: nothing is going to check it.

    assert_passes "the Maven build is GREEN with a file no gate inspects -- which is the point" \
        "${LOGS}/11-census-hole-maven.log" clean validate -pl "${MODULE}"
    assert_cmd_fails "the census catches the file no file set covers" \
        "UNCHECKED" \
        "${LOGS}/11-census-hole-census.log" \
        bash "${ROOT}/scripts/build.sh" --census-root "${SANDBOX}"

    rm -rf "${SANDBOX}/${DERIVED_PKG_DIR}"
    cp "${ROOT}/pom.xml" "${SANDBOX}/pom.xml"
    assert_removed "census hole" "${DERIVED_PKG_DIR}"
    assert_passes "the sandbox rebuilds once the pattern and the file are restored" \
        "${LOGS}/11-census-hole-clean-maven.log" clean validate -pl "${MODULE}"
    assert_cmd_passes "the census accepts the restored tree" \
        "${LOGS}/11-census-hole-clean-census.log" \
        bash "${ROOT}/scripts/build.sh" --census-root "${SANDBOX}"
}

control_ruleset_drift() {
    banner "12  a module removed from checkstyle-derived.xml: the two rule sets drifting apart"
    # checkstyle-derived.xml must stay a SUPERSET of checkstyle.xml, or derived
    # files end up held to a shorter list of rules than every other file while
    # the build stays green.  Two rule sets that must stay in step drift; the
    # census checks the superset property on every build, and this proves it
    # bites.
    sed -i '/<module name="StringLiteralEquality"\/>/d' \
        "${SANDBOX}/config/checkstyle/checkstyle-derived.xml"
    if grep -qF '<module name="StringLiteralEquality"/>' \
            "${SANDBOX}/config/checkstyle/checkstyle-derived.xml"; then
        die "HARNESS ERROR (rule-set drift): the module was not removed from the sandbox rule set."
    fi
    grep -qF '<module name="StringLiteralEquality"/>' \
        "${SANDBOX}/config/checkstyle/checkstyle.xml" \
        || die "HARNESS ERROR (rule-set drift): the ordinary rule set no longer has the module either."

    assert_cmd_fails "the drift check catches a rule dropped from the derived rule set" \
        "DRIFT" \
        "${LOGS}/12-ruleset-drift.log" \
        bash "${ROOT}/scripts/build.sh" --census-root "${SANDBOX}"

    cp "${ROOT}/config/checkstyle/checkstyle-derived.xml" \
        "${SANDBOX}/config/checkstyle/checkstyle-derived.xml"
    assert_cmd_passes "the drift check accepts the restored rule set" \
        "${LOGS}/12-ruleset-drift-clean.log" \
        bash "${ROOT}/scripts/build.sh" --census-root "${SANDBOX}"
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
    control_derived_baseline
    control_derived_ordinary_header
    control_derived_missing_record
    control_derived_header_on_ordinary_file
    control_derived_formatting
    control_census_hole
    control_ruleset_drift

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
