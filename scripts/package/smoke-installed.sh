#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# smoke-installed.sh -- prove the packaging traps absent from an INSTALLED
# CometGUI, from outside the application, with no JDK on PATH and JAVA_HOME
# unset (Phase 14, unit 5; P14-10).  No test bridge: everything is read from
# the operating system, the X server and the file system.
#
#   bash scripts/package/smoke-installed.sh INSTALL_DIR [options]
#   bash scripts/package/smoke-installed.sh --self-test
#   bash scripts/package/smoke-installed.sh --help
#
# INSTALL_DIR is what scripts/package/install-archive.sh installed into; the
# launcher is INSTALL_DIR/CometGUI/bin/CometGUI.
#
# Options:
#   --converter-jar JAR    the pinned Limelight converter JAR (default
#                          scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar);
#                          its SHA-256 must equal manifests/tools.json's pin
#   --work DIR             where the fresh home, the launch directory and the
#                          display's files go (default: a new directory under
#                          ${TMPDIR:-/tmp}, outside the repository, removed
#                          afterwards); must not exist yet
#   --keep                 keep the work directory
#   --window-deadline S    how long the window may take to appear (default 120)
#   --self-test-path-prepend DIR
#                          SELF-TEST ONLY: put DIR first on the launch PATH,
#                          so the "no java on PATH" check can be seen to fail
#
# THE CHECKS (each prints [PASS] or [FAIL] with its ID and a diagnostic; the
# script exits 1 if any failed):
#   S1  the launch environment: no java on PATH, JAVA_HOME unset, LC_ALL=C.UTF-8
#   S2  the installed runtime has bin/java, it runs, its java.home is the
#       installed runtime, and its sun.jnu.encoding is UTF-8 in that environment
#   S3  its --list-modules shows javafx.base, javafx.graphics, javafx.controls
#   S4  that java runs a JAR: the pinned converter's --version under
#       LANG=C.UTF-8 (the product launches the converter and PDV this way)
#   S5  the install tree is read-only, and a probe write into it is refused
#   S6  the installed launcher starts the application: a VIEWABLE X window
#       titled CometGUI whose _NET_WM_PID is the launcher's process appears
#       on a private project-local Xvfb
#   S7  that process is the installed launcher running the installed runtime:
#       /proc/PID/exe is INSTALL_DIR/CometGUI/bin/CometGUI, and the only
#       libjvm.so and JavaFX glass library it has mapped are the installed
#       runtime's
#   S8  it stops cleanly: WM_DELETE_WINDOW (a window manager's close button)
#       makes it exit, with status 0, within 60 s
#   S9  it printed nothing but the two lines Prism prints on a display without
#       GLX (no restricted-method warning, no exception, no write refusal)
#   S10 nothing in the install tree changed (path, type, mode, size, mtime and
#       SHA-256 of every entry, before and after)
#   S11 what it wrote went under the fresh home: the directory it was launched
#       from is still empty, nothing appeared elsewhere in the work directory,
#       and nothing new appeared in the real home's ~/.java, ~/.cache,
#       ~/.config or ~/.local/share/cometgui
#
# THE LAUNCH ENVIRONMENT on this host, exactly (Tier B needs the same):
#   PATH=/usr/bin:/bin   LC_ALL=C.UTF-8   no JAVA_HOME
#   HOME=<work>/protéomique run/home   XDG_DATA_HOME=$HOME/.local/share
#   DISPLAY=:N   (a private Xvfb from tools/x11-bookworm-20260829, started with
#                 the xkbcomp path shim, -displayfd, -nolisten tcp)
#   LD_LIBRARY_PATH=<x11 closure>/usr/lib/x86_64-linux-gnu:<x11 closure>/lib/x86_64-linux-gnu:
#                   <font stack>/usr/lib/x86_64-linux-gnu
#   FONTCONFIG_PATH=<font stack>/etc/fonts
#   FONTCONFIG_FILE=<work>/display/fonts.conf -- the stack's fonts.conf plus an
#                   explicit <dir> for its fonts, because that fonts.conf finds
#                   them through XDG_DATA_HOME, which is now the fresh home;
#                   without it JavaFX reports "detected no fonts" and start fails
# The library and font variables stand in for a desktop's system libraries
# (this host has no libX11 and no fonts); a user's machine needs none of them.
#
# --self-test builds damaged copies of the built artefact
# (_build/package/linux, from scripts/package/build-app-image.sh), installs
# each outside the repository, and requires each damage to be caught by the
# check that exists to catch it, with its diagnostic -- then requires the
# undamaged artefact to pass every check.  A check that has never been seen to
# fail has not been shown to work.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"
X11_ROOT="${ROOT}/tools/x11-bookworm-20260829/root"
FONT_ROOT="${ROOT}/tools/fontstack-bookworm-20260829/root"
SHIM_SOURCE="${ROOT}/scripts/feasibility/gui-spike/xkbcomp-path-shim.c"
X11_HELPER="${SCRIPT_DIR}/x11_windows.py"
DEFAULT_CONVERTER="${ROOT}/scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar"
CONVERTER_URL_SUFFIX="/v2.8.1/cometPercolator2LimelightXML.jar"
CONVERTER_BANNER="cometPercolator2LimelightXML.jar v2.8.1"
WINDOW_TITLE="CometGUI"
LAUNCH_PATH="/usr/bin:/bin"
STOP_DEADLINE=60
FRESH_SEGMENT="protéomique run"
# Prism on a display with no GLX 1.3 (Xvfb) falls back to software rendering
# and says so; that is the only output a healthy start produces here.
ALLOWED_OUTPUT='^(ES2 Prism: Error - GLX extension is not supported|[[:space:]]*GLX version 1\.3 or higher is required|[[:space:]]*)$'

