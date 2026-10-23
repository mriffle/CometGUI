#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# install-archive.sh -- install the packaged Linux archive the way a user does:
# check it against its SHA-256 file, extract it into a fresh directory OUTSIDE
# the repository, and make the installed tree read-only, so that anything that
# writes into its own install directory at run time fails loudly instead of
# quietly working on a developer's machine (Phase 14, unit 5; P14-10).
#
#   bash scripts/package/install-archive.sh ARCHIVE.tar.gz INSTALL_DIR
#   bash scripts/package/install-archive.sh --uninstall INSTALL_DIR
#   bash scripts/package/install-archive.sh --help
#
# INSTALL_DIR must not exist yet (an install is fresh), and must not be inside
# the repository (the build tree is exactly where a path that only works in
# development would go unnoticed).  Its parent must exist.  The launcher is
# then INSTALL_DIR/CometGUI/bin/CometGUI.
#
# --leave-writable, before the archive, skips the read-only step.  It exists
# only so that scripts/package/smoke-installed.sh --self-test can show its
# read-only check failing; an install for testing never uses it.
#
# --uninstall removes a tree this script installed (it restores write
# permission first, which a plain rm -rf of a read-only tree would need).
#
# Exit 0 means: the checksum matched, the launcher is in place, no file or
# directory in the tree is writable by anyone, and a probe write into the tree
# was refused.  Refuses to run as root, for whom
# permission bits refuse nothing.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"

die() { printf 'install-archive.sh: FAILED: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '7,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

LEAVE_WRITABLE=0
UNINSTALL=0
ARGS=()
for arg in "$@"; do
    case "${arg}" in
        --help|-h) usage; exit 0 ;;
        --leave-writable) LEAVE_WRITABLE=1 ;;
        --uninstall) UNINSTALL=1 ;;
        --*) printf 'install-archive.sh: unknown option %s\n' "${arg}" >&2; usage >&2; exit 2 ;;
        *) ARGS+=("${arg}") ;;
    esac
done

if [ "${UNINSTALL}" -eq 1 ]; then
    [ "${#ARGS[@]}" -eq 1 ] || { usage >&2; exit 2; }
    target="${ARGS[0]}"
    [ -d "${target}" ] || die "${target} is not a directory"
    [ -f "${target}/.cometgui-installed" ] || die "${target} was not installed by this script (no .cometgui-installed marker); refusing to delete it"
    chmod -R u+w -- "${target}"
    rm -rf -- "${target}"
    [ ! -e "${target}" ] || die "${target} still exists after removal"
    echo "install-archive.sh: removed ${target}"
    exit 0
fi

[ "${#ARGS[@]}" -eq 2 ] || { usage >&2; exit 2; }
ARCHIVE="${ARGS[0]}"
INSTALL="${ARGS[1]}"

[ "$(id -u)" -ne 0 ] || die "running as root: a read-only tree refuses root nothing, so the install could not prove the application never writes into it"
[ -f "${ARCHIVE}" ] || die "no archive at ${ARCHIVE}"
[ -f "${ARCHIVE}.sha256" ] || die "no checksum file ${ARCHIVE}.sha256 beside the archive"
[ ! -e "${INSTALL}" ] || die "${INSTALL} already exists; an install goes into a fresh directory"
PARENT="$(dirname -- "${INSTALL}")"
[ -d "${PARENT}" ] || die "the parent directory ${PARENT} does not exist"
PARENT_REAL="$(cd -- "${PARENT}" && pwd -P)"
INSTALL="${PARENT_REAL}/$(basename -- "${INSTALL}")"
case "${INSTALL}/" in
    "${ROOT}/"*) die "${INSTALL} is inside the repository ${ROOT}; install outside the build tree" ;;
esac

# The checksum file names the archive by its base name, as the build writes it.
(cd -- "$(dirname -- "${ARCHIVE}")" && sha256sum -c --quiet -- "$(basename -- "${ARCHIVE}").sha256") \
    || die "${ARCHIVE} does not match ${ARCHIVE}.sha256"

mkdir -- "${INSTALL}"
tar -xzf "${ARCHIVE}" -C "${INSTALL}" || die "extraction into ${INSTALL} failed"
APPDIR="${INSTALL}/CometGUI"
[ -x "${APPDIR}/bin/CometGUI" ] || die "the archive did not install a launcher at ${APPDIR}/bin/CometGUI"
# Whether the runtime is complete (bin/java, JavaFX) is not judged here: an
# install is what a user's tar does, and scripts/package/smoke-installed.sh
# grades what it installed.
{
    echo "archive=$(cd -- "$(dirname -- "${ARCHIVE}")" && pwd -P)/$(basename -- "${ARCHIVE}")"
    echo "sha256=$(cut -d' ' -f1 "${ARCHIVE}.sha256")"
    echo "installed=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "${INSTALL}/.cometgui-installed"

if [ "${LEAVE_WRITABLE}" -eq 1 ]; then
    echo "install-archive.sh: WARNING: --leave-writable: ${INSTALL} is NOT read-only (self-test use only)" >&2
    echo "install-archive.sh: installed ${APPDIR}"
    exit 0
fi

chmod -R a-w -- "${INSTALL}"
writable="$(find "${INSTALL}" \( -perm -u=w -o -perm -g=w -o -perm -o=w \) ! -type l -print | sed -n "1,5p")"
[ -z "${writable}" ] || die "entries are still writable after chmod: ${writable}"
probe="${APPDIR}/lib/app/.write-probe"
if (: > "${probe}") 2>/dev/null; then
    rm -f -- "${probe}" 2>/dev/null || true
    die "a probe write into ${APPDIR}/lib/app succeeded; the tree is not read-only"
fi
echo "install-archive.sh: installed ${APPDIR} (read-only; launcher ${APPDIR}/bin/CometGUI)"
