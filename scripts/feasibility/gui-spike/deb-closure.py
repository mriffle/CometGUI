#!/usr/bin/env python3
"""Compute a Debian binary-package dependency closure, project-locally.

CometGUI -- Phase 00, work unit 7: GUI automation spike.

THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.  This exists only so that the
*headed* half of the GUI automation spike is reproducible: it is what produced
``headed-x11-closure.tsv``, the provenance manifest for the Xvfb + GTK3 tree
the headed run needs.  The product does not resolve Debian dependencies; it
extracts a small, named set of payloads (see work unit 3's ``extract_deb.py``).

The host has no ``apt``, and nothing may be installed on it, so the closure is
computed from the archive's own ``Packages`` index and the payloads are
extracted into ``tools/`` with ``extract_deb.py``.

Usage::

    python3 deb-closure.py --suite bookworm --arch amd64 \\
        xvfb x11-xkb-utils xkb-data libgtk-3-0 libxtst6 > closure.tsv

Output is one tab-separated row per package: name, version, URL, SHA-256, size.
The SHA-256 comes from the signed archive index, so re-running this is how the
pinned checksums in the manifest were obtained.

``--exclude-base`` (on by default) drops packages that are already present in
this project's Debian 12 host image, or that are only needed by ``dpkg``
maintainer scripts we never run.  A package wrongly excluded shows up
immediately as an unresolved ``ldd`` entry, so the list is checked by use
rather than by trust.
"""

from __future__ import annotations

import argparse
import lzma
import sys
import urllib.request

# Present in the host image already, or irrelevant because no maintainer
# script is ever executed.  Verified by ldd against the extracted tree.
BASE = set("""
libc6 libgcc-s1 libstdc++6 zlib1g libbz2-1.0 libexpat1 libselinux1 libpcre2-8-0
libmount1 libblkid1 libuuid1 libffi8 libbrotli1 libzstd1 liblzma5 libcrypt1
libtinfo6 libgpm2 debconf ucf adduser perl-base perl dpkg base-files
init-system-helpers sensible-utils libdebconfclient0 x11-common dbus dbus-bin
lsb-base passwd systemd fontconfig libpam0g libaudit1 libcap-ng0 libgcrypt20
libgpg-error0 libsystemd0 libudev1 libapparmor1 libdb5.3 libattr1 libacl1
libtirpc3 libnsl2 libkeyutils1 libcom-err2 libaom3 libnss-systemd
""".split())


def load_index(suite: str, arch: str, component: str = "main") -> tuple[dict, dict]:
    url = (f"https://deb.debian.org/debian/dists/{suite}/{component}/"
           f"binary-{arch}/Packages.xz")
    sys.stderr.write(f"fetching {url}\n")
    with urllib.request.urlopen(url) as fh:
        raw = lzma.decompress(fh.read()).decode("utf-8", "replace")

    packages: dict[str, dict] = {}
    provides: dict[str, str] = {}
    for block in raw.split("\n\n"):
        if not block.strip():
            continue
        fields: dict[str, str] = {}
        for line in block.split("\n"):
            if line[:1] in (" ", "\t") or ":" not in line:
                continue
            key, _, value = line.partition(":")
            fields[key.strip()] = value.strip()
        name = fields.get("Package")
        if not name or name in packages:
            continue
        packages[name] = fields
        for provided in fields.get("Provides", "").split(","):
            provided = provided.strip().split(" ")[0]
            if provided:
                provides.setdefault(provided, name)
    return packages, provides


def depends_of(fields: dict) -> list[str]:
    out = []
    for dep in fields.get("Depends", "").split(","):
        dep = dep.strip()
        if not dep:
            continue
        # "a (>= 1) | b" -- take the first alternative, as apt would.
        first = dep.split("|")[0].strip()
        out.append(first.split(" ")[0].split(":")[0])
    return out


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--suite", default="bookworm")
    ap.add_argument("--arch", default="amd64")
    ap.add_argument("--component", default="main")
    ap.add_argument("--no-exclude-base", action="store_true",
                    help="do not drop packages assumed present in the host image")
    ap.add_argument("roots", nargs="+")
    args = ap.parse_args(argv)

    packages, provides = load_index(args.suite, args.arch, args.component)
    skip = set() if args.no_exclude_base else BASE

    seen: set[str] = set()
    unresolved: set[str] = set()
    stack = list(args.roots)
    while stack:
        name = stack.pop()
        if name in seen or name in skip:
            continue
        real = name if name in packages else provides.get(name)
        if real is None:
            unresolved.add(name)
            continue
        if real in seen or real in skip:
            continue
        seen.add(real)
        stack.extend(depends_of(packages[real]))

    for name in sorted(seen):
        f = packages[name]
        print("\t".join([name, f["Version"],
                         "https://deb.debian.org/debian/" + f["Filename"],
                         f["SHA256"], f["Size"]]))

    if unresolved:
        sys.stderr.write("unresolved (virtual or excluded): %s\n"
                         % " ".join(sorted(unresolved)))
    sys.stderr.write("%d package(s) in the closure\n" % len(seen))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
