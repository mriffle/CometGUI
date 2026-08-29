#!/usr/bin/env bash
#
# CometGUI -- the one documented build command.
#
#   bash scripts/build.sh              build and test everything
#   bash scripts/build.sh --help       usage
#
# A clean checkout of this repository plus this script must produce a built,
# tested project using only project-local tools.  Nothing is installed on the
# host: no apt, no sudo, no host-level pip.  The JDK and Maven live under
# tools/, Python tooling lives in .venv/, and the Maven local repository is
# forced to _build/m2repo so that ~/.m2 is never written to.
#
# ADDING A STAGE:
#   1. write a `stage_<id>` function;
#   2. add one "<id>:<one-line description>" entry to STAGES, in order.
# That is the whole contract.  Stages run in order, each is banner-delimited,
# and the summary at the end reports every one of them.  Do not bolt extra work
# onto an existing stage: a stage that fails must say which check failed.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT

# Pinned by scripts/feasibility/install-toolchain.sh and by phase 00's
# toolchain manifest.  The build asserts these exact versions rather than
# trusting whatever happens to be on PATH.
readonly EXPECTED_MAVEN_VERSION="3.9.16"
readonly EXPECTED_JAVA_VERSION="25.0.4.1"

readonly M2REPO="${ROOT}/_build/m2repo"
readonly VENV="${ROOT}/.venv"

# Modules that must produce a jar.  cometgui-archtests is deliberately absent:
# it has test sources only and its jar is skipped (see its POM).
readonly PRODUCT_MODULES=(
    cometgui-domain
    cometgui-provenance
    cometgui-process
    cometgui-tools
    cometgui-install
    cometgui-params-comet
    cometgui-params-percolator
    cometgui-results
    cometgui-workflow
    cometgui-ui
    cometgui-app
)

# The ordered stage list.  "<id>:<description>".
readonly STAGES=(
    "toolchain:Project-local JDK and Maven"
    "fontstack:Project-local font stack for the headless JavaFX tests"
    "python:Project virtualenv for the documentation toolchain"
    "build:Maven clean verify -- compile, unit tests, package"
    "artefacts:Verify the build produced what it claims to have produced"
    "format:Evidence that Spotless, Checkstyle and SpotBugs inspected the code"
    "gates:JaCoCo coverage, ArchUnit and PIT mutation gates"
    "integration:Real-tool integration tests on Linux"
    "docs:Strict Sphinx build and the traceability report"
    "supplychain:SBOM generation and dependency vulnerability scan"
    "workflows:CI workflow definitions match the scripts they invoke"
)

# ----------------------------------------------------------------- plumbing --
MVN_OFFLINE=""
ONLY=""
STAGE_RESULTS=()

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- build and test CometGUI with project-local tools only.

Usage:
  bash scripts/build.sh [options]

Options:
  --offline        Pass -o to Maven and require .venv to exist already.  Only
                   works once a previous online run has populated
                   _build/m2repo; it resolves nothing from the network.
  --only ID[,ID]   Run only the named stages, in the order listed below.  This
                   exists so that the CI step scripts in scripts/ci/ can invoke
                   THIS script rather than reimplementing a stage: the local
                   gate and the pull-request pipeline then cannot drift into
                   running different commands.  An unknown ID is a fatal error.
                   Stages are not independent -- `gates` reads what `build`
                   wrote -- so a selection that skips a producer only makes
                   sense inside one CI job on one checkout, which is how the
                   workflows use it.
  -h, --help       Show this message and exit.

Stages (in order):
$(for s in "${STAGES[@]}"; do printf '  %-12s %s\n' "${s%%:*}" "${s#*:}"; done)

What it guarantees:
  * Nothing is installed on the host.  tools/ holds the pinned JDK
    (Liberica ${EXPECTED_JAVA_VERSION}, which bundles JavaFX) and Maven
    ${EXPECTED_MAVEN_VERSION}; .venv/ holds the pinned Python tooling from
    requirements-dev.txt.  Both are bootstrapped if missing.
  * The Maven local repository is ${M2REPO#"${ROOT}/"}, never ~/.m2.
  * The final stage checks that the expected jars and test reports exist and
    are correct.  Exit code 0 is not evidence on its own.
USAGE
}

# Fingerprint of ~/.m2 so the last stage can prove this run did not write to it.
m2_state() {
    if [ -e "${HOME}/.m2" ]; then
        printf 'exists, mtime %s' "$(stat -c %Y "${HOME}/.m2")"
    else
        printf 'absent'
    fi
}

die() {
    printf '\nFATAL: %s\n' "$*" >&2
    exit 1
}

banner() {
    printf '\n===============================================================================\n'
    printf ' STAGE %d/%d  %-12s %s\n' "$1" "$2" "$3" "$4"
    printf '===============================================================================\n'
}

# ------------------------------------------------------------------ stages --

