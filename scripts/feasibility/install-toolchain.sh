#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 5: project-local toolchain installer.
#
# Recreates /workspace/tools/ from scratch. Nothing is installed on the host:
# no apt, no sudo, no host-level pip, no writes outside /workspace.
#
#   bash scripts/feasibility/install-toolchain.sh
#
# Properties this script must keep (they are the acceptance conditions):
#
#   * Re-runnable and idempotent. A second run re-verifies and does no work.
#   * Able to recreate tools/ after "rm -rf /workspace/tools" -- tools/ is
#     gitignored, so this script plus docs/feasibility/toolchain.rst are the
#     only committed record of the toolchain.
#   * Every download's SHA-256 is verified BEFORE unpacking, against a value
#     pinned in this file. A mismatch is a loud, fatal error. Never relax this.
#
# Environment variables:
#
#   COMETGUI_TOOLCHAIN_CACHE  archive download cache
#                             (default: <root>/scratch/toolchain-cache)
#   COMETGUI_TOOLCHAIN_NO_CACHE=1  ignore the cache and re-download everything
#
# Provenance for every entry in the PINS table below is recorded in
# docs/feasibility/toolchain.rst.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
TOOLS_DIR="${ROOT}/tools"
CACHE_DIR="${COMETGUI_TOOLCHAIN_CACHE:-${ROOT}/scratch/toolchain-cache}"
NO_CACHE="${COMETGUI_TOOLCHAIN_NO_CACHE:-0}"

# --------------------------------------------------------------------------
# Pinned toolchain.
#
# Fields, tab-separated:
#   1 id             short identifier used in log lines
#   2 install_dir    directory created under tools/ (versioned; stable path)
#   3 archive        file name in the cache
#   4 url            exact download URL
#   5 sha256         SHA-256 of the archive, computed locally with sha256sum
#   6 strip_top      name of the single top-level directory inside the archive
#   7 sentinel       file that must exist under install_dir after unpacking
# --------------------------------------------------------------------------
PINS=$(cat <<'EOF'
jdk	liberica-jdk-25.0.4.1+1	bellsoft-jdk25.0.4.1+1-linux-amd64-full.tar.gz	https://github.com/bell-sw/Liberica/releases/download/25.0.4.1%2B1/bellsoft-jdk25.0.4.1%2B1-linux-amd64-full.tar.gz	74de69863cfa8d58dd49992a97249ad041169ad01daa14a545ef9c7ef173cbd0	jdk-25.0.4.1-full	bin/jpackage
maven	apache-maven-3.9.16	apache-maven-3.9.16-bin.tar.gz	https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz	80ffca22aed9e8b9713a232f3394fd81d7f20322df75efdb2b047dbd3e3a23bb	apache-maven-3.9.16	bin/mvn
EOF
)

# Exact versions asserted after installation. If the archive ever changes
# behind its URL the checksum catches it first; these catch a mis-pin.
EXPECT_JAVA_VERSION="25.0.4.1"
EXPECT_MAVEN_VERSION="3.9.16"
EXPECT_JAVAFX_MODULES="javafx.base javafx.controls javafx.fxml javafx.graphics javafx.media javafx.swing javafx.web"

log()  { printf '[toolchain] %s\n' "$*"; }
fail() { printf '\n[toolchain] FATAL: %s\n' "$*" >&2; exit 1; }

sha256_of() { sha256sum -- "$1" | cut -d' ' -f1; }

