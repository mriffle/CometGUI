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
    # unit 2  "format:Spotless, Checkstyle and SpotBugs"
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
