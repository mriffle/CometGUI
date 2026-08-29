#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 7: JavaFX startup smoke test and GUI
# automation spike.
#
#   bash scripts/feasibility/javafx-smoke.sh [--no-maven] [--no-negative]
#
# Re-runnable from scratch.  It bootstraps everything it needs into tools/
# (verifying a pinned SHA-256 for every fetched file), proves that a real
# JavaFX Application starts headless on the pinned JDK 25.0.4.1+1 /
# JavaFX 25.0.4+1 pair, proves the harness genuinely fails when an expected
# value is wrong, and then runs the TestFX and fallback spikes through Maven --
# including a deliberately broken assertion that must fail.
#
# WHY ANY OF THIS IS NEEDED (the findings this script re-derives every run):
#
#   * The pinned Liberica JDK's javafx.graphics contains NO Monocle: the only
#     Glass platform packages in the runtime image are com.sun.glass.ui.gtk and
#     com.sun.glass.ui.delegate.  Step 1 asserts that, so a future JDK that
#     does ship Monocle is noticed rather than silently papered over.
#   * This host has no libX11, so the default (GTK) Glass platform cannot even
#     load libglass.so.  Headless is not a preference here, it is the only
#     option.
#   * Monocle therefore comes from org.testfx:openjfx-monocle on Maven Central
#     and is injected with --patch-module, because javafx.graphics is a named
#     system module whose PlatformFactory lookup cannot see the class path.
#   * JavaFX needs a real font stack for anything with a Control in it (the
#     first Node in a Scene initialises CssStyleHelper, which calls
#     Font.getDefault()).  This host has no libfreetype, no fontconfig, no
#     pango and no font files at all, so those are fetched from the Debian 12
#     archive and extracted project-locally with the work-unit-3 .deb
#     extractor.  Nothing is installed on the host.
#
# Everything fetched lands under tools/ (gitignored); everything built lands
# under _build/ (gitignored).  No apt, no sudo, no host-level pip, no write
# outside /workspace, nothing written to ~/.m2.
#
# Exit status: 0 only if every stage produced the expected output.  Exit code 0
# from the JVM proves nothing here -- a JavaFX application that fails to start
# can still exit 0 -- so every stage checks content, not just status.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

SPIKE_DIR="${SCRIPT_DIR}/gui-spike"
BUILD="${ROOT}/_build/gui-spike"
CLASSES="${BUILD}/classes"
M2REPO="${ROOT}/_build/m2repo"

MONOCLE_VERSION="21.0.2"
MONOCLE_DIR="${ROOT}/tools/openjfx-monocle-${MONOCLE_VERSION}"
MONOCLE_JAR="${MONOCLE_DIR}/openjfx-monocle-${MONOCLE_VERSION}.jar"
MONOCLE_URL="https://repo1.maven.org/maven2/org/testfx/openjfx-monocle/${MONOCLE_VERSION}/openjfx-monocle-${MONOCLE_VERSION}.jar"
MONOCLE_SHA256="3d0b0c186a9f495aa4e3d058c612b2a9cf44a97ffbcecd75d441aed8263fac50"

FONTSTACK_DIR="${ROOT}/tools/fontstack-bookworm-20260829"
DEB_DIR="${FONTSTACK_DIR}/debs"
SYSROOT="${FONTSTACK_DIR}/root"
DEB_BASE="https://deb.debian.org/debian/pool/main"

RUN_MAVEN=1
RUN_NEGATIVE=1
for arg in "$@"; do
    case "${arg}" in
        --no-maven)    RUN_MAVEN=0 ;;
        --no-negative) RUN_NEGATIVE=0 ;;
        *) echo "usage: javafx-smoke.sh [--no-maven] [--no-negative]" >&2; exit 2 ;;
    esac
done

STAGE_RESULTS=()
note()  { printf '\n=== %s\n' "$1"; }
pass()  { printf '[stage PASS] %s\n' "$1"; STAGE_RESULTS+=("PASS  $1"); }
fail()  { printf '[stage FAIL] %s\n' "$1" >&2; STAGE_RESULTS+=("FAIL  $1"); FAILED=1; }
die()   { printf 'javafx-smoke.sh: %s\n' "$1" >&2; exit 2; }

