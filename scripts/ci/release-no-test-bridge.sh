#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# release-no-test-bridge.sh -- verify, by inspecting the SHIPPED ARTEFACT, that
# no test bridge, no test class, no test framework and no bridge-enabling
# option is in it (specification R-TEST-06, AC-TST-12; Phase 14 unit 6, design
# decision P14-12; release.yml's "Verify that no test bridge is present").
# Replaces the Phase 01 placeholder that exited 70.
#
#   bash scripts/ci/release-no-test-bridge.sh                inspect the archive
#                                                            scripts/package/build-app-image.sh
#                                                            wrote, building it first if absent
#   bash scripts/ci/release-no-test-bridge.sh ARTEFACT       a .tar.gz, an application image
#                                                            (bin/, lib/app/, lib/runtime/) or a
#                                                            directory holding CometGUI/
#   bash scripts/ci/release-no-test-bridge.sh --self-test    show every check failing on a
#                                                            bridge-included build, and passing
#                                                            on the clean one
#   bash scripts/ci/release-no-test-bridge.sh --help
#
# WHAT IS INSPECTED (scripts/ci/no_test_bridge.py does the reading): every JAR
# in the image and every JAR inside a JAR, entry by entry; every manifest;
# every loose class file; the bundled runtime's module image
# (lib/runtime/lib/modules, listed with the project JDK's jimage); and every
# text file, the launcher configuration lib/app/*.cfg first among them.
#
# WHAT FAILS IT, naming what was found and where:
#   * a class of the test bridge (org/cometgui/app/e2e/**), of the GUI test
#     driver it runs (org/cometgui/app/uidriver/**), of cometgui-app's test
#     support, of TestFX, Monocle, JUnit, opentest4j, apiguardian or another
#     test framework; any class compiled from a */src/test/java source of this
#     repository; the D-007 fake Limelight server;
#   * a JAR whose manifest makes it a Java agent (Premain-Class, Agent-Class,
#     Launcher-Agent-Class) or extends the boot class path;
#   * a file or nested archive named like test machinery;
#   * in the launcher configuration or any other text: -javaagent, -agentlib,
#     -agentpath, -Xbootclasspath, JAVA_TOOL_OPTIONS, the end-to-end flag
#     cometgui.e2e, the bridge's jar name or package, its port-file= argument;
#   * a runtime module named like test machinery.
# It reports how much it inspected -- files, JARs, classes, launcher
# configurations, text files, runtime modules and their classes -- and
# "found nothing" is never "looked at nothing": an artefact with no JAR, no
# class, no launcher configuration or no runtime module is exit 2.
#
# PLATFORMS.  The Linux artefact (a tar.gz of a jpackage app-image) is
# inspected here.  The macOS and Windows installers (.dmg/.pkg, .msi/.exe) are
# not unpacked by this script yet, so on those hosts it exits 1 saying so: an
# artefact that has not been inspected has not passed (Phase 16 builds those).
#
# Exit status: 0 inspected and clean; 1 something forbidden found, or a
# self-test variant graded wrongly; 2 misuse, or nothing inspected.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
INSPECTOR="${SCRIPT_DIR}/no_test_bridge.py"
WORK_ROOT="${ROOT}/_build/no-test-bridge"

usage() { sed -n '7,52p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
die() { printf 'release-no-test-bridge.sh: %s\n' "$*" >&2; exit "${2:-2}"; }
export PYTHONDONTWRITEBYTECODE=1

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run scripts/feasibility/install-toolchain.sh"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
JIMAGE="${JAVA_HOME}/bin/jimage"
[ -x "${JIMAGE}" ] || die "the project JDK at ${JAVA_HOME} has no jimage"
PYTHON=""
for candidate in "${ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] && { PYTHON="${candidate}"; break; }
done
[ -n "${PYTHON}" ] || die "no Python 3"

case "$(uname -s)" in
    Linux) PLATFORM=linux ;;
    Darwin) PLATFORM=macos ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=windows ;;
    *) die "unrecognised host $(uname -s)" ;;
esac

# built_archive -> the archive build-app-image.sh wrote, building it if absent.
built_archive() {
    local built="${ROOT}/_build/package/linux" archives
    shopt -s nullglob
    archives=("${built}"/CometGUI-*-linux-x86_64.tar.gz)
    shopt -u nullglob
    if [ "${#archives[@]}" -eq 0 ]; then
        mkdir -p -- "${WORK_ROOT}"
        echo "no packaged archive in ${built}: building it with scripts/package/build-app-image.sh" >&2
        bash "${ROOT}/scripts/package/build-app-image.sh" > "${WORK_ROOT}/build-app-image.log" 2>&1 \
            || { tail -20 "${WORK_ROOT}/build-app-image.log" >&2; die "scripts/package/build-app-image.sh failed; see ${WORK_ROOT}/build-app-image.log" 1; }
        shopt -s nullglob
        archives=("${built}"/CometGUI-*-linux-x86_64.tar.gz)
        shopt -u nullglob
    fi
    [ "${#archives[@]}" -eq 1 ] || die "expected one CometGUI-*-linux-x86_64.tar.gz in ${built}, found ${#archives[@]}" 1
    printf '%s\n' "${archives[0]}"
}