usage() { sed -n '7,31p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
die() { printf 'smoke-installed.sh: %s\n' "$*" >&2; exit 2; }

export LC_ALL=C.UTF-8
# Never "producer | head" here: under pipefail the producer's SIGPIPE (exit
# 141) would abort the script instead of letting the check report.  The
# first lines are taken with sed -n, which reads its whole input.

# ===========================================================================
# The smoke run.
# ===========================================================================
smoke() {
    # work, keep and the two process IDs are globals: the EXIT trap that cleans
    # them up runs after this function has returned.
    local install="" converter="${DEFAULT_CONVERTER}" window_deadline=120 prepend=""
    work="" keep=0 xvfb_pid="" app_pid=""
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --converter-jar) converter="$2"; shift 2 ;;
            --work) work="$2"; shift 2 ;;
            --keep) keep=1; shift ;;
            --window-deadline) window_deadline="$2"; shift 2 ;;
            --self-test-path-prepend) prepend="$2"; shift 2 ;;
            --*) die "unknown option $1" ;;
            *) [ -z "${install}" ] || die "one INSTALL_DIR only"; install="$1"; shift ;;
        esac
    done
    [ -n "${install}" ] || { usage >&2; exit 2; }
    [ -d "${install}" ] || die "no install directory ${install}"
    install="$(cd -- "${install}" && pwd -P)"
    local appdir="${install}/CometGUI"
    local runtime="${appdir}/lib/runtime"
    local launcher="${appdir}/bin/CometGUI"
    [ -e "${launcher}" ] || die "${install} holds no CometGUI/bin/CometGUI; install it with scripts/package/install-archive.sh"

    for need in "${X11_ROOT}/usr/bin/Xvfb" "${X11_ROOT}/usr/bin/xkbcomp" "${X11_ROOT}/usr/share/X11/xkb" \
        "${FONT_ROOT}/etc/fonts/fonts.conf" "${FONT_ROOT}/usr/share/fonts" "${SHIM_SOURCE}" "${X11_HELPER}"; do
        [ -e "${need}" ] || die "the project-local display needs ${need}, which is missing (rebuild with scripts/feasibility/javafx-smoke.sh then scripts/feasibility/javafx-headed-xvfb.sh)"
    done
    command -v gcc >/dev/null || die "no gcc: the xkbcomp path shim the project Xvfb needs cannot be built"
    [ -f "${converter}" ] || die "no converter JAR at ${converter} (the pinned v2.8.1 is ${ROOT}/manifests/tools.json's URL ending ${CONVERTER_URL_SUFFIX})"

    if [ -z "${work}" ]; then
        work="$(mktemp -d "${TMPDIR:-/tmp}/cometgui-smoke.XXXXXX")"
    else
        [ ! -e "${work}" ] || die "--work ${work} already exists"
        mkdir -p -- "${work}"
        work="$(cd -- "${work}" && pwd -P)"
    fi
    case "${work}/" in "${ROOT}/"*|"${install}/"*) die "the work directory ${work} must be outside the repository and the install" ;; esac

    local fresh="${work}/${FRESH_SEGMENT}"
    local home="${fresh}/home"
    local cwd="${work}/launch-directory"
    local display_dir="${work}/display"
    local logs="${work}/logs"
    local tooldir="${work}/tool"
    mkdir -p -- "${home}/.local/share" "${cwd}" "${display_dir}" "${logs}" "${tooldir}"

    local failures=0
    local results=()
    pass() { printf '[PASS] %s\n' "$*"; results+=("PASS  $*"); }
    fail() { printf '[FAIL] %s\n' "$*"; results+=("FAIL  $*"); failures=$((failures + 1)); }

    # shellcheck disable=SC2317
    cleanup_smoke() {
        if [ -n "${app_pid}" ] && kill -0 "${app_pid}" 2>/dev/null; then
            kill -KILL "${app_pid}" 2>/dev/null || true
            wait "${app_pid}" 2>/dev/null || true
        fi
        if [ -n "${xvfb_pid}" ] && kill -0 "${xvfb_pid}" 2>/dev/null; then
            kill -TERM "${xvfb_pid}" 2>/dev/null || true
            wait "${xvfb_pid}" 2>/dev/null || true
        fi
        [ -n "${work}" ] || return 0
        if [ "${keep}" -eq 0 ]; then
            chmod -R u+w -- "${work}" 2>/dev/null || true
            rm -rf -- "${work}"
        else
            echo "work directory kept: ${work}"
        fi
    }
    trap cleanup_smoke EXIT

    local x11_libs="${X11_ROOT}/usr/lib/x86_64-linux-gnu:${X11_ROOT}/lib/x86_64-linux-gnu:${FONT_ROOT}/usr/lib/x86_64-linux-gnu"
    local launch_path="${LAUNCH_PATH}"
    [ -z "${prepend}" ] || launch_path="${prepend}:${launch_path}"

    echo "install:  ${install}"
    echo "work:     ${work}"

    # ---- S1 ---------------------------------------------------------------
    local java_on_path="" dir
    local IFS_SAVED="${IFS}"
    IFS=':'
    for dir in ${launch_path}; do
        if [ -x "${dir}/java" ]; then java_on_path="${dir}/java"; break; fi
    done
    IFS="${IFS_SAVED}"
    if [ -n "${java_on_path}" ]; then
        fail "S1 launch environment: PATH=${launch_path} finds a java at ${java_on_path}; the installed artefact must not be able to lean on one"
    else
        pass "S1 launch environment: PATH=${launch_path} has no java, JAVA_HOME unset, LC_ALL=C.UTF-8"
    fi

    # ---- S2, S3 -----------------------------------------------------------
    local java="${runtime}/bin/java"
    if [ ! -e "${java}" ]; then
        fail "S2 runtime java: ${java} does not exist (lib/runtime/bin holds: $(ls "${runtime}/bin" 2>&1 | paste -sd' ' -)) -- the image was built without --jlink-options that keep it, so PDV and the converter cannot be started"
        fail "S3 JavaFX modules: not checked, because there is no ${java} to ask"
    else
        local props
        if props="$(env -i PATH="${launch_path}" LC_ALL=C.UTF-8 "${java}" -XshowSettings:properties -version 2>&1)"; then
            local reported_home jnu
            reported_home="$(printf '%s\n' "${props}" | sed -n 's/^ *java\.home = //p')"
            jnu="$(printf '%s\n' "${props}" | sed -n 's/^ *sun\.jnu\.encoding = //p')"
            if [ "${reported_home}" = "${runtime}" ] && [ "${jnu}" = "UTF-8" ]; then
                pass "S2 runtime java: ${java} runs; java.home=${reported_home}; sun.jnu.encoding=${jnu}; java.runtime.version=$(printf '%s\n' "${props}" | sed -n 's/^ *java\.runtime\.version = //p')"
            else
                fail "S2 runtime java: java.home=${reported_home:-?} (expected ${runtime}), sun.jnu.encoding=${jnu:-?} (expected UTF-8)"
            fi
        else
            fail "S2 runtime java: ${java} -version failed: $(printf '%s' "${props}" | tail -3 | paste -sd' ' -)"
        fi
        local listed missing=""
        listed="$(env -i PATH="${launch_path}" LC_ALL=C.UTF-8 "${java}" --list-modules 2>&1 || true)"
        for module in javafx.base javafx.graphics javafx.controls; do
            printf '%s\n' "${listed}" | grep -q "^${module}@" || missing="${missing} ${module}"
        done
        if [ -z "${missing}" ]; then
            pass "S3 JavaFX modules: $(printf '%s\n' "${listed}" | grep '^javafx\.' | paste -sd' ' -)"
        else
            fail "S3 JavaFX modules: --list-modules lacks${missing}; it lists $(printf '%s\n' "${listed}" | wc -l) modules: $(printf '%s\n' "${listed}" | sed 's/@.*//' | paste -sd' ' -)"
        fi
    fi

    # ---- S4 ---------------------------------------------------------------
    local pinned actual_sha
    pinned="$(python3 - "${ROOT}/manifests/tools.json" "${CONVERTER_URL_SUFFIX}" <<'PY'