FAILED=0

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh missing -- run scripts/feasibility/install-toolchain.sh first"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
[ -x "${JAVA_HOME}/bin/java" ] || die "no java at ${JAVA_HOME}/bin/java"

# ---------------------------------------------------------------------------
# fetch_verify URL DEST SHA256
# ---------------------------------------------------------------------------
fetch_verify() {
    local url="$1" dest="$2" want="$3" got
    if [ ! -f "${dest}" ]; then
        printf 'fetching %s\n' "${url}"
        curl -fsSL --retry 3 -o "${dest}.part" "${url}" || die "download failed: ${url}"
        mv -- "${dest}.part" "${dest}"
    fi
    got="$(sha256sum -- "${dest}" | cut -d' ' -f1)"
    if [ "${got}" != "${want}" ]; then
        die "SHA-256 mismatch for ${dest}: got ${got}, pinned ${want}"
    fi
    printf 'verified %-52s %s\n' "$(basename -- "${dest}")" "${got}"
}

# ---------------------------------------------------------------------------
# Stage 0 -- record the environment this evidence was produced in
# ---------------------------------------------------------------------------
note "Stage 0: environment"
printf 'date            = %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf 'JAVA_HOME       = %s\n' "${JAVA_HOME}"
printf 'java -version   = %s\n' "$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1)"
printf 'JAVA_RUNTIME_VERSION = %s\n' "$(grep -E '^JAVA_RUNTIME_VERSION' "${JAVA_HOME}/release" || echo '(absent)')"
printf 'javafx.properties:\n'; sed 's/^/    /' "${JAVA_HOME}/lib/javafx.properties"
printf 'DISPLAY         = [%s]\n' "${DISPLAY:-}"
printf '/tmp/.X11-unix  = %s\n' "$([ -d /tmp/.X11-unix ] && echo present || echo absent)"
printf 'Xvfb on PATH    = %s\n' "$(command -v Xvfb || echo absent)"
printf 'xvfb-run        = %s\n' "$(command -v xvfb-run || echo absent)"
printf 'libX11.so.6     = %s\n' "$(find /lib /usr/lib -name 'libX11.so.6' 2>/dev/null | head -1 || true)"

# ---------------------------------------------------------------------------
# Stage 1 -- the JDK has no Monocle, and no Glass platform that works here
# ---------------------------------------------------------------------------
note "Stage 1: Glass platforms present in the pinned JDK"
mkdir -p -- "${BUILD}"
"${JAVA_HOME}/bin/jimage" list "${JAVA_HOME}/lib/modules" > "${BUILD}/jimage-list.txt"
glass_pkgs="$(grep -oE 'com/sun/glass/ui/[a-z0-9]+/' "${BUILD}/jimage-list.txt" | sort -u | tr '\n' ' ')"
monocle_hits="$(grep -ci 'monocle' "${BUILD}/jimage-list.txt" || true)"
printf 'glass platform packages in javafx.graphics : %s\n' "${glass_pkgs:-(none)}"
printf 'entries matching "monocle" in the runtime image: %s\n' "${monocle_hits}"
if [ "${monocle_hits}" -eq 0 ]; then
    pass "Stage 1: the pinned JDK ships no Monocle (finding confirmed)"
else
    fail "Stage 1: this JDK DOES contain Monocle (${monocle_hits} entries) -- the document's central finding is stale, re-check before trusting it"
fi

# ---------------------------------------------------------------------------
# Stage 2 -- bootstrap Monocle and the font stack into tools/
# ---------------------------------------------------------------------------
note "Stage 2: bootstrap tools/ (Monocle + Debian 12 font stack)"
mkdir -p -- "${MONOCLE_DIR}" "${DEB_DIR}" "${SYSROOT}"
fetch_verify "${MONOCLE_URL}" "${MONOCLE_JAR}" "${MONOCLE_SHA256}"

