#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 5: the jpackage proof (exit gate item 4).
#
#   bash scripts/feasibility/jpackage-proof.sh
#
# Requires scripts/feasibility/install-toolchain.sh to have been run first.
#
# Builds the throwaway spike in scripts/feasibility/jpackage-spike/, packages
# it with jpackage, then LAUNCHES the produced bundle and checks what it
# printed. Exit code 0 from a launcher proves nothing, so the spike asserts
# the pinned java.version, the pinned vendor, that java.home is the runtime
# inside the app-image, that its own jar came from that image, and that the
# JavaFX modules resolve. The launch is also repeated with an emptied
# environment (no JAVA_HOME, no JDK on PATH) to prove the image does not lean
# on an external JDK.
#
# The .deb and .rpm targets are attempted too, and their failures captured
# verbatim: nothing may be installed on this host, so what they need is itself
# a finding for Phase 01 and Phase 16.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
BUILD="${ROOT}/_build/jpackage-spike"
LOGS="${BUILD}/logs"
APP_NAME="ToolchainProbe"

[ -f "${ROOT}/tools/env.sh" ] || {
    echo "FATAL: ${ROOT}/tools/env.sh missing. Run scripts/feasibility/install-toolchain.sh first." >&2
    exit 1
}
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"

log() { printf '\n[jpackage-proof] %s\n' "$*"; }
run() {                      # run <logfile> <command...> -- tee to log, keep rc
    local lf="$1"; shift
    printf '$ %s\n' "$*" | tee "${lf}"
    set +e
    "$@" > >(tee -a "${lf}") 2> >(tee -a "${lf}" >&2)
    local rc=$?
    set -e
    printf '[exit code: %s]\n' "${rc}" | tee -a "${lf}"
    return "${rc}"
}

rm -rf -- "${BUILD}"
mkdir -p -- "${BUILD}/classes" "${BUILD}/input" "${LOGS}" "${BUILD}/dest"

log "toolchain"
echo "JAVA_HOME  = ${JAVA_HOME}"
echo "javac      = $(javac -version 2>&1)"
echo "jpackage   = $(jpackage --version)"

log "1. compile the throwaway spike"
javac -d "${BUILD}/classes" "${SCRIPT_DIR}/jpackage-spike/ToolchainProbe.java"
jar --create --file "${BUILD}/input/toolchain-probe.jar" \
    --main-class "${APP_NAME}" -C "${BUILD}/classes" .
ls -l "${BUILD}/input/toolchain-probe.jar"

log "2. sanity: run the plain executable jar on the toolchain JDK"
# probe.requireBundle=false because this run is deliberately NOT from a bundle.
run "${LOGS}/01-plain-jar.txt" \
    java -Dprobe.requireBundle=false -jar "${BUILD}/input/toolchain-probe.jar"

log "3. jpackage --type app-image"
JPACKAGE_APPIMAGE_ARGS=(
    --type app-image
    --name "${APP_NAME}"
    --app-version 0.0.1
    --input "${BUILD}/input"
    --main-jar toolchain-probe.jar
    --main-class "${APP_NAME}"
    --dest "${BUILD}/dest"
    --add-modules java.base,java.logging,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web
    --verbose
)
run "${LOGS}/02-jpackage-app-image.txt" jpackage "${JPACKAGE_APPIMAGE_ARGS[@]}"

APPDIR="${BUILD}/dest/${APP_NAME}"
[ -x "${APPDIR}/bin/${APP_NAME}" ] || { echo "FATAL: launcher not produced" >&2; exit 1; }

log "4. the bundle carries its own runtime"
{
    echo "\$ find ${APPDIR} -maxdepth 2 -mindepth 1 | sort"
    find "${APPDIR}" -maxdepth 2 -mindepth 1 | sort
    echo
    echo "\$ ls -l ${APPDIR}/lib/runtime/lib/modules ${APPDIR}/lib/runtime/lib/server/libjvm.so"
    ls -l "${APPDIR}/lib/runtime/lib/modules" "${APPDIR}/lib/runtime/lib/server/libjvm.so"
    echo
    echo "# JavaFX native libraries inside the bundled runtime:"
    echo "\$ ls ${APPDIR}/lib/runtime/lib | grep -E '^lib(glass|javafx|prism|decora|fxplugins)'"
    ls "${APPDIR}/lib/runtime/lib" | grep -E '^lib(glass|javafx|prism|decora|fxplugins)'
    echo
    echo "# jpackage runs jlink with --strip-native-commands by default, so the"
    echo "# bundled runtime has NO bin/java launcher:"
    echo "\$ ls ${APPDIR}/lib/runtime/bin 2>&1"
    ls "${APPDIR}/lib/runtime/bin" 2>&1 || true
    echo
    echo "\$ cat ${APPDIR}/lib/runtime/release"
    cat "${APPDIR}/lib/runtime/release"
    echo
    echo "\$ du -sh ${APPDIR}"
    du -sh "${APPDIR}"
} 2>&1 | tee "${LOGS}/03-bundle-contents.txt"

log "5. LAUNCH the bundled application"
run "${LOGS}/04-launch.txt" "${APPDIR}/bin/${APP_NAME}"

log "6. LAUNCH again with JAVA_HOME unset and no JDK on PATH"
run "${LOGS}/05-launch-clean-env.txt" \
    env -i PATH=/usr/bin:/bin HOME="${HOME}" "${APPDIR}/bin/${APP_NAME}"

log "7. attempt the installer targets (expected to need host tooling)"
for t in deb rpm; do
    rc=0
    run "${LOGS}/06-jpackage-${t}.txt" jpackage \
        --type "${t}" \
        --name "${APP_NAME}" \
        --app-version 0.0.1 \
        --app-image "${APPDIR}" \
        --dest "${BUILD}/dest" || rc=$?
    echo "[${t} attempt exit code: ${rc}] (0 would mean the installer was built)"
done

log "8. variant: keep the runtime's own bin/java (needed to run external JARs)"
# jpackage's default jlink invocation includes --strip-native-commands. Supplying
# --jlink-options replaces those defaults, so the bundled runtime keeps bin/java.
# CometGUI has to run the Limelight converter, which is distributed as a JAR, so
# whether the shipped runtime has a java launcher is a product-level question.
run "${LOGS}/07-jpackage-app-image-with-java.txt" jpackage \
    --type app-image \
    --name "${APP_NAME}WithJava" \
    --app-version 0.0.1 \
    --input "${BUILD}/input" \
    --main-jar toolchain-probe.jar \
    --main-class "${APP_NAME}" \
    --dest "${BUILD}/dest" \
    --add-modules java.base,java.logging,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web \
    --jlink-options "--strip-debug --no-man-pages --no-header-files"
{
    APPDIR2="${BUILD}/dest/${APP_NAME}WithJava"
    echo "\$ ls ${APPDIR2}/lib/runtime/bin"
    ls "${APPDIR2}/lib/runtime/bin"
    echo
    echo "\$ ${APPDIR2}/lib/runtime/bin/java -version"
    "${APPDIR2}/lib/runtime/bin/java" -version 2>&1
    echo
    echo "\$ ${APPDIR2}/lib/runtime/bin/java --list-modules | grep javafx"
    "${APPDIR2}/lib/runtime/bin/java" --list-modules | grep javafx
    echo
    echo "\$ du -sh ${APPDIR2}"
    du -sh "${APPDIR2}"
} 2>&1 | tee "${LOGS}/08-runtime-with-java.txt"

log "done. logs in ${LOGS}"
