#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# build-app-image.sh -- build the packaged CometGUI from the reactor: the
# application's runtime JARs, a jlinked runtime with JavaFX that keeps
# bin/java, a jpackage image, and (on Linux) its tar.gz and SHA-256
# (Phase 14, unit 5; design decision P14-10).
#
#   bash scripts/package/build-app-image.sh               build for this host
#   bash scripts/package/build-app-image.sh --skip-maven  reuse the reactor's
#                                                         current outputs
#   bash scripts/package/build-app-image.sh --help
#
# OUTPUT (gitignored), for Linux:
#   _build/package/linux/image/CometGUI/          the jpackage app-image
#   _build/package/linux/CometGUI-<version>-linux-x86_64.tar.gz
#   _build/package/linux/CometGUI-<version>-linux-x86_64.tar.gz.sha256
#   _build/package/linux/input/                   the JARs jpackage was given
#   _build/package/linux/jpackage-build.args      the build's own jpackage values
#   _build/package/linux/build-info.txt           version, command, modules,
#                                                 sizes and times
#   _build/package/linux/logs/                    Maven, jdeps and jpackage logs
#
# THE CONFIGURATION IS COMMITTED, NOT WRITTEN HERE: packaging/jpackage/
# common.args (every platform), packaging/jpackage/<platform>.args, and
# packaging/runtime-modules.txt (the runtime's root modules, each with its
# reason).  This script adds only what a build knows: the version (from the
# build identity cometgui-app is built with), the input directory, the main
# JAR, the module list and the destination.
#
# PLATFORMS.  Linux x86-64 is built and verified here.  macOS and Windows are
# configured (packaging/jpackage/macos.args, windows.args) but NEVER EXECUTED:
# jpackage is not a cross-compiler and no such host exists in this
# environment.  On those hosts this script runs jpackage and checks only that
# an installer file appeared; nothing about them has been shown to work.
#
# NOT THE RELEASE STEP.  scripts/ci/release-package.sh is Phase 16's and stays
# its stub; Phase 16 wires it to this.  Signing, notarisation and the
# launcher's UTF-8 locale are Phase 16's too.
#
# EXIT 0 PROVES NOTHING, so every stage checks what it produced: the staged
# JARs are byte-identical to the reactor's, no test machinery is among them,
# the version is a filtered value, the runtime has bin/java and the JavaFX
# modules, every module the application needs (jdeps) is in the runtime, the
# launcher configuration has the native-access options and no
# sun.jnu.encoding, the bundled java runs, and the archive lists the launcher
# and the runtime's java and matches its checksum.  Any failure exits 1 with
# a diagnostic naming what was wrong.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
CONFIG="${ROOT}/packaging"
M2REPO="${ROOT}/_build/m2repo"
APP_MODULE="${ROOT}/cometgui-app"
MAIN_CLASS="org.cometgui.app.bootstrap.CometGuiLauncher"
IDENTITY_ENTRY="org/cometgui/app/config/build-identity.properties"

# The application's own reactor modules, i.e. what must come from THIS build
# rather than from the local repository (a stale copy there would package old
# code without any error).
REACTOR_JARS=(cometgui-domain cometgui-provenance cometgui-process cometgui-tools
    cometgui-install cometgui-params-comet cometgui-params-percolator cometgui-results
    cometgui-workflow cometgui-ui)

# Test machinery that must never reach a packaged artefact (R-TEST-06): by
# file name, and by class -- every class of every module's test tree is
# looked for in every staged JAR.
FORBIDDEN_JAR_NAMES='(testfx|junit|hamcrest|opentest4j|apiguardian|monocle|assertj|mockito|org\.osgi|archunit|jqwik|pitest|jacoco)'

SKIP_MAVEN=0
usage() { sed -n '7,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
for arg in "$@"; do
    case "${arg}" in
        --skip-maven) SKIP_MAVEN=1 ;;
        --help|-h) usage; exit 0 ;;
        *) printf 'build-app-image.sh: unknown argument %s\n' "${arg}" >&2; usage >&2; exit 2 ;;
    esac