# package|pool path|sha256   (Debian 12 "bookworm", amd64; glibc 2.36 host)
DEBS=(
"libfreetype6_2.12.1+dfsg-5+deb12u4_amd64.deb|f/freetype|8043e479f73f29992d652e3f9dfe8b17f9780c7ea6330afe379ec5f9f188ac44"
"libpng16-16_1.6.39-2+deb12u5_amd64.deb|libp/libpng1.6|a56d64bfaa9da12aafb83347909e62e6fd5fd251e6b34c194065911a30359978"
"libfontconfig1_2.14.1-4_amd64.deb|f/fontconfig|16ee38d374e064f534116dc442b086ef26f9831f1c0af7e5fb4fe4512e700649"
"fontconfig-config_2.14.1-4_amd64.deb|f/fontconfig|281c66e46b95f045a0282a6c7a03b33de0e9a08d016897a759aaf4a04adfddbe"
"fonts-dejavu-core_2.37-6_all.deb|f/fonts-dejavu|8892669e51aab4dc56682c8e39d8ddb7d70fad83c369344e1e240bf3ca22bb76"
"libglib2.0-0_2.74.6-2+deb12u9_amd64.deb|g/glib2.0|7ff85197685d89e150e342b29a59aab1beee400050ba7da73de81cd999ffee5a"
"libharfbuzz0b_6.0.0+dfsg-3_amd64.deb|h/harfbuzz|bfce132b7ee67b9c2d2166075b1936a25c8cc6866b6a049f99b8e94baa916e71"
"libgraphite2-3_1.3.14-1+deb12u1_amd64.deb|g/graphite2|c19a7f6ba9298db7eef041ae27b08985f2c02009e418063f8bccdb5bc5e858dc"
"libpango-1.0-0_1.50.12+ds-1_amd64.deb|p/pango1.0|851720de07441ae6bb6a7f51fc0f2edb4db7aa6f25b5bf1bf7b72dcab8947b7f"
"libpangoft2-1.0-0_1.50.12+ds-1_amd64.deb|p/pango1.0|78da3f494109f6e7a39c4626aaae7571c600c5854cecda0bc0c902224986a63b"
"libfribidi0_1.0.8-2.1_amd64.deb|f/fribidi|87fce56627aab7b2968501d370aa3ed6d1c792119efa765e71a690bdfe570e62"
"libthai0_0.1.29-1_amd64.deb|libt/libthai|37cd66bef851ea0e4af807797ba3ad14d43226f7c4954c1d0a19478e11815bae"
"libthai-data_0.1.29-1_all.deb|libt/libthai|eed65a75269411e47d7b393d82bc30471da5c499e9f311abbfd8c54ca1a42d9e"
"libdatrie1_0.2.13-2+b1_amd64.deb|libd/libdatrie|f021f193384929989b2dfd19f606a8cebe54b5f209fe387fc40683e810e01ebe"
)

for spec in "${DEBS[@]}"; do
    IFS='|' read -r name pool sha <<< "${spec}"
    fetch_verify "${DEB_BASE}/${pool}/${name}" "${DEB_DIR}/${name}" "${sha}"
done

for spec in "${DEBS[@]}"; do
    IFS='|' read -r name _ _ <<< "${spec}"
    python3 "${SCRIPT_DIR}/extract_deb.py" --dest "${SYSROOT}" "${DEB_DIR}/${name}" >/dev/null \
        || die "extraction failed for ${name}"
done
printf 'extracted %s package(s) into %s\n' "${#DEBS[@]}" "${SYSROOT}"

# Exit code 0 proves nothing: check the files that actually matter exist.
missing=0
for f in \
    "${SYSROOT}/usr/lib/x86_64-linux-gnu/libfreetype.so.6" \
    "${SYSROOT}/usr/lib/x86_64-linux-gnu/libfontconfig.so.1" \
    "${SYSROOT}/usr/lib/x86_64-linux-gnu/libpangoft2-1.0.so.0" \
    "${SYSROOT}/usr/lib/x86_64-linux-gnu/libharfbuzz.so.0" \
    "${SYSROOT}/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf" \
    "${SYSROOT}/etc/fonts/fonts.conf" ; do
    [ -s "${f}" ] || { printf 'missing after extraction: %s\n' "${f}" >&2; missing=1; }
