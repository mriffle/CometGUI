#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 7: the HEADED half of the GUI automation
# spike.
#
#   bash scripts/feasibility/javafx-headed-xvfb.sh [--no-maven]
#
# Run scripts/feasibility/javafx-smoke.sh FIRST: this script reuses the font
# stack that one bootstraps, and the classes it compiles.
#
# This host has no X display, no Xvfb, no X libraries and no GTK, and nothing
# may be installed on it.  A headed run is nevertheless possible entirely
# project-locally:
#
#   1. compute the Debian 12 dependency closure of xvfb + x11-xkb-utils +
#      xkb-data + libgtk-3-0 + libxtst6 (139 packages; the manifest is
#      committed at gui-spike/headed-x11-closure.tsv, and gui-spike/
#      deb-closure.py regenerates it from the archive index);
#   2. fetch each package, verify its SHA-256 against that manifest, and
#      extract the payload with work unit 3's extract_deb.py into
#      tools/x11-bookworm-20260829/root/ -- no dpkg, no maintainer scripts,
#      no root;
#   3. work around the one thing that cannot be relocated: Xvfb runs xkbcomp
#      at the absolute path baked in at build time, /usr/bin/xkbcomp.  /usr/bin
#      is not writable (and writing there would be a host install), and user
#      namespaces are not permitted here, so a bind mount is out.  A twenty-line
#      LD_PRELOAD shim rewrites that path inside the execl() the server uses;
#      see gui-spike/xkbcomp-path-shim.c;
#   4. start Xvfb on a private display, run the JavaFX smoke against it with
#      the GTK Glass platform (no Monocle), and run the TestFX suite with
#      -Pheaded.
#
# Everything lands under tools/ and _build/, both gitignored.  Nothing is
# installed on the host: no apt, no sudo, no host-level pip.
#
# Exit status 0 only if every stage produced the expected output.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

SPIKE_DIR="${SCRIPT_DIR}/gui-spike"
MANIFEST="${SPIKE_DIR}/headed-x11-closure.tsv"
BUILD="${ROOT}/_build/gui-spike"
CLASSES="${BUILD}/classes"
M2REPO="${ROOT}/_build/m2repo"

X11_DIR="${ROOT}/tools/x11-bookworm-20260829"
X11_DEBS="${X11_DIR}/debs"
X11_ROOT="${X11_DIR}/root"
FONT_ROOT="${ROOT}/tools/fontstack-bookworm-20260829/root"

RUN_MAVEN=1
for arg in "$@"; do
    case "${arg}" in
        --no-maven) RUN_MAVEN=0 ;;
        *) echo "usage: javafx-headed-xvfb.sh [--no-maven]" >&2; exit 2 ;;
    esac
done

STAGE_RESULTS=()
FAILED=0
note() { printf '\n=== %s\n' "$1"; }
pass() { printf '[stage PASS] %s\n' "$1"; STAGE_RESULTS+=("PASS  $1"); }
fail() { printf '[stage FAIL] %s\n' "$1" >&2; STAGE_RESULTS+=("FAIL  $1"); FAILED=1; }
die()  { printf 'javafx-headed-xvfb.sh: %s\n' "$1" >&2; exit 2; }
# A delay primitive that is not sleep(1).
pause() { timeout "$1" tail -f /dev/null >/dev/null 2>&1 || true; }

XVFB_PID=""
DISPLAY_NUM=""
cleanup() {
    if [ -n "${XVFB_PID}" ] && kill -0 "${XVFB_PID}" 2>/dev/null; then
        kill "${XVFB_PID}" 2>/dev/null || true
    fi
    [ -n "${DISPLAY_NUM}" ] && rm -f "/tmp/.X${DISPLAY_NUM}-lock" 2>/dev/null || true
}
trap cleanup EXIT

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh missing -- run scripts/feasibility/install-toolchain.sh first"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
[ -f "${MANIFEST}" ] || die "missing provenance manifest ${MANIFEST}"
[ -s "${FONT_ROOT}/usr/lib/x86_64-linux-gnu/libfreetype.so.6" ] \
    || die "font stack absent -- run scripts/feasibility/javafx-smoke.sh first"
[ -s "${CLASSES}/cometgui/spike/HeadlessSmoke.class" ] \
    || die "spike not compiled -- run scripts/feasibility/javafx-smoke.sh first"