done

die() { printf 'build-app-image.sh: FAILED: %s\n' "$*" >&2; exit 1; }
note() { printf '\n== %s\n' "$*"; }
now() { date +%s.%N; }
elapsed() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", b - a }'; }

case "$(uname -s)" in
    Linux) PLATFORM=linux ;;
    Darwin) PLATFORM=macos ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=windows ;;
    *) die "unrecognised host $(uname -s); CometGUI packages for Linux, macOS and Windows" ;;
esac
case "${PLATFORM}:$(uname -m)" in
    linux:x86_64) ARCH=x86_64 ;;
    linux:*) die "Linux $(uname -m) is not a packaged platform (only x86-64 is; specification.rst, Supported Platforms)" ;;
    *) ARCH="$(uname -m)" ;;
esac

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run scripts/feasibility/install-toolchain.sh"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
for tool in jpackage jdeps jar java; do
    [ -x "${JAVA_HOME}/bin/${tool}" ] || [ -x "${JAVA_HOME}/bin/${tool}.exe" ] \
        || die "the project JDK at ${JAVA_HOME} has no ${tool}"
done
for f in common.args "${PLATFORM}.args"; do
    [ -s "${CONFIG}/jpackage/${f}" ] || die "packaging/jpackage/${f} is missing"
done
[ -s "${CONFIG}/runtime-modules.txt" ] || die "packaging/runtime-modules.txt is missing"

OUT="${ROOT}/_build/package/${PLATFORM}"
LOGS="${OUT}/logs"
INPUT="${OUT}/input"
IMAGE_DEST="${OUT}/image"
rm -rf -- "${OUT}"
mkdir -p -- "${LOGS}" "${INPUT}" "${IMAGE_DEST}"

# ---------------------------------------------------------------------------
note "1. the reactor: package cometgui-app and copy its runtime dependencies"
T0="$(now)"
if [ "${SKIP_MAVEN}" -eq 0 ]; then
    # Both goals in ONE reactor invocation, so that copy-dependencies resolves
    # the sibling modules to the JARs this run just built and not to whatever
    # the local repository holds.  Run from the root: .mvn/maven.config's
    # relative local repository resolves against the working directory.
    rm -rf -- "${APP_MODULE}/target/dependency"
    (cd -- "${ROOT}" && mvn -B -o -Dmaven.repo.local="${M2REPO}" \
        -pl cometgui-app -am -DskipTests \
        package dependency:copy-dependencies -DincludeScope=runtime) \
        > "${LOGS}/maven.log" 2>&1 \
        || { tail -40 "${LOGS}/maven.log" >&2; die "Maven failed; see ${LOGS}/maven.log"; }
    grep -q 'BUILD SUCCESS' "${LOGS}/maven.log" || die "Maven exited 0 without BUILD SUCCESS; see ${LOGS}/maven.log"
else
    echo "skipped (--skip-maven): using the reactor's current target/ directories"
fi
T1="$(now)"
MAVEN_SECONDS="$(elapsed "${T0}" "${T1}")"