done
if [ "${missing}" -eq 0 ]; then
    pass "Stage 2: Monocle jar and font stack present and checksum-verified"
else
    fail "Stage 2: the extracted font stack is incomplete"
fi

# The environment every JVM below needs.  LD_LIBRARY_PATH points at the
# project-local sysroot; XDG_DATA_HOME makes fontconfig's "xdg" font directory
# resolve to the DejaVu fonts we extracted; XDG_CACHE_HOME keeps fontconfig's
# cache inside _build/ rather than in the user's home directory.
export LD_LIBRARY_PATH="${SYSROOT}/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"
export FONTCONFIG_PATH="${SYSROOT}/etc/fonts"
export XDG_DATA_HOME="${SYSROOT}/usr/share"
export XDG_CACHE_HOME="${BUILD}/fccache"
mkdir -p -- "${XDG_CACHE_HOME}"

JVM_ARGS=(
    "--patch-module" "javafx.graphics=${MONOCLE_JAR}"
    "--add-exports" "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED"
    "--add-exports" "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"
    "--enable-native-access=javafx.graphics"
    "-Dglass.platform=Monocle"
    "-Dmonocle.platform=Headless"
    "-Dprism.order=sw"
    "-Djava.awt.headless=true"
)

# ---------------------------------------------------------------------------
# Stage 3 -- compile the spike
# ---------------------------------------------------------------------------
note "Stage 3: compile the spike sources"
rm -rf -- "${CLASSES}"
mkdir -p -- "${CLASSES}"
"${JAVA_HOME}/bin/javac" \
    --add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
    --add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    -d "${CLASSES}" \
    "${SPIKE_DIR}"/src/main/java/cometgui/spike/*.java
if [ -s "${CLASSES}/cometgui/spike/HeadlessSmoke.class" ]; then
    pass "Stage 3: spike compiled"
else
    fail "Stage 3: javac exited 0 but produced no HeadlessSmoke.class"
fi

# ---------------------------------------------------------------------------
# Stage 4 -- the headless startup proof
# ---------------------------------------------------------------------------
note "Stage 4: JavaFX Application startup, headless"
set +e
"${JAVA_HOME}/bin/java" "${JVM_ARGS[@]}" -cp "${CLASSES}" cometgui.spike.HeadlessSmoke \
    > "${BUILD}/headless-smoke.out" 2>&1
smoke_status=$?
set -e
cat -- "${BUILD}/headless-smoke.out"
n_pass="$(grep -c '\[PASS\]' "${BUILD}/headless-smoke.out" || true)"
n_fail="$(grep -c '\[FAIL\]' "${BUILD}/headless-smoke.out" || true)"
if [ "${smoke_status}" -eq 0 ] \
   && grep -q '^HEADLESS SMOKE: PASS' "${BUILD}/headless-smoke.out" \
   && [ "${n_fail}" -eq 0 ] && [ "${n_pass}" -ge 14 ]; then
    pass "Stage 4: JavaFX Application started headless; ${n_pass} checks passed, ${n_fail} failed"
else
    fail "Stage 4: headless startup did not produce the expected evidence (exit ${smoke_status}, ${n_pass} PASS, ${n_fail} FAIL)"
fi

# ---------------------------------------------------------------------------
# Stage 5 -- the same harness must FAIL when an expectation is wrong
# ---------------------------------------------------------------------------
if [ "${RUN_NEGATIVE}" -eq 1 ]; then
    note "Stage 5: negative control -- the smoke must fail on a wrong expectation"
    set +e
    "${JAVA_HOME}/bin/java" "${JVM_ARGS[@]}" \
        -Dsmoke.expect.fxVersion=0.0.0-deliberately-wrong \
        -cp "${CLASSES}" cometgui.spike.HeadlessSmoke \
        > "${BUILD}/headless-smoke-negative.out" 2>&1
    neg_status=$?
    set -e
    grep -E '^\s+\[(PASS|FAIL)\] javafx.runtime.version|^HEADLESS SMOKE' \
        "${BUILD}/headless-smoke-negative.out" || true
    if [ "${neg_status}" -ne 0 ] \
       && grep -q '^HEADLESS SMOKE: FAIL' "${BUILD}/headless-smoke-negative.out"; then
        pass "Stage 5: harness failed as required (exit ${neg_status}) -- it is falsifiable"
    else
        fail "Stage 5: harness reported success on a wrong expectation (exit ${neg_status}) -- it proves nothing"
    fi
fi

# ---------------------------------------------------------------------------
# Stage 6 -- TestFX and the fallback, through Maven
# ---------------------------------------------------------------------------
if [ "${RUN_MAVEN}" -eq 1 ]; then
    note "Stage 6: TestFX and fallback spikes (Maven)"
    command -v mvn >/dev/null || die "no mvn on PATH after sourcing tools/env.sh"
    mkdir -p -- "${M2REPO}"
    set +e
    ( cd "${SPIKE_DIR}" && mvn -B -Dmaven.repo.local="${M2REPO}" test ) \
        > "${BUILD}/maven-test.out" 2>&1
    mvn_status=$?
    set -e
    grep -E 'Tests run:|BUILD ' "${BUILD}/maven-test.out" || true
    if [ "${mvn_status}" -eq 0 ] \
       && grep -qE '^\[INFO\] Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: 0$' "${BUILD}/maven-test.out"; then
        pass "Stage 6: TestFX and fallback tests passed (full log ${BUILD}/maven-test.out)"
    else
        fail "Stage 6: Maven test run did not pass (exit ${mvn_status}, log ${BUILD}/maven-test.out)"
    fi

    # ---------------------------------------------------------------------
    # Stage 7 -- the TestFX assertion must genuinely bite
    # ---------------------------------------------------------------------
    if [ "${RUN_NEGATIVE}" -eq 1 ]; then
        note "Stage 7: negative control -- break the TestFX assertion, it must fail"
        NEG="${BUILD}/negative"
        rm -rf -- "${NEG}"
        mkdir -p -- "${NEG}"
        cp -r -- "${SPIKE_DIR}/pom.xml" "${SPIKE_DIR}/src" "${NEG}/"
        # Keep the negative build's output inside itself.
        sed -i 's|<cometgui.buildDir>.*</cometgui.buildDir>|<cometgui.buildDir>${project.basedir}/target</cometgui.buildDir>|' \
            "${NEG}/pom.xml"
        # The ONLY change: the expected value the button handler must produce.
        sed -i 's/assertEquals("=COMET", output.getText(),/assertEquals("=NOT-WHAT-THE-HANDLER-PRODUCES", output.getText(),/' \
            "${NEG}/src/test/java/cometgui/spike/TestFxSpikeTest.java"
        grep -n 'NOT-WHAT-THE-HANDLER-PRODUCES' "${NEG}/src/test/java/cometgui/spike/TestFxSpikeTest.java" \
            || die "Stage 7: could not inject the broken assertion -- the test source must have changed"
        set +e
        ( cd "${NEG}" && mvn -B -Dmaven.repo.local="${M2REPO}" -Dtest=TestFxSpikeTest \
              -Dsurefire.failIfNoSpecifiedTests=false test ) > "${BUILD}/maven-negative.out" 2>&1
        neg_mvn_status=$?
        set -e
        grep -E 'Tests run:|expected:|BUILD ' "${BUILD}/maven-negative.out" || true
        if [ "${neg_mvn_status}" -ne 0 ] \
           && grep -q 'expected: <=NOT-WHAT-THE-HANDLER-PRODUCES> but was: <=COMET>' "${BUILD}/maven-negative.out"; then
            pass "Stage 7: the TestFX test failed on the broken assertion, for the right reason"
        else
            fail "Stage 7: the TestFX test did not fail on a broken assertion (exit ${neg_mvn_status}) -- it asserts nothing"
        fi
    fi
fi

# ---------------------------------------------------------------------------
note "Summary"
for r in "${STAGE_RESULTS[@]}"; do printf '  %s\n' "${r}"; done
if [ "${FAILED}" -eq 0 ]; then
    printf '\njavafx-smoke.sh: OK -- every stage produced the expected evidence.\n'
    exit 0
fi
printf '\njavafx-smoke.sh: FAILED -- see the stages marked FAIL above.\n' >&2
exit 1
