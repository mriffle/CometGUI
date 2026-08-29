#!/usr/bin/env bash
#
# CometGUI -- fetch the project-local font stack the headless JavaFX tests need.
#
#   bash scripts/fetch-fontstack.sh              fetch, verify and extract
#   bash scripts/fetch-fontstack.sh --verify     verify what is already there
#
# WHY THIS EXISTS.  A JavaFX Scene containing any Control initialises CSS on
# its first Node, which calls Font.getDefault().  With no font stack that call
# fails with "fontFactory is null" and every GUI test dies before its first
# assertion.  This host has no libfreetype, no fontconfig, no pango and no font
# files at all, and nothing may be installed on it (no apt, no sudo), so the
# stack is fetched from the Debian 12 archive and extracted project-locally
# into tools/, which is gitignored.  Phase 00 unit 7 established the exact
# package set; scripts/feasibility/javafx-smoke.sh is where it was first proved.
#
# Every file is pinned by SHA-256 and re-verified on every run: a download that
# silently returns an error page, a proxy, or a different build of the same
# version is rejected rather than extracted.
#
# This script writes only under tools/fontstack-bookworm-20260829/.  It is
# idempotent: a second run verifies and exits without re-downloading.

set -Eeuo pipefail

readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT

readonly FONTSTACK_DIR="${ROOT}/tools/fontstack-bookworm-20260829"
readonly DEB_DIR="${FONTSTACK_DIR}/debs"
readonly SYSROOT="${FONTSTACK_DIR}/root"
readonly DEB_BASE="https://deb.debian.org/debian/pool/main"
readonly EXTRACTOR="${ROOT}/scripts/feasibility/extract_deb.py"

# The pinned package set: name|pool path|sha256.  Debian 12 "bookworm", amd64,
# against this host's glibc 2.36.
#
# THIS TABLE IS A DELIBERATE COPY of the one in
# scripts/feasibility/javafx-smoke.sh, which is Phase 00 evidence and must not
# be edited by later phases.  Two copies of a checksum table is a drift risk, so
# check_table_matches_phase00() below compares them line by line on every run
# and fails if they have diverged.
readonly DEBS=(
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

# The files that must exist afterwards.  Extraction exiting 0 proves nothing:
# an empty payload, a changed Debian layout or a partial extraction all exit 0.
readonly REQUIRED=(
"usr/lib/x86_64-linux-gnu/libfreetype.so.6"
"usr/lib/x86_64-linux-gnu/libfontconfig.so.1"
"usr/lib/x86_64-linux-gnu/libpangoft2-1.0.so.0"
"usr/lib/x86_64-linux-gnu/libharfbuzz.so.0"
"usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
"etc/fonts/fonts.conf"
)

die() { printf '\n%s: FATAL: %s\n' "${SCRIPT_NAME}" "$*" >&2; exit 1; }

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- fetch the project-local font stack for headless JavaFX tests.

Usage:
  bash scripts/${SCRIPT_NAME} [--verify] [-h|--help]

  --verify   check the extracted stack and the pinned checksums without
             downloading anything.  Exits non-zero if anything is missing.

Everything lands in tools/fontstack-bookworm-20260829/ (gitignored).  Nothing
is installed on the host.
USAGE
}

# Fail if this table and the Phase 00 one have drifted apart.
check_table_matches_phase00() {
    local phase00="${ROOT}/scripts/feasibility/javafx-smoke.sh"
    if [ ! -f "${phase00}" ]; then
        echo "note: ${phase00#"${ROOT}/"} is absent; cannot cross-check the pinned table."
        return 0
    fi
    local spec missing=0
    for spec in "${DEBS[@]}"; do
        grep -qF -- "\"${spec}\"" "${phase00}" || {
            printf '   DRIFT   %s is not the entry Phase 00 pinned\n' "${spec%%|*}"
            missing=$((missing + 1))
        }
    done
    [ "${missing}" -eq 0 ] \
        || die "${missing} pinned package(s) differ from scripts/feasibility/javafx-smoke.sh. One of the two tables has been changed; reconcile them deliberately rather than letting the build fetch something Phase 00 never verified."
    printf '   ok       all %d pinned package(s) match the Phase 00 table\n' "${#DEBS[@]}"
}

verify_sysroot() {
    local rel missing=0
    for rel in "${REQUIRED[@]}"; do
        if [ -s "${SYSROOT}/${rel}" ]; then
            printf '   ok       %-52s %8s bytes\n' "${rel}" "$(stat -Lc %s "${SYSROOT}/${rel}")"
        else
            printf '   MISSING  %s\n' "${rel}"
            missing=$((missing + 1))
        fi
    done
    return "${missing}"
}

fetch_verify() {
    local url="$1" dest="$2" want="$3" got
    if [ ! -f "${dest}" ]; then
        printf '   fetching %s\n' "$(basename -- "${dest}")"
        curl -fsSL --retry 3 -o "${dest}.part" "${url}" || die "download failed: ${url}"
        mv -- "${dest}.part" "${dest}"
    fi
    got="$(sha256sum -- "${dest}" | cut -d' ' -f1)"
    [ "${got}" = "${want}" ] \
        || die "SHA-256 mismatch for $(basename -- "${dest}"): got ${got}, pinned ${want}"
}

main() {
    local verify_only=0
    case "${1:-}" in
        --verify) verify_only=1 ;;
        -h|--help) usage; exit 0 ;;
        "") ;;
        *) usage >&2; die "unknown option: $1" ;;
    esac

    echo "-- the pinned package table"
    check_table_matches_phase00

    if [ "${verify_only}" -eq 1 ]; then
        echo "-- extracted font stack (${SYSROOT#"${ROOT}/"})"
        verify_sysroot || die "the extracted font stack is incomplete; run bash scripts/${SCRIPT_NAME} without --verify."
        echo "OK: the font stack is present and complete."
        return 0
    fi

    if verify_sysroot >/dev/null 2>&1; then
        echo "-- extracted font stack already present; verifying it rather than refetching"
        verify_sysroot || die "the sysroot became incomplete between two checks."
        echo "OK: the font stack is present and complete."
        return 0
    fi

    [ -f "${EXTRACTOR}" ] || die "${EXTRACTOR#"${ROOT}/"} is missing; it is the .deb extractor this script reuses."
    command -v curl >/dev/null || die "curl is not available."
    command -v python3 >/dev/null || die "python3 is not available."

    mkdir -p -- "${DEB_DIR}" "${SYSROOT}"

    echo "-- download and checksum-verify ${#DEBS[@]} Debian package(s)"
    local spec name pool sha
    for spec in "${DEBS[@]}"; do
        IFS='|' read -r name pool sha <<< "${spec}"
        fetch_verify "${DEB_BASE}/${pool}/${name}" "${DEB_DIR}/${name}" "${sha}"
    done
    printf '   ok       %d package(s) verified against their pinned SHA-256\n' "${#DEBS[@]}"

    echo "-- extract into ${SYSROOT#"${ROOT}/"}"
    for spec in "${DEBS[@]}"; do
        IFS='|' read -r name pool sha <<< "${spec}"
        python3 "${EXTRACTOR}" --dest "${SYSROOT}" "${DEB_DIR}/${name}" >/dev/null \
            || die "extraction failed for ${name}"
    done

    echo "-- the files the JavaFX font subsystem actually opens"
    verify_sysroot || die "extraction exited 0 but the font stack is incomplete. Exit code 0 would have lied."
    echo "OK: font stack fetched, verified and extracted; nothing was installed on the host."
}

main "$@"