shopt -s nullglob
APP_JARS=("${APP_MODULE}"/target/cometgui-app-*.jar)
DEP_JARS=("${APP_MODULE}"/target/dependency/*.jar)
shopt -u nullglob
[ "${#APP_JARS[@]}" -eq 1 ] || die "expected exactly one cometgui-app JAR in cometgui-app/target, found ${#APP_JARS[@]}"
[ "${#DEP_JARS[@]}" -gt 0 ] || die "cometgui-app/target/dependency is empty; run without --skip-maven"
MAIN_JAR="$(basename -- "${APP_JARS[0]}")"
cp -- "${APP_JARS[0]}" "${DEP_JARS[@]}" "${INPUT}/"
echo "staged ${MAIN_JAR} and ${#DEP_JARS[@]} runtime dependencies (Maven: ${MAVEN_SECONDS} s)"

# Every reactor JAR must be staged, and byte-identical to its module's target/.
for module in "${REACTOR_JARS[@]}"; do
    shopt -s nullglob
    staged=("${INPUT}/${module}"-[0-9]*.jar)
    built=("${ROOT}/${module}/target/${module}"-[0-9]*.jar)
    shopt -u nullglob
    [ "${#staged[@]}" -eq 1 ] || die "${module}: expected one staged JAR, found ${#staged[@]}"
    [ "${#built[@]}" -eq 1 ] || die "${module}: expected one JAR in ${module}/target, found ${#built[@]}"
    cmp -s -- "${staged[0]}" "${built[0]}" \
        || die "${module}: the staged JAR differs from ${built[0]} -- a stale copy from the local repository?"
done
echo "every reactor JAR is byte-identical to its module's target/ output"

# No test machinery, by name or by class.
for jar in "${INPUT}"/*.jar; do
    if basename -- "${jar}" | grep -Eqi "${FORBIDDEN_JAR_NAMES}"; then
        die "test machinery staged for the image: $(basename -- "${jar}")"
    fi
done
python3 - "${ROOT}" "${INPUT}" <<'PY' || die "test classes found among the staged JARs (above)"
import glob, os, sys, zipfile
root, staged = sys.argv[1], sys.argv[2]
test_classes = set()
for tree in glob.glob(os.path.join(root, "*", "target", "test-classes")):
    for dirpath, _, files in os.walk(tree):
        for name in files:
            if name.endswith(".class"):
                test_classes.add(os.path.relpath(os.path.join(dirpath, name), tree))
if not test_classes:
    print("no module test-classes directory was found, so the class-level test-machinery "
          "check would compare against nothing; build the reactor's tests first", file=sys.stderr)
    sys.exit(1)
forbidden_prefixes = ("org/cometgui/app/e2e/", "org/cometgui/app/testing/",
                      "org/cometgui/app/uidriver/", "org/testfx/", "org/junit/",
                      "com/sun/glass/ui/monocle/")
bad = []
for jar in sorted(glob.glob(os.path.join(staged, "*.jar"))):
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if entry in test_classes or entry.startswith(forbidden_prefixes):
                bad.append(f"{os.path.basename(jar)}!{entry}")
if bad:
    print("\n".join(bad[:40]), file=sys.stderr)
    sys.exit(1)
print(f"no staged JAR holds any of {len(test_classes)} test classes or a test-only package")
PY

# ---------------------------------------------------------------------------
note "2. the version, from the build identity cometgui-app was built with"
IDENTITY="$(python3 - "${INPUT}/${MAIN_JAR}" "${IDENTITY_ENTRY}" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    sys.stdout.write(archive.read(sys.argv[2]).decode("utf-8"))
PY
)" || die "${MAIN_JAR} has no ${IDENTITY_ENTRY}"
VERSION="$(printf '%s\n' "${IDENTITY}" | sed -n 's/^cometgui\.version=//p' | tr -d '\r')"
[ -n "${VERSION}" ] || die "${IDENTITY_ENTRY} names no cometgui.version"
case "${VERSION}" in *'${'*) die "the build identity was not filtered: cometgui.version=${VERSION}" ;; esac
# jpackage's --app-version must be numeric on macOS and Windows; the full
# version (with any -SNAPSHOT) names the archive.
APP_VERSION="$(printf '%s' "${VERSION}" | grep -Eo '^[0-9]+(\.[0-9]+){0,2}' || true)"
[ -n "${APP_VERSION}" ] || die "cometgui.version=${VERSION} does not begin with a numeric version"
echo "cometgui.version=${VERSION}; jpackage --app-version ${APP_VERSION}"

# ---------------------------------------------------------------------------
note "3. the runtime's root modules (packaging/runtime-modules.txt)"
MODULES="$(grep -Ev '^[[:space:]]*(#|$)' "${CONFIG}/runtime-modules.txt" | tr -d ' \t\r' | paste -sd, -)"
[ -n "${MODULES}" ] || die "packaging/runtime-modules.txt lists no module"
echo "${MODULES}"
# What the application itself needs, by static analysis; checked against the
# runtime once it exists.
jdeps --print-module-deps --ignore-missing-deps --multi-release 25 \
    --class-path "${INPUT}/*" "${INPUT}/${MAIN_JAR}" > "${LOGS}/jdeps.txt" 2>&1 \
    || { cat "${LOGS}/jdeps.txt" >&2; die "jdeps failed over the staged JARs"; }
APP_NEEDS="$(grep -E '^[a-z][a-z0-9.,]*$' "${LOGS}/jdeps.txt" | tail -1)"
[ -n "${APP_NEEDS}" ] || die "jdeps printed no module list; see ${LOGS}/jdeps.txt"
echo "jdeps: the application needs ${APP_NEEDS}"

# ---------------------------------------------------------------------------
note "4. jpackage"
BUILD_ARGS="${OUT}/jpackage-build.args"
{
    echo "# Written by scripts/package/build-app-image.sh: the values only a build knows."
    echo "--app-version ${APP_VERSION}"
    echo "--input \"${INPUT}\""
    echo "--main-jar ${MAIN_JAR}"
    echo "--add-modules ${MODULES}"
} > "${BUILD_ARGS}"
JPACKAGE_CMD=(jpackage "@${CONFIG}/jpackage/common.args" "@${CONFIG}/jpackage/${PLATFORM}.args"
    "@${BUILD_ARGS}" --dest "${IMAGE_DEST}" --verbose)
printf '%q ' "${JPACKAGE_CMD[@]}" > "${OUT}/jpackage-command.txt"; echo >> "${OUT}/jpackage-command.txt"
cat "${OUT}/jpackage-command.txt"
T2="$(now)"
"${JPACKAGE_CMD[@]}" > "${LOGS}/jpackage.log" 2>&1 \
    || { tail -40 "${LOGS}/jpackage.log" >&2; die "jpackage failed; see ${LOGS}/jpackage.log"; }
T3="$(now)"
JPACKAGE_SECONDS="$(elapsed "${T2}" "${T3}")"
echo "jpackage: ${JPACKAGE_SECONDS} s"
JLINK_LINE="$(grep -m1 'jlink --output' "${LOGS}/jpackage.log" | sed 's/^[[:space:]]*//' || true)"
[ -n "${JLINK_LINE}" ] || die "jpackage's log shows no jlink command; see ${LOGS}/jpackage.log"
case "${JLINK_LINE}" in
    *--strip-native-commands*) die "jlink ran with --strip-native-commands, so the runtime has no bin/java: ${JLINK_LINE}" ;;