import json, sys
suffix = sys.argv[2]
found = set()
def walk(node):
    if isinstance(node, dict):
        if str(node.get("url", "")).endswith(suffix) and "sha256" in node:
            found.add(node["sha256"])
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)
walk(json.load(open(sys.argv[1], encoding="utf-8")))
print(found.pop() if len(found) == 1 else "")
PY
)"
    actual_sha="$(sha256sum -- "${converter}" | cut -d' ' -f1)"
    if [ -z "${pinned}" ] || [ "${pinned}" != "${actual_sha}" ]; then
        fail "S4 runtime runs a JAR: ${converter} has SHA-256 ${actual_sha}, manifests/tools.json pins ${pinned:-nothing unique}"
    elif [ ! -e "${java}" ]; then
        fail "S4 runtime runs a JAR: there is no ${java} to run ${converter##*/} with -- the product's 'java -jar' of the converter and PDV would fail the same way"
    else
        cp -- "${converter}" "${tooldir}/cometPercolator2LimelightXML.jar"
        local converter_out converter_status=0
        converter_out="$(cd -- "${tooldir}" && env -i LANG=C.UTF-8 "${java}" -jar "${tooldir}/cometPercolator2LimelightXML.jar" --version 2>&1)" || converter_status=$?
        if [ "${converter_status}" -eq 0 ] && printf '%s\n' "${converter_out}" | grep -qF "${CONVERTER_BANNER}"; then
            pass "S4 runtime runs a JAR: ${java##"${install}"/} -jar (SHA-256 ${actual_sha:0:12}...) --version printed '${CONVERTER_BANNER}'"
        else
            fail "S4 runtime runs a JAR: exit ${converter_status}, output: $(printf '%s' "${converter_out}" | tail -4 | paste -sd' ' -)"
        fi
    fi

    # ---- S5 ---------------------------------------------------------------
    local writable probe="${appdir}/lib/app/.smoke-write-probe"
    writable="$(find "${install}" \( -perm -u=w -o -perm -g=w -o -perm -o=w \) ! -type l -print 2>/dev/null | sed -n "1,3p" | paste -sd' ' -)"
    if [ -n "${writable}" ]; then
        fail "S5 read-only install: writable entries in the install tree, e.g. ${writable}"
    elif (: > "${probe}") 2>/dev/null; then
        rm -f -- "${probe}" 2>/dev/null || true
        fail "S5 read-only install: a probe write into ${appdir}/lib/app succeeded"
    else
        pass "S5 read-only install: no writable entry, and a probe write into lib/app was refused"
    fi

    # ---- the display ------------------------------------------------------
    gcc -shared -fPIC -O1 -o "${display_dir}/xkbcomp-path-shim.so" "${SHIM_SOURCE}" \
        || die "gcc did not build the xkbcomp path shim"
    env -i PATH="${LAUNCH_PATH}" LD_LIBRARY_PATH="${x11_libs}" \
        LD_PRELOAD="${display_dir}/xkbcomp-path-shim.so" \
        XKBCOMP_PATH="${X11_ROOT}/usr/bin/xkbcomp" XKB_CONFIG_ROOT="${X11_ROOT}/usr/share/X11/xkb" \
        "${X11_ROOT}/usr/bin/Xvfb" -displayfd 3 -screen 0 1280x1024x24 -nolisten tcp \
        -xkbdir "${X11_ROOT}/usr/share/X11/xkb" \
        3> "${display_dir}/number" > "${logs}/xvfb.log" 2>&1 < /dev/null &
    xvfb_pid=$!
    local i number=""
    for i in $(seq 1 120); do
        number="$(tr -dc '0-9' < "${display_dir}/number" 2>/dev/null || true)"
        [ -z "${number}" ] || break
        kill -0 "${xvfb_pid}" 2>/dev/null || break
        timeout 0.25 tail -f /dev/null 2>/dev/null || true   # bounded poll interval
    done
    [ -n "${number}" ] || die "the project Xvfb announced no display: $(tail -5 "${logs}/xvfb.log" | paste -sd' ' -)"
    local display=":${number}"
    cat > "${display_dir}/fonts.conf" <<EOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig>
  <include>${FONT_ROOT}/etc/fonts/fonts.conf</include>
  <dir>${FONT_ROOT}/usr/share/fonts</dir>