stage_toolchain() {
    if [ ! -f "${ROOT}/tools/env.sh" ]; then
        echo "tools/env.sh is missing; bootstrapping the toolchain into tools/ ..."
        bash "${ROOT}/scripts/feasibility/install-toolchain.sh"
    else
        echo "tools/env.sh present; not re-installing."
    fi
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh still missing after bootstrap."

    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"

    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh."
    command -v java >/dev/null || die "java is not on PATH after sourcing tools/env.sh."

    echo
    mvn -v
    echo

    local mvn_version java_version
    mvn_version="$(mvn -v | awk '/^Apache Maven /{print $3}')"
    java_version="$(mvn -v | sed -n 's/^Java version: \([^,]*\),.*/\1/p')"

    [ "${mvn_version}" = "${EXPECTED_MAVEN_VERSION}" ] \
        || die "expected Apache Maven ${EXPECTED_MAVEN_VERSION}, got '${mvn_version}'."
    [ "${java_version}" = "${EXPECTED_JAVA_VERSION}" ] \
        || die "expected Java ${EXPECTED_JAVA_VERSION}, got '${java_version}'."

    # JavaFX must come from the JDK image, not from a Maven artefact.
    "${JAVA_HOME}/bin/java" --list-modules | grep -q '^javafx.controls@' \
        || die "the JDK at ${JAVA_HOME} does not bundle javafx.controls; the Full JDK is required."

    echo "OK: Maven ${mvn_version}, Java ${java_version}, JavaFX bundled in the JDK image."
}

stage_fontstack() {
    # The headless JavaFX test in cometgui-ui builds a real Scene, and the first
    # Node in a Scene initialises CSS, which calls Font.getDefault().  With no
    # freetype, no fontconfig and no font files that call fails with
    # "fontFactory is null" and the test dies before its first assertion.  This
    # host has none of them and nothing may be installed on it, so the stack is
    # fetched from the Debian archive by pinned SHA-256 and extracted into
    # tools/ (gitignored) -- see scripts/fetch-fontstack.sh, which is idempotent
    # and verifies rather than refetches when the files are already there.
    if [ -n "${MVN_OFFLINE}" ]; then
        echo "--offline: verifying the existing font stack rather than fetching."
        bash "${ROOT}/scripts/fetch-fontstack.sh" --verify
    else
        bash "${ROOT}/scripts/fetch-fontstack.sh"
    fi
}

stage_python() {
    if [ ! -d "${VENV}" ]; then
        [ -z "${MVN_OFFLINE}" ] || die ".venv is missing and --offline forbids creating it."
        echo ".venv is missing; creating it from requirements-dev.txt ..."
        command -v python3 >/dev/null || die "python3 is not available."
        python3 -m venv "${VENV}"
        "${VENV}/bin/pip" install --disable-pip-version-check --quiet \
            -r "${ROOT}/requirements-dev.txt"
    else
        echo ".venv present; not reinstalling (delete it to rebuild from requirements-dev.txt)."
    fi

    [ -x "${VENV}/bin/sphinx-build" ] \
        || die "${VENV}/bin/sphinx-build is missing; delete .venv and re-run to rebuild it."
    echo
    "${VENV}/bin/sphinx-build" --version
    echo "OK: documentation toolchain available in .venv (nothing installed on the host)."
}

stage_build() {
    # -B batch mode: no ANSI progress spam in logs.  The local repository is
    # forced here as well as in .mvn/maven.config, because this script must be
    # correct even if that file is ever lost.
    echo "+ mvn -B ${MVN_OFFLINE} -Dmaven.repo.local=${M2REPO} clean verify"
    echo
    mvn -B ${MVN_OFFLINE} -Dmaven.repo.local="${M2REPO}" clean verify
}