esac

if [ "${PLATFORM}" != linux ]; then
    # NEVER EXECUTED (see the header).  Only the existence of an installer is
    # checked; nothing here has been run on such a host.
    shopt -s nullglob
    produced=("${IMAGE_DEST}"/CometGUI*)
    shopt -u nullglob
    [ "${#produced[@]}" -ge 1 ] || die "jpackage produced nothing in ${IMAGE_DEST}"
    for f in "${produced[@]}"; do
        [ -f "${f}" ] && (cd -- "$(dirname -- "${f}")" && sha256sum -- "$(basename -- "${f}")" > "$(basename -- "${f}").sha256")
    done
    note "UNVERIFIED ${PLATFORM} output (this path has never been executed by the project)"
    ls -l "${IMAGE_DEST}"
    exit 0
fi

# ---------------------------------------------------------------------------
note "5. the image"
APPDIR="${IMAGE_DEST}/CometGUI"
RUNTIME="${APPDIR}/lib/runtime"
CFG="${APPDIR}/lib/app/CometGUI.cfg"
[ -x "${APPDIR}/bin/CometGUI" ] || die "no launcher at ${APPDIR}/bin/CometGUI"
[ -x "${RUNTIME}/bin/java" ] || die "the bundled runtime has no bin/java (${RUNTIME}/bin): PDV and the Limelight converter could not be started"
[ -s "${RUNTIME}/lib/server/libjvm.so" ] || die "the bundled runtime has no lib/server/libjvm.so"
[ -s "${CFG}" ] || die "no launcher configuration at ${CFG}"