# ---------------------------------------------------------------------------
note "Stage H1: fetch and verify the X11 + GTK3 closure"
mkdir -p -- "${X11_DEBS}" "${X11_ROOT}"
n_pkgs=0
while IFS=$'\t' read -r name version url sha size rest; do
    case "${name}" in ''|'#'*) continue ;; esac
    file="${X11_DEBS}/$(basename -- "${url}")"
    if [ ! -f "${file}" ]; then
        curl -fsSL --retry 3 -o "${file}.part" "${url}" || die "download failed: ${url}"
        mv -- "${file}.part" "${file}"
    fi
    got="$(sha256sum -- "${file}" | cut -d' ' -f1)"
    [ "${got}" = "${sha}" ] || die "SHA-256 mismatch for ${name}: got ${got}, manifest ${sha}"
    n_pkgs=$((n_pkgs + 1))
done < "${MANIFEST}"
printf 'verified %s package(s) against %s\n' "${n_pkgs}" "${MANIFEST}"

while IFS=$'\t' read -r name version url sha size rest; do
    case "${name}" in ''|'#'*) continue ;; esac
    python3 "${SCRIPT_DIR}/extract_deb.py" --dest "${X11_ROOT}" \
        "${X11_DEBS}/$(basename -- "${url}")" >/dev/null || die "extraction failed: ${name}"
done < "${MANIFEST}"

if [ -x "${X11_ROOT}/usr/bin/Xvfb" ] && [ -x "${X11_ROOT}/usr/bin/xkbcomp" ] \
   && [ -s "${X11_ROOT}/usr/lib/x86_64-linux-gnu/libgtk-3.so.0" ]; then
    pass "Stage H1: ${n_pkgs} packages verified and extracted (Xvfb, xkbcomp, libgtk-3 present)"
else
    fail "Stage H1: the extracted X11 tree is missing Xvfb, xkbcomp or libgtk-3"
fi

export LD_LIBRARY_PATH="${X11_ROOT}/usr/lib/x86_64-linux-gnu:${X11_ROOT}/lib/x86_64-linux-gnu:${FONT_ROOT}/usr/lib/x86_64-linux-gnu"
export FONTCONFIG_PATH="${FONT_ROOT}/etc/fonts"
export XDG_DATA_HOME="${FONT_ROOT}/usr/share"
export XDG_CACHE_HOME="${BUILD}/fccache"
mkdir -p -- "${XDG_CACHE_HOME}"

unmet="$(ldd "${X11_ROOT}/usr/bin/Xvfb" 2>/dev/null | grep -c 'not found' || true)"
unmet_gtk="$(ldd "${X11_ROOT}/usr/lib/x86_64-linux-gnu/libgtk-3.so.0" 2>/dev/null | grep -c 'not found' || true)"
printf 'unresolved shared libraries: Xvfb=%s libgtk-3=%s\n' "${unmet}" "${unmet_gtk}"

# ---------------------------------------------------------------------------
note "Stage H2: build the xkbcomp path shim"
command -v gcc >/dev/null || die "no gcc; the headed run cannot start Xvfb without the shim"
SHIM="${BUILD}/xkbcomp-path-shim.so"
gcc -shared -fPIC -O1 -o "${SHIM}" "${SPIKE_DIR}/xkbcomp-path-shim.c"
if [ -s "${SHIM}" ]; then
    pass "Stage H2: LD_PRELOAD shim built at ${SHIM}"
else
    fail "Stage H2: gcc exited 0 but produced no shim"
fi

# ---------------------------------------------------------------------------
note "Stage H3: start a project-local Xvfb"
DISPLAY_NUM=""
for n in 99 98 97 96 95; do
    if [ ! -e "/tmp/.X${n}-lock" ] && [ ! -S "/tmp/.X11-unix/X${n}" ]; then
        DISPLAY_NUM="${n}"; break
    fi
done
[ -n "${DISPLAY_NUM}" ] || die "no free X display number in 95..99"

mkdir -p /tmp/.X11-unix 2>/dev/null || true
LD_PRELOAD="${SHIM}" \
XKBCOMP_PATH="${X11_ROOT}/usr/bin/xkbcomp" \
XKB_CONFIG_ROOT="${X11_ROOT}/usr/share/X11/xkb" \
    "${X11_ROOT}/usr/bin/Xvfb" ":${DISPLAY_NUM}" -screen 0 1280x800x24 -nolisten tcp \
    -xkbdir "${X11_ROOT}/usr/share/X11/xkb" > "${BUILD}/xvfb.log" 2>&1 &
XVFB_PID=$!

