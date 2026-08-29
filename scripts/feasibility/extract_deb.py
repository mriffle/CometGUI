#!/usr/bin/env python3
"""Pure-Python extractor for Debian ``.deb`` packages.

Phase 00, work unit 3.  This host has no ``dpkg``, no ``bsdtar``, no ``7z``
and no ``file``, and nothing may be installed on it.  More importantly the
shipping product performs this extraction inside the JVM, so the proof that
matters is a self-contained implementation of the container formats rather
than a shell-out.  Only the two decompressors are borrowed from the standard
library (``gzip``/``zlib`` and ``lzma``), which have direct JDK equivalents
(``java.util.zip.GZIPInputStream``, and XZ via a library) -- the ``ar`` and
``tar`` container parsing is implemented here.

A ``.deb`` is a Unix ``ar`` archive holding, in order::

    debian-binary       the format version, "2.0\\n"
    control.tar.<c>     maintainer scripts and metadata
    data.tar.<c>        the payload: the installed file tree

Nothing here requires root: the payload is a plain relative file tree that is
unpacked wherever it is told to go.

Usage::

    python3 extract_deb.py --list PKG.deb
    python3 extract_deb.py --dest DIR PKG.deb
    python3 extract_deb.py --dest DIR --strip-usr PKG.deb
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import lzma
import os
import stat
import sys
import bz2

AR_MAGIC = b"!<arch>\n"


# --------------------------------------------------------------------------
# ar
# --------------------------------------------------------------------------

def read_ar(data: bytes):
    """Yield ``(name, member_bytes)`` for every member of an ``ar`` archive.

    Handles the BSD/GNU short-name form used by ``.deb`` (names terminated by
    ``/``) and the GNU long-name table (``//`` plus ``/offset`` references),
    which ``.deb`` does not use but which costs six lines to support.
    """
    if data[:8] != AR_MAGIC:
        raise ValueError("not an ar archive: magic is %r, expected %r"
                         % (data[:8], AR_MAGIC))
    pos = 8
    longnames = b""
    while pos + 60 <= len(data):
        header = data[pos:pos + 60]
        if header[58:60] != b"`\n":
            raise ValueError("bad ar member header magic at offset %d: %r"
                             % (pos, header[58:60]))
        raw_name = header[0:16].decode("ascii", "replace")
        size_field = header[48:58].decode("ascii", "replace").strip()
        if not size_field.isdigit():
            raise ValueError("bad ar member size at offset %d: %r"
                             % (pos, size_field))
        size = int(size_field)
        body = data[pos + 60:pos + 60 + size]
        if len(body) != size:
            raise ValueError("truncated ar member at offset %d: wanted %d "
                             "bytes, got %d" % (pos, size, len(body)))
        name = raw_name.strip()
        if name == "//":
            longnames = body
        elif name.startswith("/") and name[1:].strip().isdigit():
            off = int(name[1:].strip())
            end = longnames.find(b"/\n", off)
            name = longnames[off:end].decode("ascii", "replace")
        if name.endswith("/"):
            name = name[:-1]
        if name not in ("//", "/"):
            yield name, body
        pos += 60 + size
        if size % 2:            # members are padded to an even offset
            pos += 1


# --------------------------------------------------------------------------
# decompression
# --------------------------------------------------------------------------

def decompress(name: str, blob: bytes) -> bytes:
    if name.endswith(".gz"):
        return gzip.decompress(blob)
    if name.endswith(".xz") or name.endswith(".lzma"):
        return lzma.decompress(blob)
    if name.endswith(".bz2"):
        return bz2.decompress(blob)
    if name.endswith(".zst"):
        raise ValueError("zstd-compressed member %r: the standard library has "
                         "no zstd decoder on Python 3.11; the product will "
                         "need one for zstd .debs" % name)
    if name.endswith(".tar"):
        return blob
    raise ValueError("unknown compression for member %r" % name)


# --------------------------------------------------------------------------
# tar (ustar / GNU)
# --------------------------------------------------------------------------

class TarEntry:
    __slots__ = ("name", "mode", "size", "typeflag", "linkname", "mtime",
                 "data")

    def __init__(self, name, mode, size, typeflag, linkname, mtime, data):
        self.name = name
        self.mode = mode
        self.size = size
        self.typeflag = typeflag
        self.linkname = linkname
        self.mtime = mtime
        self.data = data

    @property
    def is_file(self) -> bool:
        return self.typeflag in ("0", "\0", "")

    @property
    def is_dir(self) -> bool:
        return self.typeflag == "5"

    @property
    def is_symlink(self) -> bool:
        return self.typeflag == "2"

    @property
    def is_hardlink(self) -> bool:
        return self.typeflag == "1"


def _octal(field: bytes) -> int:
    text = field.split(b"\0", 1)[0].strip()
    return int(text, 8) if text else 0


def read_tar(data: bytes):
    """Yield :class:`TarEntry` for every entry of an uncompressed tar stream."""
    pos = 0
    pending_long_name = None
    pending_long_link = None
    while pos + 512 <= len(data):
        header = data[pos:pos + 512]
        if header == b"\0" * 512:               # end-of-archive marker
            break
        checksum = _octal(header[148:156])
        probe = header[:148] + b" " * 8 + header[156:]
        unsigned = sum(probe)
        signed = sum(b - 256 if b > 127 else b for b in probe)
        if checksum not in (unsigned, signed):
            raise ValueError("tar header checksum mismatch at offset %d "
                             "(stored %d, computed %d)" % (pos, checksum, unsigned))
        name = header[0:100].split(b"\0", 1)[0].decode("utf-8", "replace")
        mode = _octal(header[100:108])
        size = _octal(header[124:136])
        mtime = _octal(header[136:148])
        typeflag = header[156:157].decode("ascii", "replace")
        linkname = header[157:257].split(b"\0", 1)[0].decode("utf-8", "replace")
        prefix = header[345:500].split(b"\0", 1)[0].decode("utf-8", "replace")
        if prefix:
            name = prefix + "/" + name
        body = data[pos + 512:pos + 512 + size]
        pos += 512 + ((size + 511) // 512) * 512
        if typeflag == "L":                     # GNU long name
            pending_long_name = body.split(b"\0", 1)[0].decode("utf-8", "replace")
            continue
        if typeflag == "K":                     # GNU long link target
            pending_long_link = body.split(b"\0", 1)[0].decode("utf-8", "replace")
            continue
        if typeflag in ("x", "g"):              # pax headers: not needed here
            continue
        if pending_long_name is not None:
            name, pending_long_name = pending_long_name, None
        if pending_long_link is not None:
            linkname, pending_long_link = pending_long_link, None
        yield TarEntry(name, mode, size, typeflag, linkname, mtime, body)


# --------------------------------------------------------------------------
# safe unpacking
# --------------------------------------------------------------------------

def safe_join(dest: str, name: str) -> str:
    """Resolve ``name`` under ``dest``, refusing escapes and absolute paths."""
    clean = name.lstrip("/")
    while clean.startswith("./"):
        clean = clean[2:]
    target = os.path.normpath(os.path.join(dest, clean))
    root = os.path.normpath(dest)
    if target != root and not target.startswith(root + os.sep):
        raise ValueError("archive member %r escapes the destination" % name)
    return target


def unpack(entries, dest: str, strip_prefix: str | None = None) -> list[dict]:
    written = []
    for e in entries:
        name = e.name
        if strip_prefix:
            if not name.startswith(strip_prefix):
                continue
            name = name[len(strip_prefix):]
            if not name.strip("./"):
                continue
        target = safe_join(dest, name)
        if e.is_dir:
            os.makedirs(target, exist_ok=True)
            continue
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if e.is_symlink:
            if os.path.lexists(target):
                os.unlink(target)
            os.symlink(e.linkname, target)
            written.append({"path": target, "kind": "symlink",
                            "target": e.linkname})
            continue
        if e.is_hardlink:
            source = safe_join(dest, e.linkname)
            if os.path.exists(source):
                if os.path.lexists(target):
                    os.unlink(target)
                os.link(source, target)
                written.append({"path": target, "kind": "hardlink",
                                "target": e.linkname})
            continue
        if not e.is_file:
            continue
        with open(target, "wb") as fh:
            fh.write(e.data)
        mode = e.mode & 0o7777
        os.chmod(target, mode if mode else 0o644)
        written.append({"path": target, "kind": "file", "size": e.size,
                        "mode": oct(mode),
                        "sha256": hashlib.sha256(e.data).hexdigest()})
    return written


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("deb")
    ap.add_argument("--dest", help="directory to unpack the data payload into")
    ap.add_argument("--list", action="store_true",
                    help="list the payload without writing anything")
    ap.add_argument("--strip-prefix", default=None,
                    help="drop this leading path from every member, e.g. './usr/'")
    ap.add_argument("--control", action="store_true",
                    help="also print the package control file")
    args = ap.parse_args(argv)

    blob = open(args.deb, "rb").read()
    print("archive : %s" % os.path.abspath(args.deb))
    print("bytes   : %d" % len(blob))
    print("sha256  : %s" % hashlib.sha256(blob).hexdigest())

    members = list(read_ar(blob))
    print("ar members:")
    for name, body in members:
        print("   %-28s %10d bytes" % (name, len(body)))

    data_member = None
    control_member = None
    for name, body in members:
        if name.startswith("data.tar"):
            data_member = (name, body)
        elif name.startswith("control.tar"):
            control_member = (name, body)
    if data_member is None:
        print("no data.tar member: not a usable .deb", file=sys.stderr)
        return 2

    if args.control and control_member:
        ctrl = list(read_tar(decompress(control_member[0], control_member[1])))
        for e in ctrl:
            if os.path.basename(e.name) == "control":
                print("\n--- control ---")
                print(e.data.decode("utf-8", "replace").rstrip())
                print("--- end control ---\n")

    tar_bytes = decompress(data_member[0], data_member[1])
    print("payload : %s -> %d bytes of tar" % (data_member[0], len(tar_bytes)))

    entries = list(read_tar(tar_bytes))
    if args.list or not args.dest:
        for e in entries:
            kind = ("d" if e.is_dir else "l" if e.is_symlink else
                    "h" if e.is_hardlink else "-")
            extra = (" -> " + e.linkname) if (e.is_symlink or e.is_hardlink) else ""
            print("   %s %-6s %10d  %s%s"
                  % (kind, oct(e.mode & 0o7777), e.size, e.name, extra))
        print("entries : %d" % len(entries))
        if not args.dest:
            return 0

    os.makedirs(args.dest, exist_ok=True)
    written = unpack(entries, args.dest, args.strip_prefix)
    print("wrote   : %d files into %s" % (len(written), os.path.abspath(args.dest)))
    for w in written:
        if w["kind"] == "file" and int(w["mode"], 8) & stat.S_IXUSR:
            print("   exec  %s  sha256=%s" % (w["path"], w["sha256"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