RUNTIME_MODULES="$(sed -n 's/^MODULES="\(.*\)"$/\1/p' "${RUNTIME}/release" | tr ' ' '\n' | sort)"
[ -n "${RUNTIME_MODULES}" ] || die "${RUNTIME}/release names no MODULES"
for required in javafx.base javafx.graphics javafx.controls $(printf '%s' "${MODULES},${APP_NEEDS}" | tr ',' ' '); do
    printf '%s\n' "${RUNTIME_MODULES}" | grep -qx -- "${required}" \
        || die "the bundled runtime lacks ${required} (its modules: $(printf '%s' "${RUNTIME_MODULES}" | paste -sd' ' -))"
done
echo "runtime modules ($(printf '%s\n' "${RUNTIME_MODULES}" | wc -l)): $(printf '%s' "${RUNTIME_MODULES}" | paste -sd' ' -)"

# The bundled java must run on its own: no JAVA_HOME, no JDK on PATH.
VERSION_OUT="$(env -i PATH=/usr/bin:/bin "${RUNTIME}/bin/java" -XshowSettings:properties -version 2>&1)" \
    || die "the bundled java does not run: ${VERSION_OUT}"
printf '%s\n' "${VERSION_OUT}" | grep -q "java.home = ${RUNTIME}\$" \
    || die "the bundled java reports a java.home other than ${RUNTIME}: $(printf '%s\n' "${VERSION_OUT}" | grep 'java.home')"
RUNTIME_LISTED="$(env -i PATH=/usr/bin:/bin "${RUNTIME}/bin/java" --list-modules | sed 's/@.*//' | sort)"
[ "${RUNTIME_LISTED}" = "${RUNTIME_MODULES}" ] \
    || die "java --list-modules disagrees with lib/runtime/release"

# The launcher configuration.
grep -qx "app.mainclass=${MAIN_CLASS}" "${CFG}" || die "${CFG} does not name ${MAIN_CLASS} as its main class"
grep -qx "app.classpath=\$APPDIR/${MAIN_JAR}" "${CFG}" || die "${CFG} does not put ${MAIN_JAR} on the class path"
for option in --enable-native-access=ALL-UNNAMED --enable-native-access=javafx.graphics; do
    grep -qx "java-options=${option}" "${CFG}" || die "${CFG} lacks the java option ${option}"
done
if grep -qi 'jnu' "${CFG}"; then
    die "${CFG} mentions sun.jnu.encoding, which is inert as a java option (STATUS.rst, Risks currently live, item 1): $(grep -i jnu "${CFG}")"
fi

# lib/app holds exactly what was staged, byte for byte.
(cd -- "${INPUT}" && ls -1 -- *.jar) > "${LOGS}/staged-jars.txt"
(cd -- "${APPDIR}/lib/app" && ls -1 -- *.jar) > "${LOGS}/image-jars.txt"
cmp -s "${LOGS}/staged-jars.txt" "${LOGS}/image-jars.txt" \
    || die "lib/app's JARs differ from the staged set: $(diff "${LOGS}/staged-jars.txt" "${LOGS}/image-jars.txt" | tr '\n' ' ')"
while IFS= read -r jar; do
    cmp -s -- "${INPUT}/${jar}" "${APPDIR}/lib/app/${jar}" || die "lib/app/${jar} differs from the staged copy"
done < "${LOGS}/staged-jars.txt"
echo "lib/app holds the $(wc -l < "${LOGS}/staged-jars.txt") staged JARs, byte-identical"

# ---------------------------------------------------------------------------
note "6. the archive"
ARCHIVE_NAME="CometGUI-${VERSION}-linux-${ARCH}.tar.gz"
ARCHIVE="${OUT}/${ARCHIVE_NAME}"
T4="$(now)"
tar --sort=name --owner=0 --group=0 --numeric-owner -C "${IMAGE_DEST}" -cf - CometGUI \
    | gzip -n -6 > "${ARCHIVE}"