up=0
for _ in $(seq 1 40); do
    if [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && kill -0 "${XVFB_PID}" 2>/dev/null; then
        up=1; break
    fi
    kill -0 "${XVFB_PID}" 2>/dev/null || break
    pause 0.25
done
printf 'Xvfb log:\n'; sed 's/^/    /' "${BUILD}/xvfb.log" || true
if [ "${up}" -eq 1 ]; then
    pass "Stage H3: Xvfb running on :${DISPLAY_NUM} (pid ${XVFB_PID}), socket /tmp/.X11-unix/X${DISPLAY_NUM}"
else
    fail "Stage H3: Xvfb did not come up -- see ${BUILD}/xvfb.log"
    note "Summary"
    for r in "${STAGE_RESULTS[@]}"; do printf '  %s\n' "${r}"; done
    exit 1
fi
export DISPLAY=":${DISPLAY_NUM}"

# ---------------------------------------------------------------------------
note "Stage H4: JavaFX Application startup, HEADED (GTK Glass platform)"
# No Monocle and no glass.platform here: JavaFX picks its own GTK platform.
# smoke.expect.glass is overridden because the expected Glass implementation is
# genuinely different on a headed run -- every other expectation stays pinned.
set +e
"${JAVA_HOME}/bin/java" \
    --add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
    --add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    --enable-native-access=javafx.graphics \
    -Dsmoke.expect.glass=com.sun.glass.ui.gtk.GtkApplication \
    -cp "${CLASSES}" cometgui.spike.HeadlessSmoke > "${BUILD}/headed-smoke.out" 2>&1
headed_status=$?
set -e
cat -- "${BUILD}/headed-smoke.out"
n_pass="$(grep -c '\[PASS\]' "${BUILD}/headed-smoke.out" || true)"
n_fail="$(grep -c '\[FAIL\]' "${BUILD}/headed-smoke.out" || true)"
if [ "${headed_status}" -eq 0 ] && [ "${n_fail}" -eq 0 ] && [ "${n_pass}" -ge 14 ]; then
    pass "Stage H4: JavaFX started headed on GtkApplication; ${n_pass} checks passed"
else
    fail "Stage H4: headed startup failed (exit ${headed_status}, ${n_pass} PASS, ${n_fail} FAIL)"
fi

note "Stage H5: negative control -- the headed smoke must fail on a wrong expectation"
set +e
"${JAVA_HOME}/bin/java" \
    --add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
    --add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    --enable-native-access=javafx.graphics \
    -Dsmoke.expect.glass=com.sun.glass.ui.gtk.GtkApplication \
    -Dsmoke.expect.pixel=ffdeadbe \
    -cp "${CLASSES}" cometgui.spike.HeadlessSmoke > "${BUILD}/headed-smoke-negative.out" 2>&1
neg_status=$?
set -e
grep -E 'snapshotPixel|^HEADLESS SMOKE' "${BUILD}/headed-smoke-negative.out" || true
if [ "${neg_status}" -ne 0 ] && grep -q '^HEADLESS SMOKE: FAIL' "${BUILD}/headed-smoke-negative.out"; then
    pass "Stage H5: headed harness failed as required (exit ${neg_status})"
else
    fail "Stage H5: headed harness reported success on a wrong expectation"
fi

# ---------------------------------------------------------------------------
if [ "${RUN_MAVEN}" -eq 1 ]; then
    note "Stage H6: TestFX and fallback spikes, HEADED (mvn -Pheaded)"
    command -v mvn >/dev/null || die "no mvn on PATH after sourcing tools/env.sh"
    mkdir -p -- "${M2REPO}"
    set +e
    ( cd "${SPIKE_DIR}" && mvn -B -Pheaded -Dmaven.repo.local="${M2REPO}" test ) \
        > "${BUILD}/maven-headed.out" 2>&1
    mvn_status=$?
    set -e
    grep -E 'Tests run:|BUILD ' "${BUILD}/maven-headed.out" || true
    if [ "${mvn_status}" -eq 0 ] \
       && grep -qE '^\[INFO\] Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: 0$' "${BUILD}/maven-headed.out"; then
        pass "Stage H6: TestFX and fallback tests passed headed (log ${BUILD}/maven-headed.out)"
    else
        fail "Stage H6: headed Maven run did not pass (exit ${mvn_status}, log ${BUILD}/maven-headed.out)"
    fi
fi

# ---------------------------------------------------------------------------
note "Summary"
for r in "${STAGE_RESULTS[@]}"; do printf '  %s\n' "${r}"; done
if [ "${FAILED}" -eq 0 ]; then
    printf '\njavafx-headed-xvfb.sh: OK -- headed JavaFX verified on a project-local X server.\n'
    exit 0
fi
printf '\njavafx-headed-xvfb.sh: FAILED -- see the stages marked FAIL above.\n' >&2
exit 1