# inspect ARTEFACT -> the inspector's verdict and exit status
inspect() {
    local artefact="$1" image extracted="" status=0
    if [ -f "${artefact}" ]; then
        case "${artefact}" in
            *.tar.gz|*.tgz) ;;
            *) die "${artefact}: not a .tar.gz, and installers of other kinds are not unpacked here" ;;
        esac
        mkdir -p -- "${WORK_ROOT}"
        extracted="$(mktemp -d "${WORK_ROOT}/extract.XXXXXX")"
        tar -xzf "${artefact}" -C "${extracted}" || { rm -rf -- "${extracted}"; die "${artefact} cannot be extracted" 1; }
        image="${extracted}/CometGUI"
        [ -d "${image}" ] || { rm -rf -- "${extracted}"; die "${artefact} holds no CometGUI/ application image" 1; }
    elif [ -d "${artefact}/lib/app" ] || [ -d "${artefact}/bin" ]; then
        image="${artefact}"
    elif [ -d "${artefact}/CometGUI" ]; then
        image="${artefact}/CometGUI"
    elif [ -d "${artefact}" ]; then
        image="${artefact}"
    else
        die "no artefact at ${artefact}"
    fi
    echo "artefact: ${artefact}"
    "${PYTHON}" "${INSPECTOR}" --root "${ROOT}" --jimage "${JIMAGE}" --image "${image}" \
        --label "${artefact}" || status=$?
    [ -z "${extracted}" ] || rm -rf -- "${extracted}"
    return "${status}"
}

