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
# ADDING A STAGE (phase 01 units 2, 3, 6 and 7 will):
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
    "python:Project virtualenv for the documentation toolchain"
    "build:Maven clean verify -- compile, unit tests, package"
    "artefacts:Verify the build produced what it claims to have produced"
    "format:Evidence that Spotless, Checkstyle and SpotBugs inspected the code"
    # unit 3  "gates:JaCoCo coverage, ArchUnit and PIT mutation gates"
    # unit 6  "docs:Strict Sphinx build and the traceability report"
    # unit 7  "supplychain:SBOM generation and dependency vulnerability scan"
)

# ----------------------------------------------------------------- plumbing --
MVN_OFFLINE=""
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

# -------------------------------------------------------------------- main --
main() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --offline) MVN_OFFLINE="-o"; shift ;;
            -h|--help) usage; exit 0 ;;
            *) usage >&2; die "unknown option: $1" ;;
        esac
    done

    cd -- "${ROOT}"

    M2_STATE_BEFORE="$(m2_state)"
    readonly M2_STATE_BEFORE

    local started total index id description stage_started elapsed
    started="$(date +%s)"
    total="${#STAGES[@]}"
    index=0

    for entry in "${STAGES[@]}"; do
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