# Download <url> to <path> unless a cached copy already matches <sha256>.
# Verifies the checksum of whatever ends up on disk and fails loudly if it
# does not match. Nothing is unpacked before this function returns 0.
fetch_verified() {
    local id="$1" url="$2" path="$3" want="$4" got=""

    if [ "${NO_CACHE}" = "1" ] && [ -e "${path}" ]; then
        log "${id}: COMETGUI_TOOLCHAIN_NO_CACHE=1, discarding cached archive"
        rm -f -- "${path}"
    fi

    if [ -s "${path}" ]; then
        got="$(sha256_of "${path}")"
        if [ "${got}" = "${want}" ]; then
            log "${id}: cached archive verified (sha256 ${got})"
            return 0
        fi
        log "${id}: cached archive checksum mismatch, re-downloading"
        rm -f -- "${path}"
    fi

    log "${id}: downloading ${url}"
    mkdir -p -- "$(dirname -- "${path}")"
    rm -f -- "${path}.part"
    curl -fSL --retry 3 --retry-delay 5 --connect-timeout 30 --max-time 3600 \
         -o "${path}.part" -- "${url}" \
        || fail "${id}: download failed from ${url}"
    mv -f -- "${path}.part" "${path}"

    got="$(sha256_of "${path}")"
    if [ "${got}" != "${want}" ]; then
        rm -f -- "${path}"
        fail "${id}: SHA-256 MISMATCH for ${url}
        expected ${want}
        actual   ${got}
    The downloaded archive was deleted and NOTHING was unpacked. Do not
    weaken or bypass this check: either the pin in this script is stale (fix
    the pin and docs/feasibility/toolchain.rst together, after establishing
    why upstream changed) or the download is not the artefact we pinned."
    fi
    log "${id}: downloaded archive verified (sha256 ${got})"
}

# Unpack <archive> into tools/<install_dir>, atomically. A stamp file records
# the archive checksum so a re-run is a no-op instead of a re-extract.
install_archive() {
    local id="$1" install_dir="$2" archive="$3" want="$4" strip_top="$5" sentinel="$6"
    local dest="${TOOLS_DIR}/${install_dir}"
    local stamp="${dest}/.cometgui-toolchain-stamp"

    if [ -f "${stamp}" ] && [ -e "${dest}/${sentinel}" ] \
       && [ "$(cat -- "${stamp}")" = "${want}" ]; then
        log "${id}: already installed at ${dest} (stamp matches)"
        return 0
    fi

    log "${id}: unpacking into ${dest}"
    rm -rf -- "${dest}" "${dest}.tmp"
    mkdir -p -- "${dest}.tmp"
    tar -xzf "${archive}" -C "${dest}.tmp" \
        || fail "${id}: extraction of ${archive} failed"
    [ -d "${dest}.tmp/${strip_top}" ] \
        || fail "${id}: expected top-level directory '${strip_top}' inside ${archive}"
    mv -- "${dest}.tmp/${strip_top}" "${dest}"
    rmdir -- "${dest}.tmp" 2>/dev/null || rm -rf -- "${dest}.tmp"
    [ -e "${dest}/${sentinel}" ] \
        || fail "${id}: sentinel ${dest}/${sentinel} missing after extraction"
    printf '%s\n' "${want}" > "${stamp}"
    log "${id}: installed at ${dest}"
}