stage_artefacts() {
    local failures=0 module jar count

    echo "-- jars"
    for module in "${PRODUCT_MODULES[@]}"; do
        jar="$(find "${ROOT}/${module}/target" -maxdepth 1 -name '*.jar' -type f 2>/dev/null | head -1 || true)"
        if [ -z "${jar}" ]; then
            echo "   MISSING  ${module}/target/*.jar"
            failures=$((failures + 1))
            continue
        fi
        count="$("${JAVA_HOME}/bin/jar" --list --file "${jar}" | grep -c '\.class$' || true)"
        if [ "${count}" -eq 0 ]; then
            echo "   EMPTY    ${jar#"${ROOT}/"} (no class files)"
            failures=$((failures + 1))
        else
            printf '   ok       %-56s %s classes\n' "${jar#"${ROOT}/"}" "${count}"
        fi
    done

    echo "-- the one class with behaviour must really be in the domain jar"
    jar="$(find "${ROOT}/cometgui-domain/target" -maxdepth 1 -name '*.jar' -type f 2>/dev/null | head -1 || true)"
    if [ -n "${jar}" ] && "${JAVA_HOME}/bin/jar" --list --file "${jar}" \
            | grep -qx 'org/cometgui/domain/build/BuildIdentity.class'; then
        echo "   ok       org/cometgui/domain/build/BuildIdentity.class"
    else
        echo "   MISSING  org/cometgui/domain/build/BuildIdentity.class"
        failures=$((failures + 1))
    fi

    echo "-- surefire reports"
    local reports total=0 fails=0 errors=0 skipped=0 file tests
    # Only this reactor's modules.  A whole-tree find also picks up the
    # Phase 00 feasibility spikes under _build/, one of which is a deliberate
    # failing negative test, and would report it as a product test failure.
    local -a report_dirs=()
    local dir
    for module in "${PRODUCT_MODULES[@]}" cometgui-archtests; do
        dir="${ROOT}/${module}/target/surefire-reports"
        if [ -d "${dir}" ]; then
            report_dirs+=("${dir}")
        fi
    done
    reports=()
    if [ "${#report_dirs[@]}" -gt 0 ]; then
        mapfile -t reports < <(find "${report_dirs[@]}" -name 'TEST-*.xml' -type f | sort)
    fi
    [ "${#reports[@]}" -gt 0 ] || { echo "   NO TEST REPORTS AT ALL"; failures=$((failures + 1)); }
    for file in "${reports[@]:-}"; do
        [ -n "${file}" ] || continue
        tests=$(sed -n 's/.*<testsuite [^>]*tests="\([0-9]*\)".*/\1/p' "${file}" | head -1)
        total=$((total + ${tests:-0}))
        fails=$((fails + $(sed -n 's/.*<testsuite [^>]*failures="\([0-9]*\)".*/\1/p' "${file}" | head -1)))
        errors=$((errors + $(sed -n 's/.*<testsuite [^>]*errors="\([0-9]*\)".*/\1/p' "${file}" | head -1)))
        skipped=$((skipped + $(sed -n 's/.*<testsuite [^>]*skipped="\([0-9]*\)".*/\1/p' "${file}" | head -1)))
    done
    printf '   %d report file(s): tests=%d failures=%d errors=%d skipped=%d\n' \
        "${#reports[@]}" "${total}" "${fails}" "${errors}" "${skipped}"
    [ "${total}" -gt 0 ] || { echo "   ZERO TESTS RAN"; failures=$((failures + 1)); }
    [ "${fails}" -eq 0 ] || { echo "   TEST FAILURES REPORTED"; failures=$((failures + 1)); }
    [ "${errors}" -eq 0 ] || { echo "   TEST ERRORS REPORTED"; failures=$((failures + 1)); }

    echo "-- this build must not have written to ~/.m2"
    local m2_after
    m2_after="$(m2_state)"
    if [ "${m2_after}" != "${M2_STATE_BEFORE}" ]; then
        echo "   CHANGED  ${HOME}/.m2 went from '${M2_STATE_BEFORE}' to '${m2_after}'."
        echo "            Something in this build resolved outside _build/m2repo."
        failures=$((failures + 1))
    else
        echo "   ok       ${HOME}/.m2: ${m2_after} (unchanged since this run started)"
    fi

    [ "${failures}" -eq 0 ] || die "${failures} artefact check(s) failed. Exit code 0 would have lied."
    echo "OK: every expected artefact exists and every test report is clean."
}


# Every .java file under a module's main and test source roots.  This is the
# denominator for "did the analyser really look at everything?".
count_java_sources() {
    local module="$1" total=0 root
    for root in "${ROOT}/${module}/src/main/java" "${ROOT}/${module}/src/test/java"; do
        [ -d "${root}" ] || continue
        total=$(( total + $(find "${root}" -name '*.java' -type f | wc -l) ))
    done
    printf '%s' "${total}"
}

# Every compiled class under a module's main and test output directories.
# Written as a loop rather than `find dirA dirB | grep -q .`: with pipefail, a
# find over a directory that does not exist returns non-zero even when the other
# directory matched, which silently reported eight modules as having no classes
# and skipped their SpotBugs evidence check entirely.
count_class_files() {
    local module="$1" total=0 dir
    for dir in "${ROOT}/${module}/target/classes" "${ROOT}/${module}/target/test-classes"; do
        [ -d "${dir}" ] || continue
        total=$(( total + $(find "${dir}" -name '*.class' -type f | wc -l) ))
    done
    printf '%s' "${total}"
}

# Count occurrences (not lines) of a fixed string in a file.  Zero matches is a
# legitimate answer here (no violations), so the non-zero grep exit that
# pipefail would otherwise turn into a script abort is absorbed.
count_occurrences() {
    local count
    count="$(grep -o -- "$2" "$1" 2>/dev/null | wc -l)" || count=0
    printf '%s' "${count}"
}