(cd -- "${OUT}" && sha256sum -- "${ARCHIVE_NAME}" > "${ARCHIVE_NAME}.sha256")
T5="$(now)"
TAR_SECONDS="$(elapsed "${T4}" "${T5}")"
(cd -- "${OUT}" && sha256sum -c --quiet -- "${ARCHIVE_NAME}.sha256") || die "the archive does not match its own checksum"
tar -tzf "${ARCHIVE}" > "${LOGS}/archive-listing.txt" || die "the archive cannot be listed"
for member in CometGUI/bin/CometGUI CometGUI/lib/runtime/bin/java CometGUI/lib/app/CometGUI.cfg \
    "CometGUI/lib/app/${MAIN_JAR}" CometGUI/lib/runtime/lib/server/libjvm.so; do
    grep -qx -- "${member}" "${LOGS}/archive-listing.txt" || die "the archive lacks ${member}"
done
FILES_IN_IMAGE="$(find "${APPDIR}" ! -type d | wc -l)"
FILES_IN_ARCHIVE="$(grep -cv '/$' "${LOGS}/archive-listing.txt")"
[ "${FILES_IN_IMAGE}" -eq "${FILES_IN_ARCHIVE}" ] \
    || die "the image has ${FILES_IN_IMAGE} entries (files and links) and the archive ${FILES_IN_ARCHIVE}"

IMAGE_BYTES="$(du -sb "${APPDIR}" | cut -f1)"
RUNTIME_BYTES="$(du -sb "${RUNTIME}" | cut -f1)"
ARCHIVE_BYTES="$(stat -c %s "${ARCHIVE}")"
{
    echo "CometGUI packaged build -- scripts/package/build-app-image.sh"
    echo "built:            $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "commit:           $(git -C "${ROOT}" rev-parse HEAD 2>/dev/null || echo unknown)$(git -C "${ROOT}" diff --quiet 2>/dev/null || echo ' (working tree modified)')"
    echo "version:          ${VERSION} (jpackage --app-version ${APP_VERSION})"
    echo "jdk:              $("${JAVA_HOME}/bin/java" -version 2>&1 | sed -n "1,1p")"
    echo "jpackage:         $(cat "${OUT}/jpackage-command.txt")"
    echo "jpackage values:  $(grep -v '^#' "${BUILD_ARGS}" | paste -sd' ' -)"
    echo "jlink:            ${JLINK_LINE}"
    echo "root modules:     ${MODULES}"
    echo "app needs (jdeps): ${APP_NEEDS}"
    echo "runtime modules:  $(printf '%s' "${RUNTIME_MODULES}" | paste -sd' ' -)"
    echo "java.instrument:  $(printf '%s\n' "${RUNTIME_MODULES}" | grep -qx java.instrument && echo present || echo absent)"
    echo "jdk.attach:       $(printf '%s\n' "${RUNTIME_MODULES}" | grep -qx jdk.attach && echo present || echo absent)"
    echo "runtime bin:      $(ls "${RUNTIME}/bin" | paste -sd' ' -)"
    echo "image:            ${APPDIR} (${IMAGE_BYTES} bytes, runtime ${RUNTIME_BYTES} bytes, ${FILES_IN_IMAGE} files and links)"
    echo "archive:          ${ARCHIVE} (${ARCHIVE_BYTES} bytes)"
    echo "sha256:           $(cut -d' ' -f1 "${ARCHIVE}.sha256")"
    echo "seconds:          maven ${MAVEN_SECONDS}$([ "${SKIP_MAVEN}" -eq 1 ] && echo ' (skipped)'), jpackage ${JPACKAGE_SECONDS}, tar+gzip+sha256 ${TAR_SECONDS}"
} > "${OUT}/build-info.txt"

note "built"
cat "${OUT}/build-info.txt"
echo
echo "build-app-image.sh: PASSED -- ${ARCHIVE_NAME}"