# ===========================================================================
# The self-test: bridge-included variants of the real image, each rejected.
# ===========================================================================
self_test() {
    [ "${PLATFORM}" = linux ] || die "the self-test builds Linux image variants; this host is ${PLATFORM}" 1
    local image="${ROOT}/_build/package/linux/image/CometGUI"
    mkdir -p -- "${WORK_ROOT}"
    [ -d "${image}" ] || built_archive > /dev/null || exit 1
    [ -d "${image}/lib/app" ] || die "no application image at ${image}" 1
    # The real bridge jar, from cometgui-app's test classes.
    local bridge="${ROOT}/_build/e2e/bridge/cometgui-e2e-bridge.jar"
    bash "${ROOT}/scripts/e2e/build-bridge.sh" > "${WORK_ROOT}/build-bridge.log" 2>&1 \
        || { tail -20 "${WORK_ROOT}/build-bridge.log" >&2; die "scripts/e2e/build-bridge.sh failed" 1; }
    [ -s "${bridge}" ] || die "scripts/e2e/build-bridge.sh wrote no ${bridge}" 1
    local junit
    junit="$(tr ':' '\n' < "${ROOT}/cometgui-app/target/e2e-bridge.classpath" | grep -E '/junit-jupiter-api-[0-9][^/]*\.jar$' | sed -n '1p')"
    [ -f "${junit}" ] || die "no junit-jupiter-api jar on cometgui-app's test class path" 1
    local fake="${ROOT}/cometgui-tools/target/test-classes/org/cometgui/tools/limelight/fake"
    local domain_tests="${ROOT}/cometgui-domain/target/test-classes"
    [ -f "${fake}/FakeLimelightServer.class" ] || die "no compiled D-007 fake at ${fake}; compile the tests (mvn -o test-compile)" 1
    local domain_test
    domain_test="$(cd -- "${domain_tests}" && find . -name '*Test.class' | sort | sed -n '1p' | sed 's|^\./||')"
    [ -n "${domain_test}" ] || die "no compiled cometgui-domain test class in ${domain_tests}" 1

    # st is a global: the EXIT trap that removes it runs after this returns.
    st="${WORK_ROOT}/self-test"
    rm -rf -- "${st}"
    mkdir -p -- "${st}"
    # shellcheck disable=SC2317
    cleanup_self_test() { [ -n "${st}" ] && rm -rf -- "${st}"; }
    trap cleanup_self_test EXIT
    echo "self-test workspace: ${st}"
    local caught=0 missed=0 total=0 verdicts=()

    # variant NAME -> a copy of the clean image at ${st}/NAME/CometGUI.  Hard
    # links (same file system): a file is changed only through own(), which
    # replaces the link by a copy first, so the real image is never touched.
    variant() { mkdir -p -- "${st}/$1"; cp -al -- "${image}" "${st}/$1/CometGUI"; }
    own() { cp -- "$1" "$1.own"; mv -f -- "$1.own" "$1"; }

    # expect NAME ARTEFACT STATUS REGEX... -> the check's exit status and diagnostics
    expect() {
        local name="$1" artefact="$2" want="$3"
        shift 3
        total=$((total + 1))
        printf '\n######## variant %s\n' "${name}"
        local log="${st}/${name}.log" status=0
        inspect "${artefact}" > "${log}" 2>&1 || status=$?
        sed 's/^/    /' "${log}" | sed -n '1,14p'
        local ok=1 regex
        [ "${status}" -eq "${want}" ] || { ok=0; echo "    !! exit ${status}, expected ${want}"; }
        for regex in "$@"; do
            grep -Eq -- "${regex}" "${log}" || { ok=0; echo "    !! no line matching /${regex}/"; }
        done
        if [ "${ok}" -eq 1 ]; then
            caught=$((caught + 1))
            verdicts+=("OK      ${name}: exit ${status}, $# expected diagnostic(s) seen")
        else
            missed=$((missed + 1))
            verdicts+=("MISSED  ${name} (exit ${status}; see ${log})")
        fi
    }

    local app_jar
    app_jar="$(cd -- "${image}/lib/app" && ls cometgui-app-*.jar)"

    # A: the bridge jar, renamed innocently, in lib/app.
    variant bridge-jar
    cp -- "${bridge}" "${st}/bridge-jar/CometGUI/lib/app/commons-util-1.0.jar"
    expect bridge-jar "${st}/bridge-jar" 1 \
        'FOUND lib/app/commons-util-1.0.jar: org/cometgui/app/e2e/bridge/BridgeAgent\.class -- the end-to-end harness' \
        'FOUND lib/app/commons-util-1.0.jar: org/cometgui/app/uidriver/RobotFxUiDriver\.class -- the GUI test driver' \
        'FOUND lib/app/commons-util-1.0.jar: org/junit/jupiter/api/Assertions\.class -- JUnit' \
        "FOUND lib/app/commons-util-1.0.jar!META-INF/MANIFEST.MF: manifest 'Premain-Class: org\.cometgui\.app\.e2e\.bridge\.BridgeAgent' -- a Java agent"

    # B: the bridge's classes repackaged inside cometgui-app's own jar.
    variant bridge-in-app-jar
    own "${st}/bridge-in-app-jar/CometGUI/lib/app/${app_jar}"
    "${PYTHON}" - "${bridge}" "${st}/bridge-in-app-jar/CometGUI/lib/app/${app_jar}" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as bridge, zipfile.ZipFile(sys.argv[2], "a") as app:
    for name in bridge.namelist():
        if name.startswith("org/cometgui/app/e2e/bridge/"):
            app.writestr(name, bridge.read(name))
PY
    expect bridge-in-app-jar "${st}/bridge-in-app-jar" 1 \
        "FOUND lib/app/${app_jar//./\\.}: org/cometgui/app/e2e/bridge/BridgeServer\\.class -- the end-to-end harness"

    # C: -javaagent added to the launcher's java-options.
    variant javaagent-option
    own "${st}/javaagent-option/CometGUI/lib/app/CometGUI.cfg"
    printf 'java-options=-javaagent:$APPDIR/commons-util-1.0.jar=port-file=/tmp/p\n' >> "${st}/javaagent-option/CometGUI/lib/app/CometGUI.cfg"
    expect javaagent-option "${st}/javaagent-option" 1 \
        "FOUND lib/app/CometGUI\\.cfg:[0-9]+: launcher configuration 'java-options=-javaagent:.*' -- a Java agent option.*\\(-javaagent\\)" \
        "FOUND lib/app/CometGUI\\.cfg:[0-9]+: .*\\(port-file=\\)"

    # D: the end-to-end flag added as a launcher property.
    variant e2e-flag
    own "${st}/e2e-flag/CometGUI/lib/app/CometGUI.cfg"
    printf 'java-options=-Dcometgui.e2e=true\n' >> "${st}/e2e-flag/CometGUI/lib/app/CometGUI.cfg"
    expect e2e-flag "${st}/e2e-flag" 1 \
        "FOUND lib/app/CometGUI\\.cfg:[0-9]+: launcher configuration 'java-options=-Dcometgui\\.e2e=true' -- the end-to-end flag"

    # E: JUnit's jar in lib/app.
    variant junit-jar
    cp -- "${junit}" "${st}/junit-jar/CometGUI/lib/app/"
    expect junit-jar "${st}/junit-jar" 1 \
        "FOUND lib/app/$(basename -- "${junit}" | sed 's/\./\\./g'): a file named like test machinery" \
        "FOUND lib/app/$(basename -- "${junit}" | sed 's/\./\\./g'): org/junit/jupiter/api/Assertions\\.class -- JUnit"

    # F: the bridge jar nested inside another jar.
    variant nested-bridge
    "${PYTHON}" - "${bridge}" "${st}/nested-bridge/CometGUI/lib/app/support-bundle-1.0.jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[2], "w") as bundle:
    bundle.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
    with open(sys.argv[1], "rb") as inner:
        bundle.writestr("lib/inner-1.0.jar", inner.read())
PY
    expect nested-bridge "${st}/nested-bridge" 1 \
        'FOUND lib/app/support-bundle-1\.0\.jar!lib/inner-1\.0\.jar: org/cometgui/app/e2e/bridge/BridgeAgent\.class'

    # G: the D-007 fake and an ordinary test class of another module, in a jar.
    variant test-classes
    (cd -- "${ROOT}/cometgui-tools/target/test-classes" && jar --create --file "${st}/test-classes/CometGUI/lib/app/limelight-client-2.0.jar" org/cometgui/tools/limelight/fake)
    (cd -- "${domain_tests}" && jar --update --file "${st}/test-classes/CometGUI/lib/app/limelight-client-2.0.jar" "${domain_test}")
    expect test-classes "${st}/test-classes" 1 \
        'FOUND lib/app/limelight-client-2\.0\.jar: org/cometgui/tools/limelight/fake/FakeLimelightServer\.class -- the D-007 fake' \
        "FOUND lib/app/limelight-client-2\\.0\\.jar: ${domain_test//./\\.} -- a test class of this project \\(cometgui-domain/src/test/java\\)"

    # H: a loose bridge class file dropped into the image.
    variant loose-class
    mkdir -p -- "${st}/loose-class/CometGUI/lib/app/classes/org/cometgui/app/e2e/bridge"
    (cd -- "${st}" && jar --extract --file "${bridge}" org/cometgui/app/e2e/bridge/BridgeAgent.class \
        && mv org/cometgui/app/e2e/bridge/BridgeAgent.class "${st}/loose-class/CometGUI/lib/app/classes/org/cometgui/app/e2e/bridge/" && rm -rf -- org)
    expect loose-class "${st}/loose-class" 1 \
        'FOUND lib/app/classes/org/cometgui/app/e2e/bridge/BridgeAgent\.class: org/cometgui/app/e2e/bridge/BridgeAgent\.class -- the end-to-end harness'

    # I: variant C shipped as the archive -- the .tar.gz path of the check.
    tar --sort=name -C "${st}/javaagent-option" -czf "${st}/javaagent-option.tar.gz" CometGUI
    expect javaagent-option-archive "${st}/javaagent-option.tar.gz" 1 \
        "inspected ${st//./\\.}/javaagent-option\\.tar\\.gz" \
        "FOUND lib/app/CometGUI\\.cfg:[0-9]+: launcher configuration 'java-options=-javaagent:"

    # J: an image with nothing in it is not clean, it is uninspected.
    mkdir -p -- "${st}/empty/CometGUI/lib/app" "${st}/empty/CometGUI/bin"
    expect nothing-inspected "${st}/empty" 2 \
        'NOTHING INSPECTED -- no JAR, no class, no launcher configuration, no runtime module'

    # And the clean image, as built, passes.
    expect clean "${image}" 0 \
        '^  [1-9][0-9]* files; [1-9][0-9]* JAR\(s\).* [1-9][0-9]* class entries.* 1 launcher configuration\(s\).* [1-9][0-9]* runtime modules holding [1-9][0-9]* classes' \
        'release-no-test-bridge: PASSED'

    printf '\n== self-test verdicts\n'
    printf '  %s\n' "${verdicts[@]}"
    if [ "${missed}" -eq 0 ] && [ "${caught}" -eq "${total}" ]; then
        echo "release-no-test-bridge.sh --self-test: PASSED -- $((total - 2)) bridge-included variant(s) each rejected with its diagnostic, the empty image refused as uninspected, the clean image accepted"
        return 0
    fi
    echo "release-no-test-bridge.sh --self-test: FAILED -- ${missed} of ${total} variant(s) not graded as expected (a control that is not caught is a harness failure)"
    return 1
}

case "${1:-}" in
    --help|-h) usage; exit 0 ;;
    --self-test) [ "$#" -eq 1 ] || die "--self-test takes no other argument"; self_test ;;
    "")
        [ "${PLATFORM}" = linux ] \
            || die "the ${PLATFORM} artefact is an installer this check does not unpack yet; it has NOT been inspected, so it has not passed (Phase 16 builds it)" 1
        archive="$(built_archive)" || exit $?
        inspect "${archive}"
        ;;
    -*) die "unknown option $1 (try --help)" ;;
    *) [ "$#" -eq 1 ] || die "one artefact at a time"; inspect "$1" ;;
esac