main() {
    log "root       ${ROOT}"
    log "tools      ${TOOLS_DIR}"
    log "cache      ${CACHE_DIR}"
    mkdir -p -- "${TOOLS_DIR}" "${CACHE_DIR}"

    command -v curl     >/dev/null || fail "curl is required and was not found"
    command -v tar      >/dev/null || fail "tar is required and was not found"
    command -v sha256sum>/dev/null || fail "sha256sum is required and was not found"

    while IFS=$'\t' read -r id install_dir archive url sha256 strip_top sentinel; do
        [ -n "${id}" ] || continue
        fetch_verified   "${id}" "${url}" "${CACHE_DIR}/${archive}" "${sha256}"
        install_archive  "${id}" "${install_dir}" "${CACHE_DIR}/${archive}" \
                         "${sha256}" "${strip_top}" "${sentinel}"
    done <<< "${PINS}"

    local java_home maven_home java_dir maven_dir
    java_dir="$(printf '%s\n' "${PINS}" | awk -F'\t' '$1=="jdk"{print $2}')"
    maven_dir="$(printf '%s\n' "${PINS}" | awk -F'\t' '$1=="maven"{print $2}')"
    java_home="${TOOLS_DIR}/${java_dir}"
    maven_home="${TOOLS_DIR}/${maven_dir}"

    # ---- generated environment file (tools/ is gitignored; regenerated here)
    #
    # The pinned directory NAMES are baked in -- that is the point of the pins.
    # The PATH TO them is resolved when the file is sourced, from the file's own
    # location, so that relocating the checkout does not strand the toolchain.
    # It did: the checkout moved off /workspace on 2026-08-31 and every Maven
    # invocation then failed with nothing but "The JAVA_HOME environment
    # variable is not defined correctly", because scripts/build.sh skips this
    # bootstrap whenever tools/env.sh merely EXISTS (build.sh, "toolchain"
    # stage) and so never regenerated the stale absolute paths.
    cat > "${TOOLS_DIR}/env.sh" <<EOF
# Generated by scripts/feasibility/install-toolchain.sh -- do not edit.
# Usage:  . "\$(git rev-parse --show-toplevel)/tools/env.sh"
#
# Pinned directory names, path resolved at source time from this file's own
# location: moving the checkout must not strand the toolchain.
__cometgui_tools="\$(cd -- "\$(dirname -- "\${BASH_SOURCE[0]:-\$0}")" && pwd)"
export JAVA_HOME="\${__cometgui_tools}/${java_dir}"
export MAVEN_HOME="\${__cometgui_tools}/${maven_dir}"
export PATH="\${JAVA_HOME}/bin:\${MAVEN_HOME}/bin:\${PATH}"
unset __cometgui_tools
EOF
    log "wrote ${TOOLS_DIR}/env.sh"

    # ---------------------------------------------------------------- checks
    # Exit code 0 proves nothing: assert the installed tools report the
    # versions we pinned, and that JavaFX really is in this runtime.
    log "verifying installation"
    export JAVA_HOME="${java_home}"
    export PATH="${java_home}/bin:${maven_home}/bin:${PATH}"

    local jv
    jv="$("${java_home}/bin/java" -XshowSettings:properties -version 2>&1 \
          | awk -F'= ' '/java\.version = /{print $2; exit}')"
    [ "${jv}" = "${EXPECT_JAVA_VERSION}" ] \
        || fail "java.version is '${jv}', expected '${EXPECT_JAVA_VERSION}'"
    log "java.version = ${jv}"

    for t in javac jlink jpackage jar; do
        [ -x "${java_home}/bin/${t}" ] || fail "missing ${java_home}/bin/${t}"
    done
    "${java_home}/bin/jpackage" --version >/dev/null \
        || fail "jpackage is present but did not run"
    log "jpackage --version = $("${java_home}/bin/jpackage" --version)"

    local modules
    modules="$("${java_home}/bin/java" --list-modules)"
    for m in ${EXPECT_JAVAFX_MODULES}; do
        printf '%s\n' "${modules}" | grep -q "^${m}@" \
            || fail "JavaFX module ${m} is not in the runtime image -- this JDK is not a JavaFX-bundling build"
        [ -f "${java_home}/jmods/${m}.jmod" ] \
            || fail "JavaFX jmod ${m}.jmod is missing -- jlink/jpackage could not include JavaFX"
    done
    log "JavaFX modules present in runtime image and jmods: ${EXPECT_JAVAFX_MODULES}"

    local mv
    mv="$("${maven_home}/bin/mvn" -v 2>/dev/null | awk '/^Apache Maven /{print $3; exit}')"
    [ "${mv}" = "${EXPECT_MAVEN_VERSION}" ] \
        || fail "Apache Maven version is '${mv}', expected '${EXPECT_MAVEN_VERSION}'"
    log "Apache Maven ${mv}"

    cat <<EOF

[toolchain] OK. Project-local toolchain installed and verified.

  JAVA_HOME  ${java_home}
  MAVEN_HOME ${maven_home}

  Put it on PATH for the current shell with:

    export JAVA_HOME="${java_home}"
    export PATH="\$JAVA_HOME/bin:${maven_home}/bin:\$PATH"

  or simply:

    . "${TOOLS_DIR}/env.sh"

EOF
}

main "$@"