stage_format() {
    # Spotless, Checkstyle and SpotBugs all run inside `mvn verify`, which the
    # build stage above already ran: Spotless and Checkstyle bound to validate,
    # SpotBugs to verify (see the parent POM).  This stage deliberately does NOT
    # re-run them.  It reads what they left behind in each module's target/ and
    # checks they really inspected this tree, because a static-analysis plugin
    # that is silently misconfigured exits 0 having analysed nothing, and the
    # build would be just as green.
    local failures=0 module expected
    local index result xml compiled
    local listed found errors classes missing bugs
    local total_spotless=0 total_checkstyle=0 total_classes=0

    # google-java-format reads jdk.compiler internals.  Without these exports
    # Spotless forces the packages open itself through sun.misc.Unsafe, which is
    # terminally deprecated and will be removed; the build would still pass
    # today and break on a later JDK for a reason nobody would connect to the
    # formatter.  .mvn/jvm.config holds nothing else and cannot hold a comment:
    # Maven 3.9 splits it on whitespace and hands every token to the JVM, so a
    # "#" line becomes an unloadable main class.
    echo "-- .mvn/jvm.config exports the jdk.compiler packages google-java-format needs"
    local pkg missing_exports=0
    for pkg in api file parser tree util; do
        if ! grep -q -- "--add-exports jdk.compiler/com.sun.tools.javac.${pkg}=ALL-UNNAMED" \
                "${ROOT}/.mvn/jvm.config" 2>/dev/null; then
            echo "   MISSING  add-exports for com.sun.tools.javac.${pkg}"
            missing_exports=$((missing_exports + 1))
        fi
    done
    if [ "${missing_exports}" -eq 0 ]; then
        echo "   ok       all five exports present"
    else
        failures=$((failures + missing_exports))
    fi

    echo "-- Spotless: target/spotless-index names every file it certified clean"
    for module in "${PRODUCT_MODULES[@]}" cometgui-archtests; do
        expected="$(count_java_sources "${module}")"
        index="${ROOT}/${module}/target/spotless-index"
        if [ ! -f "${index}" ]; then
            echo "   MISSING  ${module}/target/spotless-index (did spotless:check run?)"
            failures=$((failures + 1))
            continue
        fi
        # First line is the formatter's state hash; one line per file after it.
        listed=$(( $(wc -l < "${index}") - 1 ))
        if [ "${listed}" -ne "${expected}" ]; then
            echo "   MISMATCH ${module}: ${expected} .java source(s) on disk, ${listed} in spotless-index"
            failures=$((failures + 1))
        else
            printf '   ok       %-28s %2d file(s) formatted and header-checked\n' "${module}" "${listed}"
            total_spotless=$((total_spotless + listed))
        fi
    done

    echo "-- Checkstyle: target/checkstyle-result.xml names every file it parsed"
    for module in "${PRODUCT_MODULES[@]}" cometgui-archtests; do
        expected="$(count_java_sources "${module}")"
        result="${ROOT}/${module}/target/checkstyle-result.xml"
        if [ ! -f "${result}" ]; then
            echo "   MISSING  ${module}/target/checkstyle-result.xml (did checkstyle:check run?)"
            failures=$((failures + 1))
            continue
        fi
        found="$(count_occurrences "${result}" '<file name=')"
        errors="$(count_occurrences "${result}" '<error ')"
        if [ "${found}" -ne "${expected}" ]; then
            echo "   MISMATCH ${module}: ${expected} .java source(s) on disk, ${found} in checkstyle-result.xml"
            failures=$((failures + 1))
        elif [ "${errors}" -ne 0 ]; then
            echo "   VIOLATIONS ${module}: ${errors} Checkstyle error(s) in a build that passed"
            failures=$((failures + 1))
        else
            printf '   ok       %-28s %2d file(s) checked, 0 violations\n' "${module}" "${found}"
            total_checkstyle=$((total_checkstyle + found))
        fi
    done

    echo "-- SpotBugs: target/spotbugsXml.xml reports how many classes it read"
    for module in "${PRODUCT_MODULES[@]}" cometgui-archtests; do
        # A module that compiles to no class file at all gives SpotBugs nothing
        # to do; that is reported, not silently counted as a pass.
        compiled="$(count_class_files "${module}")"
        if [ "${compiled}" -eq 0 ]; then
            printf '   skip     %-28s no compiled classes to analyse\n' "${module}"
            continue
        fi
        xml="${ROOT}/${module}/target/spotbugsXml.xml"
        if [ ! -f "${xml}" ]; then
            echo "   MISSING  ${module}/target/spotbugsXml.xml, but the module has classes"
            failures=$((failures + 1))
            continue
        fi
        classes="$(sed -n "s/.*total_classes='\([0-9]*\)'.*/\1/p" "${xml}" | head -1)"
        missing="$(sed -n "s/.*missingClasses='\([0-9]*\)'.*/\1/p" "${xml}" | head -1)"
        errors="$(sed -n "s/.*<Errors[^>]*errors='\([0-9]*\)'.*/\1/p" "${xml}" | head -1)"
        bugs="$(count_occurrences "${xml}" '<BugInstance ')"
        if [ -z "${classes}" ] || [ "${classes}" -eq 0 ]; then
            echo "   VACUOUS  ${module}: SpotBugs analysed 0 classes. Java ${EXPECTED_JAVA_VERSION}"
            echo "            emits class file major version 69; an analyser that cannot read it"
            echo "            reports nothing and exits 0.  Do NOT lower maven.compiler.release."
            failures=$((failures + 1))
        elif [ "${missing:-0}" -ne 0 ] || [ "${errors:-0}" -ne 0 ]; then
            echo "   ERRORS   ${module}: missingClasses=${missing} errors=${errors} in the SpotBugs run"
            failures=$((failures + 1))
        elif [ "${bugs}" -ne 0 ]; then
            echo "   BUGS     ${module}: ${bugs} SpotBugs finding(s) in a build that passed"
            failures=$((failures + 1))
        else
            printf '   ok       %-28s %2d class(es) analysed, 0 findings\n' "${module}" "${classes}"
            total_classes=$((total_classes + classes))
        fi
    done

    [ "${total_spotless}" -gt 0 ] || { echo "   SPOTLESS FORMATTED NOTHING"; failures=$((failures + 1)); }
    [ "${total_checkstyle}" -gt 0 ] || { echo "   CHECKSTYLE CHECKED NOTHING"; failures=$((failures + 1)); }
    [ "${total_classes}" -gt 0 ] || { echo "   SPOTBUGS ANALYSED NOTHING"; failures=$((failures + 1)); }

    [ "${failures}" -eq 0 ] || die "${failures} quality-gate evidence check(s) failed."
    printf 'OK: Spotless %d file(s), Checkstyle %d file(s), SpotBugs %d class(es).\n' \
        "${total_spotless}" "${total_checkstyle}" "${total_classes}"
    echo "    The gates themselves are proved to fail on the defects they catch by"
    echo "    bash scripts/verify-quality-gates.sh (run separately: it rebuilds a"
    echo "    deliberately damaged copy of the tree under _build/)."
}