</fontconfig>
EOF
    x11() { env -i PATH="${LAUNCH_PATH}" LD_LIBRARY_PATH="${x11_libs}" DISPLAY="${display}" python3 "${X11_HELPER}" "$@"; }
    echo "display:  ${display} (Xvfb pid ${xvfb_pid})"

    # ---- the install tree, before ------------------------------------------
    tree_manifest() {
        python3 - "$1" <<'PY'
import hashlib, os, stat, sys
root = sys.argv[1]
for dirpath, dirnames, filenames in os.walk(root):
    dirnames.sort()
    for name in sorted(dirnames + filenames):
        path = os.path.join(dirpath, name)
        st = os.lstat(path)
        digest = "-"
        if stat.S_ISREG(st.st_mode):
            h = hashlib.sha256()
            with open(path, "rb") as f:
                for block in iter(lambda: f.read(1 << 20), b""):
                    h.update(block)
            digest = h.hexdigest()
        elif stat.S_ISLNK(st.st_mode):
            digest = "->" + os.readlink(path)
        print(f"{os.path.relpath(path, root)}\t{stat.filemode(st.st_mode)}\t{st.st_size}\t{st.st_mtime_ns}\t{digest}")
PY
    }
    tree_manifest "${install}" > "${logs}/install-before.tsv"
    local real_home real_watch=()
    real_home="$(getent passwd "$(id -u)" | cut -d: -f6)"
    for dir in "${real_home}/.java" "${real_home}/.cache" "${real_home}/.config" "${real_home}/.local/share/cometgui" "${HOME}/.java" "${HOME}/.cache" "${HOME}/.config"; do
        [ -d "${dir}" ] && real_watch+=("${dir}")
    done
    touch -- "${logs}/launch-marker"

    # ---- S6 ---------------------------------------------------------------
    (
        cd -- "${cwd}"
        exec env -i PATH="${launch_path}" LC_ALL=C.UTF-8 \
            HOME="${home}" XDG_DATA_HOME="${home}/.local/share" DISPLAY="${display}" \
            LD_LIBRARY_PATH="${x11_libs}" FONTCONFIG_PATH="${FONT_ROOT}/etc/fonts" \
            FONTCONFIG_FILE="${display_dir}/fonts.conf" \
            "${launcher}"
    ) > "${logs}/application.log" 2>&1 < /dev/null &
    app_pid=$!
    echo "launched: ${launcher} (pid ${app_pid}) from ${cwd}"
    local window_line="" window_id=""
    if window_line="$(x11 wait --pid "${app_pid}" --title "${WINDOW_TITLE}" --deadline "${window_deadline}" 2>"${logs}/window-wait.err")"; then
        window_id="${window_line%% *}"
        pass "S6 window: ${window_line}"
    else
        fail "S6 window: $(cat "${logs}/window-wait.err") -- the application said: $(grep -v -E "${ALLOWED_OUTPUT}" "${logs}/application.log" | sed -n "1,6p" | paste -sd'|' -)"
    fi

    # ---- S7 ---------------------------------------------------------------
    if [ -r "/proc/${app_pid}/maps" ] && [ "$(awk '{print $3}' "/proc/${app_pid}/stat" 2>/dev/null)" != Z ]; then
        local exe cmdline jvms glass
        exe="$(readlink -f "/proc/${app_pid}/exe" 2>/dev/null || true)"
        cmdline="$(tr '\0' ' ' < "/proc/${app_pid}/cmdline" 2>/dev/null || true)"
        jvms="$(awk '$6 ~ /libjvm\.so$/ {print $6}' "/proc/${app_pid}/maps" | sort -u | paste -sd' ' -)"
        glass="$(awk '$6 ~ /libglass[a-z0-9]*\.so$/ {print $6}' "/proc/${app_pid}/maps" | sort -u | paste -sd' ' -)"
        if [ "${exe}" = "${launcher}" ] && [ "${jvms}" = "${runtime}/lib/server/libjvm.so" ] \
            && [ -n "${glass}" ] && ! printf '%s\n' ${glass} | grep -qv "^${runtime}/lib/"; then
            pass "S7 installed runtime: exe=${exe}; cmdline=${cmdline}; libjvm=${jvms}; glass=${glass}"
        else
            fail "S7 installed runtime: exe=${exe:-?} (expected ${launcher}); cmdline=${cmdline}; libjvm=${jvms:-none} (expected ${runtime}/lib/server/libjvm.so); glass=${glass:-none} (expected under ${runtime}/lib)"
        fi
    else
        fail "S7 installed runtime: process ${app_pid} is no longer running, so what it ran cannot be read"
    fi

    # ---- S8 ---------------------------------------------------------------
    local status=""
    if [ -n "${window_id}" ]; then
        x11 close --window "${window_id}" > "${logs}/close.out" 2>&1 || true
        for i in $(seq 1 $((STOP_DEADLINE * 4))); do
            [ "$(awk '{print $3}' "/proc/${app_pid}/stat" 2>/dev/null || echo Z)" != Z ] || break
            timeout 0.25 tail -f /dev/null 2>/dev/null || true   # bounded poll interval
        done
        if [ "$(awk '{print $3}' "/proc/${app_pid}/stat" 2>/dev/null || echo Z)" = Z ]; then
            status=0; wait "${app_pid}" || status=$?
            app_pid=""
            if [ "${status}" -eq 0 ]; then
                pass "S8 clean stop: WM_DELETE_WINDOW to ${window_id}; the process exited with status 0"
            else
                fail "S8 clean stop: the process exited with status ${status} after WM_DELETE_WINDOW"
            fi
        else
            fail "S8 clean stop: still running ${STOP_DEADLINE} s after WM_DELETE_WINDOW to ${window_id} ($(cat "${logs}/close.out")); windows now: $(x11 list | paste -sd';' -)"
        fi
    else
        fail "S8 clean stop: no window to close"
    fi
    if [ -n "${app_pid}" ]; then
        kill -TERM "${app_pid}" 2>/dev/null || true
        for i in $(seq 1 40); do
            [ "$(awk '{print $3}' "/proc/${app_pid}/stat" 2>/dev/null || echo Z)" != Z ] || break
            timeout 0.25 tail -f /dev/null 2>/dev/null || true
        done
        kill -KILL "${app_pid}" 2>/dev/null || true
        wait "${app_pid}" 2>/dev/null || true
        app_pid=""
    fi

    # ---- S9 ---------------------------------------------------------------
    local unexpected
    unexpected="$(grep -v -E "${ALLOWED_OUTPUT}" "${logs}/application.log" || true)"
    if [ -z "${unexpected}" ]; then
        pass "S9 launcher output: only the expected no-GLX lines ($(wc -l < "${logs}/application.log") lines)"
    else
        fail "S9 launcher output: $(printf '%s\n' "${unexpected}" | wc -l) unexpected line(s): $(printf '%s\n' "${unexpected}" | sed -n "1,4p" | paste -sd'|' -)"
    fi

    # ---- S10 --------------------------------------------------------------
    tree_manifest "${install}" > "${logs}/install-after.tsv"
    if cmp -s "${logs}/install-before.tsv" "${logs}/install-after.tsv"; then
        pass "S10 install tree unchanged: $(wc -l < "${logs}/install-after.tsv") entries identical by path, mode, size, mtime and SHA-256"
    else
        fail "S10 install tree unchanged: it changed: $(diff "${logs}/install-before.tsv" "${logs}/install-after.tsv" | grep '^[<>]' | cut -f1 | sed -n "1,4p" | paste -sd' ' -)"
    fi

    # ---- S11 --------------------------------------------------------------
    local in_cwd outside in_home in_real=""
    in_cwd="$(find "${cwd}" -mindepth 1 | sed -n "1,3p" | paste -sd' ' -)"
    outside="$(find "${work}" -newer "${logs}/launch-marker" ! -type d \
        ! -path "${home}/*" ! -path "${logs}/*" ! -path "${display_dir}/*" ! -path "${cwd}/*" | sed -n "1,3p" | paste -sd' ' -)"
    in_home="$(find "${home}" -newer "${logs}/launch-marker" ! -type d | sed "s|^${home}/||" | sort)"
    if [ "${#real_watch[@]}" -gt 0 ]; then
        in_real="$(find "${real_watch[@]}" -newer "${logs}/launch-marker" ! -type d 2>/dev/null | sed -n "1,3p" | paste -sd' ' -)"
    fi
    if [ -z "${in_cwd}" ] && [ -z "${outside}" ] && [ -z "${in_real}" ]; then
        pass "S11 writes confined to the fresh home: launch directory empty; under the home: $(printf '%s\n' "${in_home}" | grep -c . || true) file(s) [$(printf '%s\n' "${in_home}" | sed 's|/[^/]*$||' | sort -u | paste -sd' ' -)]"
    else
        fail "S11 writes confined to the fresh home: in the launch directory: ${in_cwd:-nothing}; elsewhere in the work directory: ${outside:-nothing}; in the real home: ${in_real:-nothing}"
    fi

    echo
    echo "== summary"
    printf '  %s\n' "${results[@]}"
    if [ "${failures}" -eq 0 ]; then
        echo "smoke-installed.sh: PASSED -- every check (S1-S11) on ${install}"
        return 0
    fi
    echo "smoke-installed.sh: FAILED -- ${failures} check(s) failed on ${install}"
    return 1
}

# ===========================================================================
# The self-test: damaged artefacts, each caught by its own check.
# ===========================================================================
self_test() {
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    local built="${ROOT}/_build/package/linux"
    local image="${built}/image/CometGUI"
    shopt -s nullglob
    local archives=("${built}"/CometGUI-*-linux-x86_64.tar.gz)
    shopt -u nullglob
    [ -d "${image}" ] && [ "${#archives[@]}" -eq 1 ] && [ -s "${built}/jpackage-build.args" ] \
        || die "no built artefact in ${built}; run bash scripts/package/build-app-image.sh first"
    local good_archive="${archives[0]}"
    # st is a global: the EXIT trap that removes it runs after this returns.
    st="$(mktemp -d "${TMPDIR:-/tmp}/cometgui-smoke-selftest.XXXXXX")"
    # shellcheck disable=SC2317
    cleanup_self_test() { [ -n "${st}" ] || return 0; chmod -R u+w -- "${st}" 2>/dev/null || true; rm -rf -- "${st}"; }
    trap cleanup_self_test EXIT
    echo "self-test workspace: ${st}"
    local caught=0 missed=0 total=0 verdicts=()

    # pack DIR NAME -> an archive (and .sha256) of DIR as CometGUI/
    pack() {
        local dir="$1" name="$2"
        tar --sort=name -C "$(dirname -- "${dir}")" -cf - "$(basename -- "${dir}")" | gzip -n -1 > "${st}/${name}.tar.gz"
        (cd -- "${st}" && sha256sum -- "${name}.tar.gz" > "${name}.tar.gz.sha256")
    }
    # copy_image NAME -> a writable copy of the good image at ${st}/NAME/CometGUI
    copy_image() {
        mkdir -p -- "${st}/$1"
        cp -a -- "${image}" "${st}/$1/CometGUI"
        chmod -R u+w -- "${st}/$1/CometGUI"
    }
    # jpackage_variant NAME COMMON_ARGS BUILD_ARGS -> ${st}/NAME/CometGUI
    jpackage_variant() {
        mkdir -p -- "${st}/$1"
        jpackage "@$2" "@${ROOT}/packaging/jpackage/linux.args" "@$3" --dest "${st}/$1" \
            > "${st}/$1.jpackage.log" 2>&1 || { tail -20 "${st}/$1.jpackage.log" >&2; die "jpackage failed for variant $1"; }
        [ -x "${st}/$1/CometGUI/bin/CometGUI" ] || die "variant $1: jpackage produced no launcher"
    }
    # a class with a main method, compiled into a JAR in a variant's lib/app,
    # and made the launcher's main class
    replace_main() {
        local name="$1" class="$2" source="$3"
        local src="${st}/${name}-src"
        mkdir -p -- "${src}/classes"
        printf '%s\n' "${source}" > "${src}/${class}.java"
        javac --release 25 -cp "${image}/lib/app/*" -d "${src}/classes" "${src}/${class}.java" \
            || die "variant ${name}: javac failed"
        jar --create --file "${st}/${name}/CometGUI/lib/app/${class,,}.jar" -C "${src}/classes" .
        local cfg="${st}/${name}/CometGUI/lib/app/CometGUI.cfg"
        sed -i "s/^app\.mainclass=.*/app.mainclass=${class}/" "${cfg}"
        sed -i "0,/^app\.classpath=/s||app.classpath=\$APPDIR/${class,,}.jar\napp.classpath=|" "${cfg}"
    }
    # expect NAME ARCHIVE "INSTALL_OPTIONS" "SMOKE_OPTIONS" ID:REGEX...
    expect() {
        local name="$1" archive="$2" install_opts="$3" smoke_opts="$4"
        shift 4
        total=$((total + 1))
        local dest="${st}/install-${name}"
        printf '\n######## variant %s\n' "${name}"
        # shellcheck disable=SC2086
        bash "${SCRIPT_DIR}/install-archive.sh" ${install_opts} "${archive}" "${dest}" > "${st}/${name}.install.log" 2>&1 \
            || { cat "${st}/${name}.install.log"; die "variant ${name}: install failed"; }
        local status=0
        # shellcheck disable=SC2086
        bash "${BASH_SOURCE[0]}" "${dest}" ${smoke_opts} > "${st}/${name}.smoke.log" 2>&1 || status=$?
        sed 's/^/    /' "${st}/${name}.smoke.log" | grep -E '^\s+\[(PASS|FAIL)\]|smoke-installed' || true
        local ok=1 spec id regex
        if [ "${#}" -eq 0 ]; then
            [ "${status}" -eq 0 ] && grep -q 'PASSED -- every check' "${st}/${name}.smoke.log" || ok=0
        else
            [ "${status}" -eq 1 ] || ok=0
            for spec in "$@"; do
                id="${spec%%:*}"; regex="${spec#*:}"
                grep -E "^\[FAIL\] ${id} .*${regex}" "${st}/${name}.smoke.log" > /dev/null \
                    || { ok=0; echo "    !! expected [FAIL] ${id} matching /${regex}/"; }
            done
        fi
        bash "${SCRIPT_DIR}/install-archive.sh" --uninstall "${dest}" > /dev/null
        if [ "${ok}" -eq 1 ]; then
            caught=$((caught + 1))
            verdicts+=("OK      ${name}: $([ "$#" -eq 0 ] && echo 'every check passed' || echo "caught by $(printf '%s ' "${@%%:*}")")")
        else
            missed=$((missed + 1))
            verdicts+=("MISSED  ${name} (exit ${status}; see ${st}/${name}.smoke.log)")
        fi
    }

    local good_common="${ROOT}/packaging/jpackage/common.args"
    local good_build="${built}/jpackage-build.args"

    # A: jpackage's default jlink options -- bin/java stripped.
    grep -v '^--jlink-options' "${good_common}" > "${st}/no-jlink-options.args"
    jpackage_variant default-jlink "${st}/no-jlink-options.args" "${good_build}"
    pack "${st}/default-jlink/CometGUI" default-jlink
    expect default-jlink "${st}/default-jlink.tar.gz" "" "" \
        "S2:does not exist" "S4:no .*bin/java to run"

    # B: a runtime without JavaFX.
    sed 's/^--add-modules .*/--add-modules java.se,jdk.unsupported,jdk.httpserver,jdk.charsets,jdk.localedata,jdk.zipfs/' \
        "${good_build}" > "${st}/no-javafx.build.args"
    jpackage_variant no-javafx "${good_common}" "${st}/no-javafx.build.args"
    pack "${st}/no-javafx/CometGUI" no-javafx
    expect no-javafx "${st}/no-javafx.tar.gz" "" "--window-deadline 60" \
        "S3:lacks javafx.base javafx.graphics javafx.controls" "S6:exited before a viewable window"

    # C: a launcher that runs whatever java is on PATH, with the project JDK put there.
    copy_image java-from-path
    cat > "${st}/java-from-path/CometGUI/bin/CometGUI" <<'EOF'
#!/bin/sh
APPDIR="$(cd "$(dirname "$0")/../lib/app" && pwd)"
exec java --enable-native-access=ALL-UNNAMED --enable-native-access=javafx.graphics -cp "$APPDIR/*" org.cometgui.app.bootstrap.CometGuiLauncher "$@"
EOF
    chmod +x "${st}/java-from-path/CometGUI/bin/CometGUI"
    pack "${st}/java-from-path/CometGUI" java-from-path
    expect java-from-path "${st}/java-from-path.tar.gz" "" "--self-test-path-prepend ${JAVA_HOME}/bin" \
        "S1:finds a java at .*liberica.*/bin/java" "S7:exe=.*liberica.*/bin/java \\(expected"

    # D: an application that writes into its install directory, installed writable.
    copy_image writes-install
    printf 'java-options=-Xlog:gc:file=$APPDIR/gc.log\n' >> "${st}/writes-install/CometGUI/lib/app/CometGUI.cfg"
    pack "${st}/writes-install/CometGUI" writes-install
    expect writes-install-writable "${st}/writes-install.tar.gz" "--leave-writable" "" \
        "S5:writable entries" "S10:changed: .*gc.log"
    # D2: the same application, installed read-only: the write fails, loudly.
    expect writes-install-readonly "${st}/writes-install.tar.gz" "" "" \
        "S9:unexpected line.*gc.log"

    # E: an application that writes into the directory it was started from.
    copy_image writes-cwd
    printf 'java-options=-Xlog:gc:file=gc-in-working-directory.log\n' >> "${st}/writes-cwd/CometGUI/lib/app/CometGUI.cfg"
    pack "${st}/writes-cwd/CometGUI" writes-cwd
    expect writes-cwd "${st}/writes-cwd.tar.gz" "" "" \
        "S11:in the launch directory: .*gc-in-working-directory.log"

    # F: a launcher whose process runs but never shows a window.
    copy_image no-window
    replace_main no-window NoWindowMain 'public final class NoWindowMain {
    public static void main(String[] args) throws InterruptedException {
        new java.util.concurrent.CountDownLatch(1).await();
    }
}'
    pack "${st}/no-window/CometGUI" no-window
    expect no-window "${st}/no-window.tar.gz" "" "--window-deadline 20" \
        "S6:no viewable window titled 'CometGUI' owned by pid"

    # G: an application whose process outlives its closed window.
    copy_image never-exits
    replace_main never-exits NeverExitsMain 'public final class NeverExitsMain {
    public static void main(String[] args) {
        Thread keeper = new Thread(() -> {
            try { new java.util.concurrent.CountDownLatch(1).await(); } catch (InterruptedException e) { }
        }, "keeps-the-jvm-alive");
        keeper.start();
        org.cometgui.app.bootstrap.CometGuiLauncher.main(args);
    }
}'
    pack "${st}/never-exits/CometGUI" never-exits
    expect never-exits "${st}/never-exits.tar.gz" "" "" \
        "S8:still running 60 s after WM_DELETE_WINDOW"

    # H: the javafx.graphics native-access option dropped from the launcher.
    copy_image native-warning
    sed -i '/^java-options=--enable-native-access=javafx.graphics$/d' "${st}/native-warning/CometGUI/lib/app/CometGUI.cfg"
    pack "${st}/native-warning/CometGUI" native-warning
    expect native-warning "${st}/native-warning.tar.gz" "" "" \
        "S9:WARNING: A restricted method"

    # I: a converter JAR that is not the pinned one.
    printf 'not the converter\n' > "${st}/not-the-converter.jar"
    expect wrong-converter "${good_archive}" "" "--converter-jar ${st}/not-the-converter.jar" \
        "S4:manifests/tools.json pins"

    # And the undamaged artefact passes everything.
    expect undamaged "${good_archive}" "" ""

    printf '\n== self-test verdicts\n'
    printf '  %s\n' "${verdicts[@]}"
    if [ "${missed}" -eq 0 ] && [ "${caught}" -eq "${total}" ]; then
        echo "smoke-installed.sh --self-test: PASSED -- $((total - 1)) damaged artefact(s) each caught by its check, the undamaged one passed every check"
        return 0
    fi
    echo "smoke-installed.sh --self-test: FAILED -- ${missed} of ${total} variant(s) not graded as expected"
    return 1
}

case "${1:-}" in
    --help|-h) usage; exit 0 ;;
    --self-test) [ "$#" -eq 1 ] || die "--self-test takes no other argument"; self_test ;;
    "") usage >&2; exit 2 ;;
    *) smoke "$@" ;;
esac