# The report-level counters of a JaCoCo XML report are the last <counter/>
# element of each type in the document, after the last </package>.  Taking the
# last match is exact rather than a heuristic, and it avoids parsing XML in
# bash.  Prints "<covered> <missed>", or nothing if the counter is absent --
# which is the normal case for a module whose only classes are package-info,
# since those carry no lines and no branches at all.
jacoco_counter() {
    local file="$1" type="$2" match
    match="$(grep -o "<counter type=\"${type}\" missed=\"[0-9]*\" covered=\"[0-9]*\"/>" \
        "${file}" 2>/dev/null | tail -1)" || true
    [ -n "${match}" ] || return 0
    printf '%s %s\n' \
        "$(printf '%s' "${match}" | sed 's/.*covered="\([0-9]*\)".*/\1/')" \
        "$(printf '%s' "${match}" | sed 's/.*missed="\([0-9]*\)".*/\1/')"
}

# Percentage to one decimal place, without bc (which this host does not have).
percent_of() {
    local covered="$1" total="$2"
    [ "${total}" -gt 0 ] || { printf 'n/a'; return 0; }
    printf '%d.%d%%' $(( covered * 100 / total )) $(( covered * 1000 / total % 10 ))
}

# Class files a module actually compiled, excluding package-info: a package-info
# class has no methods, no lines and no branches, so a module that has only
# those has nothing for a coverage or mutation gate to measure.  Prints paths
# relative to target/classes.
real_classes_of() {
    local module="$1" dir="${ROOT}/$1/target/classes"
    [ -d "${dir}" ] || return 0
    ( cd "${dir}" && find . -name '*.class' -type f ! -name 'package-info.class' \
        | sed 's|^\./||' | sort )
}

# True when a module's POM opts into one of the gate switches.
module_opts_in() {
    local module="$1" property="$2"
    grep -qF "<${property}>false</${property}>" "${ROOT}/${module}/pom.xml" 2>/dev/null
}

# The package prefixes the coverage gate covers, from the specification's
# sentence "core domain, parameter and provenance logic".  They are named here
# and not derived from the POMs on purpose: this check exists to catch a POM
# whose switch was turned off, so it must not read its answer from the POMs.
readonly COVERAGE_GATED_PREFIXES=(
    org/cometgui/domain
    org/cometgui/params
    org/cometgui/provenance
)

stage_gates() {
    # Everything the main build already ran is READ here, not re-run: JaCoCo's
    # agent, report and two check executions are bound into `mvn verify`, and
    # the ArchUnit rules are ordinary tests in cometgui-archtests.  What this
    # stage adds is the evidence that they were not vacuous -- JaCoCo passes a
    # module with no execution data, ArchUnit passes every rule when its import
    # comes back empty, and both exit 0 while proving nothing.  PIT is the one
    # gate that is genuinely run here, because it is deliberately not part of
    # `mvn verify`.
    local failures=0 module

    echo "-- JaCoCo: what was measured, per module"
    local xml covered missed line_pair branch_pair gated real_count measured=0
    for module in "${PRODUCT_MODULES[@]}"; do
        real_count="$(real_classes_of "${module}" | wc -l)"
        xml="${ROOT}/${module}/target/site/jacoco/jacoco.xml"
        gated="no gate"
        module_opts_in "${module}" "cometgui.coverage.core.skip" \
            && gated="core >=90% line >=85% branch"
        module_opts_in "${module}" "cometgui.coverage.viewmodel.skip" \
            && gated="view-model >=80% line"

        if [ ! -f "${xml}" ]; then
            if [ "${real_count}" -eq 0 ]; then
                printf '   inert    %-28s no classes with code yet   [%s]\n' "${module}" "${gated}"
            else
                echo "   MISSING  ${module}: ${real_count} class(es) with code but no jacoco.xml;"
                echo "            the coverage agent did not run, so nothing was measured."
                failures=$((failures + 1))
            fi
            continue
        fi

        line_pair="$(jacoco_counter "${xml}" LINE)"
        branch_pair="$(jacoco_counter "${xml}" BRANCH)"
        if [ -z "${line_pair}" ]; then
            printf '   inert    %-28s report has no LINE counter [%s]\n' "${module}" "${gated}"
            continue
        fi
        covered="${line_pair% *}"
        missed="${line_pair#* }"
        local lines=$(( covered + missed ))
        printf '   ok       %-28s line %s (%s/%s)' \
            "${module}" "$(percent_of "${covered}" "${lines}")" "${covered}" "${lines}"
        if [ -n "${branch_pair}" ]; then
            local branch_covered="${branch_pair% *}"
            local branch_missed="${branch_pair#* }"
            local branches=$(( branch_covered + branch_missed ))
            printf '  branch %s (%s/%s)' \
                "$(percent_of "${branch_covered}" "${branches}")" \
                "${branch_covered}" "${branches}"
        fi
        printf '  [%s]\n' "${gated}"
        measured=$(( measured + lines ))
    done
    [ "${measured}" -gt 0 ] \
        || { echo "   JACOCO MEASURED NO LINES AT ALL IN ANY MODULE"; failures=$((failures + 1)); }

    echo "-- JaCoCo: a module holding gated code must have its gate switched on"
    local class_file prefix hit coverage_drift=0
    for module in "${PRODUCT_MODULES[@]}"; do
        module_opts_in "${module}" "cometgui.coverage.core.skip" && continue
        hit=""
        while IFS= read -r class_file; do
            [ -n "${class_file}" ] || continue
            for prefix in "${COVERAGE_GATED_PREFIXES[@]}"; do
                case "${class_file}" in
                    "${prefix}"/*) hit="${class_file}" ;;
                esac
            done
        done < <(real_classes_of "${module}")
        if [ -n "${hit}" ]; then
            echo "   OFF      ${module} compiles ${hit}, which the specification gates at"
            echo "            90% line / 85% branch, but its POM does not set"
            echo "            <cometgui.coverage.core.skip>false</cometgui.coverage.core.skip>."
            coverage_drift=$((coverage_drift + 1))
        fi
    done
    failures=$(( failures + coverage_drift ))
    if [ "${coverage_drift}" -eq 0 ]; then
        echo "   ok       every module with gated code has its coverage gate on"
    fi

    echo "-- ArchUnit: the class import the rules were actually checked against"
    local census="${ROOT}/cometgui-archtests/target/archunit-import.txt"
    if [ ! -f "${census}" ]; then
        echo "   MISSING  cometgui-archtests/target/archunit-import.txt."
        echo "            ClassImportCensusTest did not run, so no rule in that module is"
        echo "            known to have had anything to check."
        failures=$((failures + 1))
    else
        local imported empty_modules=0 name count
        imported="$(sed -n 's/^imported-classes \([0-9]*\)$/\1/p' "${census}")"
        if [ -z "${imported}" ] || [ "${imported}" -eq 0 ]; then
            echo "   VACUOUS  ArchUnit imported ${imported:-no} classes. Every noClasses() rule in"
            echo "            cometgui-archtests passes when the import is empty."
            failures=$((failures + 1))
        else
            printf '   ok       %d classes imported from org.cometgui\n' "${imported}"
        fi
        while read -r name count; do
            case "${name}" in
                imported-classes|"") continue ;;
            esac
            if [ "${count}" -eq 0 ]; then
                echo "   EMPTY    no classes imported from ${name}; that module is missing from"
                echo "            the cometgui-archtests class path and its rules check nothing."
                empty_modules=$((empty_modules + 1))
            else
                printf '            %-34s %3d class(es)\n' "${name}" "${count}"
            fi
        done < "${census}"
        failures=$(( failures + empty_modules ))
    fi

    local arch_report="${ROOT}/cometgui-archtests/target/surefire-reports/TEST-org.cometgui.archtests.LayeringRulesTest.xml"
    if [ -f "${arch_report}" ]; then
        local arch_tests arch_fail arch_err
        arch_tests="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9]*\)".*/\1/p' "${arch_report}" | head -1)"
        arch_fail="$(sed -n 's/.*<testsuite [^>]*failures="\([0-9]*\)".*/\1/p' "${arch_report}" | head -1)"
        arch_err="$(sed -n 's/.*<testsuite [^>]*errors="\([0-9]*\)".*/\1/p' "${arch_report}" | head -1)"
        if [ "${arch_tests:-0}" -lt 1 ] || [ "${arch_fail:-1}" -ne 0 ] || [ "${arch_err:-1}" -ne 0 ]; then
            echo "   BAD      LayeringRulesTest: tests=${arch_tests} failures=${arch_fail} errors=${arch_err}"
            failures=$((failures + 1))
        else
            printf '   ok       %d architecture rule(s) checked, 0 failures\n' "${arch_tests}"
        fi
    else
        echo "   MISSING  no surefire report for LayeringRulesTest; the rules did not run."
        failures=$((failures + 1))
    fi

    echo "-- PIT: mutation testing over the critical packages (R-TEST-02, >= 80%)"
    # Not part of `mvn verify` on purpose (see the pitest block in pom.xml), so
    # it is run here, against the classes the build stage already produced.  The
    # test-compile prefix is needed rather than the bare goal: a goal-only
    # invocation cannot resolve reactor dependencies to their target/classes and
    # fails on the first module that has a sibling dependency.
    echo "+ mvn -B ${MVN_OFFLINE} -Dmaven.repo.local=${M2REPO} test-compile org.pitest:pitest-maven:mutationCoverage"
    mvn -B ${MVN_OFFLINE} -Dmaven.repo.local="${M2REPO}" \
        test-compile org.pitest:pitest-maven:mutationCoverage > "${ROOT}/_build/pitest.log" 2>&1 \
        || { sed -n '/ERROR/p' "${ROOT}/_build/pitest.log" | head -20; \
             die "PIT failed. Full log: _build/pitest.log"; }
    echo "   full PIT output: _build/pitest.log"

    local pit_xml total killed score_x10 ran=0
    for module in "${PRODUCT_MODULES[@]}"; do
        pit_xml="${ROOT}/${module}/target/pit-reports/mutations.xml"
        if ! module_opts_in "${module}" "cometgui.mutation.skip"; then
            if [ -f "${pit_xml}" ]; then
                echo "   UNEXPECTED ${module} produced a PIT report but its POM does not switch"
                echo "            the mutation gate on; one of the two is wrong."
                failures=$((failures + 1))
            fi
            continue
        fi
        if [ ! -f "${pit_xml}" ]; then
            echo "   MISSING  ${module} switches the mutation gate on but produced no"
            echo "            target/pit-reports/mutations.xml. PIT did not run there."
            failures=$((failures + 1))
            continue
        fi
        total="$(grep -c '<mutation ' "${pit_xml}" || true)"
        killed="$(grep -c "status='KILLED'" "${pit_xml}" || true)"
        if [ "${total}" -eq 0 ]; then
            echo "   VACUOUS  ${module}: PIT generated 0 mutations and still exited 0."
            echo "            A mutation score over an empty run is not a score."
            failures=$((failures + 1))
            continue
        fi
        score_x10=$(( killed * 1000 / total ))
        printf '   ok       %-28s %d/%d mutations killed = %d.%d%%\n' \
            "${module}" "${killed}" "${total}" $(( score_x10 / 10 )) $(( score_x10 % 10 ))
        if [ "${score_x10}" -lt 800 ]; then
            echo "   BELOW    ${module} is under the R-TEST-02 threshold of 80%."
            failures=$((failures + 1))
        fi
        ran=$((ran + 1))
    done
    [ "${ran}" -gt 0 ] \
        || { echo "   NO MODULE RAN A MUTATION ANALYSIS AT ALL"; failures=$((failures + 1)); }

    echo "-- PIT: a module holding critical-package code must have its gate switched on"
    # The target packages are read out of pom.xml rather than repeated here, so
    # that extending the list in the POM extends this check with it.
    local -a mutation_prefixes=()
    local mutation_drift=0
    while IFS= read -r prefix; do
        mutation_prefixes+=("$(printf '%s' "${prefix%.\*}" | tr '.' '/')")
    done < <(sed -n '/<targetClasses>/,/<\/targetClasses>/p' "${ROOT}/pom.xml" \
        | grep -o '<param>[^<]*</param>' | sed 's|</\?param>||g')
    [ "${#mutation_prefixes[@]}" -gt 0 ] \
        || die "could not read <targetClasses> out of pom.xml; this check would pass vacuously."
    printf '   %d critical package prefix(es) read from pom.xml\n' "${#mutation_prefixes[@]}"
    for module in "${PRODUCT_MODULES[@]}"; do
        module_opts_in "${module}" "cometgui.mutation.skip" && continue
        hit=""
        while IFS= read -r class_file; do
            [ -n "${class_file}" ] || continue
            for prefix in "${mutation_prefixes[@]}"; do
                case "${class_file}" in
                    "${prefix}"/*) hit="${class_file}" ;;
                esac
            done
        done < <(real_classes_of "${module}")
        if [ -n "${hit}" ]; then
            echo "   OFF      ${module} compiles ${hit}, which is inside a package R-TEST-02"
            echo "            calls critical, but its POM does not set"
            echo "            <cometgui.mutation.skip>false</cometgui.mutation.skip>."
            mutation_drift=$((mutation_drift + 1))
        fi
    done
    failures=$(( failures + mutation_drift ))
    if [ "${mutation_drift}" -eq 0 ]; then
        echo "   ok       every module with critical-package code has its mutation gate on"
    fi

    [ "${failures}" -eq 0 ] || die "${failures} test-gate evidence check(s) failed."
    echo "OK: coverage measured and gated, architecture rules checked against a real import,"
    echo "    mutation score above the R-TEST-02 threshold."
    echo "    The gates themselves are proved to fail on the defects they catch by"
    echo "    bash scripts/verify-test-gates.sh (run separately: it builds a"
    echo "    deliberately damaged copy of the tree under _build/)."
}

# The four stages below are thin on purpose.  Each one delegates to the same
# scripts/ci/<step>.sh that the pull-request workflow runs, so there is exactly
# one implementation of each check and `bash scripts/build.sh` runs the whole
# content of the pull-request pipeline locally.  See .github/workflows/ and
# scripts/ci/check-workflows.py, which enforces that correspondence.

stage_integration() {
    bash "${ROOT}/scripts/ci/integration-tests.sh"
}

stage_docs() {
    bash "${ROOT}/scripts/ci/docs-build.sh"
    bash "${ROOT}/scripts/ci/traceability.sh"
}

stage_supplychain() {
    bash "${ROOT}/scripts/ci/sbom.sh"
    bash "${ROOT}/scripts/ci/dependency-scan.sh"
}

stage_workflows() {
    bash "${ROOT}/scripts/ci/check-workflows.sh"
}

# -------------------------------------------------------------------- main --
main() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --offline) MVN_OFFLINE="-o"; shift ;;
            --only) ONLY="${2:-}"; [ -n "${ONLY}" ] || die "--only needs a stage id"; shift 2 ;;
            -h|--help) usage; exit 0 ;;
            *) usage >&2; die "unknown option: $1" ;;
        esac
    done

    cd -- "${ROOT}"

    # Select the stages to run, in STAGES order.  An id that names no stage is
    # fatal: a workflow step that quietly ran nothing because someone renamed a
    # stage is precisely the drift these scripts exist to prevent.
    local -a selected=()
    if [ -n "${ONLY}" ]; then
        local want found
        local -a wanted=()
        IFS=',' read -r -a wanted <<< "${ONLY}"
        for want in "${wanted[@]}"; do
            found=0
            for entry in "${STAGES[@]}"; do
                [ "${entry%%:*}" = "${want}" ] && found=1
            done
            [ "${found}" -eq 1 ] || die "--only: no such stage '${want}'. Stages: $(for _s in "${STAGES[@]}"; do printf '%s ' "${_s%%:*}"; done)"
        done
        for entry in "${STAGES[@]}"; do
            for want in "${wanted[@]}"; do
                [ "${entry%%:*}" = "${want}" ] && selected+=("${entry}")
            done
        done
    else
        selected=("${STAGES[@]}")
    fi

    # Every stage after `toolchain` needs the project-local JDK and Maven on
    # PATH.  A full run gets that from stage_toolchain; a --only run that does
    # not include it would otherwise inherit the host PATH, which has no JDK at
    # all, and fail with a confusing "mvn: not found".
    if [ -n "${ONLY}" ] && [[ ",${ONLY}," != *,toolchain,* ]] && [ -f "${ROOT}/tools/env.sh" ]; then
        # shellcheck disable=SC1091
        . "${ROOT}/tools/env.sh"
    fi

    M2_STATE_BEFORE="$(m2_state)"
    readonly M2_STATE_BEFORE

    local started total index id description stage_started elapsed
    started="$(date +%s)"
    total="${#selected[@]}"
    index=0

    for entry in "${selected[@]}"; do
        index=$((index + 1))
        id="${entry%%:*}"
        description="${entry#*:}"
        banner "${index}" "${total}" "${id}" "${description}"
        stage_started="$(date +%s)"
        "stage_${id}"
        elapsed=$(( $(date +%s) - stage_started ))
        STAGE_RESULTS+=("$(printf '  %-12s OK   %4ds  %s' "${id}" "${elapsed}" "${description}")")
    done

    printf '\n===============================================================================\n'
    printf ' SUMMARY\n'
    printf '===============================================================================\n'
    printf '%s\n' "${STAGE_RESULTS[@]}"
    printf '\n  %d/%d stages OK in %d seconds. Maven local repository: %s\n' \
        "${total}" "${total}" "$(( $(date +%s) - started ))" "${M2REPO#"${ROOT}/"}"
    printf '  BUILD OK\n\n'
}

trap 'printf "\n  BUILD FAILED in stage %s\n\n" "${id:-<none>}" >&2' ERR
main "$@"
